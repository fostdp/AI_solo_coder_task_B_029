# 智慧水厂絮凝沉淀工艺智能加药系统

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Docker Compose 编排                          │
│                                                                     │
│  ┌──────────────┐   Modbus TCP    ┌──────────────┐                 │
│  │   Modbus      │ ◄────────────── │  SpringBoot   │                │
│  │   模拟器      │    :502         │   App         │                │
│  │   (Python)    │                 │   :8080       │                │
│  └──────────────┘                 └──────┬───────┘                 │
│                                          │ JDBC                     │
│                                          ▼                          │
│                                   ┌──────────────┐                 │
│                                   │  TimescaleDB  │                 │
│                                   │  :5432        │                 │
│                                   └──────────────┘                 │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     浏览器 ←── :8080 ──→ 前端 (Vue3 + Canvas) │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 模块架构

```
SpringBoot App
├── poller/ModbusPoller          设备轮询 & 数据采集
│   └── 发布 DataCollectedEvent
├── predictor/DosingPredictor    加药预测 & 模型管理
│   ├── 监听 DataCollectedEvent → 计算预测投加量
│   └── 监听 ModelRetrainRequestEvent → 重训练
├── notifier/AlarmNotifier       告警评估 & 钉钉推送
│   ├── 监听 DataCollectedEvent → 评估告警条件
│   └── 监听 AlarmEvent → 持久化 + 异步钉钉推送
├── scheduler/SystemScheduler    定时任务触发
│   └── 发布 ModelRetrainRequestEvent (每2小时)
├── event/                       Spring Events 事件定义
│   ├── DataCollectedEvent
│   ├── AlarmEvent
│   └── ModelRetrainRequestEvent
└── config/
    ├── SchedulerConfig          统一线程池 + 异步事件多播
    ├── WebConfig                CORS + Gzip + 静态资源缓存
    └── ModbusHealthIndicator    自定义 Actuator 健康指标
```

### 数据流

```
ModbusPoller ──DataCollectedEvent──→ DosingPredictor (预测+存加药记录)
             ──DataCollectedEvent──→ AlarmNotifier (评估告警)
                                    AlarmNotifier ──AlarmEvent──→ 持久化+钉钉
SystemScheduler ──ModelRetrainRequestEvent──→ DosingPredictor (重训练)
```

---

## 快速部署

### 前置条件

- Docker 20.10+
- Docker Compose v2+

### 一键启动

```bash
cd smart-water-dosing
docker compose up -d --build
```

启动后访问：
- 前端界面：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health
- Prometheus指标：http://localhost:8080/actuator/prometheus
- TimescaleDB：localhost:5432

### 查看日志

```bash
docker compose logs -f app              # SpringBoot 日志
docker compose logs -f modbus-simulator  # 模拟器日志
docker compose logs -f timescaledb       # 数据库日志
```

### 停止服务

```bash
docker compose down
docker compose down -v   # 同时删除数据卷
```

---

## 本地开发（不用 Docker）

### 1. 启动 TimescaleDB

```bash
docker run -d --name timescaledb \
  -p 5432:5432 \
  -e POSTGRES_DB=water_dosing \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  timescale/timescaledb:2.13.0-pg14

# 等待数据库就绪后初始化
psql -h localhost -U postgres -d water_dosing -f src/main/resources/db/init.sql
```

### 2. 启动 Modbus 模拟器

```bash
cd modbus-simulator
python modbus_simulator.py
```

### 3. 启动 SpringBoot

```bash
mvn spring-boot:run
```

### 4. 启动前端开发服务器

```bash
cd frontend
npm install
npm run dev     # http://localhost:3000, 代理API到8080
```

---

## Modbus 模拟器配置

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `LISTEN_HOST` | `0.0.0.0` | 监听地址 |
| `LISTEN_PORT` | `502` | 监听端口 |
| `UPDATE_INTERVAL` | `300` | 数据更新间隔（秒） |

### 原水水质参数

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `RAW_TURBIDITY` | `18.0` | 原水浊度基准值 (NTU) |
| `RAW_PH` | `7.1` | 原水pH基准值 |
| `RAW_TEMPERATURE` | `18.0` | 原水温度基准值 (°C) |
| `RAW_FLOW_RATE` | `8333` | 流量基准值 (m³/h) |

### 各工艺段浊度衰减比

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FLOCCULATION_TURB_RATIO` | `0.5` | 絮凝池浊度 = 原水浊度 × 比值 |
| `SEDIMENTATION_TURB_RATIO` | `0.12` | 沉淀池浊度 = 原水浊度 × 比值 |
| `FILTRATION_TURB_RATIO` | `0.02` | 滤池浊度 = 原水浊度 × 比值 |
| `OUTLET_TURB_RATIO` | `0.015` | 出水浊度 = 原水浊度 × 比值 |

### 水质波动范围

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FLUCT_TURBIDITY` | `2.0` | 浊度高斯噪声标准差 (NTU) |
| `FLUCT_PH` | `0.1` | pH高斯噪声标准差 |
| `FLUCT_TEMPERATURE` | `0.5` | 温度高斯噪声标准差 (°C) |
| `FLUCT_FLOW_RATE` | `200` | 流量高斯噪声标准差 (m³/h) |

### JSON 批量配置

通过 `WATER_PARAMS` 和 `WATER_FLUCTUATION` 环境变量可传入完整 JSON 配置，优先级低于单独环境变量：

```bash
docker run -e WATER_FLUCTUATION='{"turbidity":3.0,"ph":0.2,"temperature":1.0,"flow_rate":300.0}' \
  modbus-simulator
```

### 配置示例

模拟高浊度原水工况：

```yaml
# docker-compose.yml
modbus-simulator:
  environment:
    RAW_TURBIDITY: "45.0"
    FLUCT_TURBIDITY: "5.0"
    FLOCCULATION_TURB_RATIO: "0.4"
    SEDIMENTATION_TURB_RATIO: "0.08"
    FILTRATION_TURB_RATIO: "0.015"
    OUTLET_TURB_RATIO: "0.01"
```

模拟低温低浊工况：

```yaml
modbus-simulator:
  environment:
    RAW_TURBIDITY: "5.0"
    RAW_TEMPERATURE: "4.0"
    FLUCT_TURBIDITY: "0.8"
    FLUCT_TEMPERATURE: "0.3"
```

---

## Actuator 端点

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 健康状态（含数据库、Modbus、模型状态详情） |
| `GET /actuator/info` | 应用信息 |
| `GET /actuator/metrics` | 可用指标列表 |
| `GET /actuator/metrics/{name}` | 指标详情 |
| `GET /actuator/prometheus` | Prometheus 格式指标 |

---

## TimescaleDB 数据管理

### 自动压缩策略

| 超表 | 压缩条件 | 分段键 | 排序键 |
|------|----------|--------|--------|
| `water_quality` | 7天前 | `stage` | `time DESC` |
| `dosing_record` | 7天前 | `stage` | `time DESC` |
| `cost_indicator` | 30天前 | - | `time DESC` |

### 数据保留策略

| 超表 | 保留期限 |
|------|----------|
| `water_quality` | 90天 |
| `dosing_record` | 90天 |
| `cost_indicator` | 180天 |
| `alert_record` | 180天 |

### Chunk 粒度

| 超表 | Chunk 间隔 |
|------|-----------|
| `water_quality` | 1天 |
| `dosing_record` | 1天 |
| `cost_indicator` | 7天 |

---

## 前端静态资源优化

| 优化项 | 配置 |
|--------|------|
| Gzip 压缩 | `server.compression.enabled=true`，阈值 1KB，覆盖 HTML/CSS/JS/JSON |
| 浏览器缓存 | `Cache-Control: max-age=86400, public` |
| 压缩链 | `spring.web.resources.chain.compressed=true`（自动提供 .gz 预压缩文件） |
