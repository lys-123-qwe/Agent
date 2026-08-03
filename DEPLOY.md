# 🐳 Claw 助手 — Docker 一键部署指南

> 适用于：夏令营演示 / 同学部署 / 任何想快速跑起来的人。
> 不需要装 Java、不需要装 Maven，只需要装一个 **Docker**。

---

## 一、准备工作（只需要一次）

### 1. 安装 Docker

| 系统 | 安装方式 |
|------|---------|
| **Windows** | 下载安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（需要 WSL2，安装向导会提示） |
| **macOS** | 下载安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Linux** | `curl -fsSL https://get.docker.com | sh` |

装完打开 Docker（右下角鲸鱼图标变绿/运行中表示就绪）。

> 💡 **Windows 老机器没有 WSL2 的**：装 Docker Desktop 时勾选 "Use Windows containers instead" 之外的默认选项即可，或百度 "Docker Desktop WSL2 安装教程"。

### 2. 获取项目文件

**方式 A：Git 拉取源码**（需要网络和 Git）

```bash
git clone https://github.com/Constantglow/youkeda_pj.git
cd youkeda_pj
```

**方式 B：拿到镜像包**（不需要 Git，见下方"方式二"）

---

## 二、部署（三选一）

### 方式一：源码 + Docker Compose（推荐，构建+运行一条龙）

```bash
cd youkeda_pj

# 1. 配置密钥（可选但强烈建议：不填则 AI 对话等核心功能不可用）
cp .env.example .env
# 然后用记事本打开 .env，把 = 后面填上自己的 API Key

# 2. 一键构建并启动（第一次构建要下载依赖，约 5~10 分钟）
docker compose up -d

# 3. 看二维码并扫码登录
docker compose logs -f
```

看到类似这样的输出：

```
QR Code URL:
https://xxx.weixin.qq.com/qr/xxxx
```

**用浏览器打开这个网址 → 显示二维码 → 微信扫码确认 → 机器人上线！**
（`Ctrl+C` 退出日志查看，机器人继续后台运行）

### 方式二：直接加载别人给你的镜像包（免构建）

别人会给你一个 `claw-assistant.tar` 文件：

```bash
# 1. 导入镜像
docker load -i claw-assistant.tar

# 2. 准备配置目录
mkdir claw && cd claw
# 创建 .env 并填入密钥（模板同上，或找提供者要）

# 3. 运行（docker compose 需要 compose 文件；没有 compose 时用 docker run）
docker run -d --name claw \
  -e TZ=Asia/Shanghai \
  --env-file .env \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/data/home:/app/home \
  claw-assistant:latest

# 4. 看二维码
docker logs -f claw
```

### 方式三：从镜像仓库拉取（发布到 Docker Hub 后）

```bash
docker pull <你的账号>/claw-assistant:latest
# 运行方式同方式二
```

---

## 三、常用操作

| 想做什么 | 命令 |
|---------|------|
| 查看机器人日志/二维码 | `docker compose logs -f` |
| 停止机器人 | `docker compose stop` |
| 再次启动（不用重新扫码） | `docker compose start` |
| 彻底删除（数据保留在 ./data） | `docker compose down` |
| 清空所有数据重新开始 | 删除 `./data` 文件夹后 `docker compose up -d` |

> 💡 **数据都在 `./data` 目录**：聊天记录、登录会话、定时任务都存在这里。
> 删容器不删数据，重启免重新扫码。

---

## 四、常见问题

**Q: 扫码后一直没反应？**
A: 二维码有效期约 2 分钟，过期后看日志里有没有新二维码；没有就 `docker compose restart`。

**Q: 不填 API Key 能跑吗？**
A: 能启动、能扫码登录，但 AI 对话会提示"未配置 DeepSeek API Key"。天气/汇率等也各自需要对应 key。

**Q: 日志乱码？**
A: 容器内已强制 UTF-8，Windows 终端执行 `chcp 65001` 后再看日志。

**Q: 换一台机器/删了 data 目录会怎样？**
A: 会丢失登录态，需要重新扫码；聊天记录也会清空。

---

## 五、打包分发镜像（给开发者）

在项目根目录：

```bash
# 构建镜像
docker build -t claw-assistant:latest .

# 导出为文件（发给别人，别人用 docker load -i 导入）
docker save -o claw-assistant.tar claw-assistant:latest

# 推到 Docker Hub（需要先 docker login）
docker tag claw-assistant:latest 你的账号/claw-assistant:latest
docker push 你的账号/claw-assistant:latest
```
