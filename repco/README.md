# Repco Pharmaceutical app

Static Repco app entry point: `index.html`.

This app uses the same Supabase project as the Nipa app, so accounts, order history, app state, and messages remain in the shared database. When deploying through Cloudflare Pages, set the build output directory to `repco` (the folder containing this `index.html`).
