package com.example.seckill.cache;

import com.example.seckill.service.GoodsService;
import com.example.seckill.vo.GoodsVo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 秒杀 Redis 服务：Lua 原子预扣库存、结果回写、失败补偿、库存预热。
 *
 * <p>核心思路：Redis 是第一道库存拦截与防重复下单屏障，数据库仍是最终库存与订单事实来源。
 */
@Service
public class MiaoshaRedisService {

  private static final Logger log = LoggerFactory.getLogger(MiaoshaRedisService.class);

  /** 用户抢购标记 / 结果的默认过期时间（秒），1 天 */
  private static final long DEFAULT_TTL_SECONDS = 86400L;

  /** 库存 key 在预热时的额外保留时间（活动结束后 30 分钟） */
  private static final long STOCK_TTL_BUFFER_SECONDS = 1800L;

  private static final long MIN_TTL_SECONDS = 3600L;

  public static final int TRY_OK = 0;
  public static final int TRY_STOCK_EMPTY = 1;
  public static final int TRY_REPEAT = 2;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> tryScript;
  private final RedisScript<Long> compensateScript;
  private final GoodsService goodsService;

  public MiaoshaRedisService(
      StringRedisTemplate redisTemplate,
      RedisScript<Long> miaoshaTryScript,
      RedisScript<Long> miaoshaCompensateScript,
      GoodsService goodsService) {
    this.redisTemplate = redisTemplate;
    this.tryScript = miaoshaTryScript;
    this.compensateScript = miaoshaCompensateScript;
    this.goodsService = goodsService;
  }

  /** 生成一个新的 requestId，用于本次秒杀请求的全链路标识与补偿校验。 */
  public String newRequestId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * 执行 Lua 原子预扣库存。
   *
   * @return {@link #TRY_OK} / {@link #TRY_STOCK_EMPTY} / {@link #TRY_REPEAT}
   */
  public int tryMiaosha(Long goodsId, Long userId, String requestId) {
    List<String> keys =
        List.of(
            RedisKeyBuilder.stock(goodsId),
            RedisKeyBuilder.user(goodsId, userId),
            RedisKeyBuilder.result(goodsId, userId));
    Long ret =
        redisTemplate.execute(
            tryScript, keys, requestId, String.valueOf(DEFAULT_TTL_SECONDS));
    return ret == null ? TRY_STOCK_EMPTY : ret.intValue();
  }

  /** DB 下单成功后，更新 Redis 结果为 SUCCESS:{orderId}。 */
  public void setResultSuccess(Long goodsId, Long userId, Long orderId) {
    try {
      redisTemplate
          .opsForValue()
          .set(
              RedisKeyBuilder.result(goodsId, userId),
              "SUCCESS:" + orderId,
              Duration.ofSeconds(DEFAULT_TTL_SECONDS));
    } catch (Exception e) {
      log.warn("setResultSuccess 失败 goodsId={} userId={}: {}", goodsId, userId, e.getMessage());
    }
  }

  /**
   * DB 下单失败后的 Redis 补偿：回补库存 + 清除用户标记 + 标记 FAILED。
   *
   * <p>仅当 userKey 的值仍等于本次 requestId 时才补偿，避免误操作其它请求的状态。
   */
  public void compensate(Long goodsId, Long userId, String requestId) {
    try {
      List<String> keys =
          List.of(
              RedisKeyBuilder.stock(goodsId),
              RedisKeyBuilder.user(goodsId, userId),
              RedisKeyBuilder.result(goodsId, userId));
      redisTemplate.execute(
          compensateScript, keys, requestId, String.valueOf(DEFAULT_TTL_SECONDS));
    } catch (Exception e) {
      log.error(
          "compensate 失败 goodsId={} userId={}: {}", goodsId, userId, e.getMessage(), e);
    }
  }

  /**
   * 活动开始前预热库存：从数据库读取 stock_count 写入 Redis。
   *
   * <p>TTL = 活动结束后 30 分钟，最小 1 小时；endDate 为空时取默认 1 天。
   */
  public void preheatStock(Long goodsId) {
    GoodsVo goods = goodsService.getGoodsVo(goodsId);
    if (goods == null || goods.getStockCount() == null) {
      throw new IllegalStateException("商品不存在或库存未配置: goodsId=" + goodsId);
    }
    long ttl = computeTtl(goods.getEndDate());
    redisTemplate
        .opsForValue()
        .set(RedisKeyBuilder.stock(goodsId), String.valueOf(goods.getStockCount()),
            Duration.ofSeconds(ttl));
    log.info(
        "预热秒杀库存 goodsId={} stock={} ttl={}s", goodsId, goods.getStockCount(), ttl);
  }

  private long computeTtl(LocalDateTime endDate) {
    if (endDate == null) {
      return DEFAULT_TTL_SECONDS;
    }
    long ttl =
        Duration.between(LocalDateTime.now(), endDate).getSeconds() + STOCK_TTL_BUFFER_SECONDS;
    return Math.max(ttl, MIN_TTL_SECONDS);
  }
}
