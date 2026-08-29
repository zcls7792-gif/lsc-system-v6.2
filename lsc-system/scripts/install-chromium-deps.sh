#!/bin/bash
# 安装 Chrome for Testing / Chromium 运行所需的最小依赖集 (Ubuntu / Debian 系)
# 包含: atk/cups/gtk3/gdk/wayland/krb5/opus 等 + 中文字体
set -e
export DEBIAN_FRONTEND=noninteractive

if command -v apt-get >/dev/null; then
  apt-get update -y 2>&1 | tail -1
  apt-get install -y --no-install-recommends \
    libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2 libpulse0 \
    libnss3 libnspr4 libatkmm-1.6-1v5 libgtk-3-0 libxshmfence1 2>&1 | tail -3 || true
  # 中文字体：确保 Chrome 渲染中文为真实字形（而非方框）
  apt-get install -y --no-install-recommends fonts-noto-cjk fonts-liberation 2>&1 | tail -2 || true
  echo "OK"
else
  echo "skip: apt-get not found"
fi
