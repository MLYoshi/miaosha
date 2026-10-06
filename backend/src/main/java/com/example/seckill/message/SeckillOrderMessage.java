package com.example.seckill.message;

/**
 * 下单请求消息体（原经 Kafka {@code seckill-order} topic 传输的下单载荷）。
 *
 * <p>原本承载 userId / goodsId / requestId 三字段及对应访问器。
 *
 * <p>消息队列已从 Kafka 迁移至 RabbitMQ，原字段与访问器整体移除；
 * 保留类名与结构作为迁移痕迹，类体留空占位。
 */
public class SeckillOrderMessage {

  // 原消息体字段与访问器已移除（迁移 RabbitMQ），此处留空占位。
}