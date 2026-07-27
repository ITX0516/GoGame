Leela Zero Assets
==================

此目录存放 Leela Zero 二进制文件和权重文件。

需要放入的文件（均不包含在 git 中）：

1. leelaz       - Leela Zero 预编译二进制文件 (ARM64/arm64-v8a)
2. lz_network.lz - 神经网络权重文件 (原 .gz 格式，改后缀避免 aapt 自动解压)

获取方式：
- 二进制: 参考 https://github.com/leela-zero/leela-zero 或 leelaApplication 项目预编译
- 权重: https://github.com/leela-zero/leela-zero/releases 或 leela-zero 网络服务器

注意：
- 只放 arm64-v8a 架构的二进制
- 权重文件保持 .gz 格式，leelaz 会自动解压
- 首次启动会自动复制到 filesDir 并 chmod +x
