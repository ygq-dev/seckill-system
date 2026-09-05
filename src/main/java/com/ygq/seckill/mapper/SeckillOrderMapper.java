package com.ygq.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
    /**
     * 批量插入秒杀订单
     */
    @Insert("<script>" +
            "INSERT INTO sk_seckill_order (id, user_id, goods_id, create_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.goodsId}, #{item.createTime})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<SeckillOrder> list);

    @Insert("<script>" +
            "INSERT IGNORE INTO sk_seckill_order (user_id, goods_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.userId}, #{item.goodsId})" +
            "</foreach>" +
            "</script>")
    int batchInsertIgnore(@Param("list") List<SeckillOrder> orders);

    /**
     * 根据用户ID列表和商品ID列表查询已存在的秒杀订单
     * 用于批量处理前的预过滤，减少重复插入
     * 利用联合索引 uk_user_goods (user_id, goods_id) 保证性能
     */
    @Select("<script>" +
            "SELECT id, user_id, goods_id, create_time FROM sk_seckill_order WHERE " +
            "<foreach collection='userIds' item='uid' open='(' separator=' OR ' close=')'>" +
            "(user_id = #{uid} AND goods_id IN " +
            "<foreach collection='goodsIds' item='gid' open='(' separator=',' close=')'>" +
            "#{gid}" +
            "</foreach>" +
            ")" +
            "</foreach>" +
            "</script>")
    List<SeckillOrder> selectByUserIdsAndGoodsIds(@Param("userIds") List<Long> userIds,
                                                  @Param("goodsIds") List<Long> goodsIds);

}
