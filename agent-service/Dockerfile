# Agent 微服务 Docker 镜像
FROM python:3.11-slim
WORKDIR /app

# 非 root 运行（S6）：创建专用用户，容器内不以 root 身份跑服务
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY requirements.txt .
# 国内服务器无法访问 PyPI 官方源，使用阿里云 PyPI 镜像
RUN pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt

COPY --chown=appuser:appuser . .

# 数据目录归 appuser 所有（记忆层落盘需要写权限）
RUN mkdir -p /app/data && chown -R appuser:appuser /app/data

USER appuser

EXPOSE 3201
# 启动参数：GUNICORN_WORKERS=N 可切换 gunicorn 多 worker（默认单进程）
CMD ["python", "main.py"]
