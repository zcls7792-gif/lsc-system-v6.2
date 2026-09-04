patches/ 目录说明：
  每个 .patch 是对 src/main/java 对应文件的 unified diff。
应用方式：
  cd /workspace/lsc-release-service  # 项目根
  patch -p1 < patches/P2-C-feishu-logging-fallback.patch
应用后重新打包 Jar 发布。
