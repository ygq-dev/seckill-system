package com.ygq.seckill.controller;

import com.ygq.seckill.entity.User;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.service.GoodsService;
import com.ygq.seckill.service.UserService;
import com.ygq.seckill.vo.GoodsDetailVo;
import com.ygq.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public Result<List<GoodsVo>> list() {
        return Result.success(goodsService.listGoodsVo());
    }

    @GetMapping("/detail/{goodsId}")
    public Result<GoodsDetailVo> detail(@PathVariable Long goodsId, @AuthenticationPrincipal Long userId) {
        GoodsVo goods = goodsService.getGoodsVoById(goodsId);
        if (goods == null) {
            return Result.error(CodeMsg.GOODS_NOT_EXIST);  // 需要添加错误码
        }

        // 获取当前登录用户信息（可选）
        User user = userService.getById(userId);

        GoodsDetailVo vo = new GoodsDetailVo();
        vo.setGoods(goods);
        vo.setUser(user);

        // 计算秒杀状态和剩余时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = goods.getStartDate();
        LocalDateTime end = goods.getEndDate();

        if (now.isBefore(start)) {
            vo.setSeckillStatus(0);
            vo.setRemainSeconds((int) Duration.between(now, start).getSeconds());
        } else if (now.isAfter(end)) {
            vo.setSeckillStatus(2);
            vo.setRemainSeconds(-1);
        } else {
            vo.setSeckillStatus(1);
            vo.setRemainSeconds(0);
        }

        return Result.success(vo);
    }

//    @GetMapping("/detail/{goodsId}")
//    public Result<GoodsVo> detail(@PathVariable Long goodsId) {
//        return Result.success(goodsService.getGoodsVoById(goodsId));
//    }
}
