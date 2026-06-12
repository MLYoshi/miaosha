package com.example.seckill.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.seckill.domain.Goods;
import com.example.seckill.vo.GoodsVo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoodsMapperTest {

  @Autowired
  private GoodsMapper goodsMapper;

  @Test
  void getById_existingId_returnsGoods() {
    Goods goods = goodsMapper.getById(1L);

    assertNotNull(goods);
    assertEquals(1L, goods.getId());
    assertEquals("iphoneX", goods.getGoodsName());
    assertNotNull(goods.getGoodsPrice());
  }

  @Test
  void getById_nonExistingId_returnsNull() {
    assertNull(goodsMapper.getById(999999L));
  }

  @Test
  void listGoodsVo_returnsSeckillGoods() {
    List<GoodsVo> list = goodsMapper.listGoodsVo();

    assertNotNull(list);
    assertEquals(4, list.size());
    assertNotNull(list.get(0).getMiaoshaPrice());
    assertNotNull(list.get(0).getStartDate());
  }

  @Test
  void getGoodsVo_existingId_returnsJoinedGoods() {
    GoodsVo goodsVo = goodsMapper.getGoodsVo(1L);

    assertNotNull(goodsVo);
    assertEquals(1L, goodsVo.getId());
    assertEquals("iphoneX", goodsVo.getGoodsName());
    assertNotNull(goodsVo.getMiaoshaPrice());
  }

  @Test
  void getGoodsVo_nonExistingId_returnsNull() {
    assertNull(goodsMapper.getGoodsVo(999999L));
  }
}
