package com.ygq.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.seckill.entity.Order;
import com.ygq.seckill.entity.OrderInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 批量插入订单关联表
     */
    @Insert("<script>" +
            "INSERT INTO sk_order (id, user_id, goods_id, order_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.goodsId}, #{item.orderId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<Order> list);
}
