const allowedOrigins = new Set([
  "https://repco.pages.dev",
  "https://repco-app.pages.dev",
]);

function corsFor(request: Request) {
  const origin = request.headers.get("Origin") || "";
  return {
    "Access-Control-Allow-Origin": allowedOrigins.has(origin) ? origin : "https://repco.pages.dev",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Content-Type": "application/json",
    "Vary": "Origin",
  };
}

const json = (request: Request, body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: corsFor(request) });
const env = (name: string) => Deno.env.get(name) || "";

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function fcmAccessToken(): Promise<string> {
  const email = env("FCM_CLIENT_EMAIL");
  let privateKey = env("FCM_PRIVATE_KEY").replace(/^\uFEFF/, "").trim();
  if (!email || !privateKey) throw new Error("FCM credentials are not configured");
  // Accept raw PEM, a JSON-quoted PEM string, or a Firebase service-account JSON value.
  if (privateKey.startsWith("{") || privateKey.startsWith('"')) {
    try {
      const parsed = JSON.parse(privateKey);
      if (typeof parsed === "string") privateKey = parsed;
      else if (typeof parsed?.private_key === "string") privateKey = parsed.private_key;
    } catch {
      // Let PEM validation below return a useful configuration error.
    }
  }  privateKey = privateKey.replace(/\\r/g, "\r").replace(/\\n/g, "\n").replace(/\r\n?/g, "\n").trim();
  const pemMatch = privateKey.match(/-----BEGIN PRIVATE KEY-----([\s\S]+?)-----END PRIVATE KEY-----/);
  if (!pemMatch) throw new Error("FCM_PRIVATE_KEY must contain a PKCS#8 PEM private key");
  const encodedKey = pemMatch[1].replace(/[^A-Za-z0-9+/=]/g, "");
  if (!encodedKey || encodedKey.length % 4 === 1) throw new Error("FCM_PRIVATE_KEY formatting is invalid");

  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({
    iss: email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = header + "." + claim;
  const keyBytes = Uint8Array.from(atob(encodedKey), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key,
    new TextEncoder().encode(unsigned)));
  const assertion = unsigned + "." + base64Url(signature);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion,
    }),
  });
  const result = await response.json();
  if (!response.ok || !result.access_token) throw new Error("Google OAuth token request failed");
  return result.access_token as string;
}

Deno.serve(async request => {
  const cors = corsFor(request);
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(request, { error: "Method not allowed" }, 405);
  const supabaseUrl = env("SUPABASE_URL");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");
  const projectId = env("FCM_PROJECT_ID");
  if (!supabaseUrl || !serviceKey || !projectId) {
    return json(request, { error: "Server secrets are not configured" }, 500);
  }

  const authorization = request.headers.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ")) return json(request, { error: "Login required" }, 401);
  const authResult = await fetch(supabaseUrl + "/auth/v1/user", {
    headers: { apikey: serviceKey, Authorization: authorization },
  });
  if (!authResult.ok) return json(request, { error: "Invalid login session" }, 401);
  const user = await authResult.json();
  let payload: { recipient_id?: string; body?: string };
  try { payload = await request.json(); } catch { return json(request, { error: "Invalid JSON" }, 400); }
  const recipientId = String(payload.recipient_id || "");
  const body = String(payload.body || "").trim();
  if (!/^[0-9a-f-]{36}$/i.test(recipientId) || !body || body.length > 4000) {
    return json(request, { error: "Invalid message details" }, 400);
  }

  const restHeaders = { apikey: serviceKey, Authorization: "Bearer " + serviceKey };
  const cutoff = new Date(Date.now() - 120_000).toISOString();
  const filters = new URLSearchParams({
    sender_id: "eq." + user.id,
    recipient_id: "eq." + recipientId,
    body: "eq." + body,
    created_at: "gte." + cutoff,
    select: "id",
    order: "created_at.desc",
    limit: "1",
  });
  const messageResult = await fetch(supabaseUrl + "/rest/v1/app_messages?" + filters, { headers: restHeaders });
  const messages = await messageResult.json();
  if (!messageResult.ok || !messages?.[0]) {
    return json(request, { sent: 0, failed: 0, message: "Message record not found; notification skipped" });
  }
  const tokenQuery = new URLSearchParams({ user_id: "eq." + recipientId, select: "device_token" });
  const tokenResult = await fetch(supabaseUrl + "/rest/v1/repco_device_tokens?" + tokenQuery, { headers: restHeaders });
  const tokens = await tokenResult.json();
  if (!tokenResult.ok) return json(request, { error: "Could not load recipient phone" }, 500);
  if (!tokens.length) return json(request, { sent: 0, failed: 0, message: "Recipient phone has not registered for notifications" });

  const senderResult = await fetch(supabaseUrl + "/rest/v1/profiles?id=eq." + encodeURIComponent(user.id) + "&select=full_name&limit=1", { headers: restHeaders });
  const senders = await senderResult.json();
  const title = "নতুন মেসেজ • " + String(senders?.[0]?.full_name || "Rephco App").slice(0, 70);
  const preview = body.replace(/\s+/g, " ").slice(0, 140);
  const accessToken = await fcmAccessToken();
  let sent = 0, failed = 0;
  for (const row of tokens) {
    const result = await fetch("https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(projectId) + "/messages:send", {
      method: "POST",
      headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
      body: JSON.stringify({ message: {
        token: row.device_token,
        notification: { title, body: preview },
        data: { type: "new_message", sender_id: user.id, recipient_id: recipientId },
        android: { priority: "high", notification: { channel_id: "repco_product_updates", sound: "default" } },
      } }),
    });
    if (result.ok) sent++;
    else { failed++; console.error("FCM message send failed", result.status, (await result.text()).slice(0, 500)); }
  }
  return json(request, { sent, failed });
});
