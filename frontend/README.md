# CodeJudge 在线判题系统 · 前端

Vue3 + TypeScript + Vite + Pinia + Vue Router 的纯前端，与后端（`../backend`）前后端分离。

## 技术栈

| 层 | 选型 |
|---|---|
| 框架 | Vue 3.5（`<script setup>` 语法） |
| 语言 | TypeScript 5.6（strict） |
| 构建 | Vite 5 |
| 状态 | Pinia 2 |
| 路由 | Vue Router 4（含登录守卫 + 管理员路由守卫） |
| 请求 | Axios（统一响应拦截，自动带 token、非 0 码抛错） |

## 目录结构

```
src/
├── api/         接口封装（auth / problem / submission / leaderboard）
├── components/  AppLayout（导航壳）、CodeEditor（代码框）
├── router/      路由 + 守卫
├── stores/      auth（token / 用户信息）
├── types/       与后端 DTO/VO 对应的类型
└── views/       登录 / 注册 / 题目列表 / 题目详情(提交) / 我的提交 / 排行榜 / 题目管理
```

## 开发

```bash
npm install        # 或 pnpm install
npm run dev        # 默认 5173，/api 已通过 proxy 转发到后端 8080
```

## 生产构建

```bash
npm run build      # 产物在 dist/，交给 Nginx 同域托管即可
```

> 前端不内置任何后端地址硬编码，所有请求走相对路径 `/api`，本地靠 Vite proxy、线上靠 Nginx 转发，天然避免跨域。
