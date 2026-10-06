package com.example.seckill.message;

/**
 * 发送失败回调接缝（原 {@code @FunctionalInterface}，受理方实现、发送方异步触发）。
 *
 * <p>原本提供 {@code onSendFailure(message, cause)} 回调，供发送方在异步失败时
 * 上报、由受理方执行降级落库 / 补偿。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原回调契约移除；
 * 保留接口名与结构作为迁移痕迹。
 */
public interface SendFailureListener {

  // 原回调方法 onSendFailure(message, cause) 已移除，此处留空占位。
}