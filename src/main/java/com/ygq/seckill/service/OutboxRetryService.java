package com.ygq.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.entity.MqOutbox;
import com.ygq.seckill.mapper.MqOutboxMapper;
import com.ygq.seckill.rabbitmq.StockDecreaseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class OutboxRetryService {

    @Autowired
    private MqOutboxMapper outboxMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000) // 每5秒执行一次
    @Transactional
    public void retryOutboxMessages() {
        // 查询待发送且到达重试时间的消息（最多批量100条）
        List<MqOutbox> list = outboxMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MqOutbox>()
                        .eq(MqOutbox::getStatus, 0)
                        .le(MqOutbox::getNextRetryTime, LocalDateTime.now())
                        .last("limit 100")
        );

        if (list.isEmpty()) return;

        for (MqOutbox outbox : list) {
            try {
                // 使用 Jackson 将 JSON 字符串反序列化为对象
                StockDecreaseMessage msg = objectMapper.readValue(outbox.getPayload(), StockDecreaseMessage.class);
                rabbitTemplate.convertAndSend(outbox.getExchange(), outbox.getRoutingKey(), msg);

                // 发送成功，更新状态为已发送
                outbox.setStatus(1);
                outboxMapper.updateById(outbox);
                log.debug("Outbox 消息重发成功: id={}", outbox.getId());
            } catch (Exception e) {
                // 失败，增加重试次数，推迟下次重试时间（指数退避）
                outbox.setRetryCount(outbox.getRetryCount() + 1);
                if (outbox.getRetryCount() >= 5) {
                    outbox.setStatus(2); // 标记最终失败，等待人工介入
                } else {
                    outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(10L * (1 << outbox.getRetryCount())));
                }
                outboxMapper.updateById(outbox);
                log.error("Outbox 消息重发失败: id={}", outbox.getId(), e);
            }
        }
    }
}
