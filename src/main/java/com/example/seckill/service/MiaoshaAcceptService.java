package com.example.seckill.service;

import com.example.seckill.cache.MiaoshaRedisStore;
import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.OrderInfo;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 秒杀受理：一次秒杀请求的完整编排——Redis 预扣库存 → DB 事务下单 → 成功回写 / 失败补偿。
 *
 * <p>模块唯一入口 {@link #execute(Long, Long)}，不依赖 HTTP / Redis 实现，可直测：
 * Redis 侧经 {@link MiaoshaRedisStore} 接缝注入（生产 Redis 适配器 / 测试内存假适配器）。
 *
 * <p>语义约定（F9 已固化）：未预热 / 商品不存在时先由 Redis 库存闸门返回「库存不足」
 * 500214，不把商品存在性校验前置到预扣之前。
 *
 * <p>Redis 异常时降级直接落库，由 DB 条件扣库存 + 唯一键兜底。
 */
@Service
public class MiaoshaAcceptService {

  private static final Logger log = LoggerFactory.getLogger(MiaoshaAcceptService.class);

  private final MiaoshaRedisStore store;
  private final MiaoshaService miaoshaService;

  public MiaoshaAcceptService(MiaoshaRedisStore store, MiaoshaService miaoshaService) {
    this.store = store;
    this.miaoshaService = miaoshaService;
  }

  /**
   * 受理一次秒杀请求，成功返回订单；失败抛出对应业务码的 {@link MiaoshaException}。
   */
  public OrderInfo execute(Long userId, Long goodsId) {
    String requestId = newRequestId();

    MiaoshaRedisStore.TryResult result;
    try {
      result = store.tryMiaosha(goodsId, userId, requestId);
    } catch (Exception e) {
      // Redis 不可用时降级：直接走数据库，由 DB 条件扣库存 + 唯一键兜底
      log.warn("Redis 预扣异常，降级直连 DB goodsId={} userId={}: {}", goodsId, userId,
          e.getMessage());
      return miaoshaService.createOrder(userId, goodsId);
    }

    switch (result) {
      case REPEAT -> throw new MiaoshaException(CodeMsg.MIAOSHA_REPEAT);
      case STOCK_EMPTY -> throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
      case OK -> { }
    }

    // 预扣成功，同步落库
    try {
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      store.markSuccess(goodsId, userId, order.getId());
      return order;
    } catch (RuntimeException e) {
      // DB 下单失败：补偿 Redis 库存
      store.compensate(goodsId, userId, requestId);
      throw e;
    }
  }

  /** 生成本次受理请求的全链路标识，落库失败补偿时凭它校验归属。 */
  private String newRequestId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
