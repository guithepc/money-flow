# Backups

Database backups are encrypted with [age](https://age-encryption.org) before they touch the disk (#25).

- `ops/backup-db.sh [label]` pipes `pg_dump` straight into `age`, so no plain-text dump is ever written.
- The server only has the age **public** key (`/root/.config/money-flow/backup-recipients.txt`). Anyone with access to the VPS can create backups but cannot read them.
- The **private** key lives off the server, with the owner (`~/.config/money-flow/backup-age-key.txt`). Keep a copy in a password manager: without it the backups cannot be restored.
- Backups go to `/root/backups/money-flow-<UTC timestamp>-<label>.sql.age`, mode `600`.
- A systemd timer runs a `daily` backup at 03:30 UTC. Daily backups older than 30 days are deleted. Labelled backups (`pre-deploy`, `manual`, …) are kept until removed by hand.

## Setup on the VPS (once)

```bash
apt-get install -y age
mkdir -p /root/.config/money-flow && chmod 700 /root/.config/money-flow
# public key only — generated on the owner's machine with: age-keygen -o backup-age-key.txt
echo 'age1...' > /root/.config/money-flow/backup-recipients.txt
cp /opt/money-flow/ops/systemd/money-flow-backup.{service,timer} /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now money-flow-backup.timer
```

## Before a deploy

```bash
/opt/money-flow/ops/backup-db.sh pre-deploy
```

## Restore

On the machine that has the private key:

```bash
scp root@<vps>:/root/backups/money-flow-<...>.sql.age .
age -d -i ~/.config/money-flow/backup-age-key.txt money-flow-<...>.sql.age > dump.sql
```

Then on the VPS (this replaces the current database):

```bash
docker compose stop backend
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE \"$POSTGRES_DB\";" -c "CREATE DATABASE \"$POSTGRES_DB\";"'
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < dump.sql
docker compose up -d backend
rm dump.sql
```

## Rotating the key

Generate a new key pair, replace the public key on the VPS, and re-encrypt the backups you want to keep:
`age -d -i old.txt f.age | age -R new-recipients.txt -o f.new.age`.
