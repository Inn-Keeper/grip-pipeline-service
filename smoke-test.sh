#!/usr/bin/env bash
# Usage: ./smoke-test.sh <email> <password>
# Logs in via Supabase, captures the access token, then hits all three pipeline endpoints.

set -euo pipefail

EMAIL="${1:?Usage: $0 <email> <password>}"
PASSWORD="${2:?Usage: $0 <email> <password>}"

SUPABASE_URL="${SUPABASE_URL:?Set SUPABASE_URL=https://<project-ref>.supabase.co}"
ANON_KEY="${SUPABASE_ANON_KEY:?Set SUPABASE_ANON_KEY from Supabase dashboard → Settings → API}"

BASE_URL="http://localhost:8080"

echo "Logging in as $EMAIL …"
RESPONSE=$(curl -sS -X POST \
  "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null || true)

if [[ -z "$TOKEN" ]]; then
  echo "Login failed. Response:"
  echo "$RESPONSE"
  exit 1
fi

echo "Token obtained."
echo "$TOKEN" > .grip-token
echo "Saved to .grip-token"
echo ""

for ENDPOINT in funnel velocity due; do
  echo "--- /api/pipeline/$ENDPOINT ---"
  curl -sS \
    -H "Authorization: Bearer $TOKEN" \
    "$BASE_URL/api/pipeline/$ENDPOINT" | python3 -m json.tool
  echo ""
done
