# rating-service

Stateless OpenSkill (PlackettLuce) calculator for the Duels plugin.

## Deploy (Tailnet-only)

1. Install Tailscale on the host. Note the Tailnet IP (e.g., `100.64.0.1`).
2. Copy `.env.example` -> `.env`, set `RATING_SHARED_SECRET` (32+ chars random) and `RATING_BIND_HOST` to the Tailnet IP.
3. `docker compose up -d --build`
4. Verify: `curl http://100.64.0.1:8088/healthz` from another Tailnet node.
5. Confirm the public interface refuses the port: `nc -zv <public-ip> 8088` -> refused.

## API

All POST endpoints require HMAC headers:
- `X-Rating-Timestamp`: unix seconds
- `X-Rating-Signature`: hex(HMAC-SHA256(secret, timestamp + "." + body))

Example signed `/v1/predict` request:

```bash
export RATING_URL=http://100.64.0.1:8088
export RATING_SHARED_SECRET='replace-with-the-same-secret-from-.env'

cat > /tmp/rating-body.json <<'JSON'
{"teams":[{"rank":0,"players":[{"uuid":"00000000-0000-0000-0000-000000000001","mu":25.0,"sigma":8.333}]},{"rank":1,"players":[{"uuid":"00000000-0000-0000-0000-000000000002","mu":25.0,"sigma":8.333}]}]}
JSON

export TS=$(date +%s)
sig=$(python - <<'PY'
import hashlib
import hmac
import os

body = open("/tmp/rating-body.json", "rb").read()
timestamp = os.environ["TS"].encode()
secret = os.environ["RATING_SHARED_SECRET"].encode()
print(hmac.new(secret, timestamp + b"." + body, hashlib.sha256).hexdigest())
PY
)

curl -sS "$RATING_URL/v1/predict" \
  -H "Content-Type: application/json" \
  -H "X-Rating-Timestamp: $TS" \
  -H "X-Rating-Signature: $sig" \
  --data-binary @/tmp/rating-body.json
```

### POST /v1/rate

Body: `{ "mode_id": "...", "teams": [{"rank": 0, "players": [{"uuid": "...", "mu": 25.0, "sigma": 8.333}]}, ...] }`
Response: per-player `mu_after`, `sigma_after`, `ordinal_after`, `delta_ordinal`.

### POST /v1/predict

Body: `{ "teams": [...] }` -> `{ "win_probabilities": [...], "draw_probability": ... }`

### GET /healthz

No auth. Returns `{"status":"ok"}`.
