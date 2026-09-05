package com.ygq.seckill.controller;

import com.ygq.seckill.entity.OrderInfo;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.service.GoodsService;
import com.ygq.seckill.service.OrderService;
import com.ygq.seckill.vo.GoodsVo;
import com.ygq.seckill.vo.OrderDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private GoodsService goodsService;

    @GetMapping("/detail/{orderId}")
    public Result<OrderDetailVo> orderDetail(@PathVariable Long orderId) {
        OrderInfo order = orderService.getOrderById(orderId);
        if (order == null) {
            return Result.error(CodeMsg.ORDER_NOT_EXIST);
        }
        GoodsVo goods = goodsService.getGoodsVoById(order.getGoodsId());
        OrderDetailVo vo = new OrderDetailVo();
        vo.setOrder(order);
        vo.setGoods(goods);
        return Result.success(vo);
    }
}
