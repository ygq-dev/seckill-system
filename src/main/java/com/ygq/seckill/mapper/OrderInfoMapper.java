package com.ygq.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.seckill.entity.OrderInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 批量插入订单详情
     */
    @Insert("<script>" +
            "INSERT INTO sk_order_info (id, user_id, goods_id, goods_name, goods_count, goods_price, order_channel, status, create_date) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.goodsId}, #{item.goodsName}, #{item.goodsCount}, #{item.goodsPrice}, #{item.orderChannel}, #{item.status}, #{item.createDate})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<OrderInfo> list);
}
