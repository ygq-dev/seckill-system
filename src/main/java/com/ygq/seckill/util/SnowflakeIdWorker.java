package com.ygq.seckill.util;

import org.springframework.stereotype.Component;

/**
 * Twitter Snowflake 算法生成分布式唯一ID
 */
@Component
public class SnowflakeIdWorker {

    // 机器ID所占位数
    private final long workerIdBits = 5L;
    // 数据中心ID所占位数
    private final long datacenterIdBits = 5L;
    // 支持的最大机器ID，结果是31
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // 支持的最大数据中心ID，结果是31
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    // 序列在id中占的位数
    private final long sequenceBits = 12L;
    // 机器ID向左移12位
    private final long workerIdShift = sequenceBits;
    // 数据中心ID向左移17位 (12+5)
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    // 时间戳向左移22位 (12+5+5)
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    // 生成序列的掩码，这里为4095 (0b111111111111=0xfff=4095)
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private long workerId;
    private long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdWorker() {
        // 默认取本地IP或随机生成，生产环境应从配置中心获取
        // 这里简单使用静态值，确保不同实例不同
        this.workerId = 1L;
        this.datacenterId = 1L;
    }

    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        // 如果当前时间小于上一次生成的时间戳，说明系统时钟回退过，抛出异常
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        // 如果是同一时间生成的，则进行毫秒内序列
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 毫秒内序列溢出
            if (sequence == 0) {
                // 阻塞到下一个毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳改变，序列重置
            sequence = 0L;
        }

        // 上次生成ID的时间戳
        lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成64位的ID
        return ((timestamp - 1420041600000L) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }
}