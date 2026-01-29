# 域名配置快速指南

本文档提供域名配置的快速步骤，帮助你将项目部署到自定义域名。

## 架构说明

- **前端**：部署到 GitHub Pages（`www.yourdomain.com`）
- **后端**：部署到你的服务器（`yourdomain.com/api`）
- **通信**：前端通过 HTTPS 调用后端 API

## 快速配置步骤

### 步骤 1：购买域名（如已有可跳过）

在域名注册商（阿里云、腾讯云、Namecheap 等）购买域名。

### 步骤 2：配置 DNS 解析

登录域名管理后台，添加以下 DNS 记录：

#### 2.1 后端服务器 A 记录
```
类型：A
主机记录：@
记录值：你的服务器公网 IP
TTL：600
```

#### 2.2 前端 GitHub Pages CNAME 记录
```
类型：CNAME
主机记录：www
记录值：你的GitHub用户名.github.io
TTL：600
```

**示例**：如果你的 GitHub 用户名是 `zhaoxx`，记录值就是 `zhaoxx.github.io`

### 步骤 3：配置服务器 Nginx

#### 3.1 安装 Nginx 和 Certbot

```bash
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx
```

#### 3.2 创建 Nginx 配置文件

```bash
sudo nano /etc/nginx/sites-available/smart-velp
```

将以下内容复制到文件中（**记得替换 `yourdomain.com` 为你的实际域名**）：

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

#### 3.3 启用配置并申请 SSL 证书

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/smart-velp /etc/nginx/sites-enabled/

# 删除默认配置（可选）
sudo rm /etc/nginx/sites-enabled/default

# 测试配置
sudo nginx -t

# 启动 Nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# 申请 SSL 证书（自动配置）
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com

# 测试自动续期
sudo certbot renew --dry-run
```

### 步骤 4：配置后端 CORS

在服务器上的 `/opt/velp/docker-compose.yml` 文件中，添加环境变量：

```yaml
backend:
  environment:
    # ... 其他环境变量 ...
    - VELP_CORS_ALLOWED_ORIGINS=https://www.yourdomain.com,https://yourdomain.com
```

然后重启服务：

```bash
cd /opt/velp
docker compose down
docker compose up -d
```

### 步骤 5：配置 GitHub Pages

#### 5.1 在 GitHub 仓库中配置

1. 进入仓库 **Settings > Pages**
2. **Source**：选择 `GitHub Actions`
3. **Custom domain**：输入你的域名（如 `www.yourdomain.com`）
4. 勾选 **Enforce HTTPS**

#### 5.2 配置 CNAME 文件

编辑项目中的 `frontend/CNAME` 文件，将内容替换为你的域名：

```
www.yourdomain.com
```

#### 5.3 配置 GitHub Secrets（可选）

如果前端和后端使用不同域名，可以在 GitHub Secrets 中添加：

- **Name**: `VITE_API_BASE`
- **Value**: `https://yourdomain.com/api`

如果不设置，前端会自动根据当前域名判断后端地址。

### 步骤 6：提交代码并部署

1. **提交代码**：
   ```bash
   git add .
   git commit -m "配置域名部署"
   git push origin master
   ```

2. **等待部署完成**：
   - 查看 GitHub Actions 工作流状态
   - 后端会自动部署到服务器
   - 前端会自动部署到 GitHub Pages

3. **验证部署**：
   - 访问 `https://www.yourdomain.com`（前端）
   - 访问 `https://yourdomain.com/api/parser/tasks`（后端 API）

## 常见问题

### Q1: DNS 解析不生效？

A: DNS 解析可能需要几分钟到几小时才能生效。可以使用以下命令检查：

```bash
nslookup yourdomain.com
nslookup www.yourdomain.com
```

### Q2: SSL 证书申请失败？

A: 确保：
- 域名已正确解析到服务器 IP
- 服务器防火墙开放了 80 和 443 端口
- Nginx 已启动并可以访问

### Q3: 前端无法访问后端 API？

A: 检查：
- 后端 CORS 配置是否正确
- 后端服务是否正常运行（`docker ps`）
- Nginx 配置是否正确
- 浏览器控制台是否有错误信息

### Q4: 如何更新域名？

A: 
1. 更新 DNS 解析
2. 更新 Nginx 配置中的 `server_name`
3. 更新 `frontend/CNAME` 文件
4. 更新 GitHub Pages 设置中的自定义域名
5. 更新后端 CORS 配置

## 验证清单

- [ ] DNS 解析已配置
- [ ] Nginx 已安装并配置
- [ ] SSL 证书已申请
- [ ] 后端 CORS 已配置
- [ ] GitHub Pages 已配置自定义域名
- [ ] CNAME 文件已更新
- [ ] 代码已提交并部署
- [ ] 前端可以正常访问
- [ ] 后端 API 可以正常访问
- [ ] HTTPS 正常工作

## 技术支持

如果遇到问题，请检查：
1. GitHub Actions 工作流日志
2. 服务器日志：`docker compose logs`
3. Nginx 日志：`sudo tail -f /var/log/nginx/error.log`
4. 浏览器控制台错误信息
