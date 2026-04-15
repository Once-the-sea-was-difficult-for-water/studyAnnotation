#!/usr/bin/env bash
# =============================================================================
# 多智能体编排平台 — 一键部署脚本
# 支持: macOS / Linux (Ubuntu 20.04+, CentOS 7+)
# 用法: bash deploy.sh [选项]
#   --env-file <path>   指定 .env 文件路径（默认 .env）
#   --skip-build        跳过构建步骤（直接使用已有镜像/JAR）
#   --skip-infra        跳过基础设施启动（MySQL/Redis/Milvus/Nacos）
#   --frontend-only     仅部署前端
#   --backend-only      仅部署后端
#   --down              停止并清理所有容器
#   --help              显示帮助
# =============================================================================

set -euo pipefail

# ── 颜色输出 ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()      { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_section() { echo -e "\n${BOLD}${CYAN}══════════════════════════════════════${NC}"; \
                echo -e "${BOLD}${CYAN}  $*${NC}"; \
                echo -e "${BOLD}${CYAN}══════════════════════════════════════${NC}"; }

# ── 默认参数 ──────────────────────────────────────────────────────────────────
ENV_FILE=".env"
SKIP_BUILD=false
SKIP_INFRA=false
FRONTEND_ONLY=false
BACKEND_ONLY=false
DO_DOWN=false

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── 解析参数 ──────────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    --env-file)      ENV_FILE="$2"; shift 2 ;;
    --skip-build)    SKIP_BUILD=true; shift ;;
    --skip-infra)    SKIP_INFRA=true; shift ;;
    --frontend-only) FRONTEND_ONLY=true; shift ;;
    --backend-only)  BACKEND_ONLY=true; shift ;;
    --down)          DO_DOWN=true; shift ;;
    --help)
      echo "用法: bash deploy.sh [选项]"
      echo "  --env-file <path>   指定 .env 文件路径（默认 .env）"
      echo "  --skip-build        跳过构建步骤"
      echo "  --skip-infra        跳过基础设施启动"
      echo "  --frontend-only     仅部署前端"
      echo "  --backend-only      仅部署后端"
      echo "  --down              停止并清理所有容器"
      exit 0 ;;
    *) log_error "未知参数: $1"; exit 1 ;;
  esac
done

# ── 停止模式 ──────────────────────────────────────────────────────────────────
if $DO_DOWN; then
  log_section "停止所有服务"
  docker compose down --remove-orphans
  log_ok "所有容器已停止"
  exit 0
fi

# =============================================================================
# 步骤 1: 检查依赖工具
# =============================================================================
log_section "检查依赖工具"

check_cmd() {
  if command -v "$1" &>/dev/null; then
    log_ok "$1 已安装 ($(command -v "$1"))"
  else
    log_error "$1 未安装，请先安装后重试"
    exit 1
  fi
}

check_cmd docker
check_cmd docker compose 2>/dev/null || check_cmd "docker-compose"

# 检查 Docker 是否运行
if ! docker info &>/dev/null; then
  log_error "Docker 未运行，请先启动 Docker"
  exit 1
fi
log_ok "Docker 运行正常"

# 构建时才需要 Java / Node
if ! $SKIP_BUILD; then
  if ! $FRONTEND_ONLY; then
    check_cmd java
    JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
    if [[ "$JAVA_VER" -lt 17 ]]; then
      log_error "需要 JDK 17+，当前版本: $JAVA_VER"
      exit 1
    fi
    log_ok "JDK $JAVA_VER"
    check_cmd mvn
  fi
  if ! $BACKEND_ONLY; then
    check_cmd node
    NODE_VER=$(node -e "process.stdout.write(process.version.slice(1).split('.')[0])")
    if [[ "$NODE_VER" -lt 18 ]]; then
      log_error "需要 Node.js 18+，当前版本: $NODE_VER"
      exit 1
    fi
    log_ok "Node.js $NODE_VER"
    check_cmd npm
  fi
fi

# =============================================================================
# 步骤 2: 加载 / 生成 .env 文件
# =============================================================================
log_section "配置环境变量"

if [[ ! -f "$ENV_FILE" ]]; then
  log_warn ".env 文件不存在，正在生成默认配置: $ENV_FILE"
  JWT_SECRET=$(LC_ALL=C tr -dc 'A-Za-z0-9!@#$%^&*' </dev/urandom | head -c 48 || true)
  cat > "$ENV_FILE" << EOF
# ── 数据库 ────────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=RootPass$(date +%s)
MYSQL_PASSWORD=MultiAgent$(date +%s)

# ── Redis ─────────────────────────────────────────────────
REDIS_PASSWORD=Redis$(date +%s)

# ── LLM 接口（必填）──────────────────────────────────────
# OpenAI:  https://api.openai.com
# 通义千问: https://dashscope.aliyuncs.com/compatible-mode/v1
# 智谱GLM: https://open.bigmodel.cn/api/paas/v4
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_BASE_URL=https://api.openai.com
AI_MODEL=gpt-4

# ── JWT 密钥（已自动生成）────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── WebSocket 允许来源 ────────────────────────────────────
WS_ALLOWED_ORIGINS=*
EOF
  log_warn "已生成 $ENV_FILE，请编辑其中的 OPENAI_API_KEY 后重新运行"
  echo ""
  echo -e "  ${YELLOW}vim $ENV_FILE${NC}"
  echo ""
  read -rp "  已配置好 API Key？按 Enter 继续，Ctrl+C 退出... "
fi

# 加载环境变量
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a
log_ok "环境变量已加载: $ENV_FILE"

# 检查必填项
if [[ "${OPENAI_API_KEY:-}" == "sk-your-api-key-here" ]] || [[ -z "${OPENAI_API_KEY:-}" ]]; then
  log_error "OPENAI_API_KEY 未配置，请编辑 $ENV_FILE"
  exit 1
fi
log_ok "OPENAI_API_KEY 已配置"

# =============================================================================
# 步骤 3: 生成 Docker Compose 文件
# =============================================================================
log_section "生成 Docker Compose 配置"

cat > docker-compose.yml << 'COMPOSE_EOF'
version: '3.8'

services:
  # ── 基础设施 ──────────────────────────────────────────────────────────────

  mysql:
    image: mysql:8.0
    container_name: multi-agent-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: multi_agent
      MYSQL_USER: multiagent
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "3307:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./backend/infrastructure/src/main/resources/db/migration/V1__init_schema.sql:/docker-entrypoint-initdb.d/init.sql:ro
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  redis:
    image: redis:7.0-alpine
    container_name: multi-agent-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes --maxmemory 512mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  etcd:
    image: quay.io/coreos/etcd:v3.5.11
    container_name: milvus-etcd
    restart: unless-stopped
    environment:
      ETCD_AUTO_COMPACTION_MODE: revision
      ETCD_AUTO_COMPACTION_RETENTION: "1000"
      ETCD_QUOTA_BACKEND_BYTES: "4294967296"
      ETCD_SNAPSHOT_COUNT: "50000"
    volumes:
      - etcd_data:/etcd
    command: >
      etcd
      --advertise-client-urls=http://127.0.0.1:2379
      --listen-client-urls=http://0.0.0.0:2379
      --data-dir=/etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio:
    image: minio/minio:latest
    container_name: milvus-minio
    restart: unless-stopped
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/data
    command: minio server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    image: milvusdb/milvus:v2.3.12
    container_name: milvus-standalone
    restart: unless-stopped
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus_data:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      etcd:
        condition: service_healthy
      minio:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      timeout: 20s
      retries: 5
      start_period: 60s

  nacos:
    image: nacos/nacos-server:v2.4.3
    container_name: nacos-standalone
    restart: unless-stopped
    environment:
      MODE: standalone
      PREFER_HOST_MODE: hostname
      JVM_XMS: 256m
      JVM_XMX: 512m
    ports:
      - "8848:8848"
      - "9848:9848"
    volumes:
      - nacos_data:/home/nacos/data
      - nacos_logs:/home/nacos/logs
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 60s

  # ── 应用服务 ──────────────────────────────────────────────────────────────

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: multi-agent-backend:latest
    container_name: multi-agent-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DB: multi_agent
      MYSQL_USER: multiagent
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      MILVUS_HOST: milvus
      MILVUS_PORT: 19530
      NACOS_SERVER_ADDR: nacos:8848
      NACOS_NAMESPACE: multi-agent
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      OPENAI_BASE_URL: ${OPENAI_BASE_URL:-https://api.openai.com}
      AI_MODEL: ${AI_MODEL:-gpt-4}
      JWT_SECRET: ${JWT_SECRET}
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
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 90s

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    image: multi-agent-frontend:latest
    container_name: multi-agent-frontend
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  mysql_data:
  redis_data:
  etcd_data:
  minio_data:
  milvus_data:
  nacos_data:
  nacos_logs:
COMPOSE_EOF

log_ok "docker-compose.yml 已生成"

# =============================================================================
# 步骤 4: 生成 Dockerfile（如不存在）
# =============================================================================
log_section "生成 Dockerfile"

# 后端 Dockerfile
if [[ ! -f "backend/Dockerfile" ]]; then
  cat > backend/Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app
COPY api/target/multi-agent-api-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xms512m", "-Xmx2g", \
  "-XX:+UseG1GC", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
EOF
  log_ok "backend/Dockerfile 已生成"
else
  log_info "backend/Dockerfile 已存在，跳过"
fi

# 前端 Dockerfile
if [[ ! -f "frontend/Dockerfile" ]]; then
  cat > frontend/Dockerfile << 'EOF'
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --prefer-offline
COPY . .
RUN npm run build

FROM nginx:1.24-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
  log_ok "frontend/Dockerfile 已生成"
else
  log_info "frontend/Dockerfile 已存在，跳过"
fi

# 前端 Nginx 配置
if [[ ! -f "frontend/nginx.conf" ]]; then
  cat > frontend/nginx.conf << 'EOF'
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # Vue Router history 模式
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }

    # WebSocket 代理（STOMP/SockJS）
    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|ico|woff2|svg)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 健康检查
    location /health {
        return 200 "ok\n";
        add_header Content-Type text/plain;
    }
}
EOF
  log_ok "frontend/nginx.conf 已生成"
else
  log_info "frontend/nginx.conf 已存在，跳过"
fi

# =============================================================================
# 步骤 5: 构建应用
# =============================================================================
if ! $SKIP_BUILD; then
  if ! $FRONTEND_ONLY; then
    log_section "构建后端 (Maven)"
    cd backend
    mvn clean package -DskipTests -q --no-transfer-progress
    log_ok "后端构建完成"
    cd "$SCRIPT_DIR"
  fi

  if ! $BACKEND_ONLY; then
    log_section "构建前端 (npm)"
    cd frontend
    npm install
    npm run build
    log_ok "前端构建完成"
    cd "$SCRIPT_DIR"
  fi
fi

# =============================================================================
# 辅助函数: 等待容器健康
# =============================================================================
wait_healthy() {
  local name=$1
  local max_wait=${2:-120}
  local elapsed=0
  echo -n "  等待 $name 就绪"
  while [[ $elapsed -lt $max_wait ]]; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "missing")
    if [[ "$STATUS" == "healthy" ]]; then
      echo -e " ${GREEN}✓${NC}"
      return 0
    fi
    echo -n "."
    sleep 3
    elapsed=$((elapsed + 3))
  done
  echo -e " ${RED}✗ 超时${NC}"
  log_error "$name 未能在 ${max_wait}s 内就绪，查看日志: docker logs $name"
  return 1
}

# =============================================================================
# 步骤 6: 启动基础设施
# =============================================================================
if ! $SKIP_INFRA; then
  log_section "启动基础设施服务"

  INFRA_SERVICES="mysql redis etcd minio milvus nacos"

  if $FRONTEND_ONLY; then
    log_info "仅部署前端，跳过基础设施"
  else
    log_info "启动: $INFRA_SERVICES"
    docker compose up -d $INFRA_SERVICES

    wait_healthy "multi-agent-mysql"   120
    wait_healthy "multi-agent-redis"    60
    wait_healthy "milvus-etcd"          60
    wait_healthy "milvus-minio"         60
    wait_healthy "milvus-standalone"   180
    wait_healthy "nacos-standalone"    120

    log_ok "所有基础设施服务已就绪"
  fi
fi

# =============================================================================
# 步骤 7: 构建并启动应用容器
# =============================================================================
log_section "构建并启动应用容器"

if $FRONTEND_ONLY; then
  docker compose build frontend
  docker compose up -d frontend
elif $BACKEND_ONLY; then
  docker compose build backend
  docker compose up -d backend
else
  docker compose build backend frontend
  docker compose up -d backend

  # 等待后端就绪后再启动前端
  wait_healthy "multi-agent-backend" 120
  docker compose up -d frontend
fi

# =============================================================================
# 步骤 8: 验证部署
# =============================================================================
log_section "验证部署"

sleep 5

check_endpoint() {
  local name=$1
  local url=$2
  local expected=${3:-200}
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null || echo "000")
  if [[ "$HTTP_CODE" == "$expected" ]]; then
    log_ok "$name — HTTP $HTTP_CODE ($url)"
  else
    log_warn "$name — HTTP $HTTP_CODE (期望 $expected) ($url)"
  fi
}

if ! $FRONTEND_ONLY; then
  check_endpoint "后端健康检查"  "http://localhost:8080/actuator/health"
  check_endpoint "JWT 认证接口"  "http://localhost:8080/api/auth/token" "405"
fi
if ! $BACKEND_ONLY; then
  check_endpoint "前端页面"      "http://localhost/health"
fi
if ! $SKIP_INFRA && ! $FRONTEND_ONLY; then
  check_endpoint "Nacos 控制台"  "http://localhost:8848/nacos"
  check_endpoint "Milvus 健康"   "http://localhost:9091/healthz" "200"
fi

# =============================================================================
# 完成
# =============================================================================
log_section "部署完成"

echo ""
echo -e "  ${GREEN}${BOLD}服务访问地址${NC}"
echo -e "  ┌─────────────────────────────────────────────┐"
echo -e "  │  前端界面    http://localhost                │"
echo -e "  │  后端 API    http://localhost:8080           │"
echo -e "  │  Nacos 控制台 http://localhost:8848/nacos    │"
echo -e "  │              账号: nacos / nacos             │"
echo -e "  │  Milvus      localhost:19530                 │"
echo -e "  └─────────────────────────────────────────────┘"
echo ""
echo -e "  ${CYAN}常用命令${NC}"
echo -e "  查看日志:   docker compose logs -f backend"
echo -e "  停止服务:   bash deploy.sh --down"
echo -e "  重新部署:   bash deploy.sh --skip-infra"
echo ""
