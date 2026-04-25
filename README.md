# Smart VELP

Smart VELP 是一个视频英语学习平台，前端基于 Vite，后端基于 Spring Boot。前端通过 `/api` 和 `/downloads` 代理到本地后端，因此本地开发时建议前后端一起启动。

## 项目结构

```text
smart-velp/
|- backend/                 # Spring Boot 后端，默认端口 9090
|- frontend/                # Vite 前端，默认端口 9091
|- scripts/                 # 本地启动脚本
|- package.json             # 根目录也保留了一套前端脚本
|- vite.config.js           # 根目录 Vite 配置
```

说明：

- 推荐使用 `frontend/` 目录作为前端本地开发入口。
- `scripts/start-frontend.ps1` 和 `scripts/start-frontend.sh` 也都是进入 `frontend/` 后再启动。
- 前端本地开发端口是 `9091`，不是默认的 `5173`。

## 环境要求

### 前端

- Node.js 18 及以上
- npm 9 及以上

### 后端

- JDK 17
- Maven 3.9+
- Python 3
- `yt-dlp`

说明：

- 如果本机没有 Maven，Windows 启动脚本会自动下载并放到 `.tools/` 下。
- 如果本机没有 `yt-dlp`，Windows 启动脚本也会自动下载 `yt-dlp.exe`。

## 前端本地启动

这是当前项目最直接、最推荐的前端本地启动方式。

### 方式一：直接进入 `frontend` 目录启动

```bash
cd frontend
npm install
npm run dev
```

启动后访问：

```text
http://localhost:9091
```

### 方式二：使用项目自带脚本启动

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-frontend.ps1
```

macOS / Linux:

```bash
bash scripts/start-frontend.sh
```

脚本行为：

- 自动进入 `frontend/`
- 如果没有 `node_modules`，先执行 `npm install`
- 然后执行 `npm run dev`

## 前后端联调启动

由于前端会把 `/api` 和 `/downloads` 代理到本地后端 `http://localhost:9090`，所以要正常提交解析任务，后端也需要启动。

### 1. 启动后端

Windows 推荐：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

或者手动启动：

```bash
cd backend
mvn clean spring-boot:run
```

后端默认地址：

```text
http://localhost:9090
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:9091
```

### 3. 打开页面验证

浏览器访问：

```text
http://localhost:9091
```

如果前后端都正常启动：

- 页面可以加载任务列表
- 提交 YouTube 链接后会调用后端接口
- 视频和下载资源会走 `/downloads` 代理

## 后端配置说明

后端配置文件位置：

[`backend/src/main/resources/application.properties`](C:/Users/zhaoxx/IdeaProjects/github/owner/smart-velp/backend/src/main/resources/application.properties)

当前项目里重点配置如下：

- `server.port=9090`
- `velp.repository.type=memory`
- `velp.python.path`
- `velp.ytdlp.path`
- 各类 LLM 提供商配置，如 Doubao / DeepSeek / OpenAI Compatible

如果你只是想看前端页面是否能启动，前端单独运行也可以；但这时涉及接口请求的功能会失败。

## 页面使用方式

当前前端本地启动后，常见使用流程如下：

1. 打开 `http://localhost:9091`
2. 在输入框中粘贴 YouTube 视频链接
3. 点击开始解析
4. 左侧任务列表会展示任务状态
5. 任务完成后点击任务，右侧加载播放器和字幕
6. 可以切换双语、英文、中文字幕模式
7. 可以调整播放倍速、字幕字号、是否循环句子
8. 可以下载处理后的视频资源

## 常用命令

### 前端开发

```bash
cd frontend
npm run dev
```

### 前端打包

```bash
cd frontend
npm run build
```

### 前端本地预览打包结果

```bash
cd frontend
npm run preview
```

### 后端开发运行

```bash
cd backend
mvn clean spring-boot:run
```

## 常见问题

### 1. 前端页面能打开，但提交任务失败

通常是后端没有启动，或者后端没有运行在 `9090`。

请检查：

- 后端是否已经启动
- `backend` 是否监听在 `http://localhost:9090`
- 前端是否运行在 `http://localhost:9091`

### 2. 前端启动后不是 9091

当前仓库的 Vite 配置里已经固定端口为 `9091`。如果你看到其他端口，通常是命令没有在当前项目目录执行，或者启动的不是这个仓库里的前端。

### 3. 只启动前端能不能用

可以打开页面和看静态界面，但以下功能依赖后端：

- 任务提交
- 任务状态轮询
- 视频下载
- 字幕和课程详情加载

## 推荐使用方式

如果你现在是要在本地开发这个项目，建议直接按下面顺序执行：

1. 启动后端：`powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1`
2. 启动前端：`powershell -ExecutionPolicy Bypass -File scripts/start-frontend.ps1`
3. 打开 `http://localhost:9091`

这样最贴近当前仓库现有配置，也最不容易踩路径和依赖问题。
