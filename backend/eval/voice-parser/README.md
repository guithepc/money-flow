# Voice parser eval

Runs `src/main/resources/prompts/voice-parser.txt` against the real OpenAI API (gpt-4o-mini) and scores each
answer the way the backend handles it (`TelegramBotServices.proccessTranscriptedMessage`).

```bash
set -a; . ./.env; set +a   # needs OPENAI_API_KEY
python3 backend/eval/voice-parser/run.py --strict-account --repeats 3 --cases backend/eval/voice-parser/fresh.json
```

| Set | What it is | Used to tune the prompt? |
|---|---|---|
| `cases.json` | base categories of a new user, Nubank + Inter | yes |
| `holdout.json` | other names (Supermercado, Comer fora, Saúde, Pets…), Itaú/Santander/Revolut | yes, after the first run |
| `fresh.json` | Alimentação, Contas, Farmácia, Viagens, Dividendos…, C6/Caixa/Nubank | one structural change only (split category lists by type) |

Results on 2026-11-22 (3 repeats each; old prompt scored with any account accepted when none is said,
new prompt must return `accountId: null` so the backend picks the most used account):

| Set | Old prompt | New prompt |
|---|---|---|
| cases | 78% | 99% |
| holdout | 56% | 100% |
| fresh, before any change from it | 63% | 89% |
| fresh, after splitting the lists | | 93% |

Every cost is a few cents. Add a case whenever a real message is parsed wrong.
