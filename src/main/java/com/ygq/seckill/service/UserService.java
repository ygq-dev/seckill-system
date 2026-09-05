package com.ygq.seckill.service;

import com.ygq.seckill.entity.User;
import com.ygq.seckill.exception.GlobalException;
import com.ygq.seckill.mapper.UserMapper;
import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.util.JwtUtil;
import com.ygq.seckill.util.MD5Util;
import com.ygq.seckill.vo.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    public User register(String mobile, String password) {
        User user = new User();
        user.setId(Long.parseLong(mobile));
        user.setNickname(mobile);
        // 使用 BCrypt 加密
        user.setPassword(passwordEncoder.encode(password));
        // salt 字段可以废弃（BCrypt 内置盐）
        user.setSalt("");
        user.setRegisterDate(LocalDateTime.now());
        user.setLoginCount(0);
        userMapper.insert(user);
        return user;
    }

    public User login(LoginVo loginVo) {
        String mobile = loginVo.getMobile();
        String rawPass = loginVo.getPassword();
        User user = userMapper.selectById(Long.parseLong(mobile));
        if (user == null) {
            throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
        }
        // 使用 BCrypt 验证
        if (!passwordEncoder.matches(rawPass, user.getPassword())) {
            throw new GlobalException(CodeMsg.PASSWORD_ERROR);
        }
        // 登录成功，更新登录次数等
        return user;
    }

//    public User login(LoginVo loginVo) {
//        String mobile = loginVo.getMobile();
//        String rawPass = loginVo.getPassword();  // 明文密码
//
//        // 判空...
//        if (mobile == null || mobile.trim().isEmpty()) {
//            throw new GlobalException(CodeMsg.MOBILE_EMPTY);
//        }
//        if (rawPass == null || rawPass.trim().isEmpty()) {
//            throw new GlobalException(CodeMsg.PASSWORD_EMPTY);
//        }
//
//        // 将明文密码转换为 formPass（第一次MD5）
//        String formPass = MD5Util.inputPassToFormPass(rawPass);
//
//        User user = userMapper.selectById(Long.parseLong(mobile));
//        if (user == null) {
//            throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
//        }
//        String dbPass = user.getPassword();
//        String salt = user.getSalt();
//        String calcPass = MD5Util.formPassToDBPass(formPass, salt);
//        if (!calcPass.equals(dbPass)) {
//            throw new GlobalException(CodeMsg.PASSWORD_ERROR);
//        }
//        return user;
//    }

    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }
}
