package com.example.seckill.message;
/**
 * 下单消息发送接缝：受理编排只依赖本接口，不感知 RabbitMQ 细节。
 * 生产实现走真实 broker，测试用假发送器等价替换。
 */
public interface OrderMessageSender {
    /**
     * 发布下单消息，并在限定时间内等待 Broker 确认。
     *
     * @return true：已收到 confirm ack，且未收到不可路由返回；
     *         不代表消费完成或订单落库成功。
     *         false：未能确认发布成功，包括 nack、不可路由、
     *         超时或发送异常；消息仍可能已被投递。
     *
     * 调用方若执行同步降级，必须保证同步路径与消费路径幂等。
     */
  boolean send(SeckillOrderMessage message);
}