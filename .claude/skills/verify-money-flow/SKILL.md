---
name: verify-money-flow
description: Prove a Money Flow change in the real app (not only tests) - start backend + frontend locally with one command, drive the PWA capture screen in headless Chrome with a fake microphone, and hand Guilherme an HTTPS link to test on the iPhone. Use before opening a PR that touches the parser, the capture endpoints or the PWA, or when asked to "testar no localhost" / "ver funcionando".
---

# Verify Money Flow

The product target is the PWA (site saved to the iPhone home screen from Safari), not the Telegram bot.
Nothing goes to production from here: production deploys are Guilherme's, after he validates locally.

## 1. Start the app

```bash
scripts/dev-pwa.sh              # backend + frontend + ngrok HTTPS tunnel, prints link and demo login
scripts/dev-pwa.sh --no-tunnel  # localhost only
```

- Separate database `money_flow_pwa` (never the developer's `money_flow`). Kept between runs.
- Demo user `demo@moneyflow.local`, password in `.dev-logs/demo-password.txt`, with Nubank (R$ 500), Inter (R$ 1.000)
  and the base categories. Web sign-up doesn't create accounts yet (PCT-82), so use this user.
- Real OpenAI (key from `.env`). Telegram calls go nowhere on purpose (`telegram.url` points at a dead port).
- Logs: `.dev-logs/backend.log`, `frontend.log`, `ngrok.log`. Ctrl+C (or SIGTERM) stops everything.
- Run it in the background from an agent, then wait for "Abra no iPhone" in its output.

## 2. Drive the capture screen

```bash
scripts/verify/capture-e2e.sh                       # against http://localhost:5173
scripts/verify/capture-e2e.sh https://<tunnel-url>  # through the HTTPS tunnel, like the iPhone
```

Headless Chrome at 390×844 with a fake microphone that says "gastei vinte e cinco reais no supermercado".
Expected output:

```
2. voz → −R$ 25,00 · Mercado · Nubank | Saldo R$ 475,00 | Ouvi: “Gastei vinte e cinco reais no supermercado.”
3. desfazer → Desfeito. O saldo voltou.
4. texto → Não achei uma categoria para isso. | Quer criar “Saúde” (gasto) e registrar?
5. criar categoria e registrar → −R$ 12,00 · Saúde · Nubank
6. editar → diálogo aberto
```

Screenshots land in `.dev-logs/e2e/`. Look at them before claiming a UI change works.
Step 4 only offers "Criar" the first time; afterwards Saúde exists and step 5 is skipped.

Gotchas that cost time:
- The fake microphone records silence unless Chrome runs with `--disable-features=AudioServiceSandbox`
  (the script does it). The audio file loops, so it carries 9s of trailing silence.
- ngrok's free domain shows a warning page to browsers. The script skips it with the `ngrok-skip-browser-warning`
  header; on the iPhone, tap "Visit Site" once.

## 3. Parser quality

Prompt changes need numbers, not vibes:

```bash
set -a; . ./.env; set +a
python3 backend/eval/voice-parser/run.py --strict-account --repeats 3 --cases backend/eval/voice-parser/fresh.json
```

See `backend/eval/voice-parser/README.md` for the sets and the last results. Never tune on `fresh.json`.

## 4. Hand off to Guilherme

Give him the tunnel link and the demo login from the script output, and what to try. The iPhone test is his:
Safari → link → Visit Site → login → microphone. For the icon: Share → Add to Home Screen, then log in again
(iOS keeps the icon's session apart from Safari).
