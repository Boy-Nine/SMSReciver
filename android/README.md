# Android 客户端

## 打开工程

1. 安装 Android Studio
2. Open Project，选择 `android/` 目录
3. 首次同步 Gradle 后连接真机或模拟器运行

## 使用步骤

1. 填写后台地址（如 `http://192.168.1.100:8080`）
2. 测试连通
3. 注册设备
4. 授予短信/通知权限
5. 启动转发服务

## 说明

- 需要真机测试短信接收（模拟器通常无法收 SMS）
- 局域网 HTTP 已允许（`network_security_config.xml`）
- 上报失败会写入本地队列，服务恢复后自动重试
