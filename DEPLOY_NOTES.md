# 部署说明文档 (DEPLOY_NOTES.md)

本文件指导如何准备服务器环境以及配置 GitHub Actions 以实现自动化 CI/CD。

## 1. 服务器环境准备

### 安装 Docker 和 Docker Compose

在 Ubuntu/Debian 服务器上，执行以下命令：

```bash
# 更新索引并安装依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# 添加 Docker 官方 GPG 密钥
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 设置存储库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 验证安装
docker compose version
```

### 创建部署目录并放置配置

1. 在服务器上创建目录：
   ```bash
   sudo mkdir -p /opt/velp
   sudo chown $USER:$USER /opt/velp
   cd /opt/velp
   ```

2. 将项目根目录下的 `docker-compose.yml` 手动复制到服务器的 `/opt/velp/docker-compose.yml`。

## 2. GitHub 仓库配置 (Secrets)

进入 GitHub 仓库的 **Settings > Secrets and variables > Actions**，点击 **New repository secret** 添加以下变量：

| Secret 名称 | 说明 | 示例 |
| :--- | :--- | :--- |
| `SSH_HOST` | 服务器的公网 IP 地址 | `1.2.3.4` |
| `SSH_USER` | 用于登录服务器的用户名 | `root` 或 `ubuntu` |
| `SSH_KEY` | 用于 SSH 登录的私钥 (需与服务器上的 `~/.ssh/authorized_keys` 对应) | `-----BEGIN OPENSSH PRIVATE KEY----- ...` |
| `DOUBAO_API_KEY` | (可选) 后端使用的豆包 API Key | `your-api-key` |
| `DEEPSEEK_API_KEY` | (可选) 后端使用的 DeepSeek API Key | `your-api-key` |

> **注意**：`GITHUB_TOKEN` 是 GitHub Actions 自动生成的，无需手动配置。

## 3. 运行逻辑说明

- **分支策略**：
  - 推送到 `master` 分支：自动构建并部署，镜像标签为 `prod`。
  - 推送到 `develop` 分支：自动构建并部署，镜像标签为 `dev`。
- **镜像仓库**：使用 GitHub Container Registry (ghcr.io)。
- **持久化**：后端下载的内容将持久化在服务器的 `/opt/velp/downloads` 目录。
