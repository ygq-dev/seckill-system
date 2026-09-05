package com.ygq.seckill.util;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;

@Slf4j
@Component
public class DynamicRateLimiter {

    @Value("${seckill.global.qps:2000}")
    private int baseQps;

    private volatile int currentQps = 2000;
    private final RateLimiter rateLimiter = RateLimiter.create(currentQps);

    // 上一次记录的GC总时间（毫秒）
    private volatile long lastGcTime = 0;

    @Scheduled(fixedDelay = 5000)
    public void adjust() {
        // 1. 获取CPU使用率（更准确）
        double cpuUsage = getCpuUsage();

        // 2. 获取最近5秒内的GC总停顿时间
        long gcTotalPause = getGcTotalPause();

        // 3. 综合判断（总停顿 > 200ms 降级，> 100ms 预警）
        if (cpuUsage > 0.75 || gcTotalPause > 200) {
            currentQps = baseQps / 2;
        } else if (cpuUsage > 0.6 || gcTotalPause > 100) {
            currentQps = (int) (baseQps * 0.75);
        } else {
            currentQps = baseQps;
        }

        // 4. 更新限流器
        if (rateLimiter.getRate() != currentQps) {
            rateLimiter.setRate(currentQps);
            log.info("动态限流调整: currentQps={}, cpuUsage={:.2f}, gcTotalPause={}ms",
                    currentQps, cpuUsage * 100, gcTotalPause);
        }
    }

    /**
     * 获取最近5秒内的GC总停顿时间（毫秒）
     */
    private long getGcTotalPause() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long currentTotal = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            currentTotal += gc.getCollectionTime();
        }
        long pause = currentTotal - lastGcTime;
        lastGcTime = currentTotal;
        return pause;
    }

    /**
     * 获取CPU使用率（0-1），如果获取不到则使用系统负载估算
     */
    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            // 尝试使用 com.sun.management 扩展（Oracle JDK/OpenJDK）
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sunOsBean.getCpuLoad();
                if (cpuLoad >= 0) {
                    return cpuLoad;
                }
            }
            // 降级使用系统平均负载（Load Average / 核心数）
            double loadAvg = osBean.getSystemLoadAverage();
            if (loadAvg < 0) {
                return 0;
            }
            int processors = osBean.getAvailableProcessors();
            return loadAvg / processors;
        } catch (Exception e) {
            log.warn("获取CPU使用率失败，使用默认值0", e);
            return 0;
        }
    }

    public boolean tryAcquire() {
        return rateLimiter.tryAcquire();
    }
}