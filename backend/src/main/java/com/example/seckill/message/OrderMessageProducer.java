package com.example.seckill.message;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 下单请求消息生产者（{@link OrderMessageSender} 的 Kafka 实现）：
 * 把 {@link SeckillOrderMessage} 发往 {@code seckill-order} topic。
 *
 * <p>可靠发送依赖 {@code application.yaml} 的 {@code acks=all} + 幂等生产者
 * （{@code enable.idempotence=true}），此处只负责投递语义。
 *
 * <p>异步化改造（基线压测 2026-08-26：{@code send().join()} 阻塞是 2s 长尾根源）：
 * 发送不再等待 broker 确认，失败经 {@link SendFailureListener} 回调、在专用降级
 * 线程池（{@code sendDegradeExecutor}）异步触发——请求线程与生产者 IO 线程零阻塞。
 *
 * <p>已定决策：消息 key 不用 goodsId（热点商品会打爆单分区），随机打散；
 * 顺序性由 DB 条件扣库存 + 唯一键兜底。
 */
@Component
public class OrderMessageProducer implements OrderMessageSender {

  private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;
  private final Executor sendDegradeExecutor;

  public OrderMessageProducer(
      KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate,
      @Qualifier("sendDegradeExecutor") Executor sendDegradeExecutor) {
    this.kafkaTemplate = kafkaTemplate;
    this.sendDegradeExecutor = sendDegradeExecutor;
  }

  /**
   * 异步发送一条下单请求消息，不阻塞等待 broker 确认（acks=all + 幂等）。
   *
   * <p>发送失败（提交即失败或异步确认失败）统一派发到降级线程池回调
   * {@code onFailure}，本方法不向调用方抛出发送失败异常。
   */
  @Override
  public void send(SeckillOrderMessage message, SendFailureListener onFailure) {
    String key = UUID.randomUUID().toString();
    try {
      kafkaTemplate
          .send(KafkaConfig.ORDER_TOPIC, key, message)
          .whenComplete(
              (result, ex) -> {
                if (ex == null) {
                  return;
                }
                dispatchDegrade(message, asException(ex), onFailure);
              });
    } catch (Exception e) {
      // 提交即失败（如序列化错误 / broker 不可达的同步异常），与异步失败同路派发
      dispatchDegrade(message, e, onFailure);
    }
  }

  /** 降级回调派发：回调线程只做提交不等待，降级任务不允许丢弃（丢则预扣库存泄漏）。 */
  private void dispatchDegrade(
      SeckillOrderMessage message, Exception cause, SendFailureListener onFailure) {
    try {
      sendDegradeExecutor.execute(() -> onFailure.onSendFailure(message, cause));
    } catch (RejectedExecutionException e) {
      // 线程池已关闭等极端场景：内联执行兜底，宁可短暂阻塞回调线程也不能丢降级
      onFailure.onSendFailure(message, cause);
    }
  }

  private static Exception asException(Throwable t) {
    return t instanceof Exception e ? e : new RuntimeException(t);
  }
}
