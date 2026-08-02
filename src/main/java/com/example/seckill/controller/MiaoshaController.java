package com.example.seckill.controller;

import com.example.seckill.cache.MiaoshaRedisService;
import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.common.Result;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.service.MiaoshaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/miaosha")
public class MiaoshaController {

  private static final Logger log = LoggerFactory.getLogger(MiaoshaController.class);

  private final MiaoshaService miaoshaService;
  private final MiaoshaRedisService miaoshaRedisService;

  public MiaoshaController(MiaoshaService miaoshaService, MiaoshaRedisService miaoshaRedisService) {
    this.miaoshaService = miaoshaService;
    this.miaoshaRedisService = miaoshaRedisService;
  }

  /**
   * 秒杀主流程：Redis Lua 预扣库存 → DB 事务下单 → 成功回写 / 失败补偿。
   *
   * <p>Redis 异常时降级直接落库，由 DB 条件扣库存 + 唯一键兜底。
   */
  @GetMapping("/do_miaosha")
  public Result<OrderInfo> doMiaosha(HttpServletRequest request, @RequestParam Long goodsId) {
    Long userId = (Long) request.getAttribute("userId");
    try {
      return Result.success(doMiaoshaWithRedis(userId, goodsId));
    } catch (MiaoshaException e) {
      return Result.error(e.getCodeMsg().getCode(), e.getCodeMsg().getMsg());
    }
  }

  /** 活动开始前预热 Redis 库存（从 DB 读取）。 */
  @GetMapping("/preheat")
  public Result<String> preheat(@RequestParam Long goodsId) {
    miaoshaRedisService.preheatStock(goodsId);
    return Result.success("ok");
  }

  private OrderInfo doMiaoshaWithRedis(Long userId, Long goodsId) {
    String requestId = miaoshaRedisService.newRequestId();

    int code;
    try {
      code = miaoshaRedisService.tryMiaosha(goodsId, userId, requestId);
    } catch (Exception e) {
      // Redis 不可用时降级：直接走数据库，由 DB 条件扣库存 + 唯一键兜底
      log.warn("Redis 预扣异常，降级直连 DB goodsId={} userId={}: {}", goodsId, userId,
          e.getMessage());
      return miaoshaService.createOrder(userId, goodsId);
    }

    if (code == MiaoshaRedisService.TRY_REPEAT) {
      throw new MiaoshaException(CodeMsg.MIAOSHA_REPEAT);
    }
    if (code == MiaoshaRedisService.TRY_STOCK_EMPTY) {
      throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
    }

    // code == TRY_OK：Redis 预扣成功，同步落库
    try {
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      miaoshaRedisService.setResultSuccess(goodsId, userId, order.getId());
      return order;
    } catch (RuntimeException e) {
      // DB 下单失败：补偿 Redis 库存
      miaoshaRedisService.compensate(goodsId, userId, requestId);
      throw e;
    }
  }
}
