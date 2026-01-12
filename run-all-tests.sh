#!/usr/bin/env bash
set -euo pipefail

GW="http://localhost:8090"

echo "===== 如果返回值出现了不符合预期的500，应该是微服务间连接不通畅，可以将验证的命令单独拿运行一下 ====="
sleep 5

echo "===== 1. 获取Token ====="
export TOKEN="$(
  curl -s -X POST "$GW/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"lbtest","email":"lbtest@example.com","password":"123456"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])' 2>/dev/null || \
  curl -s -X POST "$GW/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"lbtest","password":"123456"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'
)"
echo "TOKEN=${TOKEN:0:24}..."

echo "===== 2. 网关鉴权测试 ====="
echo "2.1 不带Token请求收藏（预期401）"
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "$GW/api/favorites" \
  -H "Content-Type: application/json" \
  -d '{"songName":"十年"}'

echo "2.2 带Token请求收藏（预期200或者被收藏过了就是409）"
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "$GW/api/favorites" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"songName":"十年"}'

echo "===== 3. 负载均衡10次调用 ====="
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "$GW/api/favorites/username" \
    -H "Authorization: Bearer $TOKEN"
done

echo "===== 3.1 打印 user-service 容器日志 ====="
docker logs --tail 5 music-microservices-user-service-1
docker logs --tail 5 music-microservices-user-service-2-1

echo "===== 4. 熔断降级测试 ====="
docker compose down user-service user-service-2
sleep 20
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "$GW/api/favorites/username" \
    -H "Authorization: Bearer $TOKEN"
done
curl -i -s "$GW/api/favorites/username" -H "Authorization: Bearer $TOKEN"
docker compose up -d user-service
sleep 20
docker compose up -d user-service-2
sleep 20

export GW="http://localhost:8090"

echo "===== 5. 异步队列+数据一致性测试 ====="
export USERNAME="mq_$(date +%s)"
export PASSWORD="123456"
export EMAIL="${USERNAME}@example.com"
resp="$(curl -s -X POST "$GW/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
TOKEN="$(echo "$resp" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')"
USER_ID="$(echo "$resp" | python3 -c 'import sys,json;print(json.load(sys.stdin)["user"]["id"])')"

echo "5.1 创建歌曲"
SONG_ID="$(curl -s -X POST "$GW/api/meta" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"songName\":\"MQ Song$(date +%s)\",\"artist\":\"MQ Artist\"}" \
| python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')"

echo "5.2 收藏歌曲"
curl -s -X POST "$GW/api/favorites" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"songName":"MQ Song"}'

echo "5.3 删除用户触发user.deleted事件"
curl -s -X DELETE "$GW/api/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN" -i

echo "5.4 验证异步消息队列"
docker logs --tail 50 music-microservices-user-service-1 2>&1 | grep -i 'outbox\|consumed' || true
docker logs --tail 50 music-microservices-list-service-1 2>&1 | grep -i 'consumed\|outbox' || true

echo "5.5 验证数据一致性"
sleep 5
curl -s "$GW/api/favorites/username" -H "Authorization: Bearer $TOKEN"

echo
echo "===== 以上命令验证了负载均衡/熔断降级/JWT鉴权/异步队列+数据一致性，下面的功能需要容器的可视化界面辅助才能进行验证 ====="
echo "===== 注册/配置中心：Nacos：http://localhost:8080 ====="
echo "===== 服务监控：Prometheus：http://localhost:9090/targets  Grafana：http://localhost:3000 ====="
echo "===== 链路追踪：Zipkin：http://localhost:9411 ====="