const cors = {
  "Access-Control-Allow-Origin": "https://repco.pages.dev",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: cors });
const env = (name: string) => Deno.env.get(name) || "";

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function fcmAccessToken(): Promise<string> {
  const email = env("FCM_CLIENT_EMAIL");
  const privateKey = env("FCM_PRIVATE_KEY").replace(/\\n/g, "\n");
  if (!email || !privateKey) throw new Error("FCM credentials are not configured");
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
  const pem = privateKey.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(pem);
  const keyBytes = Uint8Array.from(binary, c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned),
  ));
  const assertion = unsigned + "." + base64Url(signature);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const result = await response.json();
  if (!response.ok || !result.access_token) throw new Error("Google OAuth token request failed");
  return result.access_token as string;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const supabaseUrl = env("SUPABASE_URL");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");
  const projectId = env("FCM_PROJECT_ID");
  if (!supabaseUrl || !serviceKey || !projectId) return json({ error: "Server secrets are not configured" }, 500);

  const authorization = request.headers.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ")) return json({ error: "Login required" }, 401);
  const authResult = await fetch(supabaseUrl + "/auth/v1/user", {
    headers: { apikey: serviceKey, Authorization: authorization },
  });
  if (!authResult.ok) return json({ error: "Invalid login session" }, 401);
  const user = await authResult.json();

  const restHeaders = { apikey: serviceKey, Authorization: "Bearer " + serviceKey };
  const profileResult = await fetch(
    supabaseUrl + "/rest/v1/profiles?id=eq." + encodeURIComponent(user.id) + "&select=role,active&limit=1",
    { headers: restHeaders },
  );
  const profiles = await profileResult.json();
  if (!profileResult.ok || profiles?.[0]?.role !== "admin" || profiles?.[0]?.active !== true) {
    return json({ error: "Active Admin account required" }, 403);
  }

  let payload: { product_id?: number | string };
  try { payload = await request.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const productId = Number(payload.product_id);
  if (!Number.isSafeInteger(productId) || productId < 1000000) return json({ error: "Invalid product" }, 400);

  const productResult = await fetch(
    supabaseUrl + "/rest/v1/repco_products?id=eq." + productId +
      "&select=id,name,generic_name,pack_size&limit=1",
    { headers: restHeaders },
  );
  const products = await productResult.json();
  if (!productResult.ok || !products?.[0]) return json({ error: "Product not found" }, 404);

  const tokenResult = await fetch(
    supabaseUrl + "/rest/v1/repco_device_tokens?select=device_token",
    { headers: restHeaders },
  );
  const tokens = await tokenResult.json();
  if (!tokenResult.ok) return json({ error: "Could not load registered phones" }, 500);
  if (!tokens.length) return json({ sent: 0, failed: 0, message: "No phone has registered for notifications yet" });

  const accessToken = await fcmAccessToken();
  const product = products[0];
  const title = "নতুন পণ্য যোগ হয়েছে";
  const body = product.name + " • " + product.pack_size;
  let sent = 0;
  let failed = 0;
  for (const row of tokens) {
    const fcmResult = await fetch(
      "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(projectId) + "/messages:send",
      {
        method: "POST",
        headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token: row.device_token,
            notification: { title, body },
            data: {
              type: "product_added",
              product_id: String(product.id),
              product_name: product.name,
              generic_name: product.generic_name,
            },
            android: { priority: "high", notification: { channel_id: "repco_product_updates", sound: "default" } },
          },
        }),
      },
    );
    if (fcmResult.ok) sent++;
    else {
      failed++;
      const detail = await fcmResult.text();
      console.error("FCM send failed", fcmResult.status, detail.slice(0, 500));
    }
  }
  return json({ sent, failed });
});
