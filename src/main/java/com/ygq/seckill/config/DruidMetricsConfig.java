package com.ygq.seckill.config;

import com.alibaba.druid.pool.DruidDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
public class DruidMetricsConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void registerDruidMetrics() {
        if (dataSource instanceof DruidDataSource) {
            DruidDataSource druid = (DruidDataSource) dataSource;

            // 活跃连接数
            Gauge.builder("druid.pool.active.count", druid, DruidDataSource::getActiveCount)
                    .register(meterRegistry);

            // 空闲连接数 = 池中总数 - 活跃数
            Gauge.builder("druid.pool.idle.count", druid, d -> d.getPoolingCount() - d.getActiveCount())
                    .register(meterRegistry);

            // 等待获取连接的线程数（如果该方法不存在，可注释掉）
            Gauge.builder("druid.pool.wait.thread.count", druid, DruidDataSource::getWaitThreadCount)
                    .register(meterRegistry);

            // 池中总连接数
            Gauge.builder("druid.pool.connection.count", druid, DruidDataSource::getPoolingCount)
                    .register(meterRegistry);

            // 获取连接等待总时间（毫秒），如果不存在可注释
            Gauge.builder("druid.pool.wait.millis", druid, DruidDataSource::getNotEmptyWaitMillis)
                    .register(meterRegistry);

            System.out.println("✅ Druid metrics registered successfully.");
        } else {
            System.out.println("⚠️ DataSource is not DruidDataSource, actual type: " + dataSource.getClass().getName());
        }
    }
}