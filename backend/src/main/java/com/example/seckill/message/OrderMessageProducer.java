package com.example.seckill.message;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderMessageProducer implements OrderMessageSender {
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
      return false;
    }
  }
}