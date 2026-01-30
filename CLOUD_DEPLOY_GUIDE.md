# Smart VELP 上云部署全攻略

本指南提供了从零开始在一台全新的 Ubuntu/Debian 服务器上恢复 Smart VELP 所有服务的完整步骤。

## 1. 服务器基础环境初始化
在一台全新的 Ubuntu 服务器上，直接复制并运行以下命令：

```bash
# 更新系统并安装基础工具
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg lsb-release git python3 python3-pip

# 安装 Docker & Docker Compose
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=\$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \$(. /etc/os-release && echo \"\$VERSION_CODENAME\") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker

# 安装后端核心依赖: yt-dlp (视频解析工具)
sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
sudo chmod a+rx /usr/local/bin/yt-dlp

# 创建部署目录
sudo mkdir -p /opt/velp/downloads
sudo chown -R \$USER:\$USER /opt/velp
```

## 2. 准备 Docker 运行环境
在服务器的 `/opt/velp` 目录下创建 `docker-compose.yml` 文件。

```bash
cat <<EOF > /opt/velp/docker-compose.yml
services:
  backend:
    image: ghcr.io/\${GITHUB_USER_LOWER}/\${REPO_NAME_LOWER}-backend:prod
    ports:
      - "9090:9090"
    environment:
      - VELP_YTDLP_PATH=/usr/local/bin/yt-dlp
      - VELP_PYTHON_PATH=python3
      - DOUBAO_API_KEY=\${DOUBAO_API_KEY}
      - DEEPSEEK_API_KEY=\${DEEPSEEK_API_KEY}
      - VELP_CORS_ALLOWED_ORIGINS=\${VELP_CORS_ALLOWED_ORIGINS}
    restart: always
    volumes:
      - ./downloads:/app/downloads
EOF
```

## 3. Nginx 与 HTTPS 配置
由于 GitHub Pages 是 HTTPS 的，后端必须也提供 HTTPS 接口。

```bash
# 安装 Nginx 和 Certbot
sudo apt-get install -y nginx certbot python3-certbot-nginx

# 创建 Nginx 站点配置
# 请将其中的 yourdomain.com 替换为您真实的域名
sudo nano /etc/nginx/sites-available/velp
```

**粘贴以下配置内容：**
```nginx
server {
    listen 80;
    server_name yourdomain.com; # 替换为你的域名或服务器IP
    
    location /api {
        proxy_pass http://localhost:9090;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location /downloads {
        proxy_pass http://localhost:9090;
        proxy_set_header Host \$host;
    }
}
```

```bash
# 启用配置并重启
sudo ln -s /etc/nginx/sites-available/velp /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl restart nginx

# 申请 SSL 证书（必须有域名且 A 记录已指向服务器）
# sudo certbot --nginx -d yourdomain.com
```

## 4. GitHub 仓库配置 (Secrets)
进入 GitHub 仓库的 **Settings > Secrets and variables > Actions**，添加以下 **Repository secrets**：

| Secret 名称 | 说明 |
| :--- | :--- |
| `SSH_HOST` | 服务器公网 IP |
| `SSH_USER` | 登录用户名（如 root） |
| `SSH_KEY` | SSH 私钥内容 |
| `DOUBAO_API_KEY` | 豆包 API Key |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `PROD_API_BASE` | 后端 API 地址（如 https://yourdomain.com/api） |
| `ALLOWED_ORIGINS` | 前端域名（如 https://xxx.github.io） |

## 5. 部署触发说明
现在工作流已修改为**手动触发**。
1. 进入 GitHub 仓库的 **Actions** 页面。
2. 选择 **"Managed CI/CD"**。
3. 点击 **Run workflow** 按钮进行部署。
