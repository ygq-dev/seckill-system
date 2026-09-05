package com.ygq.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {
    @Update("update sk_goods_seckill set stock_count = stock_count - 1, version = version + 1 " +
            "where id = #{id} and stock_count > 0 and version = #{version}")
    int reduceStockByVersion(@Param("id") Long id, @Param("version") Integer version);

    @Update("update sk_goods_seckill set stock_count = stock_count + #{count} where goods_id = #{goodsId}")
    void increaseStock(@Param("goodsId") Long goodsId, @Param("count") Integer count);

    @Select("SELECT version FROM sk_goods_seckill WHERE goods_id = #{goodsId}")
    int getVersionByGoodsId(@Param("goodsId") Long goodsId);

    @Update("update sk_goods_seckill set stock_count = stock_count - 1 where id = #{seckillId} and stock_count > 0")
    int reduceStockDirect(@Param("seckillId") Long seckillId);

    @Update("UPDATE sk_goods_seckill SET stock_count = stock_count - #{total} " +
            "WHERE goods_id = #{goodsId} AND stock_count >= #{total}")
    int batchReduceStock(@Param("goodsId") Long goodsId, @Param("total") int total);

    @Select("SELECT * FROM sk_goods_seckill WHERE goods_id = #{goodsId}")
    SeckillGoods selectByGoodsId(@Param("goodsId") Long goodsId);

    @Update("UPDATE sk_goods_seckill SET stock_count = stock_count - 1 WHERE goods_id = #{goodsId} AND stock_count > 0")
    int reduceStockByGoodsId(@Param("goodsId") Long goodsId);
}
