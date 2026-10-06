package com.example.seckill.message;

/**
 * 下单请求消息生产者（原 {@code OrderMessageSender} 的 Kafka 实现）。
 *
 * <p>原本负责把 {@code SeckillOrderMessage} 异步发往 {@code seckill-order} topic，
 * 发送失败经降级线程池派发 {@code SendFailureListener} 回调。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原生产者逻辑整体移除；
 * 保留类名与实现关系作为迁移痕迹，类体留空占位。
 */
public class OrderMessageProducer implements OrderMessageSender {

  // 原 Kafka 发送逻辑已移除（迁移 RabbitMQ），此处留空占位。
}