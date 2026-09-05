package com.example.seckill.message;

/**
 * 发送失败回调接缝（受理方实现，发送方异步触发）。
 *
 * <p>发送改为异步后（基线压测 2026-08-26：{@code send().join()} 阻塞是 2s 长尾根源），
 * 发送失败不再沿请求路径上抛，而是经本回调上报。回调在发送方派发的专用线程池执行，
 * 不占用请求线程与生产者 IO 线程——实现方不得在其中阻塞等待。
 */
@FunctionalInterface
public interface SendFailureListener {

  /**
   * 发送失败回调（同步抛出与异步确认失败统一走这里）。
   *
   * <p>实现方在此执行降级（同步落库 / 补偿），异常应自行消化，不得向发送方上抛。
   *
   * @param message 发送失败的那条消息（含全链路 requestId，补偿归属校验用）
   * @param cause 失败原因（broker 不可达 / 超时等）
   */
  void onSendFailure(SeckillOrderMessage message, Exception cause);
}
