package com.example.seckill.message;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
public class RabbitConfig {
  public static final String ORDER_QUEUE = "seckill.order.queue";
  public static final String ORDER_EXCHANGE = "seckill.order.exchange";
  public static final String ORDER_ROUTING_KEY = "seckill.order";

  @Bean
  public Queue orderQueue() {
    return new Queue(ORDER_QUEUE);
  }

  @Bean
  public DirectExchange orderExchange() {
    return new DirectExchange(ORDER_EXCHANGE);
  }

  @Bean
  public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
    return BindingBuilder
      .bind(orderQueue)
      .to(orderExchange)
      .with(ORDER_ROUTING_KEY);
  }

  @Bean
  public MessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}


