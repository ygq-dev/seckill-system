package com.ygq.seckill.rabbitmq;

import com.ygq.seckill.config.RabbitMQConfig;
import com.ygq.seckill.entity.OrderInfo;
import com.ygq.seckill.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class OrderTimeoutReceiver {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.DELAYED_QUEUE)
    public void handleTimeout(Long orderId) {
        OrderInfo order = orderService.getOrderById(orderId);
        if (order != null && order.getStatus() == 0) { // 待支付
            orderService.cancelOrder(orderId);
            log.info("订单 {} 超时未支付，已取消", orderId);
        }
    }
}
