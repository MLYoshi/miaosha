package com.example.seckill.service;

/**
 * 消费者落库编排（原 {@code @Service}）。
 *
 * <p>原本负责把一条下单请求消息变成数据库订单并回写 Redis 结果，含幂等快跳、
 * 落库、markSuccess、业务失败补偿等编排语义。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原落库编排逻辑整体移除；
 * 保留类名与结构作为迁移痕迹，类体留空占位。
 */
public class OrderFulfillmentService {

  // 原消费者落库编排逻辑已移除（迁移 RabbitMQ），此处留空占位。
}