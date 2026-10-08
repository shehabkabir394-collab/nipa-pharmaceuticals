export async function onRequestPost(context) {
  try {
    const body = await context.request.json().catch(() => null);
    const message = String(body?.message || "").trim();
    if (!message) return json({ error: "Message is required." }, 400);
    if (message.length > 2000) return json({ error: "Message is too long." }, 400);

    const apiKey = String(context.env?.GEMINI_API_KEY || "").trim();
    if (!apiKey) {
      console.error("GEMINI_API_KEY is missing.");
      return json({ error: "Cloudflare GEMINI_API_KEY পাওয়া যায়নি।" }, 500);
    }

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{
              text: "You are Nipa AI Assistant inside the Nipa Pharmaceuticals MPO Order app. Reply in simple Bangla by default, or English if asked. Help with app usage, New Order, History, Ledger, Profile, MPO Code and business calculations. Do not claim access to private user data unless it is included in the message. Do not invent prices, stock, orders or balances. For medical diagnosis, prescription or dosage questions, advise consulting a qualified doctor or pharmacist. Keep replies concise and practical."
            }]
          },
          contents: [{ role: "user", parts: [{ text: message }] }],
          generationConfig: { temperature: 0.3, maxOutputTokens: 700 }
        })
      }
    );

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      console.error("Gemini error:", response.status, data);
      return json({ error: data?.error?.message || "Gemini API request failed.", status: response.status }, response.status);
    }

    const reply = (data?.candidates?.[0]?.content?.parts || [])
      .map(part => part?.text || "").join("").trim();

    if (!reply) return json({ error: "Gemini কোনো উত্তর দেয়নি।" }, 502);
    return json({ reply });
  } catch (error) {
    console.error("Nipa Chat API error:", error);
    return json({ error: "AI server error হয়েছে।" }, 500);
  }
}

export async function onRequest(context) {
  if (context.request.method === "POST") return onRequestPost(context);
  return json({ error: "Use POST /api/chat" }, 405);
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}
