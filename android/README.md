# Repco Pharmaceutical Android app

This Android WebView app opens the live Repco website at https://repco.pages.dev. It needs an internet connection and uses the same Supabase-backed app as the website. Sign in on the Android app with the existing account.

## Build an installable APK

The GitHub Actions workflow at .github/workflows/build-repco-apk.yml builds a debug APK and uploads it as the `repco-android-apk` artifact. Open the repository's Actions tab, choose **Build Repco APK**, and run the workflow. Download and extract the artifact ZIP; install `app-debug.apk` on the Android device.

This debug APK is suitable for direct installation and testing. A separate release signing key is needed before publishing through Google Play.
