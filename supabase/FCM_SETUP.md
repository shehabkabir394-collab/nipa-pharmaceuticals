# Repco product push notifications

The Android client is registered in Firebase as `com.repcopharma.app` (project `repcopharma-app`). The app requests notification permission, registers each signed-in Android device token in Supabase, and calls the Edge Function only after an Admin's product insert succeeds.

## One-time Supabase setup

Run `supabase/repco_push_notifications_setup.sql` in the existing Supabase project's SQL Editor. This creates the per-user token table and RLS policies. The existing `repco_products` table and product SQL are unchanged.

## Firebase server credentials

In Firebase Console, open Project settings > Service accounts and generate a private key for a service account allowed to send Firebase Cloud Messaging messages. Keep that private key out of GitHub and do not send it in chat.

In the Supabase Dashboard, open Edge Functions > Secrets and add:
- `FCM_PROJECT_ID`: `repcopharma-app`
- `FCM_CLIENT_EMAIL`: service account `client_email`
- `FCM_PRIVATE_KEY`: service account `private_key`

Supabase injects `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` into Edge Functions.

## Deploy the sender

From the repository root, deploy the function to the same Supabase project:

```sh
supabase functions deploy notify-new-product --project-ref YOUR_SUPABASE_PROJECT_REF
```

The function keeps JWT verification enabled. It checks that the caller is an active Admin, verifies the product exists, then sends FCM messages to registered devices. FCM credentials remain server-side.

## Install the new Android APK

The GitHub Actions workflow builds the debug APK after the Android FCM changes. Download the `repco-android-apk` artifact from the successful “Build Repco APK” workflow run and install it on each phone. Users must open the app and allow notifications once; the app then registers its token. New products added from Product List will notify registered phones.
