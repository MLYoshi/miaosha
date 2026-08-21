package com.example.seckill.service;

import com.example.seckill.cache.MiaoshaRedisStore;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.message.SeckillOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 消费者落库编排：把一条下单请求消息变成数据库订单，并回写 Redis 结果。
 *
 * <p>编排语义（票 03）：
 * <ul>
 *   <li>落库成功 → {@code markSuccess}，轮询接口可查到订单号
 *   <li>业务失败（{@link MiaoshaException}，如 DB 已有记录、时间窗外）→
 *       {@code compensate}（库存回补、标记清除、result=FAILED）后正常返回，
 *       listener ack——不重试业务失败
 *   <li>迟到的重复消息（受理侧发送失败降级同步落库后消息又送达）：DB 唯一键拦下，
 *       但 result 已是 SUCCESS——跳过补偿，避免覆盖成功结果与错误回补库存
 *   <li>意外异常上抛，listener 不 ack（票 04 加固重试与死信）
 * </ul>
 *
 * <p>不依赖 Kafka，可经内存假 Redis + mock 下单事务直测。
 */
@Service
public class OrderFulfillmentService {

  private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentService.class);

  private static final String SUCCESS_PREFIX = "SUCCESS:";

  private final MiaoshaService miaoshaService;
  private final MiaoshaRedisStore store;

  public OrderFulfillmentService(MiaoshaService miaoshaService, MiaoshaRedisStore store) {
    this.miaoshaService = miaoshaService;
    this.store = store;
  }

  /** 消费一条下单请求消息：落库 + 回写 / 补偿，业务失败正常返回，意外异常上抛。 */
  public void fulfill(SeckillOrderMessage message) {
    Long userId = message.getUserId();
    Long goodsId = message.getGoodsId();
    try {
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      store.markSuccess(goodsId, userId, order.getId());
    } catch (MiaoshaException e) {
      if (alreadySucceeded(goodsId, userId)) {
        // 降级同步落库已成功，这条是迟到的重复消息：DB 唯一键已拦下，跳过补偿
        log.warn("迟到重复消息跳过补偿 goodsId={} userId={} requestId={}", goodsId, userId,
            message.getRequestId());
        return;
      }
      log.warn("下单业务失败，执行补偿 goodsId={} userId={} requestId={}: {}", goodsId, userId,
          message.getRequestId(), e.getCodeMsg().getMsg());
      store.compensate(goodsId, userId, message.getRequestId());
    }
  }

  /** 该用户该商品的受理是否已有成功结果（降级同步落库后 result=SUCCESS:{orderId}）。 */
  private boolean alreadySucceeded(Long goodsId, Long userId) {
    String result = store.getResult(goodsId, userId);
    return result != null && result.startsWith(SUCCESS_PREFIX);
  }
}
