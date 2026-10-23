#!/usr/bin/env bash
#---- Daily Postgres backup for the VPS. Runs from cron (see ops/money-flow-backup.cron).
#---- Dumps the database through the running compose service, gzips it and keeps the last RETENTION_DAYS days.
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/money-flow}"
BACKUP_DIR="${BACKUP_DIR:-/root/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
cd "$PROJECT_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
target="$BACKUP_DIR/money-flow-daily-$timestamp.sql.gz"
partial="$target.partial"

#---- Write to a .partial file first so a failed dump never looks like a valid backup.
trap 'rm -f "$partial"' EXIT
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$partial"

#---- A dump of this schema is never this small; treat it as a failure.
if [ "$(gzip -dc "$partial" | wc -c)" -lt 1000 ]; then
    echo "$(date -Is) backup too small, discarded" >&2
    exit 1
fi

mv "$partial" "$target"
trap - EXIT

#---- Only daily backups rotate; manual pre-deploy dumps are left alone.
find "$BACKUP_DIR" -name 'money-flow-daily-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete

echo "$(date -Is) backup ok: $target"
