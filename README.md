# Nipa Pharmaceuticals — AI Assistant

Cloudflare Pages / GitHub structure:

- `index.html` — current Nipa Pharmaceuticals app with Nipa AI Assistant UI added
- `functions/api/ai.js` — Cloudflare Pages Function for Gemini

## Cloudflare setup

Add a Production secret named exactly:

`GEMINI_API_KEY`

The Gemini API key must be stored only in Cloudflare Secrets, not in `index.html`.

The AI endpoint is:

`POST /api/ai`

This package keeps the existing Supabase Login/Registration/Profile code in the current `index.html` and adds the AI assistant without changing the Supabase authentication configuration.
