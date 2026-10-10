package com.example.seckill.message;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.seckill.service.OrderFulfillmentService;

@Component 
public class OrderMessageConsumer {
    
    private final OrderFulfillmentService fulfillmentService;

    public OrderMessageConsumer(OrderFulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
      }

    @RabbitListener(queues = RabbitConfig.ORDER_QUEUE)
    public void consume(SeckillOrderMessage msg){
        fulfillmentService.fulfillOrder(msg);
    }

}