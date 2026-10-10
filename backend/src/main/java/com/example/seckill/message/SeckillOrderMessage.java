package com.example.seckill.message;

/**
 * 发布下单消息。
 *
 * @return true：消息已成功提交到 RabbitTemplate（写入本地 socket）；
 *         不代表 broker 已确认收到或成功路由到队列。
 *         false：发送过程抛出异常。
 *         TODO: 接入 publisher confirm（simple 模式）后收紧语义。
 */
public record SeckillOrderMessage (
  Long userId,
  Long goodsId,
  String requestId){}
