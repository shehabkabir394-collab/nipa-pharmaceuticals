import webpush from "npm:web-push@3.6.7";

const corsHeaders = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type", "Access-Control-Allow-Methods": "GET, POST, OPTIONS", "Content-Type": "application/json" };
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: corsHeaders }); }

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const publicKey = Deno.env.get("VAPID_PUBLIC_KEY") ?? "";
  if (request.method === "GET") return publicKey ? json({ publicKey }) : json({ error: "Push is not configured yet." }, 503);
  if (request.method !== "POST") return json({ error: "Method not allowed." }, 405);
  const expectedSecret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
  if (!expectedSecret || request.headers.get("x-webhook-secret") !== expectedSecret) return json({ error: "Unauthorized webhook." }, 401);
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const privateKey = Deno.env.get("VAPID_PRIVATE_KEY") ?? "";
  const subject = Deno.env.get("VAPID_SUBJECT") ?? "";
  if (!supabaseUrl || !serviceRoleKey || !publicKey || !privateKey || !subject) return json({ error: "Push server secrets are incomplete." }, 500);
  try {
    const payload = await request.json();
    const message = payload?.record;
    if (payload?.type !== "INSERT" || payload?.table !== "app_messages" || !message?.recipient_id) return json({ ignored: true });
    webpush.setVapidDetails(subject, publicKey, privateKey);
    const query = new URL("/rest/v1/app_push_subscriptions", supabaseUrl);
    query.searchParams.set("select", "id,endpoint,subscription");
    query.searchParams.set("user_id", "eq." + message.recipient_id);
    const response = await fetch(query, { headers: { apikey: serviceRoleKey, Authorization: "Bearer " + serviceRoleKey } });
    if (!response.ok) throw new Error("Subscription lookup failed: " + await response.text());
    const subscriptions = await response.json();
    const notification = JSON.stringify({ title: "Nipa MPO Order", body: "আপনার নতুন message এসেছে।", url: "./", tag: "nipa-message-" + message.id });
    let sent = 0; const expired: string[] = []; const failures: string[] = [];
    for (const row of subscriptions) {
      try { await webpush.sendNotification(row.subscription, notification, { TTL: 60 }); sent++; }
      catch (error) { const code = (error as { statusCode?: number })?.statusCode; if (code === 404 || code === 410) expired.push(row.endpoint); else failures.push(String(error)); }
    }
    for (const endpoint of expired) {
      const del = new URL("/rest/v1/app_push_subscriptions", supabaseUrl); del.searchParams.set("endpoint", "eq." + endpoint);
      await fetch(del, { method: "DELETE", headers: { apikey: serviceRoleKey, Authorization: "Bearer " + serviceRoleKey } });
    }
    if (failures.length) console.error("Some push notifications failed:", failures);
    return json({ sent, expired: expired.length, failed: failures.length });
  } catch (error) { console.error("Push webhook failed:", error); return json({ error: "Push delivery failed." }, 500); }
});
