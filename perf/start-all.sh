#!/usr/bin/env bash
# Pokrece monolit (8084), users-service (8081), ticketing-service (8082) i
# api-gateway (8090) kao obicne java procese sa istim JVM podesavanjima.
# Mail ide na lokalni Mailpit (docker, port 1025) umjesto na Gmail, da slanje
# potvrde ne zavisi od interneta i ne salje prave mailove.
cd "$(dirname "$0")/.."
MAIL="--spring.mail.host=localhost --spring.mail.port=1025 \
 --spring.mail.properties.mail.smtp.auth=false \
 --spring.mail.properties.mail.smtp.starttls.enable=false \
 --spring.mail.properties.mail.smtp.starttls.required=false"
JVM="-Xmx512m"
mkdir -p perf/logs
docker start mailpit >/dev/null 2>&1 || docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit >/dev/null

java $JVM -jar backend/demo/target/demo-0.0.1-SNAPSHOT.war --spring.profiles.active=local $MAIL > perf/logs/monolith.log 2>&1 &
java $JVM -jar services/users-service/target/users-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local > perf/logs/users.log 2>&1 &
java $JVM -jar services/ticketing-service/target/ticketing-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local $MAIL > perf/logs/ticketing.log 2>&1 &
java $JVM -jar services/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar > perf/logs/gateway.log 2>&1 &

for port in 8084 8081 8082 8090; do
  until curl -s -o /dev/null "http://localhost:$port/"; do sleep 1; done
  echo "port $port gore"
done
