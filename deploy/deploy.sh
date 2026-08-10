#!/usr/bin/env bash
# ================================================================
# AI Travel 一键部署脚本（在云服务器上运行）
# 前置：deploy.sh 与项目的 travel-java/ agent-service/ trval-h5/ 在同一目录
# 用法：sudo bash deploy.sh
# ================================================================
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$BASE_DIR"

echo "========== [1/6] 检查并安装 Docker =========="
if ! command -v docker &>/dev/null; then
  echo "未检测到 Docker，使用官方脚本安装..."
  curl -fsSL https://get.docker.com | sh
  systemctl enable docker && systemctl start docker
else
  echo "Docker 已安装: $(docker --version)"
fi

if ! docker compose version &>/dev/null; then
  echo "缺少 docker compose 插件，安装..."
  apt-get update -y
  apt-get install -y docker-compose-plugin
fi

echo "========== [2/6] 检查并安装 Node.js（前端构建用）=========="
if ! command -v node &>/dev/null; then
  echo "未安装 Node，从阿里云镜像下载 Node 22 LTS..."
  cd /tmp
  curl -fsSL -o node.tar.xz https://mirrors.aliyun.com/nodejs-release/v22.23.2/node-v22.23.2-linux-x64.tar.xz
  tar -xJf node.tar.xz -C /usr/local --strip-components=1
  rm -f node.tar.xz
  echo "Node 安装完成: $(node -v) / npm $(npm -v)"
  cd "$BASE_DIR"
else
  echo "Node 已安装: $(node -v)"
fi

# npm 走国内镜像加速依赖安装（失败不影响主流程）
npm config set registry https://registry.npmmirror.com 2>/dev/null || true

echo "========== [3/6] 构建前端（dist → trval-h5/dist）=========="
cd "$BASE_DIR/trval-h5"
if [ -f package-lock.json ]; then
  npm ci --no-audit --no-fund || npm install --no-audit --no-fund
else
  npm install --no-audit --no-fund
fi
npm run build
cd "$BASE_DIR"

echo "========== [4/6] 构建并启动全部服务（首次约 5-10 分钟）=========="
cd "$BASE_DIR/travel-java"
docker compose up -d --build
cd "$BASE_DIR"

echo "========== [5/6] 等待服务健康 =========="
for i in $(seq 1 45); do
  sleep 2
  if curl -sf http://localhost/actuator/health | grep -q '"status":"UP"'; then
    echo "✅ 服务已就绪（约 $((i*2)) 秒）"
    break
  fi
  [ "$i" -eq 45 ] && echo "⚠️ 90 秒内未就绪，请查看日志排查" || true
done

echo ""
echo "========== ✅ 部署完成 =========="
echo "公网访问（替换为你的公网 IP）:  http://你的公网IP"
echo "后端直连:      http://localhost:3200"
echo "Swagger 文档:  http://localhost:3200/swagger-ui.html"
echo ""
echo "常用命令:"
echo "  查看日志:  cd $BASE_DIR/travel-java && docker compose logs -f"
echo "  重启:      cd $BASE_DIR/travel-java && docker compose restart"
echo "  停止:      cd $BASE_DIR/travel-java && docker compose down"
echo "  失败排查:  cd $BASE_DIR/travel-java && docker compose logs app"
