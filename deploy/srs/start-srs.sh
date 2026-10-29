#!/bin/sh
# 启动本地 SRS 直播服务（RTMP 1935 / HLS 8081 / HTTP API 1985）
# 二进制来自 SRS 4.0-r4 源码编译，需在 macOS arm64 上运行
set -e
cd "$(dirname "$0")"
mkdir -p media
exec ./bin/srs -c ./srs.conf
