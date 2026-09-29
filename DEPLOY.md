# 上线部署指南

这份文档带你把这个五子棋从本机搬到一个公开网址上。全程免费，大约 15 分钟。

## 为什么不能用 Vercel / GitHub Pages

那些平台只能托管静态网页（HTML/CSS/JS）。本项目有 Java 后端，必须跑在能执行 Java 的服务器上，所以我们用 **Render + Docker**。

项目里已经准备好了 `Dockerfile` 和 `render.yaml`，你不需要写任何部署配置。

## 前置条件

1. 一个 GitHub 账号（免费注册：<https://github.com/signup>）
2. 一个 Render 账号（免费注册，用 GitHub 账号一键登录：<https://render.com>）
3. Clash 保持开启（访问 GitHub 需要）

## 第一步：在 GitHub 上建一个空仓库

1. 登录 GitHub，点右上角 **+** → **New repository**
2. Repository name 填 `gomoku`
3. 选择 **Public**（私有仓库 Render 免费档也能用，但公开更省事）
4. **不要**勾选 "Add a README file"，也**不要**选 .gitignore 和 license —— 我们要推的是本地已有的代码
5. 点 **Create repository**

创建完会跳到一个写着 "Quick setup" 的页面，先不用管它，我们要用下面的脚本推送。

## 第二步：把代码推上去

本地代码**已经提交好了**，你只要执行一条命令：

```bat
cd E:\codexTest\JAVA\gomoku
push-to-github.bat 你的GitHub用户名 gomoku
```

例如用户名是 `zhangsan`，就执行：

```bat
push-to-github.bat zhangsan gomoku
```

脚本会做四件事：给本项目单独配置 Clash 代理（不改你的全局 git 设置）、关联远程仓库、测试连通性、推送代码。首次推送会**弹出浏览器窗口**，登录你的 GitHub 账号并授权即可（用的是你机器上自带的 Git 凭据管理器，密码不会经过我）。

> 如果你改了 Clash 的混合端口，打开 `push-to-github.bat` 改掉 `PROXY_PORT` 那一行，或者直接告诉我。

### 如果不想用脚本，手动执行也行

```bat
cd E:\codexTest\JAVA\gomoku
git config --local http.proxy  http://127.0.0.1:7897
git config --local https.proxy http://127.0.0.1:7897
git remote add origin https://github.com/你的用户名/gomoku.git
git push -u origin main
```

## 第三步：在 Render 上部署

1. 打开 <https://dashboard.render.com>，用 GitHub 账号登录
2. 点 **New** → **Web Service**
3. 选择 **Build and deploy from a Git repository**，点 Next
4. 在列表里找到刚推上去的 `gomoku` 仓库，点 **Connect**（如果列表里没有，点 "Configure account" 授权 Render 访问你的仓库）
5. 配置页面会**自动识别到 Dockerfile**，语言选 Docker，其余保持默认：
   - **Name**：`gomoku`（这个名字会变成网址的一部分）
   - **Region**：选 `Singapore`，国内访问比美区快
   - **Instance Type**：`Free`
   - **Environment Variables**：不用加，`render.yaml` 里已经写了 `PORT=8080`
6. 点 **Create Web Service**

接下来就是等。首次构建要拉取 JDK 镜像并编译，通常 3~8 分钟。日志里出现

```
网页五子棋已启动
浏览器打开： http://localhost:8080
```

就说明成功了。页面顶部会显示一个网址，形如：

```
https://gomoku-xxxx.onrender.com
```

这个就是你要的网址，发给任何人都能打开玩。

## 第四步：验证

把网址填到浏览器，你应该能看到棋盘。然后：

- 点一下棋盘落子，看电脑有没有应手
- 切换难度和棋盘大小
- 点「悔棋」

如果页面空白或报错，去 Render 的 **Logs** 标签页看报错信息，发给我。

## 每次改代码之后怎么更新

改完代码，在本机执行：

```bat
cd E:\codexTest\JAVA\gomoku
git add -A
git commit -m "描述你改了什么"
git push
```

Render 检测到推送会**自动重新部署**，几分钟后新版本就上线了。

## 关于免费档的注意事项

- **会自动休眠**：15 分钟没人访问就进入休眠，下次有人打开要等 30~60 秒唤醒。这是免费档的通病，不是程序有问题。
- **流量和时长**：免费档每月有额度上限，个人玩完全够。
- **没有自定义域名**：免费档只能用 `*.onrender.com` 的网址。想绑自己的域名需要升级付费档，域名本身还要另外买。
- 免费实例重启后内存里的对局会丢失——当前是单局演示，没有做持久化，重启相当于开新局。

## 备选方案

如果 Render 用着不顺手，下面这些也都能跑本项目（都认 `Dockerfile`）：

| 平台 | 说明 |
| --- | --- |
| Railway | 界面更简洁，但现在需要绑定付款方式 |
| Koyeb | 有免费档，部署流程和 Render 类似 |
| Zeabur | 中文界面，国内访问相对友好，支持 Docker |
| 阿里云 / 腾讯云 | 在国内最快，但需要自己买服务器，配域名访问还要备案 |

## 排错速查

| 现象 | 原因和处理 |
| --- | --- |
| `Connection was reset` | 没走代理。确认 Clash 开着，端口是 7897 |
| `Repository not found` | 仓库没建，或用户名/仓库名写错 |
| 推送时反复要密码 | 在 GitHub 生成 Personal Access Token 当密码用；或在凭据管理器里重新登录 |
| 部署成功但页面 404 | `Dockerfile` 里的 `COPY web ./web` 没生效，检查仓库里 `web` 目录是否推上去了 |
| 页面能开但操作无反应 | 后端接口报错，去 Render 的 Logs 看堆栈 |
| 打开很慢 | 免费档休眠后唤醒，等半分钟；或把 Region 改成 Singapore |
