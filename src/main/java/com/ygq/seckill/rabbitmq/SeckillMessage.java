package com.ygq.seckill.rabbitmq;


/**
 *
 * 消息体
 */
public class SeckillMessage {

    private Long userId;
    private Long goodsId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public long getGoodsId() {
        return goodsId;
    }

    public SeckillMessage(Long userId, Long goodsId) {
        this.userId = userId;
        this.goodsId = goodsId;
    }
}
