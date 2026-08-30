# ExamTube customization

This workspace contains a customized Android source fork under `newpipe-source/`.
The fork keeps the upstream NewPipe GPL license and attribution files intact.

## Current app identity

- App name: `ExamTube`
- Android application ID: `app.examtube.com`
- Source repository: <https://github.com/TeamNewPipe/NewPipe.git>
- Temporary icon: an ExamTube “E + play” vector; replace it with the final square logo when available.

The Android package declarations are intentionally unchanged for this first pass. The
published identity is controlled by the application ID, which avoids a risky global
package migration across the upstream source while keeping the app install identity
separate from NewPipe.

## Access flow

1. Android launches `AccessGateActivity` after the splash.
2. The user signs in with Google.
3. The server verifies the Google ID token.
4. The server creates a Razorpay subscription for the configured plan.
5. Razorpay Checkout opens in the Android app.
6. The server verifies the Razorpay signature and confirms the subscription belongs to the
   signed-in Google account.
7. Only then does the app open the existing NewPipe `MainActivity`.

`MainActivity` also checks the server-verified entitlement cache, so direct activity
launches do not skip the gate. On normal relaunch, the gate re-checks the subscription
status with Razorpay before entering the main screen. The cache is only written after
successful server verification; there is no “unlock” debug toggle.

## Required configuration

### Android build properties

Merge the keys from `newpipe-source/gradle.properties.example` into
`newpipe-source/gradle.properties` and set them, or provide
`GOOGLE_WEB_CLIENT_ID` as a shared project environment variable:

```properties
examtubeGoogleWebClientId=YOUR_GOOGLE_WEB_CLIENT_ID
examtubeRazorpayKeyId=rzp_test_or_live_key_id
examtubeRazorpayPlanId=plan_created_in_razorpay
examtubeBillingBaseUrl=https://your-api-host
```

The Android build reads `GOOGLE_WEB_CLIENT_ID` automatically when the Gradle
property is not present.

## Google Android OAuth certificate

Google’s Android OAuth client requires both the application ID and the SHA-1
fingerprint of the certificate that signs the APK. The web client ID used by
`requestIdToken` is a separate OAuth client.

This source does not contain a signing keystore. Generate the fingerprint on
the machine that builds ExamTube:

### Development/debug build

The default debug application ID is the final ID `app.examtube.com`:

```bash
keytool -list -v \
  -alias androiddebugkey \
  -keystore "$HOME/.android/debug.keystore" \
  -storepass android \
  -keypass android | grep -E 'SHA1:|SHA-1:'
```

Create an Android OAuth client in Google Cloud with:

- Package name: `app.examtube.com`
- SHA-1 certificate: `22:48:A2:47:7F:E7:4C:AC:5E:A5:29:58:4C:D0:DF:E1:FD:AC:E2:09`

This fingerprint belongs to the new debug certificate generated for the current
workspace. Build the debug APK in this same workspace so the certificate matches.

### Release build

For a release APK, use the keystore used by the release signing process:

```bash
keytool -list -v \
  -keystore /path/to/examtube-release.jks \
  -alias YOUR_RELEASE_ALIAS | grep -E 'SHA1:|SHA-1:'
```

If you use the `continuous` build type, its default package is
`app.examtube.com.continuous`, so register that package with the certificate
used by that build as well.

The Razorpay key ID is safe to use in the checkout client. The key secret must never be
added to Gradle or shipped in the APK.

### Billing server environment

The API server exposes:

- `POST /api/examtube/subscription`
- `POST /api/examtube/subscription/verify`
- `POST /api/examtube/subscription/access`

Set these server-side environment values:

```text
GOOGLE_WEB_CLIENT_ID
RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET
RAZORPAY_PLAN_ID
```

The current source includes the complete verification path but no production credentials
were added. Until these values are configured, the app intentionally shows a clear
configuration error and does not grant access.