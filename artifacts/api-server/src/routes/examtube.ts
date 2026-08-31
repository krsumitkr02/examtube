import { createHmac, timingSafeEqual } from "node:crypto";
import { Router, type IRouter } from "express";

const router: IRouter = Router();
const EXTERNAL_REQUEST_TIMEOUT_MS = 15_000;

type GoogleUser = {
  sub: string;
  email?: string;
  email_verified?: string;
  aud?: string;
};

type RazorpaySubscription = {
  id?: string;
  status?: string;
  notes?: Record<string, string>;
};

class ConfigurationError extends Error {}

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new ConfigurationError(`${name} is not configured on the billing server.`);
  }
  return value;
}

async function readGoogleUser(idToken: unknown): Promise<GoogleUser> {
  if (typeof idToken !== "string" || idToken.length < 20) {
    throw new Error("A valid Google ID token is required.");
  }

  const response = await fetch(
    `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`,
    { signal: AbortSignal.timeout(EXTERNAL_REQUEST_TIMEOUT_MS) },
  );
  const payload = (await response.json()) as GoogleUser & { error_description?: string };
  if (!response.ok || !payload.sub || payload.aud !== requiredEnv("GOOGLE_WEB_CLIENT_ID")) {
    throw new Error(payload.error_description ?? "Google ID token verification failed.");
  }
  if (payload.email_verified !== "true") {
    throw new Error("The Google account email must be verified.");
  }
  return payload;
}

async function razorpayRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const keyId = requiredEnv("RAZORPAY_KEY_ID");
  const keySecret = requiredEnv("RAZORPAY_KEY_SECRET");
  const authorization = Buffer.from(`${keyId}:${keySecret}`).toString("base64");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Basic ${authorization}`);
  headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");

  const response = await fetch(`https://api.razorpay.com/v1${path}`, {
    ...init,
    headers,
    signal: AbortSignal.timeout(EXTERNAL_REQUEST_TIMEOUT_MS),
  });
  const text = await response.text();
  const payload = (
    text ? JSON.parse(text) : {}
  ) as T & { error?: { description?: string } };
  if (!response.ok) {
    throw new Error(payload.error?.description ?? `Razorpay returned HTTP ${response.status}.`);
  }
  return payload;
}

function signatureMatches(expected: string, provided: unknown): boolean {
  if (typeof provided !== "string") {
    return false;
  }
  const expectedBuffer = Buffer.from(expected, "utf8");
  const providedBuffer = Buffer.from(provided, "utf8");
  return (
    expectedBuffer.length === providedBuffer.length &&
    timingSafeEqual(expectedBuffer, providedBuffer)
  );
}

async function getOwnedSubscription(
  subscriptionId: unknown,
  user: GoogleUser,
): Promise<RazorpaySubscription> {
  if (typeof subscriptionId !== "string" || subscriptionId.length < 8) {
    throw new Error("A valid Razorpay subscription ID is required.");
  }
  const subscription = await razorpayRequest<RazorpaySubscription>(
    `/subscriptions/${encodeURIComponent(subscriptionId)}`,
  );
  if (subscription.notes?.google_sub !== user.sub) {
    throw new Error("This subscription does not belong to the signed-in Google account.");
  }
  return subscription;
}

router.post("/examtube/subscription", async (req, res) => {
  try {
    const user = await readGoogleUser(req.body?.idToken);
    const planId = requiredEnv("RAZORPAY_PLAN_ID");
    const totalCount = Number.parseInt(process.env.RAZORPAY_TOTAL_COUNT ?? "12", 10);
    if (!Number.isSafeInteger(totalCount) || totalCount < 1) {
      throw new ConfigurationError(
        "RAZORPAY_TOTAL_COUNT must be a positive whole number of billing cycles.",
      );
    }
    const subscription = await razorpayRequest<RazorpaySubscription>("/subscriptions", {
      method: "POST",
      body: JSON.stringify({
        plan_id: planId,
        total_count: totalCount,
        customer_notify: 1,
        notes: {
          app: "ExamTube",
          google_sub: user.sub,
        },
      }),
    });
    if (!subscription.id) {
      throw new Error("Razorpay did not return a subscription ID.");
    }
    res.json({
      subscriptionId: subscription.id,
      customerEmail: user.email ?? null,
    });
  } catch (error) {
    res.status(error instanceof ConfigurationError ? 503 : 400).json({
      error: error instanceof Error ? error.message : "Unable to create subscription.",
    });
  }
});

router.post("/examtube/subscription/verify", async (req, res) => {
  try {
    const user = await readGoogleUser(req.body?.idToken);
    const subscriptionId = req.body?.razorpaySubscriptionId;
    const paymentId = req.body?.razorpayPaymentId;
    const signature = req.body?.razorpaySignature;
    if (typeof paymentId !== "string" || typeof signature !== "string") {
      throw new Error("Razorpay payment verification data is incomplete.");
    }

    const secret = requiredEnv("RAZORPAY_KEY_SECRET");
    const expectedSignature = createHmac("sha256", secret)
      .update(`${paymentId}|${subscriptionId}`)
      .digest("hex");
    if (!signatureMatches(expectedSignature, signature)) {
      throw new Error("Razorpay signature verification failed.");
    }

    const subscription = await getOwnedSubscription(subscriptionId, user);
    const active = ["authenticated", "active"].includes(subscription.status ?? "");
    res.json({ active, subscriptionId });
  } catch (error) {
    res.status(error instanceof ConfigurationError ? 503 : 400).json({
      error: error instanceof Error ? error.message : "Unable to verify subscription.",
    });
  }
});

router.post("/examtube/subscription/access", async (req, res) => {
  try {
    const user = await readGoogleUser(req.body?.idToken);
    const subscription = await getOwnedSubscription(
      req.body?.razorpaySubscriptionId,
      user,
    );
    const active = ["authenticated", "active"].includes(subscription.status ?? "");
    res.json({
      active,
      subscriptionId: subscription.id ?? req.body?.razorpaySubscriptionId,
    });
  } catch (error) {
    res.status(error instanceof ConfigurationError ? 503 : 400).json({
      error: error instanceof Error ? error.message : "Unable to check subscription.",
    });
  }
});

export default router;