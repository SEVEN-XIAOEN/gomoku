# 网页五子棋（Java 案例）

一个用 **纯 Java（零第三方依赖）** 写后端、用原生 HTML/CSS/JavaScript 写前端的五子棋人机对战案例。

Java 负责所有"真正难"的部分：棋盘状态、落子合法性、五连判定、电脑 AI 决策。
网页只负责把棋盘画出来、把点击发给后端。所以这是一个货真价实的 Java 案例，同时又有一个可以在浏览器里玩的界面。

## 技术要点

| 关注点 | 实现方式 |
| --- | --- |
| Web 服务 | JDK 自带的 `com.sun.net.httpserver.HttpServer` |
| 数据格式 | 手写极简 JSON 工具（教学用，正式项目请用 Jackson / Gson） |
| 构建工具 | 不需要，`javac` + `jar` 即可 |
| 第三方依赖 | 无 |
| 前端 | 原生 Canvas + fetch，不引入任何库 |

## 快速开始（Windows）

```bat
cd E:\codexTest\JAVA\gomoku
build.bat
run.bat
```

`build.bat` 负责编译打包，`run.bat` 负责启动服务。然后浏览器打开 <http://localhost:8080> 就能玩了。

想换端口：`run.bat 9000`

### 不用脚本，手动敲命令

```bat
javac -encoding UTF-8 -d out src\*.java
java -cp out GomokuServer
```

`-encoding UTF-8` 不能省，因为源码里有中文注释。

## 运行自测

项目自带一个不需要任何测试框架的自测程序，用来验证胜负判定和 AI 的攻防逻辑：

```bat
javac -encoding UTF-8 -d out-test src\Board.java src\GomokuAI.java src\Json.java src\Game.java test\SelfTest.java
java -cp out-test SelfTest
```

## 项目结构

```
gomoku/
├── src/
│   ├── Board.java          棋盘模型：落子、悔棋、五连判定
│   ├── GomokuAI.java       电脑对手：打分 + 堵截 + 推演
│   ├── Game.java           一局棋的状态与业务规则
│   ├── Json.java           极简 JSON 读写工具
│   └── GomokuServer.java   HTTP 服务：接口 + 静态文件
├── test/
│   └── SelfTest.java       自测程序
├── web/
│   ├── index.html          页面结构
│   ├── style.css           样式
│   └── app.js              画棋盘、发请求、处理交互
├── build.bat / run.bat     一键构建与运行
├── Dockerfile              部署到云平台用
└── README.md
```

## 怎么玩

- 你执黑棋先行，电脑执白棋。
- 点击交叉点落子，鼠标悬停会显示半透明预览。
- 顶部可以切换棋盘大小（9×9 / 13×13 / 15×15）和电脑难度（简单 / 普通 / 困难）。
- 下错了可以点「悔棋」，会同时撤销你的上一手和电脑的应手。
- 先连成五子的一方获胜，获胜的连珠会高亮显示。

## 接口说明

所有接口都返回同一个状态对象。

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/state` | 无 | 读取当前对局 |
| POST | `/api/new` | `{"size": 15}` | 开新局，size 支持 9 / 13 / 15 |
| POST | `/api/move` | `{"r": 7, "c": 7}` | 玩家落子，电脑会自动应手 |
| POST | `/api/undo` | `{}` | 悔棋 |
| POST | `/api/difficulty` | `{"level": 2}` | 切换难度，1 / 2 / 3 |

返回示例：

```json
{
  "size": 15,
  "status": "playing",
  "playerColor": 1,
  "aiColor": 2,
  "turn": 1,
  "lastMove": {"r": 7, "c": 7},
  "moves": 12,
  "difficulty": 2,
  "message": "轮到你落子",
  "aiMillis": 128,
  "winLine": null
}
```

（`board` 字段是一个 `size × size` 的二维数组，取值 0 = 空、1 = 黑棋、2 = 白棋，这里省略没写。）

`status` 取值：`playing`、`black_win`（玩家胜）、`white_win`（电脑胜）、`draw`。
参数不合法时返回 HTTP 400 和 `{"error": "..."}`。

## 代码讲解：AI 是怎么下棋的

`GomokuAI` 的思路不复杂，但很有效，一共三步：

1. **筛选候选点。** 只考虑离已有棋子两格以内的空位，把 225 个点压缩到几十个点。
2. **先看生死，再看价值。** 自己能一步成五就立刻赢；对手下一步能成五就赶紧堵；否则给每个点打分，分数 = 我下这里的收益 + 对手下这里的收益 × 0.9（后者代表"我占了这里，对手就占不到了"）。另外还会加一点"贴棋奖励"：离已有棋子越近的点分数越高，免得电脑跑到远处下孤棋。
3. **用"五格窗口"打分。** 沿横、竖、两条对角线四个方向，检查所有包含该点的、长度为 5 的窗口。窗口里混进对方棋子就不算威胁；否则自己棋子越多分越高。窗口越过棋盘边界时按"被挡住"处理，所以边上的棋力自然弱于中间，符合五子棋常识。

三个难度就是在这套逻辑上做加减：

- **简单**：随便挑一个相邻空位，而且只有 60% 的概率会去堵你的四连，所以你能赢。
- **普通**：完整执行上面三步。
- **困难**：在评分最高的前 10 个点里，再往前推一层——"我下这里之后，对手最强的一手值多少分"，把这个威胁从分数里扣掉，于是它更少走"只顾进攻不看防守"的棋。

## 部署到网址上

这是 Java 后端项目，不能直接扔到 GitHub Pages 或 Vercel（那些只能放静态页面）。需要能跑 Java 的平台，好在项目已经带了 `Dockerfile`，主流平台都能一键部署。

### 步骤

1. 把 `gomoku` 目录提交到 GitHub 仓库。
2. 打开 Render（或 Railway、Fly.io），用 GitHub 账号登录，选择 New Web Service。
3. 关联刚才的仓库。平台会发现 `Dockerfile`，自动完成构建。
4. 部署完会得到一个类似 `https://你的项目名.onrender.com` 的网址，点开就能玩。

程序会自动读取平台注入的 `PORT` 环境变量，并把服务绑定到 `0.0.0.0`，所以不用改任何代码。

### 关于成本

- Render 免费档能跑，但一段时间没人访问会休眠，下次访问要等十几秒唤醒。
- 想更稳可以选最低档付费实例。
- 免费档一般不带自定义域名，付费后可以把你自己买的域名绑上去。

## 常见问题

**端口被占用** —— 换一个端口启动：`run.bat 9000`，浏览器相应改成 `http://localhost:9000`。

**页面打开是 404** —— 说明程序没找到 `web` 目录。必须在 `gomoku` 目录下启动（`run.bat` 已经帮你切好目录了）。

**编译报"编码 GBK 不可映射字符"** —— 漏掉了 `-encoding UTF-8`。

**想换成别的游戏** —— Java 部分（`Board` + `Game` + `GomokuServer` + `Json`）是一套通用的"回合制棋盘游戏后端"骨架，把 `Board` 和 `GomokuAI` 换掉，就能改成五子棋之外的棋类。

## 可以继续练习的方向

- 把单局状态换成 `Map<String, Game>`，支持多人在线对战。
- 加一个数据库（SQLite / PostgreSQL），做排行榜和历史战绩。
- 给 AI 加阿尔法贝塔剪枝，把"困难"级别做得更强。
- 前端加音效、落子动画、复盘功能。
