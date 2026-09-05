package com.ygq.seckill.controller;

import com.ygq.seckill.entity.User;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.service.UserService;
import com.ygq.seckill.util.JwtUtil;
import com.ygq.seckill.vo.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginVo loginVo) {
        User user = userService.login(loginVo);  // 返回用户对象或抛异常
        String token = jwtUtil.generateToken(user.getId());
        return Result.success(token);
    }
}