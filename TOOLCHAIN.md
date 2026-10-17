# Windows 构建环境

准备 JDK 17、Android Build Tools 35.0.0、Android SDK Platform 35。构建脚本使用 tools/jdk/<jdk目录>、tools/build-tools/android-15、tools/platform/android-35/android.jar；工具不进入 APK 或公开源码。

Android 工具从 Google 官方 SDK 获取；JDK 可用 Eclipse Temurin 17。Python 图片脚本需要 Pillow、numpy、qrcode，应用运行不依赖 Python。

签名前设置 JUMP_KEYSTORE_PATH、JUMP_KEY_ALIAS、JUMP_KEYSTORE_PASSWORD、JUMP_KEY_PASSWORD 环境变量。密钥和口令由发行负责人保管，不写入脚本或 Git。原应用覆盖升级需要原签名；自行生成的新签名不能覆盖原版。

服务端使用 Node.js >=18.18，无 npm 第三方运行依赖。服务签名公钥可公开；私钥只能留在私有服务数据目录。
