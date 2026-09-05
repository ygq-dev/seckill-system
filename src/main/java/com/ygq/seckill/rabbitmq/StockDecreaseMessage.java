package com.ygq.seckill.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDecreaseMessage {
    private Long goodsId;
    private Integer quantity;
}