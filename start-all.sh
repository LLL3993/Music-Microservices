#!/usr/bin/env bash

services=("gateway-service" "user-service" "meta-service" "list-service")

echo "========== 开始 Maven 打包 =========="
for s in "${services[@]}"; do
  echo ">>> 打包 $s ..."
  cd "$s"
  mvn clean package -DskipTests -q
  cd ..
done

echo "========== 准备数据目录权限 =========="
chmod -R 755 ./data ./music-web/dist

echo "========== Docker 镜像构建 =========="
docker compose build

echo "========== 容器一次性全部启动可能会出现虚拟机卡死或者容器无法成功启动的情况，这里分几步启动，并且在容器启动之间保留一定的时间间隔，如果还是出现了容器无法启动的情况，可以加大时间间隔，确保在启动下一个容器之前上一个容器已经成功启动了 =========="
sleep 5

echo "========== 启动基础设施 =========="
docker compose up -d rabbitmq nacos
sleep 30

echo "========== 启动业务服务 =========="
docker compose up -d gateway-service user-service
sleep 30
docker compose up -d meta-service list-service
sleep 30
docker compose up -d music-web
sleep 30

echo "========== 启动剩余的容器 =========="
docker compose up -d
sleep 30

echo "========== 一键部署完成！ =========="
docker compose ps

echo "========== 初始化 meta_db 数据 =========="
docker exec -i music-microservices-meta-mysql-1 \
mysql -uroot -padmin --default-character-set=utf8mb4 meta_db <<'EOF'
SET NAMES utf8mb4;
INSERT INTO meta (song_name, artist) VALUES
('十年', '陈奕迅'),
('克罗地亚狂想曲', '马克西姆'),
('卡农', '约翰·帕赫贝尔'),
('寂寞沙洲冷', '周传雄'),
('Counting Stars', 'OneRepublic'),
('曹操', '林俊杰');
EOF

echo "========== 初始化 user_db 数据 =========="
docker exec -i music-microservices-user-mysql-1 \
mysql -uroot -padmin --default-character-set=utf8mb4 user_db <<'EOF'
SET NAMES utf8mb4;
INSERT INTO user (username, email, password, is_admin) VALUES
('admin', 'admin@admin.com', 'admin', 1);
EOF

echo "========== 数据初始化完成！ =========="

echo "========== 如果上面有容器未能成功启动，请再执行一遍这个命令：docker compose up -d =========="