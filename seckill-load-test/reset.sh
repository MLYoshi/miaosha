#!/usr/bin/env bash
# 压测后恢复：重跑种子 SQL（时间窗对齐当前、库存回 9）+ 删压测账号产生的订单 +
# 清空 Redis 秒杀 key + 丢弃 Kafka 积压（防止残留消息污染下一轮压测）。
#
# 压测账号（131 段）保留不删除：注册幂等、成本极低，删除后下次压测反而要重建。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SEED_SQL="${SCRIPT_DIR}/../backend/sql/fix-seed-time-window.sql"

if [ ! -f "$SEED_SQL" ]; then
  echo "未找到种子 SQL: ${SEED_SQL}"
  exit 1
fi

# 容器名/后端地址可用环境变量覆盖（多项目共存时，见 docker-compose.override.yaml）
MYSQL_CONTAINER="${MYSQL_CONTAINER:-seckill-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-seckill-redis}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-seckill-kafka}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-seckill-backend}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

wait_backend_healthy() {
  echo -n "==> 等待 backend 恢复健康"
  for _ in $(seq 1 90); do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      echo " OK"
      return 0
    fi
    sleep 1
  done
  echo " FAILED（backend 未在 90s 内恢复）"
  return 1
}

MYSQL=(docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot --default-character-set=utf8mb4 miaosha)

echo "==> [1/4] 删除压测账号（user_id >= 13100000000）产生的订单"
"${MYSQL[@]}" <<'SQL'
SET time_zone = '+08:00';
DELETE FROM miaosha_order WHERE user_id >= 13100000000;
DELETE FROM order_info   WHERE user_id >= 13100000000;
SQL

echo "==> [2/4] 重跑种子 SQL（恢复时间窗与库存 9，幂等）"
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot --default-character-set=utf8mb4 < "$SEED_SQL"

echo "==> [3/4] 清空 Redis 秒杀 key（miaosha:*）"
docker exec "${REDIS_CONTAINER}" sh -c \
  "redis-cli -a 123456 --no-auth-warning --scan --pattern 'miaosha:*' | xargs -r redis-cli -a 123456 --no-auth-warning DEL"

echo "==> [4/4] 丢弃 Kafka 积压（seckill-order / seckill-order-dlt）并重启 backend"
docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group seckill \
  --topic seckill-order --topic seckill-order-dlt \
  --reset-offsets --to-latest --execute >/dev/null 2>&1 \
  || echo "  （消费组不存在或位点已最新，跳过 reset）"
docker restart "${BACKEND_CONTAINER}" >/dev/null && wait_backend_healthy

echo "==> 恢复完成。"
