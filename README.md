# 验证码接收后台

局域网内的短信验证码接收与查看系统，包含 FastAPI 后台与 Android 转发客户端。

## 目录结构

- `backend/`：FastAPI 服务、SQLite 存储、Web 管理页
- `android/`：Android Kotlin 客户端，监听短信并上报
- `scripts/integration_test.sh`：端到端联调脚本
- `docs/部署与使用.md`：详细部署说明

## 快速启动（后台）

```bash
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8080
```

浏览器访问（首次打开会进入登录页）：

```text
http://<你的局域网IP>:8080/sms/
```

登录口令为环境变量 `SMS_ADMIN_TOKEN`（默认 `admin123`），登录后写入 Cookie `sms`，URL 不再携带口令参数。

```bash
export SMS_ADMIN_TOKEN=your-secret
```

## Android 客户端

1. 用 Android Studio 打开 `android/` 目录
2. 编译并安装到手机
3. 在配置页填写服务器地址（如 `http://192.168.1.100:8080`）
4. 点击「注册设备」获取 `device_id` 与 `api_key`
5. 授予短信与通知权限，点击「启动转发服务」
6. 建议关闭该应用的电池优化

## 联调验证

后台启动后执行：

```bash
chmod +x scripts/integration_test.sh
./scripts/integration_test.sh
```

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| POST | `/api/devices/register` | 注册设备 |
| POST | `/api/sms/inbound` | 手机端上报短信 |
| GET | `/api/sms` | 查询短信（需 Cookie `sms` 或请求头 `X-Sms-Token`） |
| GET | `/api/devices` | 查询设备（同上） |
| GET | `/sms/` | Web 管理页（登录后） |
| POST | `/sms/login` | 登录并设置 Cookie |

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SMS_ADMIN_TOKEN` | `admin123` | Web/API 管理口令 |
| `SMS_PORT` | `8080` | 服务端口 |
| `SMS_DATABASE_PATH` | `backend/data/sms.db` | SQLite 路径 |
