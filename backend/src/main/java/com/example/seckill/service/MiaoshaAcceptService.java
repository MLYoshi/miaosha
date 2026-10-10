package com.example.seckill.service;

import com.example.seckill.cache.MiaoshaStore;
import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.message.OrderMessageSender;
import com.example.seckill.message.SeckillOrderMessage;
import com.example.seckill.vo.MiaoshaAcceptVo;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 秒杀受理服务：秒杀请求进入系统的第一道编排。
 *
 * <p>职责：只回答"这个用户有没有抢到资格"，不在此建订单。
 * 抢到资格 → 返回"排队中"，订单由 MQ 消费者异步落库（削峰）。
 *
 * <p>一次请求的三种答复：
 * <ul>
 *   <li>排队中（预扣成功，等异步建单）</li>
 *   <li>重复下单（该用户已有进行中的请求）</li>
 *   <li>已抢完（库存不足）</li>
 * </ul>
 *
 * <p>降级原则：Redis 只是加速器，数据库才是最终事实来源。
 * Redis 不可用时直连 DB 建单，由 DB 条件扣库存（stock > 0）+ 唯一索引兜底正确性。
 */
@Service
public class MiaoshaAcceptService {

  private static final Logger log = LoggerFactory.getLogger(MiaoshaAcceptService.class);

  private final MiaoshaStore store;
  private final MiaoshaService miaoshaService;
  private final OrderMessageSender messageSender;

  public MiaoshaAcceptService(MiaoshaStore store, MiaoshaService miaoshaService, OrderMessageSender messageSender) {
    this.store = store;
    this.miaoshaService = miaoshaService;
    this.messageSender = messageSender;
  }

  /**
   * 受理一次秒杀请求。
   *
   * @return 预扣成功返回"排队中"；降级落库成功直接返回订单号
   * @throws MiaoshaException 重复下单或库存不足（由全局异常处理器转成业务错误码）
   */
  public MiaoshaAcceptVo execute(Long userId, Long goodsId) {
    // 全链路身份证：Redis 去重标记、MQ 消息、失败补偿都靠它串联，必须在最前面生成
    String requestId = newRequestId();

    // 第 1 步：Redis 原子预扣（Lua 脚本一步完成：去重检查 + 库存检查 + 扣减 + 打 PROCESSING 标记）
    MiaoshaStore.TryResult result;
    try {
      result = store.tryMiaosha(goodsId, userId, requestId);
    } catch (Exception e) {
      // Redis 挂了 ≠ 秒杀失败：降级直连 DB 建单，由 DB 兜底正确性（并发能力下降但功能可用）
      // 注意 try 只包住调 Redis 这一步，不能把业务异常也吞进降级分支
      log.warn("Redis 预扣异常，降级直连 DB goodsId={} userId={}: {}", goodsId, userId,
          e.getMessage());
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      return MiaoshaAcceptVo.success(order.getId());
    }

    // 第 2 步：把预扣结果翻译成给用户的答复（失败两种都抛异常，成功放行往下走）
    switch (result) {
      case REPEAT -> throw new MiaoshaException(CodeMsg.MIAOSHA_REPEAT);
      case STOCK_EMPTY -> throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
      case OK -> { }
    }

    // 第 3 步: 预扣成功后发 MQ 消息，通知消费端异步建单。
    // 发送失败时必须当场降级同步落库，否则用户会永远卡在"排队中"。
    boolean sent = messageSender.send(new SeckillOrderMessage(userId, goodsId, requestId));
    if (!sent) {
      // 发送失败降级：同步落库（与消费路径幂等，靠 DB 唯一键兜底）
      log.warn("下单消息发送失败，降级同步落库 goodsId={} userId={} requestId={}", goodsId, userId, requestId);
      OrderInfo oder = miaoshaService.createOrder(userId, goodsId);
      return MiaoshaAcceptVo.success(oder.getId());
    }
    return MiaoshaAcceptVo.processing();
  }

  /** 生成本次请求的全链路标识（去掉横线，方便做 Redis key 和日志检索）。 */
  private String newRequestId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
