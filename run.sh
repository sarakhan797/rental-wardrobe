#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

docker start rental-wardrobe-db >/dev/null

for attempt in {1..60}; do
  if docker exec rental-wardrobe-db mysqladmin ping -h 127.0.0.1 --silent \
      >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "MySQL did not start. Check: docker logs rental-wardrobe-db"
    exit 1
  fi
  sleep 1
done

export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/rental_wardrobe?sslMode=DISABLED&allowPublicKeyRetrieval=true'
export SPRING_DATASOURCE_USERNAME='wardrobe_app'
SPRING_DATASOURCE_PASSWORD="$(docker exec rental-wardrobe-db printenv MYSQL_PASSWORD)"
export SPRING_DATASOURCE_PASSWORD
export SPRING_SQL_INIT_MODE=always

exec mvn spring-boot:run
