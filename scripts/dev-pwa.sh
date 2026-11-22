#!/usr/bin/env bash
# Sobe o Money Flow no Mac para testar o PWA no iPhone, antes de qualquer deploy:
# banco local separado, backend, frontend e um endereço HTTPS temporário (ngrok).
# O iPhone só libera o microfone em HTTPS, por isso o túnel.
#
#   scripts/dev-pwa.sh            # sobe tudo e imprime o link e o login de demonstração
#   scripts/dev-pwa.sh --no-tunnel  # só localhost (http://localhost:5173/registrar)
#
# Ctrl+C derruba tudo. Logs em .dev-logs/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.dev-logs"
DB="${MF_DEV_DB:-money_flow_pwa}"
DEMO_EMAIL="demo@moneyflow.local"
TUNNEL=1
[[ "${1:-}" == "--no-tunnel" ]] && TUNNEL=0
mkdir -p "$LOGS"

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
fail() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

for tool in java node psql pg_isready curl openssl; do
  command -v "$tool" >/dev/null || fail "Falta $tool no PATH."
done
[[ $TUNNEL == 1 ]] && { command -v ngrok >/dev/null || fail "Falta ngrok (brew install ngrok) ou rode com --no-tunnel."; }
[[ -x "$ROOT/frontend/node_modules/.bin/vite" ]] || fail "Rode npm ci em frontend/ antes."
[[ -f "$ROOT/.env" ]] || fail "Falta o .env na raiz (precisa de OPENAI_API_KEY e JWT_SECRET)."
pg_isready -h localhost -p 5432 -q || fail "Postgres não responde em localhost:5432 (brew services start postgresql@18)."

# Segredos do .env no ambiente dos processos filhos; nada é impresso.
set -a; . "$ROOT/.env"; set +a
[[ -n "${OPENAI_API_KEY:-}" ]] || fail "OPENAI_API_KEY vazio no .env."
unset OPENAI_BASE_URL

# Credenciais do Postgres local: as mesmas do application-local.properties ("${DB_USER:padrão}").
# Tenta o valor do ambiente (.env) e, se não conectar, o padrão do arquivo.
LOCAL_PROPS="$ROOT/backend/src/main/resources/application-local.properties"
prop() { grep -E "^$1=" "$LOCAL_PROPS" | head -1 | cut -d= -f2-; }
placeholder() { [[ "$1" =~ ^\$\{([A-Za-z_]+):(.*)\}$ ]] && echo "${BASH_REMATCH[$2]}"; }
resolve() {
  local raw="$1" mode="$2" var def
  var="$(placeholder "$raw" 1)" || { echo "$raw"; return; }
  def="$(placeholder "$raw" 2)"
  if [[ "$mode" == env ]]; then echo "${!var:-$def}"; else echo "$def"; fi
}
export PGHOST=localhost
for mode in env default; do
  export PGUSER="$(resolve "$(prop spring.datasource.username)" $mode)" PGPASSWORD="$(resolve "$(prop spring.datasource.password)" $mode)"
  psql -d postgres -tAc "SELECT 1" >/dev/null 2>&1 && break
done
psql -d postgres -tAc "SELECT 1" >/dev/null 2>&1 || fail "Não consegui entrar no Postgres local com as credenciais do application-local.properties."

PIDS=()
cleanup() {
  printf '\nDerrubando…\n'
  for pid in ${PIDS[@]+"${PIDS[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for port in 8080 5173; do
  lsof -nP -iTCP:$port -sTCP:LISTEN >/dev/null 2>&1 && fail "A porta $port já está em uso. Feche o que está nela e rode de novo."
done

step "1/5 Banco $DB"
if ! psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB'" | grep -q 1; then
  psql -d postgres -qc "CREATE DATABASE $DB"
  echo "criado (o Flyway monta o schema quando o backend subir)"
else
  echo "já existe, mantido"
fi

step "2/5 Backend"
JAR="$ROOT/backend/target/money-flow-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]] || [[ -n "$(find "$ROOT/backend/src/main" -newer "$JAR" -type f -print -quit)" ]]; then
  echo "compilando…"
  (cd "$ROOT/backend" && ./mvnw -q -B package -DskipTests)
fi
AUTH_COOKIE_SECURE=false java -jar "$JAR" \
  --spring.profiles.active=local \
  --spring.datasource.url="jdbc:postgresql://localhost:5432/$DB" \
  --spring.datasource.username="$PGUSER" --spring.datasource.password="$PGPASSWORD" \
  --telegram.url=http://127.0.0.1:9/bot \
  --server.port=8080 > "$LOGS/backend.log" 2>&1 &
PIDS+=($!)
for _ in $(seq 1 90); do
  grep -q "Started MoneyFlowApplication" "$LOGS/backend.log" && break
  grep -q "APPLICATION FAILED TO START" "$LOGS/backend.log" && fail "Backend não subiu. Veja $LOGS/backend.log"
  sleep 1
done
grep -q "Started MoneyFlowApplication" "$LOGS/backend.log" || fail "Backend demorou demais. Veja $LOGS/backend.log"
echo "ok em http://localhost:8080"

step "3/5 Usuário de demonstração"
# O cadastro pelo site já cria as categorias base; contas ainda não (PCT-82): o seed cria duas.
OWNER="$(psql -d "$DB" -tAc "SELECT owner_id FROM owner WHERE email = '$DEMO_EMAIL'")"
if [[ -z "$OWNER" ]]; then
  DEMO_PASSWORD="demo-$(openssl rand -hex 4)"
  curl -sf -o /dev/null localhost:8080/api/auth/register -H "Content-Type: application/json" \
    -d "{\"name\":\"Demo\",\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\",\"acceptedTermsVersion\":\"1.0\"}" \
    || fail "Não consegui cadastrar o usuário de demonstração."
  psql -d "$DB" -v ON_ERROR_STOP=1 -q <<SQL
UPDATE owner SET onboarding_step = 'DONE', language = 'pt' WHERE email = '$DEMO_EMAIL';
INSERT INTO account (owner_id, description, balance, currency)
SELECT owner_id, d, b, 'BRL' FROM owner, (VALUES ('Nubank', 500), ('Inter', 1000)) v(d, b)
WHERE email = '$DEMO_EMAIL';
SQL
  echo "$DEMO_PASSWORD" > "$LOGS/demo-password.txt"
  echo "criado com Nubank (R\$ 500), Inter (R\$ 1.000) e as categorias base"
else
  echo "já existe"
fi
DEMO_PASSWORD="$(cat "$LOGS/demo-password.txt" 2>/dev/null || echo '(veja .dev-logs/demo-password.txt)')"

step "4/5 Frontend"
# exec: the PID tracked is Vite itself, so Ctrl+C really stops it (npx would leave a child behind).
(cd "$ROOT/frontend" && VITE_API_BASE_URL=/api exec ./node_modules/.bin/vite --port 5173 --strictPort > "$LOGS/frontend.log" 2>&1) &
PIDS+=($!)
for _ in $(seq 1 30); do curl -s -o /dev/null localhost:5173 && break; sleep 1; done
echo "ok em http://localhost:5173/registrar"

URL="http://localhost:5173"
if [[ $TUNNEL == 1 ]]; then
  step "5/5 Túnel HTTPS"
  ngrok http 5173 --log=stdout > "$LOGS/ngrok.log" 2>&1 &
  PIDS+=($!)
  for _ in $(seq 1 20); do
    URL="$(curl -s localhost:4040/api/tunnels | python3 -c 'import json,sys; t=[x["public_url"] for x in json.load(sys.stdin)["tunnels"] if x["public_url"].startswith("https")]; print(t[0] if t else "")' 2>/dev/null || true)"
    [[ -n "$URL" ]] && break
    sleep 1
  done
  [[ -n "$URL" ]] || fail "O ngrok não devolveu um endereço. Veja $LOGS/ngrok.log"
else
  step "5/5 Túnel desligado (--no-tunnel)"
fi

cat <<INFO

  Abra no iPhone (Safari):  $URL/registrar
  Login:                    $DEMO_EMAIL  /  $DEMO_PASSWORD

  1. Na primeira vez o ngrok mostra um aviso: toque em "Visit Site".
  2. Entre com o login acima e toque no microfone: "gastei 25 no supermercado".
  3. Para o ícone: Compartilhar → Adicionar à Tela de Início. Ao abrir pelo ícone, entre de novo
     (o iOS guarda a sessão do ícone separada do Safari).

  O link é público enquanto este terminal estiver aberto. Ctrl+C derruba tudo.
INFO
wait
