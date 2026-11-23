# 棋类对战平台（五子棋 + 中国象棋）

一个用 **纯 Java（零第三方依赖）** 写后端、用原生 HTML / CSS / JavaScript 写前端的棋类对战平台。启动后先在**大厅选择棋种**，再进入五子棋或中国象棋主界面；两种棋都支持 **人机对战** 和 **房间码联机**。

服务端是唯一裁判：棋盘状态、走法合法性、将军、将死 / 困毙、重复局面、自然限着、计时与超时全部在 Java 后端完成。浏览器只负责渲染和提交坐标，不在前端复制规则。

## 功能概览

| 能力 | 五子棋 | 中国象棋 |
| --- | --- | --- |
| 大厅选择棋种 | 支持 | 支持 |
| 人机对战 | 简单 / 普通 / 困难 | 简单 / 普通 / 困难 |
| 联机对战 | 6 位房间码 | 6 位房间码 |
| 先后手选择 | 黑 / 白 / 随机 | 红 / 黑 / 随机 |
| 服务端权威计时 | 支持，按座位停表 | 支持，按座位停表 |
| 悔棋 | 人机撤回一轮；联机撤回自己最后一手 | 人机撤回一轮；联机撤回自己最后一手 |
| 认输 / 提和 | 支持 | 支持 |
| 再来一盘 | 双方确认后换边 | 双方确认后换边 |
| 规则裁决 | 五连判定 | 走子、将军、将死、困毙、重复、自然限着 |

### 中国象棋规则范围

- 帅 / 将、仕 / 士、相 / 象、马、车、炮、兵 / 卒的完整基本走子规则；
- 九宫限制、象不过河、塞象眼、蹩马腿、炮架、兵 / 卒过河、飞将；
- 走子后不得令己方被将军；
- 将死与困毙判负；
- 三次重复局面、长将 / 长捉裁决；
- 60 回合（120 个半回合）无吃子自然限着；
- 双方均无取胜可能时判和。

> 长捉采用工程化近似判定，适合休闲对局与教学演示，不等同于官方裁判级细则。其余核心规则由 XiangqiRules 与 XiangqiMatch 统一实现。

## 快速开始（Windows）

~~~bat
cd E:\codexTest\JAVA\gomoku
build.bat
run.bat
~~~

浏览器打开 <http://localhost:8080>，先在大厅选择「五子棋」或「中国象棋」。

换端口：

~~~bat
run.bat 9000
~~~

### 手动构建

~~~bat
javac -encoding UTF-8 -d out src\*.java
java -cp out GomokuServer
~~~

-encoding UTF-8 不能省，源码和前端文件都包含中文。

## 运行测试

项目自带零依赖自测程序：

~~~bat
javac -encoding UTF-8 -d out-test src\*.java test\*.java
java -cp out-test SelfTest
java -cp out-test XiangqiTest
~~~

- SelfTest：五子棋胜负、AI、会话与房间基础逻辑；
- XiangqiTest：象棋走法、将军、将死 / 困毙、飞将、炮架、马腿、象眼、重复局面、自然限着、计时等。

## 使用流程

1. 打开首页，进入**大厅**；
2. 选择「五子棋」或「中国象棋」；
3. 在棋种主界面选择「人机对战」或「联机对战」；
4. 人机模式选择难度 / 先后手后点「新对局」；
5. 联机模式点「创建房间」，选择执先 / 执后 / 随机和时限，把 6 位房间码发给朋友；
6. 对手输入房间码加入，双方到齐后自动开局；
7. 对局结束后双方都点「再来一盘」，系统自动交换先后手。

### 五子棋

- 棋盘支持 9×9 / 13×13 / 15×15；
- 人机模式固定由玩家先行；
- 联机模式座位 1 执黑先行，座位 2 执白；
- 横、竖、斜任意方向先连成五子者获胜。

### 中国象棋

- 红方先行，座位 1 执红，座位 2 执黑；
- 先点己方棋子，服务端返回合法落点，再点目标位置完成走子；
- 人机模式可选择执红、执黑或随机；
- 联机模式创建房间时可选择执红、执黑或随机；
- 计时只走当前行棋方，走子后加秒。

## 项目结构

~~~
gomoku/
├── src/
│   ├── Board.java             五子棋棋盘模型
│   ├── Game.java              五子棋单局状态与规则
│   ├── GomokuAI.java          五子棋 AI
│   ├── GomokuRoom.java        五子棋联机房间
│   ├── GameClock.java         服务端权威对局时钟
│   ├── Room.java              联机房间公共基类
│   ├── XiangqiBoard.java      象棋棋盘与棋子编码
│   ├── XiangqiRules.java      象棋走法生成、将军与合法性
│   ├── XiangqiMove.java       象棋着法记录
│   ├── XiangqiMatch.java      象棋规则状态机与终局裁决
│   ├── XiangqiAI.java         象棋 AI
│   ├── XiangqiSolo.java       象棋人机对局
│   ├── XiangqiRoom.java       象棋联机房间
│   ├── Json.java              极简 JSON 读写工具
│   └── GomokuServer.java      HTTP 服务、接口与静态文件
├── test/
│   ├── SelfTest.java          五子棋与通用逻辑自测
│   └── XiangqiTest.java       象棋规则与计时自测
├── web/
│   ├── index.html             大厅与两个棋种页面结构
│   ├── style.css              响应式样式
│   └── js/
│       ├── core/              API、UI 工具、Hash 路由
│       ├── games/gomoku/      五子棋渲染与页面控制器
│       ├── games/xiangqi/     象棋渲染与页面控制器
│       ├── lobby.js           大厅入口
│       └── app.js             应用入口与全局轮询
├── build.bat / run.bat        一键构建与运行
├── Dockerfile                 容器部署
├── DEPLOY.md                  部署说明
└── README.md
~~~

## 架构说明

### 两层游戏模型

- **人机局**：每个浏览器会话拥有独立的 Game（五子棋）或 XiangqiSolo（象棋）。
- **联机局**：Room 负责座位、时钟、认输、提和、再来一盘；子类 GomokuRoom / XiangqiRoom 负责具体棋盘。

### 座位与颜色

| 座位 | 五子棋 | 中国象棋 |
| --- | --- | --- |
| 1（先手） | 黑棋 | 红方 |
| 2（后手） | 白棋 | 黑方 |

### 前端路由

~~~text
#/lobby    大厅，选择棋种
#/gomoku   五子棋主界面
#/xiangqi  中国象棋主界面
~~~

启动时若没有显式 Hash，始终进入大厅，不自动恢复到上次棋种。点「返回大厅」会通知服务端离开当前房间。

### 坐标与棋子编码

五子棋落子：

~~~json
{"r": 7, "c": 7}
~~~

象棋走子：

~~~json
{"fr": 7, "fc": 1, "tr": 7, "tc": 4}
~~~

棋子编码：颜色 × 10 + 类型。

- 颜色：红 = 1，黑 = 2；
- 类型：帅 / 将 = 1，仕 / 士 = 2，相 / 象 = 3，马 = 4，车 = 5，炮 = 6，兵 / 卒 = 7。

例如红车 = 15，黑将 = 21。

## 接口说明

服务端通过 Cookie gomoku_sid 区分浏览器会话。Cookie 名沿用旧版，升级后老会话仍能识别。

### 平台 / 会话

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| GET | /api/games | 无 | 棋种列表 |
| GET | /api/session/state | 无 | 当前会话状态 |
| POST | /api/session/game | {"game":"lobby"} | 切换当前棋种；lobby / gomoku / xiangqi |

### 五子棋人机

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | /api/gomoku/mode | {"mode":"solo"} | 切换人机 / 联机 |
| GET | /api/gomoku/state | 无 | 人机状态 |
| POST | /api/gomoku/new | {"size":15} | 开新局，size 支持 9 / 13 / 15 |
| POST | /api/gomoku/move | {"r":7,"c":7} | 落子，电脑自动应手 |
| POST | /api/gomoku/undo | {} | 悔棋 |
| POST | /api/gomoku/difficulty | {"level":2} | 难度 1 / 2 / 3 |

### 中国象棋人机

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | /api/xiangqi/mode | {"mode":"solo"} | 切换人机 / 联机 |
| GET | /api/xiangqi/state | 无 | 人机状态 |
| POST | /api/xiangqi/new | {"side":1,"level":2,"minutes":5,"increment":3} | 开新局；side 1 红 / 2 黑 / 0 随机 |
| POST | /api/xiangqi/move | {"fr":7,"fc":1,"tr":7,"tc":4} | 走子 |
| POST | /api/xiangqi/undo | {} | 悔棋 |
| POST | /api/xiangqi/difficulty | {"level":2} | 难度 1 / 2 / 3 |
| POST | /api/xiangqi/legal | {"r":7,"c":1} | 查询合法落点 |
| POST | /api/xiangqi/resign | {} | 认输 |

### 联机房间

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | /api/rooms/create | {"game":"xiangqi","seat":1,"minutes":5,"increment":3} | 创建房间；seat 1 先手 / 2 后手 / 0 随机 |
| POST | /api/rooms/join | {"room":"ABC234"} | 加入房间 |
| GET | /api/rooms/state | 无 | 房间状态 |
| POST | /api/rooms/move | 五子棋 {r,c}；象棋 {fr,fc,tr,tc} | 走子 |
| POST | /api/rooms/undo | {} | 撤回自己最后一手 |
| POST | /api/rooms/legal | {"r":7,"c":1} | 象棋合法落点 |
| POST | /api/rooms/resign | {} | 认输 |
| POST | /api/rooms/draw | {"action":"offer"} | 提和；也支持 accept / decline |
| POST | /api/rooms/rematch | {} | 投票再来一盘 |
| POST | /api/rooms/leave | {} | 离开房间 |

联机对战通过每 1.2 秒轮询房间状态同步；计时在前端做 100ms 本地插值，但以服务端时间为准。

## 已知限制

- 房间、会话和对局都保存在内存中，服务重启后会清空；
- 联机采用轮询，不是 WebSocket，低频对局足够，但不适合高并发竞技场景；
- 暂不支持观战；房间满员后第三位加入者会收到「房间已满」；
- 象棋 AI 在请求线程内同步计算，困难难度可能耗时 1～2 秒；
- 长捉判定为工程化近似，不宣称达到官方裁判级完整裁决；
- 构建与部署见 [DEPLOY.md](DEPLOY.md)。

## 与旧版本的关系

方案 B 采用平台化重构：

- 旧版仅五子棋的 /api/state、/api/mode、/api/new、/api/pvp/* 接口已移除；
- 前端改为大厅 + 分棋种模块；
- 保留 Cookie 名 gomoku_sid，避免老会话失效；
- 五子棋原有玩法（人机、联机、悔棋、难度、房间码）保留。
