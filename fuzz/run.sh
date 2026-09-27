#!/usr/bin/env bash
# Fuzz testiranje Schemathesis-om: perf/start-all.sh mora biti pokrenut.
#   fuzz/run.sh monolith|users|ticketing
# Prije pokretanja vraca baze iz backupa (~/Desktop/db_backup/*-prije-fuzz.dump),
# jer fuzzer salje i POST/PUT/PATCH/DELETE zahtjeve koji mijenjaju podatke.
set -e
cd "$(dirname "$0")/.."
TARGET=${1:?monolith, users ili ticketing}
case $TARGET in
  monolith)  PORT=8084; DBS="pozoriste" ;;
  users)     PORT=8081; DBS="users_db" ;;
  ticketing) PORT=8082; DBS="ticketing_db users_db" ;;  # ticketing zove i users-service
esac
PG="/c/Program Files/PostgreSQL/17/bin"
export PGPASSWORD=postgres PYTHONIOENCODING=utf-8 PYTHONUTF8=1
for db in $DBS; do
  # pg_restore 17 na serveru 16 prijavi jednu bezopasnu gresku (SET transaction_timeout), zato || true
  "$PG/pg_restore.exe" -U postgres -h localhost -d $db --clean --if-exists "$HOME/Desktop/db_backup/$db-prije-fuzz.dump" 2>/dev/null || true
done
mkdir -p "fuzz/results/${KRUG:-krug2}"
python -m schemathesis.cli run "http://localhost:$PORT/v3/api-docs" \
  --checks all --max-examples ${MAX_EXAMPLES:-100} --seed 42 --no-color \
  --report junit --report-dir "fuzz/results/${KRUG:-krug2}/$TARGET" \
  > "fuzz/results/${KRUG:-krug2}/$TARGET.txt" 2>&1 || true
sed -n '/SUMMARY/,$p' "fuzz/results/${KRUG:-krug2}/$TARGET.txt"
