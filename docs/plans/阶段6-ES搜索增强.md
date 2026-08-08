# Phase 6: ES 搜索增强

> 基于主计划 [功能拓展主计划.md](功能拓展主计划.md) Phase 6
> 日期: 2026-07-19

## Context

Phase 6 目标：补全 Elasticsearch 搜索能力，解决中文搜索、拼音搜索、数据同步、搜索筛选/高亮/排序问题。

### 变更概要

| 模块 | 改动 |
|------|------|
| Docker | ES Dockerfile 装 IK + pinyin 插件，加 Kibana，端口统一为 19200 |
| 后端 | 增强 ProductIndexService（拼音 mapping + 缺失字段），新增定时同步 + 手动触发 Controller + CRUD 实时同步 |
| agent-service | 搜索改为 IK/pinyin 双通道 bool 查询，支持分类/价格/标签筛选、高亮、多种排序 |
| 管理端 | 新增 ES 同步控制页（状态查看 + 一键全量同步） |

### 涉及文件

**Docker**
- `docker/elasticsearch/Dockerfile` — 新增，安装 IK + pinyin 插件
- `docker/elasticsearch/docker-compose.yml` — 修改，改用 build + 加 Kibana + 端口 19200

**后端**
- `fashion-server/.../config/ElasticsearchConfig.java` — 修改，host 改为 `@Value` 可配置
- `fashion-server/.../service/ProductIndexService.java` — 修改，新增 syncProduct/deleteProduct/getIndexStatus
- `fashion-server/.../service/impl/ProductIndexServiceImpl.java` — 重写，拼音 mapping + tag/status/stock 字段 + 单条同步
- `fashion-server/.../task/ProductSyncTask.java` — 新增，@Scheduled 每 5 分钟同步
- `fashion-server/.../controller/admin/EsSyncController.java` — 新增，POST /admin/es/sync + GET /admin/es/status
- `fashion-server/.../controller/admin/ProductController.java` — 修改，CRUD 时实时同步 ES

**agent-service**
- `agent-service/app/tools/search_product.py` — 重写，bool + 筛选 + 高亮 + 排序
- `agent-service/app/tools/recommend.py` — 修改，IK/pinyin 双通道
- `agent-service/app/agent/nodes.py` — 修改，适配新返回格式

**管理端**
- `frontend/fashion-admin/src/views/EsSyncControl.vue` — 新增
- `frontend/fashion-admin/src/router/index.js` — 修改，加 /es/sync 路由
- `frontend/fashion-admin/src/App.vue` — 修改，侧边栏加入口（index 6）

## 验证方式

1. Docker Compose 启动 ES + Kibana，验证 IK/pinyin 插件
2. `POST /admin/es/sync` 全量同步
3. `GET /admin/es/status` 查看索引状态
4. agent-service 搜索测试：中文、拼音、筛选、排序
5. 管理端 ES 同步页查看状态
