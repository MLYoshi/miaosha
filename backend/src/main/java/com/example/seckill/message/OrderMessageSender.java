package com.example.seckill.message;

/**
 * 下单消息发送接缝（仿 {@code MiaoshaRedisStore} 模式）。
 *
 * <p>受理编排只依赖本接口，不感知 Kafka 细节：生产实现走真实 broker，
 * 测试用 {@code FakeOrderMessageSender} 等价替换（支持注入发送失败）。
 *
 * <p>异常契约（异步化改造后，基线压测 2026-08-26）：{@link #send} 立即返回、
 * 阻塞等待 broker 确认被禁止（会把长尾带回请求路径）；发送失败（同步抛出 /
 * 异步确认失败）统一经 {@link SendFailureListener} 异步回调，由受理方降级。
 */
public interface OrderMessageSender {

  /**
   * 异步发送一条下单请求消息：立即返回，不等待 broker 确认。
   *
   * <p>消息 key 由实现方决定：随机打散（热点商品避免打爆单分区），
   * 顺序性由 DB 条件扣库存 + 唯一键兜底。
   *
   * <p>实现必须保证：本方法不向调用方抛出发送失败异常，所有失败
   * （提交即失败或异步确认失败）都经 {@code onFailure} 回调上报。
   *
   * @param onFailure 发送失败回调，由实现在专用线程池异步触发
   */
  void send(SeckillOrderMessage message, SendFailureListener onFailure);
}
