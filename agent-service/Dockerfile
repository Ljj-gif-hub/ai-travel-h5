# Agent 微服务 Docker 镜像
FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
# 国内服务器无法访问 PyPI 官方源，使用阿里云 PyPI 镜像
RUN pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt

COPY . .

EXPOSE 3201
# 启动参数：--worker 数量可由环境变量控制
CMD ["python", "main.py"]
