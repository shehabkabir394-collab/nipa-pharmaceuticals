
export async function onRequestPost(context) {
  try {
    const body = await context.request.json();
    const message = String(body?.message || '').trim();

    if (!message) return json({error:'Message is required.'},400);
    if (message.length > 2000) return json({error:'Message is too long.'},400);

    const apiKey = context.env.GEMINI_API_KEY;
    if (!apiKey) {
      return json({error:'GEMINI_API_KEY is not configured in Cloudflare.'},500);
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

    const apiUrl =
      'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=' +
      encodeURIComponent(apiKey);

    const response = await fetch(apiUrl,{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({
        system_instruction:{parts:[{text:systemPrompt}]},
        contents:[{role:'user',parts:[{text:message}]}],
        generationConfig:{temperature:0.35,maxOutputTokens:700}
      })
    });

    const result = await response.json();

    if (!response.ok) {
      return json({error:result?.error?.message || 'Gemini API error.'},response.status);
    }

    const reply = result?.candidates?.[0]?.content?.parts
      ?.map(p=>p.text||'').join('').trim();

    return json({reply:reply || 'দুঃখিত, কোনো উত্তর পাওয়া যায়নি।'});
  } catch (e) {
    return json({error:'Server error while contacting Gemini.'},500);
  }
}

function json(data,status=200){
  return new Response(JSON.stringify(data),{
    status,
    headers:{
      'Content-Type':'application/json; charset=utf-8',
      'Cache-Control':'no-store'
    }
  });
}
