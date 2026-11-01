#!/bin/bash
# burst_test.sh - fires N concurrent requests from the SAME client id
# so you can see the token bucket allow some and reject the rest with 429.
#
# Usage: ./burst_test.sh [num_requests] [client_id]

N=${1:-10}
CLIENT=${2:-clientA}

echo "Firing $N concurrent requests as client '$CLIENT'..."
echo "-----------------------------------------------------"

for i in $(seq 1 "$N"); do
  (
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
      -H "X-Client-Id: $CLIENT" \
      http://localhost:8080/api/ping)
    echo "Request $i -> HTTP $STATUS"
  ) &
done

wait
echo "-----------------------------------------------------"
echo "Done. With default config (capacity=5, refill-rate=1/s), expect"
echo "roughly the first 5 requests to return 200 and the rest 429."
