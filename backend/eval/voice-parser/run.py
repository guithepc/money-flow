"""Runs the voice-parser prompt against the real OpenAI API and scores it.

Usage (from the repo root, with OPENAI_API_KEY in .env):
    set -a; . ./.env; set +a; python3 backend/eval/voice-parser/run.py [--repeats 3]

Scored the way the backend handles the answer (TelegramBotServices.proccessTranscriptedMessage):
a category outside the list is offered for creation (same as CATEGORY_NOT_FOUND), a category of the
other type is refused (CATEGORY_TYPE_MISMATCH), and a missing,
zero or non-Brazilian amount is refused (same as NOT_UNDERSTOOD).

"NEW" in a case's category list means "offer to create a category" is a correct answer.
"account": null means the sentence names no account: the parser must still return the
transaction instead of NOT_UNDERSTOOD, with accountId null (--strict-account) or any listed account.
"""
import argparse, json, os, re, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROMPT = HERE.parents[1] / "src/main/resources/prompts/voice-parser.txt"
MODEL = "gpt-4o-mini"


def build_prompt(fixture):
    def cats(kind):
        return "".join(f"- {name}\n" for name, k in fixture["categories"] if k == kind) or "(nenhuma)\n"
    accs = "".join(f"ID: {pk} - {name} - {amount}\n" for pk, name, amount in fixture["accounts"])
    prompt = PROMPT.read_text(encoding="utf-8")
    for part in (cats("EXPENSE"), cats("INCOME"), accs):  # same order as TelegramBotServices.parseAndRegister
        prompt = prompt.replace("%s", part, 1)
    return prompt


def complete(instructions, text):
    body = json.dumps({"model": MODEL, "instructions": instructions, "input": text}).encode()
    req = urllib.request.Request("https://api.openai.com/v1/responses", body, {
        "Authorization": "Bearer " + os.environ["OPENAI_API_KEY"], "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    for item in data.get("output", []):
        for c in item.get("content", []) or []:
            if c.get("type") == "output_text":
                return c["text"]
    return ""


def amount_of(value):
    s = str(value).strip().replace("R$", "").replace(" ", "")
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


BRAZILIAN_AMOUNT = re.compile(r"-?(\d{1,3}(\.\d{3})+|\d+)(,\d{1,2})?")


def backend_view(out, categories):
    """What the backend does with the parser output: a transaction, NOT_UNDERSTOOD or CATEGORY_NOT_FOUND."""
    if out.get("error"):
        return out
    amount = out.get("amount")
    if not isinstance(amount, str) or not BRAZILIAN_AMOUNT.fullmatch(amount) or amount_of(amount) <= 0:
        return {"error": "NOT_UNDERSTOOD"}
    kind = categories.get(out.get("category"))
    if kind is None:
        return {"error": "CATEGORY_NOT_FOUND", "category": out.get("category")}
    if kind != out.get("operationType"):
        return {"error": "CATEGORY_TYPE_MISMATCH", "category": out.get("category")}
    return out


def score(case, raw, account_ids, strict_account=False, categories=None):
    try:
        out = json.loads(re.sub(r"^```(json)?|```$", "", raw.strip()).strip())
    except json.JSONDecodeError:
        return False, "invalid JSON"
    out = backend_view(out, categories or {})
    if "error" in case:
        return out.get("error") == case["error"], f"got {out}"
    if out.get("error") == "CATEGORY_NOT_FOUND":
        return "NEW" in case["category"], f"CATEGORY_NOT_FOUND {out.get('category')}"
    if out.get("error"):
        return False, f"error {out.get('error')}"
    problems = []
    if out.get("operationType") != case["op"]:
        problems.append(f"op {out.get('operationType')}")
    if out.get("category") not in case["category"]:
        problems.append(f"category {out.get('category')}")
    expected_accounts = case["account"] if isinstance(case["account"], list) else [case["account"]]
    acc = out.get("accountId")
    acc = int(acc) if isinstance(acc, (int, str)) and str(acc).isdigit() else None
    if None in expected_accounts and strict_account:
        ok_acc = acc is None or acc in expected_accounts
    elif None in expected_accounts:
        ok_acc = acc is None or acc in account_ids or acc in expected_accounts
    else:
        ok_acc = acc in expected_accounts
    if not ok_acc:
        problems.append(f"account {out.get('accountId')}")
    got = amount_of(out.get("amount"))
    if got is None or abs(got - case["amount"]) > 0.001:
        problems.append(f"amount {out.get('amount')}")
    return not problems, ", ".join(problems)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--cases", default=str(HERE / "cases.json"))
    parser.add_argument("--strict-account", action="store_true",
                        help="an unnamed account must come back as null (the backend picks the default)")
    args = parser.parse_args()
    fixture = json.loads(Path(args.cases).read_text(encoding="utf-8"))
    instructions = build_prompt(fixture)
    account_ids = {pk for pk, _, _ in fixture["accounts"]}
    jobs = [(i, case) for i, case in enumerate(fixture["cases"]) for _ in range(args.repeats)]
    with ThreadPoolExecutor(max_workers=8) as pool:
        raws = list(pool.map(lambda j: complete(instructions, j[1]["text"]), jobs))
    passes, failures = 0, {}
    for (i, case), raw in zip(jobs, raws):
        ok, why = score(case, raw, account_ids, args.strict_account, dict(fixture["categories"]))
        passes += ok
        if not ok:
            failures.setdefault(case["text"], []).append(why)
    total = len(jobs)
    print(f"{passes}/{total} passed ({100 * passes / total:.0f}%) - {len(fixture['cases'])} cases x {args.repeats} repeats, {MODEL}")
    for text, whys in failures.items():
        print(f"  FAIL {len(whys)}/{args.repeats}  {text!r}: {'; '.join(sorted(set(whys)))}")


if __name__ == "__main__":
    sys.exit(main())
