package com.example.seckill.service;

import com.example.seckill.common.MiaoshaStatus;
import com.example.seckill.dao.GoodsMapper;
import com.example.seckill.domain.Goods;
import com.example.seckill.vo.GoodsDetailVo;
import com.example.seckill.vo.GoodsVo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoodsService {

  private final GoodsMapper goodsMapper;

  public GoodsService(GoodsMapper goodsMapper) {
    this.goodsMapper = goodsMapper;
  }

  public Goods getById(Long id) {
    return goodsMapper.getById(id);
  }

  public List<GoodsVo> listGoodsVo() {
    return goodsMapper.listGoodsVo();
  }

  public GoodsVo getGoodsVo(Long goodsId) {
    return goodsMapper.getGoodsVo(goodsId);
  }

  public GoodsDetailVo getGoodsDetail(Long goodsId) {
    GoodsVo goodsVo = goodsMapper.getGoodsVo(goodsId);
    if (goodsVo == null) {
      return null;
    }
    int miaoshaStatus = MiaoshaStatus.OVER;
    int remainSeconds = 0;
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startDate = goodsVo.getStartDate();
    LocalDateTime endDate = goodsVo.getEndDate();
    if (startDate != null && now.isBefore(startDate)) {
      miaoshaStatus = MiaoshaStatus.NOT_START;
      remainSeconds = (int) Duration.between(now, startDate).getSeconds();
    } else if (endDate != null && now.isAfter(endDate)) {
      miaoshaStatus = MiaoshaStatus.OVER;
      remainSeconds = 0;
    } else {
      miaoshaStatus = MiaoshaStatus.IN_PROGRESS;
      if (endDate != null) {
        remainSeconds = (int) Duration.between(now, endDate).getSeconds();
      }
    }
    return new GoodsDetailVo(goodsVo, miaoshaStatus, remainSeconds);
  }
}
