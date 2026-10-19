#!/usr/bin/env bash
# Apply backend/supabase migrations + seed to a throwaway local Postgres that
# emulates Supabase (scripts/db/supabase_stubs.sql) and run the pgTAP suite.
#
# Requirements: PostgreSQL server binaries (16+), pgTAP extension, pg_prove.
# Usage: scripts/db/test-local.sh            (PG_BIN overrides the server bin dir)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PG_BIN="${PG_BIN:-$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1)}"
PORT="${PGPORT_TEST:-54329}"
WORK="$(mktemp -d)"
RUN_AS=()
if [ "$(id -u)" = "0" ]; then
  chown postgres "$WORK"
  RUN_AS=(runuser -u postgres --)
fi

cleanup() { "${RUN_AS[@]}" "$PG_BIN/pg_ctl" -D "$WORK/data" -m immediate stop >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

"${RUN_AS[@]}" "$PG_BIN/initdb" -D "$WORK/data" -U postgres -A trust >/dev/null
"${RUN_AS[@]}" "$PG_BIN/pg_ctl" -D "$WORK/data" -o "-p $PORT -k $WORK -c listen_addresses=''" -l "$WORK/log" -w start >/dev/null

PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "$WORK" -p "$PORT" -U postgres -d postgres)

"${PSQL[@]}" -f "$ROOT/scripts/db/supabase_stubs.sql"
for f in "$ROOT"/backend/supabase/migrations/*.sql; do
  echo "migrate: $(basename "$f")"
  "${PSQL[@]}" -f "$f"
done
for f in "$ROOT"/backend/supabase/seed/*.sql; do
  echo "seed:    $(basename "$f")"
  "${PSQL[@]}" -f "$f"
done
"${PSQL[@]}" -c "create extension if not exists pgtap"

pg_prove -h "$WORK" -p "$PORT" -U postgres -d postgres "$ROOT"/backend/supabase/tests/database/*.sql
