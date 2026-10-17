# 下载页

固定网址的二维码、分享图片位于 brand。构建签名 APK 并写 release-notes 后，运行 `python server/web/package-release.py --version 1.3.3`，生成 APK、匹配哈希的页面和说明压缩包。模板不展示价格。

复制 public 内容到自己的 HTTPS 静态目录；更新接口由 server/service.cjs 提供。使用自己的域名时同步修改模板、品牌脚本和客户端服务配置，重新生成二维码及分享图。
