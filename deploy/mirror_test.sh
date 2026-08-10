#!/usr/bin/env bash
# 测试 Docker 国内镜像加速器连通性（部署用）
for m in \
  'https://docker.m.daocloud.io' \
  'https://docker.1ms.run' \
  'https://hub.rat.dev' \
  'https://docker.xuanyuan.me' \
  'https://dockerproxy.net'; do
  r=$(timeout 8 curl -sI "${m}/v2/" -o /dev/null -w '%{http_code} %{time_total}s' 2>/dev/null)
  echo "${m} -> ${r:-不通}"
done
