---
name: Razorpay subscription plan IDs
description: Separates Razorpay checkout keys from subscription plan identifiers and preserves useful API errors.
---

Razorpay subscription creation requires a plan identifier beginning with `plan_`; the checkout key identifier beginning with `rzp_` cannot be used as `plan_id`.

**Why:** Using the key ID makes Razorpay return a non-success response, and reading an HTTP error through `HttpURLConnection.inputStream` can surface the API URL instead of the real error.

**How to apply:** Keep the plan ID server-side in the Render environment, and read `errorStream` for non-2xx Android API responses so configuration failures remain actionable.

Razorpay subscription creation also requires an explicit positive `total_count` unless perpetual subscriptions are enabled for the account.

**Why:** Accounts without perpetual subscription access reject requests that omit the billing-cycle count.

**How to apply:** Send a configured number of billing cycles, defaulting to 12 for a monthly one-year subscription unless the product's plan requires another duration.

External Google and Razorpay calls should have bounded server-side timeouts, and the Android billing request should recover from a stalled API call instead of leaving both access buttons disabled indefinitely.

**Why:** A slow or unavailable upstream service otherwise leaves the access gate stuck on its loading message with no actionable feedback.

**How to apply:** Keep the server and client timeout windows finite and return a retryable error to the user when they expire.