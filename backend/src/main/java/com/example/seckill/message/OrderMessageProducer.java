package com.example.seckill.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderMessageProducer implements OrderMessageSender {
  private static final Logger log = LoggerFactory.getLogger(OrderMessageProducer.class);

  private final RabbitTemplate rabbitTemplate;

  public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public boolean send(SeckillOrderMessage message) {
    try {
      rabbitTemplate.convertAndSend(RabbitConfig.ORDER_EXCHANGE,
      RabbitConfig.ORDER_ROUTING_KEY,
      message);
      return true;
    } catch (Exception e) {
      log.warn("下单消息发送异常 exchange={} routingKey={} message={}: {}",
          RabbitConfig.ORDER_EXCHANGE, RabbitConfig.ORDER_ROUTING_KEY, message, e.toString());
      return false;
    }
  }
}