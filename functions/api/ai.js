export async function onRequestPost(context) {
  try {
    // Read request body
    let body;
    try {
      body = await context.request.json();
    } catch {
      return json({ error: "Invalid JSON request." }, 400);
    }

    const message = String(body?.message || "").trim();

    if (!message) {
      return json({ error: "Message is required." }, 400);
    }

    if (message.length > 2000) {
      return json({ error: "Message is too long. Maximum 2000 characters." }, 400);
    }

    // Cloudflare Pages secret
    const apiKey = context.env?.GEMINI_API_KEY;

    if (!apiKey || !String(apiKey).trim()) {
      console.error("GEMINI_API_KEY is missing from Cloudflare environment.");
      return json(
        { error: "GEMINI_API_KEY is not configured in Cloudflare." },
        500
      );
    }

    const systemPrompt = `
You are Nipa AI Assistant inside a Bangladeshi pharmaceutical sales/order web app called MPO Order.
The app is used by sales representatives.

Reply in clear, simple Bangla by default. If the user asks in English, reply in English.
You may explain how to use the app, New Order, History, Shop Ledger, profile, MPO Code and general business calculations.

Do not claim to see the user's private Orders, Ledger, Products, Supabase records or account data unless that data is explicitly included in the current message.
Do not invent product prices, stock, discounts, customer balances or order records.

For medical diagnosis, prescription or medicine dosage questions, do not provide unsafe medical advice; recommend a qualified doctor or pharmacist.

Keep answers concise and practical.
`;

    // Gemini REST GenerateContent endpoint
    const apiUrl =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": String(apiKey).trim()
      },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: systemPrompt }]
        },
        contents: [
          {
            role: "user",
            parts: [{ text: message }]
          }
        ],
        generationConfig: {
          temperature: 0.35,
          maxOutputTokens: 700
        }
      })
    });

    const result = await response.json();

    if (!response.ok) {
      console.error("Gemini API error:", response.status, result);

      const messageFromGemini =
        result?.error?.message ||
        `Gemini API request failed with status ${response.status}.`;

      return json(
        {
          error: messageFromGemini,
          status: response.status
        },
        response.status
      );
    }

    const reply = result?.candidates?.[0]?.content?.parts
      ?.map((part) => part?.text || "")
      .join("")
      .trim();

    if (!reply) {
      console.error("Gemini returned no text:", result);

      return json(
        { error: "Gemini returned an empty response." },
        502
      );
    }

    return json({ reply });
  } catch (error) {
    console.error("AI function error:", error);

    return json(
      {
        error: "Server error while contacting Gemini."
      },
      500
    );
  }
}

export async function onRequest(context) {
  if (context.request.method === "POST") {
    return onRequestPost(context);
  }

  return json(
    {
      error: "Method not allowed. Use POST /api/ai."
    },
    405
  );
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}
