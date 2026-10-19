---
description: Frontend UI, style, and client-side logic updates strictly isolated from backend layers.
---

# UI Workflow

Scope:
- **Frontend JavaScript**: `hcms-web/src/main/resources/public/js/*.js`
- **Entry HTML**: `hcms-web/src/main/resources/public/index.html`
- **Styles**: `hcms-web/src/main/resources/public/css/style.css`

Strict Rules:
- Touch ONLY frontend static files listed above.
- Do NOT touch Java controllers, services, DAOs, or backend code.
- Verify UI interactions directly in browser.
- Keep `chart.min.js` untouched.
