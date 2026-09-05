package com.ygq.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.seckill.entity.Goods;
import com.ygq.seckill.vo.GoodsVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
    @Select("SELECT g.*, sg.seckill_price, sg.stock_count, sg.start_date, sg.end_date, sg.version, sg.id as seckill_id " +
            "FROM sk_goods g LEFT JOIN sk_goods_seckill sg ON g.id = sg.goods_id")
    List<GoodsVo> selectGoodsVoList();

    @Select("SELECT g.*, sg.seckill_price, sg.stock_count, sg.start_date, sg.end_date, sg.version, sg.id as seckill_id " +
            "FROM sk_goods g LEFT JOIN sk_goods_seckill sg ON g.id = sg.goods_id WHERE g.id = #{goodsId}")
    GoodsVo selectGoodsVoById(Long goodsId);
}