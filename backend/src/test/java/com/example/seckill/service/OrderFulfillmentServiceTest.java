package com.example.seckill.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.seckill.cache.RedisMiaoshaStore;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.message.SeckillOrderMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrderFulfillmentService} 单元测试：不起 Spring、不连中间件，
 * mock 两个依赖，验证消费端落库编排的三个关键语义：
 *
 * <ul>
 *   <li>成功路径：建单后回写 markSuccess，且参数顺序 goodsId 在前</li>
 *   <li>失败路径：补偿 compensate，且参数顺序 goodsId 在前</li>
 *   <li>失败必须重新抛异常（监听容器靠它决定 nack，不能吞）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

  private static final Long USER_ID = 1001L;
  private static final Long GOODS_ID = 2001L;
  private static final String REQUEST_ID = "req-test-123";
  private static final Long ORDER_ID = 9001L;

  @Mock
  private MiaoshaService miaoshaService;

  @Mock
  private RedisMiaoshaStore store;

  @InjectMocks
  private OrderFulfillmentService fulfillmentService;

  private SeckillOrderMessage message() {
    return new SeckillOrderMessage(USER_ID, GOODS_ID, REQUEST_ID);
  }

  @Test
  void fulfill_success_marksSuccessWithGoodsIdFirst() {
    OrderInfo order = new OrderInfo();
    order.setId(ORDER_ID);
    when(miaoshaService.createOrder(USER_ID, GOODS_ID)).thenReturn(order);

    fulfillmentService.fulfillOrder(message());

    // 参数顺序 goodsId 在前（与 MiaoshaStore 接口签名一致），防顺序传反回归
    verify(store).markSuccess(GOODS_ID, USER_ID, ORDER_ID);
    verify(store, never()).compensate(anyLong(), anyLong(), anyString());
  }

  @Test
  void fulfill_createOrderFails_compensatesAndRethrows() {
    RuntimeException boom = new RuntimeException("DB 连接失败");
    when(miaoshaService.createOrder(USER_ID, GOODS_ID)).thenThrow(boom);

    // 失败必须向上抛：AUTO ack 模式下，容器靠异常决定 nack，吞掉 = 消息静默丢失
    assertThatThrownBy(() -> fulfillmentService.fulfillOrder(message()))
        .isSameAs(boom);

    verify(store).compensate(GOODS_ID, USER_ID, REQUEST_ID);
    verify(store, never()).markSuccess(anyLong(), anyLong(), anyLong());
  }

  @Test
  void fulfill_markSuccessFails_doesNotTriggerCompensate() {
    // markSuccess 内部是"尽力而为"（自己 catch 不抛），即使它出问题也不应走到补偿
    OrderInfo order = new OrderInfo();
    order.setId(ORDER_ID);
    when(miaoshaService.createOrder(USER_ID, GOODS_ID)).thenReturn(order);

    fulfillmentService.fulfillOrder(message());

    verify(store, never()).compensate(anyLong(), anyLong(), anyString());
  }
}
