#!/usr/bin/env bash
# Encrypted Postgres backup (#25).
# pg_dump is piped straight into age, so a plain-text dump never touches the disk.
# Only the age *public* key lives on the server: whoever has the VPS can create backups but not read them.
#
# Usage: ops/backup-db.sh [label]     e.g. ops/backup-db.sh pre-deploy
set -euo pipefail

LABEL="${1:-daily}"
BACKUP_DIR="${BACKUP_DIR:-/root/backups}"
RECIPIENTS="${BACKUP_RECIPIENTS:-/root/.config/money-flow/backup-recipients.txt}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/money-flow}"

if [[ ! -s "$RECIPIENTS" ]]; then
    echo "backup-db: age recipients file not found: $RECIPIENTS" >&2
    exit 1
fi
if [[ ! "$LABEL" =~ ^[a-z0-9-]+$ ]]; then
    echo "backup-db: label must match [a-z0-9-]+" >&2
    exit 1
fi

umask 077
mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/money-flow-$(date -u +%Y%m%d-%H%M%S)-$LABEL.sql.age"
TMP="$OUT.partial"
trap 'rm -f "$TMP"' EXIT

cd "$COMPOSE_DIR"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
    | age -R "$RECIPIENTS" -o "$TMP"

#---- A dump of this schema is never this small (age adds ~200 bytes of header); treat it as a failure.
if [[ "$(wc -c < "$TMP")" -lt 1200 ]]; then
    echo "backup-db: backup too small, discarded" >&2
    exit 1
fi
mv "$TMP" "$OUT"

# Only scheduled backups rotate; labelled ones (pre-deploy, manual) are kept until removed by hand.
find "$BACKUP_DIR" -maxdepth 1 -name 'money-flow-*-daily.sql.age' -mtime +"$RETENTION_DAYS" -delete

echo "$OUT"
