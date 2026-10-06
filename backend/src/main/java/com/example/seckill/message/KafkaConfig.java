package com.example.seckill.message;

/**
 * Kafka 消息层配置（原 {@code @Configuration}）。
 *
 * <p>原本负责下单请求 topic {@code seckill-order} 与死信 topic
 * {@code seckill-order-dlt} 的声明、发送失败降级线程池的注册，以及消费侧
 * 错误处理（意外异常指数退避重试后发布死信）。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，上述配置逻辑整体移除；
 * 保留类名与结构作为迁移痕迹，类体留空占位。
 */
public class KafkaConfig {

  // 原 Kafka 配置逻辑已移除（迁移 RabbitMQ），此处留空占位。
}