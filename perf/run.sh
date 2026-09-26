#!/usr/bin/env bash
# Pokretanje: perf/run.sh monolith|services [trajanje, npr. 60s]
# Prije mjerenja brise karte iz prethodnih k6 pokretanja (da obje arhitekture
# krecu od istog stanja baze) i radi kratko zagrijavanje JVM-a koje se ne broji.
set -e
cd "$(dirname "$0")/.."
TARGET=${1:?monolith ili services}
DURATION=${2:-60s}
DB=$([ "$TARGET" = monolith ] && echo pozoriste || echo ticketing_db)
PGPASSWORD=postgres "/c/Program Files/PostgreSQL/17/bin/psql.exe" -U postgres -h localhost -d "$DB" -qc \
  "delete from teatar_ulaznica where ulaznica_id like 'k6-%' or ulaznica_id like 'test-%'"
RUN_ID=$(date +%Y%m%d-%H%M%S)
echo ">> zagrijavanje ($TARGET, 15s)"
k6 run -q -e TARGET=$TARGET -e DURATION=15s -e RUN_ID=warmup perf/benchmark.js > /dev/null
PGPASSWORD=postgres "/c/Program Files/PostgreSQL/17/bin/psql.exe" -U postgres -h localhost -d "$DB" -qc \
  "delete from teatar_ulaznica where ulaznica_id like 'k6-%'"
rm -f perf/results/$TARGET-warmup.json
echo ">> mjerenje ($TARGET, 2 x $DURATION)"
k6 run -e TARGET=$TARGET -e DURATION=$DURATION -e RUN_ID=$RUN_ID perf/benchmark.js
