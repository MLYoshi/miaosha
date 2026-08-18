package com.example.seckill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.seckill.cache.InMemoryMiaoshaRedisStore;
import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.OrderInfo;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 秒杀受理编排直连单测：内存假 Redis 适配器 + mock 下单事务，不起 Spring / HTTP / Redis。
 *
 * <p>覆盖降级路径（Redis 异常直连 DB）与失败补偿（DB 失败回补 Redis），以及三个拦截分支；
 * F9 语义（未预热先返 500214，先于 DB 商品校验）在此单测化。
 */
class MiaoshaAcceptServiceTest {

  private static final Long USER = 1L;
  private static final Long GOODS = 100L;
  private static final Duration TTL = Duration.ofHours(1);

  private final InMemoryMiaoshaRedisStore store = new InMemoryMiaoshaRedisStore();
  private final MiaoshaService miaoshaService = mock(MiaoshaService.class);
  private final MiaoshaAcceptService accept = new MiaoshaAcceptService(store, miaoshaService);

  private static OrderInfo order(long id) {
    OrderInfo order = new OrderInfo();
    order.setId(id);
    order.setUserId(USER);
    order.setGoodsId(GOODS);
    return order;
  }

  // ---------- 预扣成功 ----------

  @Test // 预扣 OK + DB 成功 → 返回订单，回写 SUCCESS，库存已扣
  void tryOkThenDbSuccess_marksSuccess() {
    store.setStock(GOODS, 5, TTL);
    OrderInfo created = order(7L);
    when(miaoshaService.createOrder(USER, GOODS)).thenReturn(created);

    OrderInfo result = accept.execute(USER, GOODS);

    assertThat(result).isSameAs(created);
    assertThat(store.stock(GOODS)).isEqualTo(4);
    assertThat(store.userMark(GOODS, USER)).isNotBlank();
    assertThat(store.result(GOODS, USER)).isEqualTo("SUCCESS:7");
  }

  // ---------- 拦截分支（不触碰 DB） ----------

  @Test // F9 语义：未预热（库存 key 不存在）→ 500214，先于 DB 商品校验
  void notPreheated_stockEmptyBeforeGoodsCheck() {
    assertThatThrownBy(() -> accept.execute(USER, GOODS))
        .isInstanceOfSatisfying(
            MiaoshaException.class,
            e -> assertThat(e.getCodeMsg()).isEqualTo(CodeMsg.MIAOSHA_STOCK_EMPTY));
    verify(miaoshaService, never()).createOrder(any(), any());
    assertThat(store.userMark(GOODS, USER)).isNull();
  }

  @Test // 库存为 0 → 500214
  void stockDrained_stockEmpty() {
    store.setStock(GOODS, 0, TTL);

    assertThatThrownBy(() -> accept.execute(USER, GOODS))
        .isInstanceOfSatisfying(
            MiaoshaException.class,
            e -> assertThat(e.getCodeMsg()).isEqualTo(CodeMsg.MIAOSHA_STOCK_EMPTY));
    verify(miaoshaService, never()).createOrder(any(), any());
  }

  @Test // 用户已有抢购标记 → 500212，库存不动
  void userAlreadyMarked_repeat() {
    store.setStock(GOODS, 5, TTL);
    store.seedUserMark(GOODS, USER, "other-request");

    assertThatThrownBy(() -> accept.execute(USER, GOODS))
        .isInstanceOfSatisfying(
            MiaoshaException.class,
            e -> assertThat(e.getCodeMsg()).isEqualTo(CodeMsg.MIAOSHA_REPEAT));
    verify(miaoshaService, never()).createOrder(any(), any());
    assertThat(store.stock(GOODS)).isEqualTo(5);
  }

  // ---------- 降级路径 ----------

  @Test // Redis 异常 → 降级直连 DB，订单直返，不回写不补偿
  void redisFailure_fallsBackToDb() {
    store.failTryWith(new RuntimeException("redis down"));
    OrderInfo created = order(9L);
    when(miaoshaService.createOrder(USER, GOODS)).thenReturn(created);

    OrderInfo result = accept.execute(USER, GOODS);

    assertThat(result).isSameAs(created);
    assertThat(store.stock(GOODS)).as("降级不触碰 Redis 库存").isEqualTo(-1);
    assertThat(store.userMark(GOODS, USER)).isNull();
    assertThat(store.result(GOODS, USER)).isNull();
  }

  @Test // 降级后 DB 也失败 → 业务异常原样上抛，无补偿副作用
  void redisFailureAndDbFails_exceptionPropagates() {
    store.failTryWith(new RuntimeException("redis down"));
    when(miaoshaService.createOrder(USER, GOODS))
        .thenThrow(new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY));

    assertThatThrownBy(() -> accept.execute(USER, GOODS))
        .isInstanceOfSatisfying(
            MiaoshaException.class,
            e -> assertThat(e.getCodeMsg()).isEqualTo(CodeMsg.MIAOSHA_STOCK_EMPTY));
    verify(miaoshaService).createOrder(USER, GOODS);
  }

  // ---------- 失败补偿 ----------

  @Test // 预扣 OK 但 DB 失败 → 补偿：库存回补、标记清除、result=FAILED，异常上抛
  void tryOkButDbFails_compensates() {
    store.setStock(GOODS, 5, TTL);
    when(miaoshaService.createOrder(USER, GOODS))
        .thenThrow(new MiaoshaException(CodeMsg.MIAOSHA_REPEAT));

    assertThatThrownBy(() -> accept.execute(USER, GOODS))
        .isInstanceOfSatisfying(
            MiaoshaException.class,
            e -> assertThat(e.getCodeMsg()).isEqualTo(CodeMsg.MIAOSHA_REPEAT));

    assertThat(store.stock(GOODS)).as("库存应回补").isEqualTo(5);
    assertThat(store.userMark(GOODS, USER)).as("user 标记应清除").isNull();
    assertThat(store.result(GOODS, USER)).isEqualTo("FAILED");
  }

  @Test // 幂等重试：补偿后再抢，仍先被 DB 唯一记录拦下 → 再次补偿，库存不泄漏
  void retryAfterCompensation_doesNotLeakStock() {
    store.setStock(GOODS, 5, TTL);
    when(miaoshaService.createOrder(USER, GOODS))
        .thenThrow(new MiaoshaException(CodeMsg.MIAOSHA_REPEAT));

    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> accept.execute(USER, GOODS)).isInstanceOf(MiaoshaException.class);
    }

    assertThat(store.stock(GOODS)).as("反复重试不泄漏库存").isEqualTo(5);
  }
}
