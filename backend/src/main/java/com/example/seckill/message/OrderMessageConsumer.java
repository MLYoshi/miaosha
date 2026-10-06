package com.example.seckill.message;

/**
 * 下单请求消息消费者（原 {@code @KafkaListener}）。
 *
 * <p>原本订阅 {@code seckill-order} topic、手动 ack，落库编排调用
 * {@code OrderFulfillmentService}。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原消费逻辑整体移除；
 * 保留类名与结构作为迁移痕迹，类体留空占位。
 */
public class OrderMessageConsumer {

  // 原 Kafka 消费逻辑已移除（迁移 RabbitMQ），此处留空占位。
}