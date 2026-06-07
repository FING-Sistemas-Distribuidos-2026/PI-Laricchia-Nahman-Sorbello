#!/bin/bash

API="http://localhost:8080"
TOTAL=${1:-50}

echo "Cargando $TOTAL usuarios en cola..."

for i in $(seq 1 "$TOTAL"); do
  USER_ID=$(curl -s -X POST "$API/api/usuarios" | grep -oP '"id"\s*:\s*"\K[^"]+')

  if [ -z "$USER_ID" ]; then
    echo "[$i] ERROR creando usuario"
    continue
  fi

  RESPONSE=$(curl -s -X POST "$API/api/queue/join" \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"$USER_ID\"}")

  echo "[$i] userId=$USER_ID -> $RESPONSE"

  echo "$USER_ID" >> users_test.txt
done

echo "Listo. Usuarios guardados en users_test.txt"
