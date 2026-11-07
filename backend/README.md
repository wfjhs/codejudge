# CodeJudge 在线判题系统 · 后端

> 支持多语言代码提交、异步判题、实时结果推送的在线评测平台。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言/运行时 | JDK 21（启用虚拟线程） |
| 框架 | Spring Boot 3.3.5 |
| ORM | MyBatis-Plus 3.5.9（`mybatis-plus-spring-boot3-starter`） |
| 数据库 | MySQL 8.0 |
| 缓存/队列 | Redis（Stream 判题队列、Lua 令牌桶限流、Cache-Aside 题目缓存、ZSet 排行榜）|
| 判题隔离 | 进程级沙箱（ProcessBuilder + 超时强杀 + `-Xmx`），`CodeSandbox` 接口可换容器级 |
| 鉴权 | JWT（jjwt 0.12）+ 自研拦截器，未引入 Spring Security 过滤器链 |
| 构建 | Maven 3.9 多模块 |

## 模块结构

```
backend/
├── cj-common     通用：统一响应体、业务异常、用户上下文、JWT 工具（不依赖 Spring）
├── cj-model      数据模型：实体 / DTO / VO / 枚举
├── cj-dao        数据访问：Mapper
├── cj-service    业务逻辑 + 判题引擎 + 进程级沙箱 + Stream 消费
└── cj-web        Web 层：Controller、WebSocket、配置、启动类
```

依赖方向严格单向：`cj-web → cj-service → cj-dao → cj-model → cj-common`。

---

## 判题引擎

判题通过 `codejudge.judge.engine` 切换，两个实现互斥装配：

| 取值 | 实现 | 用途 |
|---|---|---|
| `mock` | `MockJudgeEngine` | 不执行真实代码，按源码里的 `FORCE_CE/FORCE_WA/FORCE_TLE/FORCE_RE` 关键字返回对应结果，用于联调 |
| `local` | `LocalJudgeEngine` | 进程级沙箱真实执行（**默认**） |

沙箱通过 `CodeSandbox` 接口暴露，当前实现 `LocalProcessSandbox`：
`ProcessBuilder` + `waitFor` 超时强杀 + `-Xmx` 限堆 + 输出归一化比对。

它拦不住 fork 炸弹、网络访问、越权读文件，也测不准内存峰值——这些需要容器 + cgroup 才能解决。
将来要提升隔离级别，加一个 `CodeSandbox` 的实现即可，`LocalJudgeEngine` 一行都不用改。

---

## 快速开始

### 0. 环境要求

- **JDK 21**（必须，项目 `maven.compiler.release=21`，且开启了虚拟线程）
- Maven 3.9+
- MySQL 8.0（本地 3306 已运行）

### 1. 用 IDEA 打开

1. IDEA 菜单 **File → Open**，选择 **`backend`**（这个目录下才是 `pom.xml`，不要选到 `codejudge` 或仓库根目录）
2. IDEA 会自动识别为 Maven 多模块工程并开始下载依赖，等右下角进度条走完
3. 确认 SDK 版本：**File → Project Structure → Project**
   - SDK 选择 **21**（如列表里没有，点 `Add SDK → JDK` 指向本机 JDK 21 的安装目录）
   - Language level 选 **21**
4. 如果 Maven 项目没有自动加载：右键根目录 `pom.xml` → **Add as Maven Project**

> 项目已配置阿里云镜像（`~/.m2/settings.xml`），依赖下载走国内源。

### 2. 初始化数据库

```bash
mysql -u root -p --default-character-set=utf8mb4 < sql/init.sql
```

会创建 `codejudge` 库和 `user`、`problem` 两张表。

数据库账号密码在 `cj-web/src/main/resources/application.yml`：

```yaml
username: ${DB_USERNAME:root}
password: ${DB_PASSWORD:123456}
```

默认按 `root/123456` 配置，如果本机密码不同，改这里的默认值即可（或设置同名环境变量）。

### 3. 启动

运行 `com.codejudge.web.CodeJudgeApplication` 的 `main` 方法。

看到 `Started CodeJudgeApplication` 且端口 8080 监听成功，即启动完成。

### 4. 验证接口

打开 **`http/auth.http`**，IDEA 会在每个请求上方显示绿色运行箭头，点着跑即可：

- 先跑「注册」（第一个注册的用户自动成为管理员）
- 再跑「登录」（token 会自动存入变量 `token`）
- 最后跑「当前用户信息」（自动带上 token）

---

## 已实现接口

### 认证与用户

| 方法 | 路径 | 说明 | 是否需 token |
|---|---|---|---|
| POST | `/api/auth/register` | 注册 | 否 |
| POST | `/api/auth/login` | 登录，返回 JWT | 否 |
| GET | `/api/users/me` | 当前登录用户 | 是 |
| GET | `/api/users/{id}` | 查询指定用户 | 是 |

### 题目（公开，需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/problems?page=1&size=10&difficulty=&keyword=&sortBy=&asc=` | 列表分页，**不返回题面** |
| GET | `/api/problems/{id}` | 详情，**只返回示例用例** |

### 题目管理（需管理员）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/problems` | 管理端列表 |
| GET | `/api/admin/problems/{id}` | 详情，**含隐藏用例** |
| POST | `/api/admin/problems` | 创建（含测试用例） |
| PUT | `/api/admin/problems/{id}` | 更新（用例全量替换） |
| DELETE | `/api/admin/problems/{id}` | 逻辑删除 |

统一响应体：

```json
{
  "code": 0,
  "message": "成功",
  "data": { },
  "timestamp": 1761264000000
}
```

`code=0` 表示成功，其余为错误码，详见 `ResultCode`。

分页接口的 `data` 结构：

```json
{
  "records": [],
  "total": 1,
  "page": 1,
  "size": 10,
  "pages": 1
}
```

---

## 关键设计说明

### 为什么没用 Spring Security

只引入了 `spring-security-crypto` 拿 BCrypt，鉴权用自己写的 `JwtInterceptor`。
理由：全家的过滤器链对单体项目过重，且"我为什么不用它"比"我配了它"更能体现判断力。

### 为什么所有响应都是 HTTP 200

业务结果放在 `body.code` 里，前端拦截器只需处理一种响应格式。
**已知代价**：丢失 HTTP 状态码语义，网关按状态码熔断/监控会失效，接网关时需要改造。

### 为什么开启虚拟线程

`spring.threads.virtual.enabled=true`。判题链路是 IO 密集型，虚拟线程可用少量平台线程承载大量阻塞请求。
注意：`synchronized` 包裹阻塞调用会钉住载体线程，压测时要重点观察这部分。

### 第一个注册用户即为管理员

避免手工插入初始账号，克隆下来就能用。

### 权限用注解 + 拦截器，没用 AOP

`@RequireAdmin` 可以标在方法或类上，`AdminInterceptor` 通过 `HandlerMethod` 读取注解并校验角色。
选择拦截器而不是 AOP 切面的理由：

- 拦截器在 DispatcherServlet 阶段就执行，权限不通过时连 Controller 都进不去，也省掉一层代理开销
- 拦截器能直接拿到 `HandlerMethod`，读注解和 AOP 一样方便
- AOP 更适合"方法执行前后"的增强（日志、事务、限流），"能不能进"语义上属于拦截器

两个拦截器的顺序是 `JwtInterceptor(order=1)` → `AdminInterceptor(order=2)`，
后者依赖前者已经把用户写进 `UserContext`。

### 题目列表不返回题面

`content` 是 `MEDIUMTEXT`，单条几 KB 到几十 KB。列表一页 10 条就是几百 KB 的无用传输，
所以列表接口用 `select()` 显式指定字段，只有详情接口才查 `content`。

### 隐藏用例绝不外泄

公开详情只返回 `is_sample=1` 的用例。如果隐藏用例的输入输出能被前端拿到，
用户直接照着期望输出打表就能 AC —— 这是判题系统最基本的安全边界，
M3 的提交接口还会再加一层：判题时由服务端自己查用例，客户端传什么都不信。

---

## 常见问题

### 启动后端口不是 8080，而是一个随机端口

先检查有没有环境变量 `SERVER__PORT`（**双下划线**）：

```bash
# Git Bash / CMD
echo $SERVER__PORT
# PowerShell
$env:SERVER__PORT
```

有的话请删掉。Spring Boot 的松散绑定会把 `SERVER__PORT` 解析成 `server.port`，
而**系统环境变量的优先级高于 application.yml**，所以配置文件里的 8080 会被覆盖。
（本机开发环境就踩过这个坑：某个工具注入了 `SERVER__PORT=0`，导致 Tomcat 每次随机端口启动。）

命令行排查可以用 `--server.port=8080` 强制覆盖，或启动前 `unset SERVER__PORT`。

### IDEA 里 pom.xml 一片红

1. 确认打开的是 `backend` 目录（有 `pom.xml` 的那层）
2. 右键根 `pom.xml` → **Maven → Reload project**
3. 确认 Project SDK 是 21

### 命令行 `mvn` 报 "JAVA_HOME is not defined correctly"

本机系统 `JAVA_HOME` 指向了开始菜单目录（不是 JDK 安装目录）。
命令行构建时用 `tools/mvn.sh` 包装脚本即可绕开；IDEA 用的是自带 Maven，不受影响。

---

## 开发进度

- [x] **M1** 多模块骨架、统一响应体、全局异常、MyBatis-Plus 配置、JWT 登录注册
- [x] **M2** 题目模块 CRUD + 分页筛选 + 测试用例管理 + `@RequireAdmin` 管理员权限
- [x] **M3** 提交 → Redis Stream → 消费者组 → 结果回写（幂等条件更新 + `XPENDING/XCLAIM` 故障转移）
- [x] **M4** 判题引擎抽象 `JudgeEngine` + 进程级沙箱 `LocalProcessSandbox`（编译/运行/超时强杀/`-Xmx`/独立工作目录）+ `LocalJudgeEngine` 逐用例比对，可一键切回 `MockJudgeEngine`
- [x] **M5** WebSocket 实时推送判题结果（`/ws/judge/{submissionId}`，握手校验 token + 越权防护，前端 WS 优先、轮询兜底）
- [x] **M6** 三级令牌桶限流（Redis + Lua 原子脚本：全局/用户/题目）+ `requestId` 幂等（唯一索引兜底）
- [x] **M7** 题目详情 Cache-Aside（TTL 5min + 更新/删除失效）+ ZSet 解题排行榜 `/api/leaderboard`
- [x] **M8** 前端（Vue3 + TS + Vite + Pinia）：登录/注册/题目列表/题目详情(提交+轮询)/我的提交/排行榜/题目管理，已通过 `npm run build` 类型检查与生产构建
- [ ] M9 压测与优化（判题链路已端到端跑通，可直接压测产出 QPS / 耗时量化数据）

---

## 判题引擎与隔离说明

提交链路的隔离级别是**进程级**（不是容器级）：每条提交在临时目录里编译运行，超时由 `Process.waitFor` + `destroyForcibly()` 强杀，Java 用 `-Xmx` 限制堆。
进程级隔离拦不住 fork 炸弹、网络访问、越权读文件——这些是容器 + seccomp + pids limit 才能彻底解决的。
因此 `CodeSandbox` 抽象出来，日后装了 Docker 只需加一个 `DockerSandbox` 实现，`LocalJudgeEngine` 一行都不用改。

判题引擎通过 `codejudge.judge.engine` 切换：
- `mock`（默认）：不执行真实代码，按源码里的 `FORCE_CE/FORCE_WA/FORCE_TLE/FORCE_RE` 关键字返回对应结果，便于联调
- `local`：进程级沙箱真实执行代码

> ✅ **Redis 版本**：判题队列用 Redis Stream（`XADD/XREADGROUP/XPENDING/XCLAIM`），要求 **Redis ≥ 5.0**。
> 本地已升级到 **Redis 8.10.3**（msys2 构建），Stream 与消费者组实测可用。

> ⚠️ **消费者组的坑（已修）**：`XGROUP CREATE` 要求 Stream 已存在，而 Spring Data Redis 的 `createGroup`
> 不带 `MKSTREAM`。首次启动 Stream 为空时会建组失败、导致消息永远不被消费。代码里已用"占位消息建 Stream → 删除 → 建组"
> 规避，并在消费循环捕获 `NOGROUP` 自动重建组。

---

## WebSocket 实时推送判题结果

前端提交后不再傻等轮询：建立 `WebSocket` 连接到 `/ws/judge/{submissionId}`，判题完成即时收到结果。

- **端点**：`/ws/judge/{submissionId}?token=<JWT>`
- **鉴权**：握手阶段由 `JwtHandshakeInterceptor` 从 query（或 `Authorization` 头）取 token 校验，并解析出 `CurrentUser` 放进握手属性；非法 token 直接拒绝握手。
- **越权防护**：`JudgeWebSocketHandler.afterConnectionEstablished` 会查一次提交归属，只能订阅**自己的**提交（管理员例外），避免通过猜 `submissionId` 偷看他人代码/结果。
- **解耦**：判题结果写回 DB 后，`SubmissionServiceImpl` 发布一个 `JudgeCompletedEvent`（`cj-model` 里的纯 POJO，不依赖 Spring）。`cj-web` 的 `JudgeWebSocketHandler` 用 `@EventListener` 监听并推送。
  这样 **cj-service 不反向依赖 cj-web**，模块边界干净；日后把判题消费者拆成独立服务时，把这个事件换成 MQ 消息即可。
- **前端兜底**：`connectJudgeWs` 封装了 WS；连不上（老版本后端 / 代理不支持）就触发 `onError` 回退到原来的 HTTP 轮询；提交瞬间还会先拉一次详情，兜住"结果在 WS 连上前就已写回"的竞态。

---

## 端到端验证（已实测 12/12 通过）

```bash
# 1. 启动后端（确保 MySQL / Redis 已运行）
java -jar cj-web/target/codejudge.jar
# 2. 另开终端跑端到端测试
python tools/e2e_judge.py
```

覆盖真实沙箱的完整链路（提交 → Stream → 消费者 → 编译/运行 → 回写）：

| 场景 | 结果 |
|---|---|
| Java 正确解 | AC（满分 100，约 218ms） |
| Java 错误解 | WA（failCase=1） |
| Java 编译错误 | CE（返回编译错误信息） |
| Java 死循环 | TLE（`destroyForcibly` 强杀，约 3.1s） |
| Python 正确解 | AC（解释型链路约 307ms） |
| 排行榜 / 题目统计 | ZSet 榜单正确、submitCount/acceptCount 实时累加 |

---

## 种子数据

见 `sql/seed.sql`（3 道示例题 + 测试用例，可重复执行）。**注意要指定数据库名**：

```bash
mysql -u root -p123456 codejudge < sql/seed.sql
```

种子里的 `ON DUPLICATE KEY UPDATE` 会把 `deleted` 复位为 0——历史测试逻辑删除过的题目重跑种子即可复活，
否则 `selectById` 带 `deleted=0` 条件会查不到，提交时报"题目不存在"。

管理员账号从前端注册，首个注册用户自动成为管理员。
