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
# RabbitMQ za sagu brisanja gledaoca (konzola: http://localhost:15672, guest/guest).
# "-u rabbitmq": bez toga na Docker Desktopu (Windows) pada sa "erlang.cookie: eacces"
docker start rabbitmq >/dev/null 2>&1 || docker run -d --name rabbitmq --hostname rabbit -u rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management >/dev/null
for i in $(seq 1 60); do docker exec rabbitmq rabbitmq-diagnostics -q check_port_connectivity >/dev/null 2>&1 && break; sleep 1; done
echo "rabbitmq gore"

java $JVM -jar backend/demo/target/demo-0.0.1-SNAPSHOT.war --spring.profiles.active=local $MAIL > perf/logs/monolith.log 2>&1 &
java $JVM -jar services/users-service/target/users-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local > perf/logs/users.log 2>&1 &
java $JVM -jar services/ticketing-service/target/ticketing-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local $MAIL > perf/logs/ticketing.log 2>&1 &
java $JVM -jar services/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar > perf/logs/gateway.log 2>&1 &

for port in 8084 8081 8082 8090; do
  until curl -s -o /dev/null "http://localhost:$port/"; do sleep 1; done
  echo "port $port gore"
done
