package com.example.seckill.service;

import com.example.seckill.cache.MiaoshaRedisStore;
import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.vo.MiaoshaAcceptVo;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 秒杀受理：一次秒杀请求的编排——Redis 预扣库存 → 立即返回受理中。
 *
 * <p>模块唯一入口 {@link #execute(Long, Long)}，Redis 侧经 {@link MiaoshaRedisStore} 接缝注入。
 *
 * <p>降级哲学：Redis 不可用 → 直连 DB 落库（由 DB 条件扣库存 + 唯一键兜底）。
 *
 * <p>语义约定（F9 已固化）：未预热 / 商品不存在时先由 Redis 库存闸门返回「库存不足」
 * 500214，不把商品存在性校验前置到预扣之前。
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
   * 受理一次秒杀请求：成功返回受理态；失败抛出对应业务码的 {@link MiaoshaException}。
   */
  public MiaoshaAcceptVo execute(Long userId, Long goodsId) {
    String requestId = newRequestId();

    MiaoshaRedisStore.TryResult result;
    try {
      result = store.tryMiaosha(goodsId, userId, requestId);
    } catch (Exception e) {
      // Redis 不可用降级（既有路径，行为不变）：直接走数据库，由 DB 条件扣库存 + 唯一键兜底
      log.warn("Redis 预扣异常，降级直连 DB goodsId={} userId={}: {}", goodsId, userId,
          e.getMessage());
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      return MiaoshaAcceptVo.success(order.getId());
    }

    switch (result) {
      case REPEAT -> throw new MiaoshaException(CodeMsg.MIAOSHA_REPEAT);
      case STOCK_EMPTY -> throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
      case OK -> { }
    }

    // 此处原为：预扣成功后异步发送下单消息到消息队列，由消费者异步落库（削峰），
    // 并在发送失败时经异步回调降级同步落库。消息队列层现已整体移除，本段留空——
    // 预扣成功后的下单落库链路暂时停摆，待后续补充。
    return MiaoshaAcceptVo.processing();
  }

  /** 生成本次受理请求的全链路标识，凭它做 Redis 预扣的去重标记。 */
  private String newRequestId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}