package com.example.seckill.message;

/**
 * 下单消息发送接缝（原接口，仿 {@code MiaoshaRedisStore} 模式）。
 *
 * <p>受理编排原本只依赖本接口、不感知 Kafka 细节：生产实现走真实 broker，
 * 测试用假发送器等价替换。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原 {@code send(message, onFailure)}
 * 异步发送契约移除；保留接口名与结构作为迁移痕迹。
 */
public interface OrderMessageSender {

  // 原异步发送契约已移除（迁移 RabbitMQ），此处留空占位。
}