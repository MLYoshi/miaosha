package com.example.seckill.service;

import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.dao.MiaoshaGoodsMapper;
import com.example.seckill.dao.MiaoshaOrderMapper;
import com.example.seckill.dao.OrderInfoMapper;
import com.example.seckill.domain.MiaoshaOrder;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.vo.GoodsVo;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiaoshaService {

  private static final int ORDER_CHANNEL_PC = 1;
  private static final int ORDER_STATUS_NEW = 0;

  private final GoodsService goodsService;
  private final MiaoshaGoodsMapper miaoshaGoodsMapper;
  private final OrderInfoMapper orderInfoMapper;
  private final MiaoshaOrderMapper miaoshaOrderMapper;

  public MiaoshaService(
      GoodsService goodsService,
      MiaoshaGoodsMapper miaoshaGoodsMapper,
      OrderInfoMapper orderInfoMapper,
      MiaoshaOrderMapper miaoshaOrderMapper) {
    this.goodsService = goodsService;
    this.miaoshaGoodsMapper = miaoshaGoodsMapper;
    this.orderInfoMapper = orderInfoMapper;
    this.miaoshaOrderMapper = miaoshaOrderMapper;
  }

  @Transactional
  public OrderInfo doMiaosha(Long userId, Long goodsId) {
    GoodsVo goods = goodsService.getGoodsVo(goodsId);
    if (goods == null) {
      throw new MiaoshaException(CodeMsg.GOODS_NOT_EXIST);
    }

    MiaoshaOrder existing = miaoshaOrderMapper.getByUserIdAndGoodsId(userId, goodsId);
    if (existing != null) {
      throw new MiaoshaException(CodeMsg.MIAOSHA_REPEAT);
    }

    if (goods.getStockCount() == null || goods.getStockCount() <= 0) {
      throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
    }

    int rows = miaoshaGoodsMapper.reduceStock(goodsId);
    if (rows <= 0) {
      throw new MiaoshaException(CodeMsg.MIAOSHA_STOCK_EMPTY);
    }

    OrderInfo orderInfo = buildOrderInfo(userId, goods);
    orderInfoMapper.insert(orderInfo);

    MiaoshaOrder miaoshaOrder = new MiaoshaOrder();
    miaoshaOrder.setUserId(userId);
    miaoshaOrder.setOrderId(orderInfo.getId());
    miaoshaOrder.setGoodsId(goodsId);
    miaoshaOrderMapper.insert(miaoshaOrder);

    return orderInfo;
  }

  private OrderInfo buildOrderInfo(Long userId, GoodsVo goods) {
    OrderInfo orderInfo = new OrderInfo();
    orderInfo.setUserId(userId);
    orderInfo.setGoodsId(goods.getId());
    orderInfo.setGoodsName(goods.getGoodsName());
    orderInfo.setGoodsCount(1);
    orderInfo.setGoodsPrice(goods.getMiaoshaPrice());
    orderInfo.setOrderChannel(ORDER_CHANNEL_PC);
    orderInfo.setStatus(ORDER_STATUS_NEW);
    orderInfo.setCreateDate(LocalDateTime.now());
    return orderInfo;
  }
}
