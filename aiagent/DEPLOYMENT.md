# 多智能体编排平台 — 部署文档

## 目录

1. [架构总览](#架构总览)
2. [基础设施依赖](#基础设施依赖)
3. [MySQL 部署](#mysql-部署)
4. [Redis 部署](#redis-部署)
5. [Milvus 向量库部署](#milvus-向量库部署)
6. [Nacos 配置中心部署](#nacos-配置中心部署)
7. [后端服务部署](#后端服务部署)
8. [前端部署](#前端部署)
9. [环境变量汇总](#环境变量汇总)
10. [Docker Compose 一键部署](#docker-compose-一键部署)
11. [健康检查与验证](#健康检查与验证)
12. [常见问题](#常见问题)

---

## 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                     用户浏览器                           │
│              Vue 3 + Element Plus (Nginx)                │
└──────────────────────┬──────────────────────────────────┘
                       │ WebSocket (STOMP/SockJS) + HTTP
┌──────────────────────▼──────────────────────────────────┐
│              后端服务 (Spring Boot 3, :8080)              │
│   api / service / adapter / infrastructure 四模块        │
└──┬──────────┬──────────┬──────────┬──────────┬──────────┘
   │          │          │          │          │
  MySQL     Redis      Milvus     Nacos      LLM API
  :3306     :6379      :19530     :8848    (OpenAI/Qwen)
```

---

## 基础设施依赖

| 组件 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 17+ | 后端运行时 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0+ | 对话/消息持久化 |
| Redis | 7.0+ | Skill 配置缓存、会话缓存 |
| Milvus | 2.3+ | 向量存储（RAG 知识库、长期记忆） |
| Nacos | 2.3+ | 配置中心、Skill 热加载 |
| Nginx | 1.24+ | 前端静态文件服务 + 反向代理 |

---

## MySQL 部署

### Docker 方式（推荐）

```bash
docker run -d \
  --name multi-agent-mysql \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=multi_agent \
  -e MYSQL_USER=multiagent \
  -e MYSQL_PASSWORD=your_password \
  -p 3306:3306 \
  -v /data/mysql:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

### 初始化数据库表

启动后执行建表 SQL（项目已提供）：

```bash
mysql -h 127.0.0.1 -u multiagent -p multi_agent < \
  backend/infrastructure/src/main/resources/db/migration/V1__init_schema.sql
```

建表内容包含：
- `conversation` — 对话记录表
- `message` — 消息记录表（含 traceId、attachmentsJson 字段）

### 生产配置建议

```ini
# /etc/mysql/conf.d/multi-agent.cnf
[mysqld]
max_connections = 500
innodb_buffer_pool_size = 2G
slow_query_log = ON
long_query_time = 1
```

---

## Redis 部署

### Docker 方式

```bash
docker run -d \
  --name multi-agent-redis \
  -p 6379:6379 \
  -v /data/redis:/data \
  redis:7.0 \
  redis-server --requirepass your_redis_password --appendonly yes
```

### 用途说明

| Key 前缀 | 用途 | TTL |
|---------|------|-----|
| `skill:config:{skillId}` | Skill 配置缓存 | 1 小时 |
| `agent:registry:{agentId}` | Agent 注册信息缓存 | 1 小时 |

### 生产配置建议

```bash
# 内存策略：超出时淘汰最近最少使用的 key
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

---

## Milvus 向量库部署

Milvus 用于 RAG 知识库检索和对话长期记忆存储。

### 单机部署（Standalone）

```bash
# 下载 docker-compose 配置
wget https://github.com/milvus-io/milvus/releases/download/v2.3.4/milvus-standalone-docker-compose.yml \
  -O milvus-docker-compose.yml

# 启动
docker compose -f milvus-docker-compose.yml up -d
```

启动后包含三个容器：
- `milvus-standalone` — 主服务，端口 19530
- `milvus-etcd` — 元数据存储
- `milvus-minio` — 对象存储

### 手动 Docker 方式

```bash
# 先启动 etcd
docker run -d \
  --name milvus-etcd \
  -e ALLOW_NONE_AUTHENTICATION=yes \
  -v /data/milvus/etcd:/bitnami/etcd \
  bitnami/etcd:3.5

# 再启动 minio
docker run -d \
  --name milvus-minio \
  -e MINIO_ACCESS_KEY=minioadmin \
  -e MINIO_SECRET_KEY=minioadmin \
  -v /data/milvus/minio:/data \
  minio/minio:RELEASE.2023-03-13T19-46-17Z \
  server /data

# 最后启动 milvus
docker run -d \
  --name milvus-standalone \
  --link milvus-etcd:etcd \
  --link milvus-minio:minio \
  -e ETCD_ENDPOINTS=etcd:2379 \
  -e MINIO_ADDRESS=minio:9000 \
  -p 19530:19530 \
  -v /data/milvus/data:/var/lib/milvus \
  milvusdb/milvus:v2.3.4 \
  milvus run standalone
```

### 创建 Collection

项目启动后会自动连接，但需要提前创建 collection（或在代码中自动创建）：

```python
# 使用 pymilvus 创建 collection（可选，代码中也可自动创建）
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType

connections.connect(host="localhost", port="19530")

fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="session_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=4096),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1536),
    FieldSchema(name="created_at", dtype=DataType.INT64),
]
schema = CollectionSchema(fields, description="knowledge_fragments")
collection = Collection("knowledge_fragments", schema)

# 创建 IVF_FLAT 索引
collection.create_index("embedding", {
    "index_type": "IVF_FLAT",
    "metric_type": "COSINE",
    "params": {"nlist": 128}
})
```

---

## Nacos 配置中心部署

Nacos 用于 Skill 配置热加载和服务注册发现。

### Docker 单机部署

```bash
docker run -d \
  --name nacos-standalone \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=your_mysql_host \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos \
  -e MYSQL_SERVICE_USER=nacos \
  -e MYSQL_SERVICE_PASSWORD=nacos_password \
  -p 8848:8848 \
  -p 9848:9848 \
  -v /data/nacos/logs:/home/nacos/logs \
  nacos/nacos-server:v2.3.0
```

> 如果不想依赖 MySQL，可以使用内嵌数据库（仅开发环境）：
> 去掉所有 `MYSQL_*` 环境变量即可。

### 初始化 Nacos 数据库（生产环境）

```bash
# 下载 Nacos MySQL 初始化脚本
wget https://raw.githubusercontent.com/alibaba/nacos/2.3.0/distribution/conf/mysql-schema.sql

# 创建 nacos 数据库并执行
mysql -u root -p -e "CREATE DATABASE nacos CHARACTER SET utf8mb4;"
mysql -u root -p nacos < mysql-schema.sql
```

### 配置 Skill YAML

登录 Nacos 控制台（http://localhost:8848/nacos，默认账号 nacos/nacos），创建配置：

- **Data ID**: `skill-config.yaml`
- **Group**: `DEFAULT_GROUP`
- **Namespace**: `multi-agent`
- **内容示例**:

```yaml
id: disk-space-diagnose
name: 磁盘空间分析
description: 分析指定实例或地域的磁盘空间使用情况，给出优化建议
category: diagnose
agentId: rds-diagnose-agent
inputParams:
  - name: instanceId
    type: instance_selector
    required: false
  - name: region
    type: enum
    required: false
    enumValues: [cn-hangzhou, cn-shanghai, cn-beijing]
workflow:
  steps:
    - id: collect_metrics
      type: tool_call
      toolName: rds-disk-metrics
      outputKey: diskMetrics
    - id: analyze
      type: llm_call
      prompt: |
        根据以下磁盘使用数据，分析空间使用趋势并给出优化建议：
        {{diskMetrics}}
      outputKey: analysis
```

---

## 后端服务部署

### 环境要求

- JDK 17+（推荐 Eclipse Temurin 或 Amazon Corretto）
- Maven 3.8+

### 构建

```bash
cd backend

# 跳过测试快速构建
mvn clean package -DskipTests

# 构建产物位于
ls api/target/multi-agent-api-1.0.0-SNAPSHOT.jar
```

### 配置环境变量

创建 `.env` 文件或直接设置系统环境变量：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DB=multi_agent
export MYSQL_USER=multiagent
export MYSQL_PASSWORD=your_password

export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password

export MILVUS_HOST=localhost
export MILVUS_PORT=19530

export NACOS_SERVER_ADDR=localhost:8848
export NACOS_NAMESPACE=multi-agent

export OPENAI_API_KEY=sk-your-api-key
export OPENAI_BASE_URL=https://api.openai.com
export AI_MODEL=gpt-4

# 生产环境必须修改此密钥（至少 32 字符）
export JWT_SECRET=your-production-secret-key-at-least-32-chars

export WS_ALLOWED_ORIGINS=https://your-domain.com
```

### 启动

```bash
java -jar api/target/multi-agent-api-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 使用国内 LLM（通义千问 / 其他 OpenAI 兼容接口）

修改环境变量指向兼容接口：

```bash
# 通义千问
export OPENAI_API_KEY=sk-your-qwen-key
export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export AI_MODEL=qwen-max

# 智谱 GLM
export OPENAI_API_KEY=your-zhipu-key
export OPENAI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
export AI_MODEL=glm-4
```

### Systemd 服务（Linux 生产环境）

```ini
# /etc/systemd/system/multi-agent.service
[Unit]
Description=Multi-Agent Orchestration Platform
After=network.target mysql.service redis.service

[Service]
Type=simple
User=multiagent
WorkingDirectory=/opt/multi-agent
EnvironmentFile=/opt/multi-agent/.env
ExecStart=/usr/bin/java -Xms512m -Xmx2g \
  -jar /opt/multi-agent/multi-agent-api.jar \
  --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable multi-agent
systemctl start multi-agent
systemctl status multi-agent
```

---

## 前端部署

### 环境要求

- Node.js 18+
- npm 9+ 或 pnpm 8+

### 构建

```bash
cd frontend

# 安装依赖
npm install

# 生产构建
npm run build

# 产物位于 frontend/dist/
ls dist/
```

### Nginx 配置

```nginx
# /etc/nginx/conf.d/multi-agent.conf
server {
    listen 80;
    server_name your-domain.com;

    # 重定向到 HTTPS（生产环境）
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/ssl/certs/your-domain.crt;
    ssl_certificate_key /etc/ssl/private/your-domain.key;

    # 前端静态文件
    root /var/www/multi-agent/dist;
    index index.html;

    # Vue Router history 模式支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反向代理后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 代理（关键：需要升级协议）
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|ico|woff2)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

```bash
# 部署前端文件
cp -r frontend/dist/* /var/www/multi-agent/dist/

# 测试并重载 Nginx
nginx -t && nginx -s reload
```

---

## 环境变量汇总

| 变量名 | 默认值 | 说明 | 是否必须 |
|--------|--------|------|---------|
| `MYSQL_HOST` | `localhost` | MySQL 主机 | 是 |
| `MYSQL_PORT` | `3306` | MySQL 端口 | 否 |
| `MYSQL_DB` | `multi_agent` | 数据库名 | 是 |
| `MYSQL_USER` | `root` | 数据库用户 | 是 |
| `MYSQL_PASSWORD` | `root` | 数据库密码 | 是 |
| `REDIS_HOST` | `localhost` | Redis 主机 | 是 |
| `REDIS_PORT` | `6379` | Redis 端口 | 否 |
| `REDIS_PASSWORD` | _(空)_ | Redis 密码 | 否 |
| `MILVUS_HOST` | `localhost` | Milvus 主机 | 是 |
| `MILVUS_PORT` | `19530` | Milvus 端口 | 否 |
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 地址 | 是 |
| `NACOS_NAMESPACE` | `multi-agent` | Nacos 命名空间 | 否 |
| `OPENAI_API_KEY` | _(必填)_ | LLM API Key | 是 |
| `OPENAI_BASE_URL` | `https://api.openai.com` | LLM 接口地址 | 否 |
| `AI_MODEL` | `gpt-4` | 使用的模型名 | 否 |
| `JWT_SECRET` | _(默认值，生产必改)_ | JWT 签名密钥 | 是 |
| `WS_ALLOWED_ORIGINS` | `*` | WebSocket 允许的来源 | 生产必填 |

---

## Docker Compose 一键部署

将以下内容保存为项目根目录的 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: multi-agent-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-rootpassword}
      MYSQL_DATABASE: multi_agent
      MYSQL_USER: multiagent
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-multiagent123}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./backend/infrastructure/src/main/resources/db/migration/V1__init_schema.sql:/docker-entrypoint-initdb.d/init.sql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.0
    container_name: multi-agent-redis
    command: redis-server --requirepass ${REDIS_PASSWORD:-redis123} --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: milvus-etcd
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
    volumes:
      - etcd_data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  minio:
    image: minio/minio:RELEASE.2023-03-13T19-46-17Z
    container_name: milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/data
    command: minio server /data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    image: milvusdb/milvus:v2.3.4
    container_name: milvus-standalone
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus_data:/var/lib/milvus
    ports:
      - "19530:19530"
    depends_on:
      - etcd
      - minio
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      timeout: 20s
      retries: 3

  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: nacos-standalone
    environment:
      MODE: standalone
      PREFER_HOST_MODE: hostname
    ports:
      - "8848:8848"
      - "9848:9848"
    volumes:
      - nacos_data:/home/nacos/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: multi-agent-backend
    ports:
      - "8080:8080"
    environment:
      MYSQL_HOST: mysql
      MYSQL_DB: multi_agent
      MYSQL_USER: multiagent
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-multiagent123}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
      MILVUS_HOST: milvus
      NACOS_SERVER_ADDR: nacos:8848
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      OPENAI_BASE_URL: ${OPENAI_BASE_URL:-https://api.openai.com}
      AI_MODEL: ${AI_MODEL:-gpt-4}
      JWT_SECRET: ${JWT_SECRET:-change-this-in-production-32chars}
      WS_ALLOWED_ORIGINS: ${WS_ALLOWED_ORIGINS:-*}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      milvus:
        condition: service_healthy
      nacos:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: multi-agent-frontend
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend

volumes:
  mysql_data:
  redis_data:
  etcd_data:
  minio_data:
  milvus_data:
  nacos_data:
```

### 后端 Dockerfile

创建 `backend/Dockerfile`：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY api/pom.xml api/
COPY service/pom.xml service/
COPY adapter/pom.xml adapter/
COPY infrastructure/pom.xml infrastructure/
RUN mvn dependency:go-offline -q
COPY . .
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/api/target/multi-agent-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "app.jar"]
```

### 前端 Dockerfile

创建 `frontend/Dockerfile`：

```dockerfile
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.24-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

创建 `frontend/nginx.conf`：

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```

### 启动命令

```bash
# 创建 .env 文件
cat > .env << EOF
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_PASSWORD=multiagent123
REDIS_PASSWORD=redis123
OPENAI_API_KEY=sk-your-api-key
JWT_SECRET=your-production-secret-key-32chars
WS_ALLOWED_ORIGINS=http://localhost
EOF

# 一键启动所有服务
docker compose up -d

# 查看启动状态
docker compose ps

# 查看后端日志
docker compose logs -f backend
```

---

## 健康检查与验证

### 1. 检查各服务状态

```bash
# MySQL
mysql -h 127.0.0.1 -u multiagent -p multi_agent -e "SHOW TABLES;"

# Redis
redis-cli -h 127.0.0.1 -a your_password ping
# 期望输出: PONG

# Milvus
curl http://localhost:9091/healthz
# 期望输出: OK

# Nacos
curl http://localhost:8848/nacos/actuator/health
# 期望输出: {"status":"UP"}

# 后端服务
curl http://localhost:8080/actuator/health
# 期望输出: {"status":"UP"}
```

### 2. 验证 JWT 认证

```bash
# 获取 Token
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"userId": "test-user-001"}'

# 期望输出
# {"token":"eyJ...","userId":"test-user-001"}
```

### 3. 验证 WebSocket 连接

打开浏览器访问 `http://localhost`，打开开发者工具 Network 面板，
应能看到 `/ws/info` 的 HTTP 请求和后续的 WebSocket 升级连接。

### 4. 查看 Metrics

```bash
# Prometheus 格式指标
curl http://localhost:8080/actuator/prometheus | grep agent_first_byte

# 期望看到
# agent_first_byte_latency_seconds_count{...} 0.0
```

---

## 常见问题

### Q: 后端启动报 `Cannot connect to Nacos`

Nacos 启动较慢（约 30-60 秒），等待 Nacos 完全就绪后再启动后端。
或在 `application.yml` 中添加：

```yaml
spring:
  cloud:
    nacos:
      config:
        fail-fast: false  # 连接失败时不阻止启动
```

### Q: Milvus 连接超时

Milvus 依赖 etcd 和 minio，需要等待这两个服务完全就绪。
检查顺序：etcd → minio → milvus。

```bash
docker logs milvus-standalone | tail -20
```

### Q: WebSocket 连接被 Nginx 断开

确保 Nginx 配置了正确的超时时间和协议升级头：

```nginx
proxy_read_timeout 3600s;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

### Q: LLM 调用失败（国内网络）

使用国内兼容接口，修改环境变量：

```bash
# 通义千问（推荐）
OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_API_KEY=sk-your-dashscope-key
AI_MODEL=qwen-max
```

### Q: JWT 密钥警告

生产环境必须设置足够长的密钥（至少 32 字符），否则 jjwt 会抛出异常：

```bash
JWT_SECRET=$(openssl rand -base64 48)
echo $JWT_SECRET  # 保存此值
```

### Q: 前端 @ 和 / 选择器无数据

前端 `ChatView.vue` 中的 `agents` 和 `skills` 目前是空数组（demo 数据），
需要对接后端 API 接口获取真实数据。后端提供：
- `GET /api/agents` — 获取 Agent 列表
- `GET /api/skills?category=DIAGNOSE` — 按分类获取 Skill 列表
