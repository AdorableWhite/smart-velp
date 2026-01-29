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

## 3. 域名配置（方案 A：前后端使用同一域名）

本项目采用前后端分离部署方案：
- **前端**：部署到 GitHub Pages（使用自定义域名，如 `www.yourdomain.com`）
- **后端**：部署到你的服务器（使用同一域名，如 `yourdomain.com/api`）

### 3.1 购买域名并配置 DNS 解析

1. **购买域名**（如果还没有）
   - 推荐域名注册商：阿里云、腾讯云、Namecheap、GoDaddy 等

2. **配置 DNS 解析**（在域名管理后台）
   
   **后端服务器 A 记录**：
   ```
   类型：A
   主机记录：@（表示主域名 yourdomain.com）
   记录值：你的服务器公网 IP 地址
   TTL：600
   ```
   
   **前端 GitHub Pages CNAME 记录**：
   ```
   类型：CNAME
   主机记录：www
   记录值：你的GitHub用户名.github.io（例如：zhaoxx.github.io）
   TTL：600
   ```
   
   **如果使用主域名作为前端**（可选）：
   ```
   类型：A
   主机记录：@
   记录值：185.199.108.153
   记录值：185.199.109.153
   记录值：185.199.110.153
   记录值：185.199.111.153
   ```
   （这些是 GitHub Pages 的 IP 地址）

### 3.2 配置服务器 Nginx 和 SSL 证书

1. **安装 Nginx 和 Certbot**：
   ```bash
   sudo apt-get update
   sudo apt-get install -y nginx certbot python3-certbot-nginx
   ```

2. **创建 Nginx 配置文件**：
   ```bash
   sudo nano /etc/nginx/sites-available/smart-velp
   ```
   
   配置文件内容：
   ```nginx
   # HTTP 重定向到 HTTPS
   server {
       listen 80;
       server_name yourdomain.com www.yourdomain.com;
       return 301 https://$server_name$request_uri;
   }

   # HTTPS 配置
   server {
       listen 443 ssl http2;
       server_name yourdomain.com www.yourdomain.com;

       # SSL 证书路径（Certbot 会自动配置）
       ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

       # SSL 配置
       ssl_protocols TLSv1.2 TLSv1.3;
       ssl_ciphers HIGH:!aNULL:!MD5;

       # 代理后端 API 请求
       location /api {
           proxy_pass http://localhost:9090;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           
           # 支持 WebSocket（如果需要）
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }

       # 代理下载资源
       location /downloads {
           proxy_pass http://localhost:9090;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           
           # 支持大文件下载
           proxy_buffering off;
           proxy_request_buffering off;
       }
   }
   ```
   
   **重要**：将 `yourdomain.com` 替换为你的实际域名！

3. **启用配置并申请 SSL 证书**：
   ```bash
   # 创建软链接
   sudo ln -s /etc/nginx/sites-available/smart-velp /etc/nginx/sites-enabled/
   
   # 测试配置
   sudo nginx -t
   
   # 启动 Nginx
   sudo systemctl start nginx
   sudo systemctl enable nginx
   
   # 申请 SSL 证书（自动配置）
   sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
   
   # 设置自动续期（Certbot 会自动配置）
   sudo certbot renew --dry-run
   ```

4. **配置后端 CORS**：
   
   在服务器上编辑 `docker-compose.yml`，添加环境变量：
   ```yaml
   backend:
     environment:
       # ... 其他环境变量 ...
       - VELP_CORS_ALLOWED_ORIGINS=https://www.yourdomain.com,https://yourdomain.com
   ```
   
   或者在 `application.properties` 中配置（如果使用配置文件）：
   ```properties
   velp.cors.allowed-origins=https://www.yourdomain.com,https://yourdomain.com
   ```

### 3.3 配置 GitHub Pages

1. **在 GitHub 仓库中配置**：
   - 进入仓库 **Settings > Pages**
   - **Source**：选择 `GitHub Actions`
   - **Custom domain**：输入你的域名（如 `www.yourdomain.com`）
   - 勾选 **Enforce HTTPS**

2. **配置 CNAME 文件**：
   - 编辑 `frontend/CNAME` 文件，将内容替换为你的域名
   - 例如：`www.yourdomain.com`

3. **配置 GitHub Secrets**（可选）：
   - 如果前端和后端使用不同域名，可以在 GitHub Secrets 中添加：
   - `VITE_API_BASE`：后端 API 地址（如 `https://yourdomain.com/api`）
   - 如果不设置，前端会自动根据当前域名判断后端地址

### 3.4 验证部署

1. **检查 DNS 解析**：
   ```bash
   # 检查后端域名解析
   nslookup yourdomain.com
   
   # 检查前端域名解析
   nslookup www.yourdomain.com
   ```

2. **测试访问**：
   - 前端：访问 `https://www.yourdomain.com`
   - 后端 API：访问 `https://yourdomain.com/api/parser/tasks`
   - 应该能看到任务列表（JSON 格式）

3. **检查 SSL 证书**：
   - 在浏览器中访问，应该看到 HTTPS 锁图标
   - 证书应该由 Let's Encrypt 签发

## 4. 运行逻辑说明

- **分支策略**：
  - 推送到 `master` 分支：
    - 自动构建 Docker 镜像并推送到 GitHub Container Registry
    - 自动部署后端到服务器
    - 自动构建并部署前端到 GitHub Pages
  - 推送到 `develop` 分支：自动构建并部署，镜像标签为 `dev`。
- **镜像仓库**：使用 GitHub Container Registry (ghcr.io)。
- **持久化**：后端下载的内容将持久化在服务器的 `/opt/velp/downloads` 目录。
- **前端部署**：自动部署到 GitHub Pages，支持自定义域名和 HTTPS。
