package com.ygq.seckill.controller;

import com.ygq.seckill.entity.User;
import com.ygq.seckill.result.Result;
import com.ygq.seckill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/info")
    public Result<User> info(@AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        user.setPassword(null); // 脱敏
        return Result.success(user);
    }
}
