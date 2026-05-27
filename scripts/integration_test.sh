#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-admin123}"

echo "[1/5] health check"
curl -sf "${BASE_URL}/api/health" >/dev/null

echo "[2/5] register device"
REGISTER_PAYLOAD='{"device_name":"联调测试机","phone_number":"13900001111"}'
REGISTER_RESPONSE=$(curl -sf -X POST "${BASE_URL}/api/devices/register" \
  -H "Content-Type: application/json" \
  -d "${REGISTER_PAYLOAD}")

DEVICE_ID=$(python3 - <<'PY' "${REGISTER_RESPONSE}"
import json, sys
print(json.loads(sys.argv[1])["device_id"])
PY
)
API_KEY=$(python3 - <<'PY' "${REGISTER_RESPONSE}"
import json, sys
print(json.loads(sys.argv[1])["api_key"])
PY
)

echo "device_id=${DEVICE_ID}"

echo "[3/5] send inbound sms"
INBOUND_PAYLOAD='{"sender":"10690000","body":"【测试】您的验证码是654321，请勿泄露。","received_at":"2026-05-27T12:00:00+08:00","phone_number":"13900001111"}'
INBOUND_RESPONSE=$(curl -sf -X POST "${BASE_URL}/api/sms/inbound" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: ${DEVICE_ID}" \
  -H "X-Api-Key: ${API_KEY}" \
  -d "${INBOUND_PAYLOAD}")

echo "${INBOUND_RESPONSE}"

echo "[4/5] verify sms list"
SMS_RESPONSE=$(curl -sf "${BASE_URL}/api/sms?admin_token=${ADMIN_TOKEN}&keyword=654321")
python3 - <<'PY' "${SMS_RESPONSE}"
import json, sys
payload = json.loads(sys.argv[1])
assert payload["total"] >= 1, "expected at least one sms"
first = payload["items"][0]
assert first["verification_code"] == "654321", first
print("verification_code ok")
PY

echo "[5/5] verify web page"
INDEX_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/?admin_token=${ADMIN_TOKEN}")
DEVICE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/device/${DEVICE_ID}?admin_token=${ADMIN_TOKEN}")

if [[ "${INDEX_CODE}" != "200" || "${DEVICE_CODE}" != "200" ]]; then
  echo "web page check failed: index=${INDEX_CODE}, device=${DEVICE_CODE}"
  exit 1
fi

echo "integration test passed"
