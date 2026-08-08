# Docker 与部署目录

本目录存放项目容器化、服务器部署和运维相关的**非敏感**文件。后续 GitHub Actions 构建镜像、服务器通过 Docker Compose/轻量面板部署时，统一以本目录为入口。

## 目录职责

```text
docker/
├─ compose/          # 环境编排文件：后续放置生产、测试等 docker-compose 文件
├─ nginx/            # Nginx 反向代理、HTTPS、用户端/管理端静态站点配置
├─ scripts/          # 部署、备份、恢复、健康检查等脚本
└─ elasticsearch/    # Elasticsearch 镜像与本地开发相关配置
    └─ plugins/      # 本地缓存的 ES 插件压缩包（已忽略，不提交）
```

## 安全边界

以下内容不得提交到仓库，也不得写入 Dockerfile 或镜像层：

- 生产数据库、Redis、RabbitMQ 的账号密码；
- OSS、支付平台、JWT、镜像仓库的真实密钥；
- `.env`、`application-prod.yml`、证书私钥；
- MySQL、Redis、RabbitMQ、Elasticsearch 的数据卷和备份文件。

生产运行配置应保存在服务器受限目录中，并以环境变量或挂载配置文件的方式注入容器。数据卷应使用宿主机持久化目录或受管数据库服务，绝不能随应用镜像更新而删除。

## 当前状态

- `elasticsearch/`：已有用于本地 Elasticsearch 8.17.0 的 Dockerfile 与 compose 文件；第一版 4 GB 服务器不默认与核心交易服务同机部署。
- `compose/`、`nginx/`、`scripts/`：仅为后续上线阶段预留目录，目前不含生产部署配置。

> 新增部署文件前，必须先在 `docs/plans/` 创建对应阶段计划并说明涉及的文件、配置来源、验证和回滚方式。
