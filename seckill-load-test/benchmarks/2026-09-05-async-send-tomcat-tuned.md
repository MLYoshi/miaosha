# 优化复测：受理异步化 + Tomcat 线程池调优（After）

- 测量日期：2026-09-05（本地时间 20:00 起）
- 场景：`spike.js`（50 → 100 → 200 → 300 → 500 RPS，每档 1 分钟）+ 组件并行观测，与基线同口径
- 测量人：k6 v2.2.0 + `observe.sh`（独立观测，与 k6 解耦）
- 对照基线：`2026-08-26-baseline.md`（未调优首轮基线）
- 状态：**优化后复测**。对应基线「待验证假设 ①②」：
  1. 受理路径去掉阻塞发送（`send().join()` → 异步/回调）
  2. Tomcat 线程池 200 → 500
- ⚠️ 归因注意：两项改动同时上线（异步发送 + 线程池扩容），未严格执行基线文档「一次只改一处」的二分流程，报告中做了间接归因推断（见 §5.3）。

## 1. 代码/配置变更内容

| 项 | 变更 |
|---|---|
| 发送路径 | `kafkaTemplate.send(...).join()` → 异步发送不等待确认；失败经 `SendFailureListener` 回调 |
| 失败降级 | 由「请求线程内同步降级、直接拿单」改为「专用降级线程池（`sendDegradeExecutor`）异步降级，回写 SUCCESS 供轮询拿单」 |
| 补偿守卫 | 降级补偿前查结果标记，已 `SUCCESS` 跳过（与消费侧守卫对称，防「发送超时但实际送达」竞态回补库存） |
| Tomcat | `server.tomcat.threads.max: 200（默认） → 500` |
| 文档 | `backend/docs/mq-design.md` §5.1 / Agent 注意事项已同步改写 |

正确性背书：改造后全量测试套件 70/70 通过（含经真实容器的降级路径、迟到重复消息、消费可靠性测试）。

## 2. 环境信息

| 项 | 值 |
|---|---|
| 宿主机 | macOS（darwin arm64），Docker Desktop（与基线同一台机器） |
| 后端 | Spring Boot 3.3.4 / Java 17（容器 `seckill2-backend`，TZ=Asia/Shanghai） |
| 中间件 | MySQL 8.0 / Redis 7.0 / Kafka 4.3.1 KRaft 单节点（3 分区，副本 1） |
| 容器命名 | 本轮起项目与 code2 目录旧项目共存，容器名经 `docker-compose.override.yaml` 改为 `seckill2-*`，backend 端口映射 `18080:8080`（宿主 8080 被其他进程占用）；压测脚本已支持 `*_CONTAINER` / `BASE_URL` 环境变量注入 |
| 压测商品 | goodsId=1（iphoneX），`prepare.sh spike` 库存 10000，时间窗 now-1h ~ now+1d |
| 前置清理 | `prepare.sh spike`（清 DB 订单、Redis `miaosha:*`、Kafka offset 重置并重启 backend） |

### 2.1 环境事件记录（影响第一轮数据，已作废重测）

首轮压测中，code2 目录旧微服务（`order-service` 等，Kafka 地址同为 `localhost:9092`）连上新拉起的 Kafka，以同名消费组 `seckill` 抢走 p2 分区且不消费，造成 3195 条积压、部分用户轮询超时——**该轮 k6 数据作废**。停止外部进程后，容器消费者立即接管全部分区并在数秒内消化全部积压（顺带证明消费吞吐充裕）。随后 `prepare.sh` 清理重跑，本报告数据来自干净的第二轮。

## 3. k6 业务指标（spike，对比基线）

| 指标 | 基线 08-26 | 本轮 09-05 | 变化 |
|---|---|---|---|
| 完成迭代 | 67235 | 67816 | +0.9% |
| `http_req_failed` | 0.00% | 0.00% | 持平 |
| `http_req_duration` avg | 7.66ms | **4.31ms** | **-44%** |
| med | 1.27ms | 1.16ms | -9% |
| p90 | 4.64ms | **3.52ms** | **-24%** |
| p95 | 8.97ms | **7.01ms** | **-22%** |
| max | **2.02s** | **1.16s** | **-43%** |
| `accept_latency` avg | 9.66ms | **5.28ms** | **-45%** |
| med | 1.55ms | 1.31ms | -15% |
| p90 | 5.62ms | **4.18ms** | **-26%** |
| p95 | 12.03ms | **9.14ms** | **-24%** |
| max | 2.02s | 1.16s | -43% |
| `dropped_iterations` | **639**（~2.0/s，集中 500 档） | **58**（~0.18/s） | **-91%** |
| 各档 RPS 达成 | 50/100/200/300/500 全命中 | 全命中 | 持平 |
| 并发峰值 VUs max | 139 | 123 | -12% |

落库对账（本轮）：`miaosha_order`=10000、`order_info`=10000、DB/Redis 库存归 0、backend ERROR 日志 0——不超卖、无丢失。

## 4. 组件观测（observe.sh，600s × 84 次采样）

### 4.1 Docker 容器资源（avg / max CPU%）

| 容器 | 基线 avg/max | 本轮 avg/max | 解读 |
|---|---|---|---|
| backend | 42.7 / 184.7 | **25.9** / 229.1 | avg 大幅下降：请求线程不再自旋等待 broker 确认；max 略升（瞬时处理更陡） |
| kafka | 87.9 / 330.2 | **118.7** / 375.7 | avg 上升：异步化后生产无客户端背压，消息更突发；单节点 KRaft on Mac Docker 的已知开销（基线 §4.3 已预警），lag 未见恶化 |
| mysql | 8.1 / 27.0 | 5.4 / 35.8 | 依旧极空闲 |
| redis | 2.6 / 13.7 | 3.5 / 32.3 | 依旧极空闲 |

### 4.2 JVM / Kafka / Redis / MySQL

| 项 | 基线 | 本轮 |
|---|---|---|
| JVM 进程线程数 avg/max | 93 / 148 | **81 / 126** |
| Kafka 生产 | +10000（三分区均衡） | +10000（p0 +3321 / p1 +3318 / p2 +3361） |
| Kafka 消费 lag | max 3~7，latest 0 | max 8~20，latest **0** |
| Redis ops/s avg/max | 598 / 1007 | 318 / 1014（轮询更快到达终态，轮询请求数减少） |
| MySQL QPS | 413.3 | 218.4（同为无慢查询；口径含轮询期间的 DB 回源，变化原因见 §5.4） |
| Slow_queries 增量 | 0 | 0 |

## 5. 分析

### 5.1 主结论：异步化方向正确，长尾大幅收窄

- 2s 级长尾从 **2.02s → 1.16s**，`dropped_iterations` **639 → 58**（-91%），全链路延迟分布整体左移（avg -44% / p90 -25% / p95 -22%）。
- 受理路径（Redis 预扣 + 异步提交）p95 从 12.03ms → 9.14ms，med 1.31ms——请求路径已不含任何 broker 等待。
- 数据层余量依然极大（MySQL QPS 218、无慢查询；Redis ops 318；lag latest 0），瓶颈不在此。

### 5.2 残余瓶颈：500 RPS 档仍有 58 次丢弃、1.16s max

假设①预测「dropped 归零」未完全达成。候选因素（按可能性）：

1. **宿主资源紧张**：观测期宿主 free 内存仅 64~72MB、ps 汇总 CPU max 378%——Mac + Docker Desktop 单机极限临近，500 RPS 档瞬时调度抖动即可造成亚秒级尖刺；
2. **Kafka 单节点 CPU avg 118.7%**：生产更突发后 broker 侧批处理/刷盘瞬时压力上升（容器 CPU max 375.7%）；
3. **JVM GC 暂停不可见**：容器为 JRE 镜像（无 `jcmd`），本轮仍无 GC 观测手段。

### 5.3 归因推断（两改动合并的间接拆分）

- JVM 线程数 max **126 < 500**：线程池扩容后线程水位反而更低——说明 Tomcat 线程数不是本轮约束，**主要收益来自异步化**（请求线程不再排队等 broker）。
- 若要严格归因，可回退 yaml 的 `threads.max: 500` 单独复测一轮；预期差异很小，优先级低。

### 5.4 数据口径备注

- Redis ops 与 MySQL QPS 较基线近乎减半：本轮轮询更快到达终态（无积压、无长时间 PROCESSING 悬挂），`/miaosha/result` 的轮询请求数相应减少所致，非性能回退。
- Kafka 生产速率（observe 差分 ~5.6 msg/s/分区）是 600s 观测窗的均摊值，实际发送集中在 500 RPS 档。

## 6. 结论与下一步

- 基线假设①成立（大部）：受理去阻塞发送后，长尾 -43%、丢弃 -91%，容量边界更贴近 500 RPS 设计目标且 0 失败、正确性对账全绿。
- 残余 1.16s 长尾 + 58 次丢弃为下一轮实验对象，建议一次只改一处：
  1. 给 backend 开 GC 日志（`-Xlog:gc`，挂载日志目录）或换 JDK 镜像，先观测再动手；
  2. 观察 Kafka 单节点 CPU 随吞吐的恶化趋势（Mac Docker 特性，上 Linux 宿主预期显著缓解）；
  3. 宿主内存释放（free 64MB 已影响 Docker 调度公平性）。

## 7. 数据产物

- 组件观测原始数据：`seckill-load-test/results/spike-after-async/*.tsv`
- k6 原始输出：`seckill-load-test/spike-run-after-async.log`
- 基线对照：`seckill-load-test/benchmarks/2026-08-26-baseline.md`
- 复现：

```bash
cd seckill-load-test
export MYSQL_CONTAINER=seckill2-mysql REDIS_CONTAINER=seckill2-redis \
       KAFKA_CONTAINER=seckill2-kafka BACKEND_CONTAINER=seckill2-backend \
       BASE_URL=http://localhost:18080
./prepare.sh spike
./observe.sh --label spike-after-async --interval 5 --duration 600 &
k6 run -e BASE_URL=http://localhost:18080 spike.js
wait
./summarize.sh results/spike-after-async
```
