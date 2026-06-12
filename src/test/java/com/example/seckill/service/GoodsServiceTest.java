package com.example.seckill.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.example.seckill.common.MiaoshaStatus;
import com.example.seckill.dao.GoodsMapper;
import com.example.seckill.vo.GoodsDetailVo;
import com.example.seckill.vo.GoodsVo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoodsServiceTest {

  @Mock private GoodsMapper goodsMapper;

  @InjectMocks private GoodsService goodsService;

  @Test
  void getGoodsDetail_notStarted_returnsCountdownToStart() {
    GoodsVo goodsVo = new GoodsVo();
    goodsVo.setStartDate(LocalDateTime.now().plusHours(1));
    goodsVo.setEndDate(LocalDateTime.now().plusHours(2));
    when(goodsMapper.getGoodsVo(1L)).thenReturn(goodsVo);

    GoodsDetailVo detail = goodsService.getGoodsDetail(1L);

    assertEquals(MiaoshaStatus.NOT_START, detail.getMiaoshaStatus());
    assertEquals(3600, detail.getRemainSeconds(), 5);
  }

  @Test
  void getGoodsDetail_inProgress_returnsCountdownToEnd() {
    GoodsVo goodsVo = new GoodsVo();
    goodsVo.setStartDate(LocalDateTime.now().minusHours(1));
    goodsVo.setEndDate(LocalDateTime.now().plusHours(1));
    when(goodsMapper.getGoodsVo(1L)).thenReturn(goodsVo);

    GoodsDetailVo detail = goodsService.getGoodsDetail(1L);

    assertEquals(MiaoshaStatus.IN_PROGRESS, detail.getMiaoshaStatus());
    assertEquals(3600, detail.getRemainSeconds(), 5);
  }

  @Test
  void getGoodsDetail_over_returnsZeroRemainSeconds() {
    GoodsVo goodsVo = new GoodsVo();
    goodsVo.setStartDate(LocalDateTime.now().minusHours(2));
    goodsVo.setEndDate(LocalDateTime.now().minusHours(1));
    when(goodsMapper.getGoodsVo(1L)).thenReturn(goodsVo);

    GoodsDetailVo detail = goodsService.getGoodsDetail(1L);

    assertEquals(MiaoshaStatus.OVER, detail.getMiaoshaStatus());
    assertEquals(0, detail.getRemainSeconds());
  }

  @Test
  void getGoodsDetail_missingGoods_returnsNull() {
    when(goodsMapper.getGoodsVo(999L)).thenReturn(null);
    assertNull(goodsService.getGoodsDetail(999L));
  }
}
