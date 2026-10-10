package com.example.seckill.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seckill.cache.MiaoshaStore;
import com.example.seckill.dao.MiaoshaOrderMapper;
import com.example.seckill.service.GoodsService;
import com.example.seckill.vo.GoodsVo;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 消费链路端到端集成测试：真连 RabbitMQ / MySQL / Redis。
 *
 * <p>验证：发一条下单消息 → 消费者监听命中 → JSON 反序列化 → DB 落库 →
 * Redis 结果回写 SUCCESS，全链路串通（含 AUTO ack 语义）。
 *
 * <p>前置条件：
 * <ul>
 *   <li>本地 MySQL / Redis / RabbitMQ 已启动，连接信息与 application.yaml 一致</li>
 *   <li>商品表里有处于秒杀时间窗内的商品</li>
 * </ul>
 *
 * <p>用每次运行唯一 userId，避开 miaosha_order 唯一键冲突，可重复执行。
 */
@SpringBootTest
class OrderMessageConsumerTest {

  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(15);
  private static final long POLL_INTERVAL_MS = 500;

  @Autowired
  private OrderMessageSender orderMessageSender;

  @Autowired
  private MiaoshaStore store;

  @Autowired
  private GoodsService goodsService;

  @Autowired
  private MiaoshaOrderMapper miaoshaOrderMapper;

  @Test
  void send_then_consumed_orderPersistedAndResultMarked() throws InterruptedException {
    Long goodsId = pickGoodsInWindow();
    long userId = System.currentTimeMillis(); // 每次运行唯一，防唯一键冲突
    String requestId = "req-it-" + userId;

    boolean sent = orderMessageSender.send(new SeckillOrderMessage(userId, goodsId, requestId));
    assertThat(sent).as("消息应发送成功").isTrue();

    // 消费是异步的：轮询 Redis 结果键直到回写成功或超时
    String result = pollResult(goodsId, userId);
    assertThat(result)
        .as("消费者应在 %s 内完成落库并回写 SUCCESS", POLL_TIMEOUT)
        .isNotNull()
        .startsWith("SUCCESS:");

    // DB 是最终事实来源：断言 miaosha_order 真有这条记录
    assertThat(miaoshaOrderMapper.getByUserIdAndGoodsId(userId, goodsId))
        .as("数据库应存在该用户的秒杀订单")
        .isNotNull();
  }

  /** 取一个处于秒杀窗口内的商品；没有则直接失败并给出明确提示。 */
  private Long pickGoodsInWindow() {
    LocalDateTime now = LocalDateTime.now();
    return goodsService.listGoodsVo().stream()
        .filter(g -> inWindow(g, now))
        .findFirst()
        .map(GoodsVo::getId)
        .orElseThrow(() -> new IllegalStateException(
            "没有处于秒杀时间窗内的商品，请先在商品表准备测试数据"));
  }

  private boolean inWindow(GoodsVo g, LocalDateTime now) {
    boolean started = g.getStartDate() == null || !now.isBefore(g.getStartDate());
    boolean notOver = g.getEndDate() == null || now.isBefore(g.getEndDate());
    return started && notOver;
  }

  private String pollResult(Long goodsId, Long userId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      String result = store.getResult(goodsId, userId);
      if (result != null) {
        return result;
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    return null;
  }
}
