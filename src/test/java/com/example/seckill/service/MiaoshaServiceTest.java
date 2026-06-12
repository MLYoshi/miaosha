package com.example.seckill.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.seckill.common.CodeMsg;
import com.example.seckill.common.MiaoshaException;
import com.example.seckill.dao.MiaoshaGoodsMapper;
import com.example.seckill.dao.MiaoshaOrderMapper;
import com.example.seckill.dao.OrderInfoMapper;
import com.example.seckill.domain.MiaoshaOrder;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.vo.GoodsVo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MiaoshaServiceTest {

  @Mock private GoodsService goodsService;
  @Mock private MiaoshaGoodsMapper miaoshaGoodsMapper;
  @Mock private OrderInfoMapper orderInfoMapper;
  @Mock private MiaoshaOrderMapper miaoshaOrderMapper;

  @InjectMocks private MiaoshaService miaoshaService;

  @Test
  void doMiaosha_success_reducesStockAndCreatesOrder() {
    GoodsVo goods = new GoodsVo();
    goods.setId(1L);
    goods.setGoodsName("iphoneX");
    goods.setMiaoshaPrice(new BigDecimal("0.01"));
    goods.setStockCount(5);
    when(goodsService.getGoodsVo(1L)).thenReturn(goods);
    when(miaoshaOrderMapper.getByUserIdAndGoodsId(13000000000L, 1L)).thenReturn(null);
    when(miaoshaGoodsMapper.reduceStock(1L)).thenReturn(1);
    doAnswer(
            invocation -> {
              OrderInfo order = invocation.getArgument(0);
              order.setId(100L);
              return 1;
            })
        .when(orderInfoMapper)
        .insert(any(OrderInfo.class));

    OrderInfo order = miaoshaService.doMiaosha(13000000000L, 1L);

    assertEquals(13000000000L, order.getUserId());
    assertEquals(1L, order.getGoodsId());
    assertEquals("iphoneX", order.getGoodsName());
    assertEquals(new BigDecimal("0.01"), order.getGoodsPrice());
    verify(miaoshaGoodsMapper).reduceStock(1L);
    verify(orderInfoMapper).insert(any(OrderInfo.class));
    verify(miaoshaOrderMapper).insert(any(MiaoshaOrder.class));
  }

  @Test
  void doMiaosha_noStock_throwsException() {
    GoodsVo goods = new GoodsVo();
    goods.setId(1L);
    goods.setStockCount(0);
    when(goodsService.getGoodsVo(1L)).thenReturn(goods);
    when(miaoshaOrderMapper.getByUserIdAndGoodsId(13000000000L, 1L)).thenReturn(null);

    MiaoshaException ex =
        assertThrows(MiaoshaException.class, () -> miaoshaService.doMiaosha(13000000000L, 1L));

    assertEquals(CodeMsg.MIAOSHA_STOCK_EMPTY, ex.getCodeMsg());
    verify(miaoshaGoodsMapper, never()).reduceStock(1L);
  }

  @Test
  void doMiaosha_repeat_throwsException() {
    GoodsVo goods = new GoodsVo();
    goods.setId(1L);
    goods.setStockCount(5);
    when(goodsService.getGoodsVo(1L)).thenReturn(goods);
    when(miaoshaOrderMapper.getByUserIdAndGoodsId(18912341234L, 1L))
        .thenReturn(new MiaoshaOrder());

    MiaoshaException ex =
        assertThrows(MiaoshaException.class, () -> miaoshaService.doMiaosha(18912341234L, 1L));

    assertEquals(CodeMsg.MIAOSHA_REPEAT, ex.getCodeMsg());
    verify(miaoshaGoodsMapper, never()).reduceStock(1L);
  }
}
