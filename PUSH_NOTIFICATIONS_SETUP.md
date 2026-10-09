# Phone push notifications (Web Push)

The Profile page now has **ফোনে notification চালু করুন**. Web Push needs the app served from HTTPS (GitHub Pages is HTTPS), and each phone/user must enable it once. For iPhone, install the site with **Share → Add to Home Screen** first (iOS/iPadOS 16.4+).

## 1. Create the subscriptions table

In Supabase SQL Editor, run `supabase/migrations/202610090001_app_push_subscriptions.sql`. RLS lets each signed-in user manage only their own device subscriptions.

## 2. Generate VAPID keys and deploy the function

Install the Supabase CLI and log in, then from the repository folder run:

```sh
npx web-push generate-vapid-keys
supabase functions deploy send-push
```

Keep the generated private key out of GitHub and the HTML. Configure secrets in Supabase Dashboard → Edge Functions → Secrets (or with the CLI):

```sh
supabase secrets set VAPID_PUBLIC_KEY='generated-public-key' VAPID_PRIVATE_KEY='generated-private-key' VAPID_SUBJECT='mailto:admin@example.com' PUSH_WEBHOOK_SECRET='a-long-random-secret'
```

Use your real support email in VAPID_SUBJECT. Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY as Edge Function secrets too if the project does not expose them automatically. Never add the service-role key to this repository or the browser.

The repo contains `supabase/config.toml` with JWT verification disabled for this webhook endpoint; the function still rejects requests without the x-webhook-secret value.

## 3. Add the database webhook

In Supabase Dashboard → Database → Webhooks, create an HTTP POST webhook for schema `public`, table `app_messages`, event Insert only.

- URL: `https://wusappxcgwpyaqizgqfs.supabase.co/functions/v1/send-push`
- Header: `x-webhook-secret: <the exact PUSH_WEBHOOK_SECRET value>`
- Payload: standard database webhook payload, including `type`, `table`, and `record`.

The Edge Function looks up subscriptions for the inserted row’s `recipient_id`, sends a generic privacy-safe notification, and removes expired device subscriptions.

## 4. Publish and enable on phones

Publish the repository as an HTTPS site and open that URL on each phone. Sign in, open Profile, tap **ফোনে notification চালু করুন**, and allow notifications. On iPhone/iPad, add the site to Home Screen before enabling. A local `file://` copy cannot receive push.

The UI and server function are in this repository. Supabase secrets, function deployment, SQL and webhook setup must be completed in Supabase before push delivery works.