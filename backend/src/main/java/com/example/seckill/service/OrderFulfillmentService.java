package com.example.seckill.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.seckill.message.SeckillOrderMessage;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.cache.RedisMiaoshaStore;


@Service
public class OrderFulfillmentService {
  private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentService.class);

  private final MiaoshaService miaoshaService;
  private final RedisMiaoshaStore store;

  public OrderFulfillmentService(MiaoshaService miaoshaService, RedisMiaoshaStore store) {
    this.miaoshaService = miaoshaService;
    this.store = store;
  }

  public void fulfillOrder(SeckillOrderMessage msg) {
    long userId = msg.userId();
    long goodsId = msg.goodsId();
    try {
      OrderInfo order = miaoshaService.createOrder(userId, goodsId);
      store.markSuccess(goodsId, userId, order.getId());
    } catch (Exception e) {
      store.compensate(goodsId, userId, msg.requestId());
      log.error("下单落库失败，已补偿: userId={}, goodsId={}, requestId={}", userId, goodsId, msg.requestId(), e);
      throw e;
    }
  }
}