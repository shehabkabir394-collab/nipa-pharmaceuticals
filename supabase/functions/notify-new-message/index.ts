const origins = new Set(["https://repco.pages.dev", "https://repco-app.pages.dev"]);
const cors = (req: Request) => ({ "Access-Control-Allow-Origin": origins.has(req.headers.get("Origin") || "") ? req.headers.get("Origin")! : "https://repco.pages.dev", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "POST, OPTIONS", "Content-Type": "application/json", "Vary": "Origin" });
const reply = (req: Request, data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: cors(req) });
const env = (key: string) => Deno.env.get(key) || "";
function b64url(v: string | Uint8Array) { const bytes = typeof v === "string" ? new TextEncoder().encode(v) : v; let s = ""; for (const b of bytes) s += String.fromCharCode(b); return btoa(s).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_"); }
async function accessToken() {
  const email = env("FCM_CLIENT_EMAIL"), pem = env("FCM_PRIVATE_KEY").replace(/\\n/g, "\n"); if (!email || !pem) throw new Error("FCM credentials are not configured");
  const now = Math.floor(Date.now()/1000), head = b64url(JSON.stringify({alg:"RS256",typ:"JWT"})), claim = b64url(JSON.stringify({iss:email,scope:"https://www.googleapis.com/auth/firebase.messaging",aud:"https://oauth2.googleapis.com/token",iat:now,exp:now+3600})), unsigned=head+"."+claim;
  const der = Uint8Array.from(atob(pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,"")), c=>c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8",der,{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,new TextEncoder().encode(unsigned)));
  const r = await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:unsigned+"."+b64url(sig)})}); const j=await r.json(); if(!r.ok||!j.access_token)throw new Error("Google OAuth token request failed"); return j.access_token as string;
}
Deno.serve(async req => {
  if(req.method==="OPTIONS")return new Response("ok",{headers:cors(req)}); if(req.method!=="POST")return reply(req,{error:"Method not allowed"},405);
  const url=env("SUPABASE_URL"), key=env("SUPABASE_SERVICE_ROLE_KEY"), project=env("FCM_PROJECT_ID"); if(!url||!key||!project)return reply(req,{error:"Server secrets are not configured"},500);
  const auth=req.headers.get("Authorization")||""; if(!auth.startsWith("Bearer "))return reply(req,{error:"Login required"},401);
  const a=await fetch(url+"/auth/v1/user",{headers:{apikey:key,Authorization:auth}}); if(!a.ok)return reply(req,{error:"Invalid login session"},401); const user=await a.json();
  let p:{recipient_id?:string;body?:string}; try{p=await req.json()}catch{return reply(req,{error:"Invalid JSON"},400)}; const recipient=String(p.recipient_id||""), body=String(p.body||"").trim(); if(!/^[0-9a-f-]{36}$/i.test(recipient)||!body||body.length>4000)return reply(req,{error:"Invalid message details"},400);
  const h={apikey:key,Authorization:"Bearer "+key}; const qs=new URLSearchParams({sender_id:"eq."+user.id,recipient_id:"eq."+recipient,body:"eq."+body,created_at:"gte."+new Date(Date.now()-120000).toISOString(),select:"id",order:"created_at.desc",limit:"1"});
  const mr=await fetch(url+"/rest/v1/app_messages?"+qs,{headers:h}), messages=await mr.json(); if(!mr.ok||!messages?.[0])return reply(req,{sent:0,failed:0,message:"Message record not found; notification skipped"});
  const tq=new URLSearchParams({user_id:"eq."+recipient,select:"device_token"}), tr=await fetch(url+"/rest/v1/repco_device_tokens?"+tq,{headers:h}), tokens=await tr.json(); if(!tr.ok)return reply(req,{error:"Could not load recipient phone"},500); if(!tokens.length)return reply(req,{sent:0,failed:0,message:"Recipient phone has not registered for notifications"});
  const sr=await fetch(url+"/rest/v1/profiles?id=eq."+encodeURIComponent(user.id)+"&select=full_name&limit=1",{headers:h}), senders=await sr.json(); const title="নতুন মেসেজ • "+String(senders?.[0]?.full_name||"Rephco App").slice(0,70), preview=body.replace(/\s+/g," ").slice(0,140), token=await accessToken(); let sent=0,failed=0;
  for(const row of tokens){const r=await fetch("https://fcm.googleapis.com/v1/projects/"+encodeURIComponent(project)+"/messages:send",{method:"POST",headers:{Authorization:"Bearer "+token,"Content-Type":"application/json"},body:JSON.stringify({message:{token:row.device_token,notification:{title,body:preview},data:{type:"new_message",sender_id:user.id,recipient_id:recipient},android:{priority:"high",notification:{channel_id:"repco_product_updates",sound:"default"}}}})}); if(r.ok)sent++;else{failed++;console.error("FCM send failed",r.status,(await r.text()).slice(0,500))}}
  return reply(req,{sent,failed});
});
