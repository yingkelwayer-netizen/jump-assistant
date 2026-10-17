# 独立部署示例

Node.js >=18.18，无第三方运行依赖。请在自己控制的独立目录、服务用户和回环端口部署，先备份再操作。

1. 执行 `node admin.cjs init /absolute/private/data` 创建新安装的签名密钥。已有部署不可再次初始化或换钥。
2. 将生成的 signing-public.pem 提供给 Android 构建；私钥、data、兑换码输出不可公开。
3. `DATA_DIR=/absolute/private/data PUBLIC_ORIGIN=https://your-domain PORT=18761 node service.cjs`，仅监听 127.0.0.1。以非登录专用用户和系统服务托管。
4. HTTPS 反向代理至该回环端口，仅公开 HTTPS。代理覆盖设置 X-Forwarded-Proto 为 https，保留输入限制；不要直接开放后端端口。
5. `node admin.cjs generate /absolute/private/data 10` 生成销售码，只向购买者发送；不要提交 Git。
6. `node admin.cjs publish /absolute/private/data app.apk 10 1.3.3 https://your-domain release-notes.txt` 发布签名更新清单。新版本必须递增版本号和 versionCode。
7. 同步 server/web/public 下载页和 share.png，并验证 APK 哈希、签名清单及页面版本一致。默认分享活动未开启，按自身规则配置私有 data/promo-config.json。

也可复制 service-config.example.json 为 service-config.json 后运行 start.cjs。示例仅供新部署，公开仓库不提供线上管理员访问、生产配置、私钥或数据库。
