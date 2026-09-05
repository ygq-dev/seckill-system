package com.ygq.seckill;

import com.ygq.seckill.util.MD5Util;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SeckillApplicationTests {

    @Test
    void contextLoads() {
        System.out.println(MD5Util.inputPassToFormPass("123456"));//d3b1294a61a07da9b49b6e22b2cbd7f9
        String dbPass = MD5Util.inputPassToDbPass("123456", "1a2b3c4d");
        System.out.println(dbPass);
    }

}
