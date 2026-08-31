---
name: Android SDK and dependency compatibility
description: Distinguishes unavailable compile SDK metadata from actual source compilation failures in the Android build.
---

When the installed Android platform is older than the compile SDK declared by resolved libraries, separate the AAR metadata gate from real source/compiler errors before changing dependency versions.

**Why:** Upstream dependency metadata can require a newer API level than the workspace SDK mirror provides, while the app source still compiles and packages successfully against the newest installed platform.

**How to apply:** Check installed platform packages first, run the source build with the metadata gate isolated when necessary, and revalidate normally after a newer SDK becomes available.

Generated Kotlin sources must use an explicit output directory and task inputs; in-memory Gradle text resources can be treated as `NO-SOURCE` after a clean build.

**Why:** A clean build otherwise loses the shared module's generated BuildConfig source even though incremental builds appear healthy.

**How to apply:** Make generated-source tasks write their declared output directly and wire the task provider into the Kotlin source set.