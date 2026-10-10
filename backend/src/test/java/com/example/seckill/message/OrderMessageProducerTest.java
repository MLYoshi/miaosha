package com.example.seckill.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 下单消息生产者集成测试：真连本地 RabbitMQ，验证「发送 → 回读」往返。
 *
 * <p>覆盖：broker 连通、JSON converter 是否生效、{@code SeckillOrderMessage}
 * 这条 record 能否往返序列化、队列/交换机/绑定是否声明成功、路由是否命中。
 */
@SpringBootTest
class OrderMessageProducerTest {

  @Autowired
  private OrderMessageSender orderMessageSender;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Test
  void send_then_receive_roundTrips() {
    SeckillOrderMessage message = new SeckillOrderMessage(1001L, 2001L, "req-test-123");

    boolean sent = orderMessageSender.send(message);
    assertThat(sent).as("应发送成功").isTrue();

    Object received = rabbitTemplate.receiveAndConvert(RabbitConfig.ORDER_QUEUE, 5000);
    assertThat(received)
        .as("应从队列回读到一条 SeckillOrderMessage")
        .isInstanceOf(SeckillOrderMessage.class);

    SeckillOrderMessage got = (SeckillOrderMessage) received;
    assertThat(got.userId()).isEqualTo(1001L);
    assertThat(got.goodsId()).isEqualTo(2001L);
    assertThat(got.requestId()).isEqualTo("req-test-123");
  }
}