package com.example.seckill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.example.seckill.message.OrderMessageSender;
import com.example.seckill.message.SeckillOrderMessage;
import com.example.seckill.message.SendFailureListener;
import com.example.seckill.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

/**
 * 03：受理异步化的两条降级路径（经真实 MySQL/Redis/Kafka 容器）。
 *
 * <ul>
 *   <li>Kafka 发送失败 → 异步回调降级同步落库（DB 写入移出请求路径）：
 *       请求仍返回受理中，降级完成后用户轮询拿单
 *   <li>降级落库后迟到的重复消息（发送超时但实际送达）→ DB 唯一键拦下、跳过补偿：
 *       无重复订单、库存不泄漏、成功结果不被覆盖
 * </ul>
 *
 * <p>经 @SpyBean 在真实 Kafka 生产实现上注入发送失败（抛出 → 受理方请求线程同步降级，
 * 与异步回调降级共用同一编排）；降级路径的编排细节由
 * {@code MiaoshaAcceptServiceTest} / {@code OrderFulfillmentServiceTest} 单测覆盖。
 */
class MiaoshaAsyncFlowTest extends AbstractIntegrationTest {

  private static final Duration SETTLE_WINDOW = Duration.ofSeconds(10);

  @SpyBean private OrderMessageSender sender;

  @Test // Kafka 发送失败 → 回调降级同步落库：响应受理中，轮询拿到订单，DB/Redis 对账一致
  void sendFails_degradesToSyncOrder() {
    long user = insertUser(13000000300L);
    long goodsId = insertGoods("iphoneX", 5);
    preheat(goodsId, user);
    doThrow(new RuntimeException("kafka down"))
        .when(sender)
        .send(any(SeckillOrderMessage.class), any(SendFailureListener.class));

    JsonNode resp = doMiaosha(user, goodsId);
    assertThat(resp.get("code").asInt()).as(resp.toString()).isEqualTo(CODE_SUCCESS);
    assertThat(resp.get("data").get("status").asText())
        .as("发送失败不阻塞请求路径，返回受理中")
        .isEqualTo("PROCESSING");

    // 降级异步落库完成后，轮询拿到订单
    JsonNode result = awaitResult(user, goodsId, SETTLE_WINDOW);
    assertThat(result.get("data").get("status").asText())
        .as(result.toString())
        .isEqualTo("SUCCESS");
    long orderId = result.get("data").get("orderId").asLong();
    assertThat(orderId).as(result.toString()).isPositive();

    // 降级落库双边对账
    assertThat(dbStock(goodsId)).isEqualTo(4);
    assertThat(orderCount()).isEqualTo(1);
    assertThat(miaoshaOrderCount()).isEqualTo(1);
    assertThat(duplicateMiaoshaOrderCount()).isZero();
    assertThat(redisStock(goodsId)).isEqualTo(4);
    assertThat(redisResult(goodsId, user)).isEqualTo("SUCCESS:" + orderId);
  }

  @Test
  void lateDuplicateAfterDegrade_isHarmless() {
    long user = insertUser(13000000301L);
    long goodsId = insertGoods("iphoneX", 5);
    preheat(goodsId, user);

    // 模拟发送超时：捕获消息但向受理方报失败 → 受理侧降级同步落库
    AtomicReference<SeckillOrderMessage> captured = new AtomicReference<>();
    doAnswer(
            inv -> {
              captured.set(inv.getArgument(0));
              throw new RuntimeException("send timeout");
            })
        .when(sender)
        .send(any(SeckillOrderMessage.class), any(SendFailureListener.class));

    JsonNode resp = doMiaosha(user, goodsId);
    assertThat(resp.get("code").asInt()).as(resp.toString()).isEqualTo(CODE_SUCCESS);
    assertThat(resp.get("data").get("status").asText()).isEqualTo("PROCESSING");
    JsonNode result = awaitResult(user, goodsId, SETTLE_WINDOW);
    assertThat(result.get("data").get("status").asText()).as(result.toString()).isEqualTo("SUCCESS");
    long orderId = result.get("data").get("orderId").asLong();
    assertThat(captured.get()).as("应捕获到发送失败的消息").isNotNull();

    // 恢复发送并重放该消息：模拟"超时但其实送达"的迟到重复消息
    doCallRealMethod().when(sender).send(any(SeckillOrderMessage.class), any(SendFailureListener.class));
    sender.send(captured.get(), (message, cause) -> {});

    // 消费无副作用（跳过补偿），无可观测终态变化：在稳定窗口内持续断言不变式。
    // 本地 broker 消费为亚秒级，10s 窗口足够覆盖；若误补偿（回补库存/覆盖 FAILED）会立即失败。
    long deadline = System.nanoTime() + SETTLE_WINDOW.toNanos();
    try {
      while (System.nanoTime() < deadline) {
        assertLateDuplicateInvariants(goodsId, user, orderId);
        Thread.sleep(200);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("等待迟到消息消费时被中断", e);
    }
    assertLateDuplicateInvariants(goodsId, user, orderId);
  }

  private void assertLateDuplicateInvariants(long goodsId, long user, long orderId) {
    assertThat(orderCount()).as("迟到重复消息不得产生重复订单").isEqualTo(1);
    assertThat(miaoshaOrderCount()).as("迟到重复消息不得产生重复秒杀记录").isEqualTo(1);
    assertThat(duplicateMiaoshaOrderCount()).isZero();
    assertThat(redisStock(goodsId)).as("迟到重复消息不得回补库存（避免 Redis/DB 库存不一致）")
        .isEqualTo(4);
    assertThat(redisResult(goodsId, user)).as("成功结果不得被覆盖为 FAILED")
        .isEqualTo("SUCCESS:" + orderId);
    assertThat(dbStock(goodsId)).isEqualTo(4);
  }
}
