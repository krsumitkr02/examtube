# ExamTube

An Android customization fork of NewPipe with an ExamTube-branded Google sign-in and Razorpay subscription gate.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `newpipe-source/` — extracted Android/Kotlin Multiplatform NewPipe source.
- `newpipe-source/app/src/main/java/org/schabi/newpipe/examtube/` — ExamTube access gate, entitlement cache, and Razorpay client.
- `artifacts/api-server/src/routes/examtube.ts` — Google ID-token and Razorpay subscription verification endpoints.
- `EXAMTUBE_SETUP.md` — required Google/Razorpay configuration and the end-to-end access flow.

## Architecture decisions

- The Android application ID is `app.examtube.com`, while upstream source package declarations remain intact to avoid a risky global package migration.
- MainActivity refuses direct entry unless the entitlement cache was written after server-side subscription verification.
- Razorpay's secret is server-only; the Android app receives a subscription ID and sends payment data back for verification.
- The upstream NewPipe GPL license and attribution notices remain in the extracted source.

## Product

ExamTube retains NewPipe's media browsing, playback, download, and subscription features, but requires Google sign-in followed by an active Razorpay subscription before the main screen is available.

## User preferences

- Branding requested: ExamTube.
- Android application ID requested: `app.examtube.com`.
- Billing requested: Razorpay subscriptions.
- Entry flow requested: Google sign-in, then subscription purchase, then the existing main page after splash.

## Gotchas

- Production credentials must stay in server environment secrets and never be embedded in the APK.
- The Razorpay integration was scaffolded without live credentials, so checkout remains intentionally unavailable until the required values are configured.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
