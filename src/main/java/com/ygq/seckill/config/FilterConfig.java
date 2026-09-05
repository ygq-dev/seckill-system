package com.ygq.seckill.config;

import com.ygq.seckill.filter.PreCheckFilter;
import com.ygq.seckill.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Autowired
    private PreCheckFilter preCheckFilter;
    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Bean
    public FilterRegistrationBean<PreCheckFilter> preCheckFilterRegistration() {
        FilterRegistrationBean<PreCheckFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(preCheckFilter);
        bean.addUrlPatterns("/api/seckill/do");
        bean.setOrder(Integer.MIN_VALUE + 1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(rateLimitFilter);
        bean.addUrlPatterns("/api/seckill/do");
        bean.setOrder(2);
        return bean;
    }


//    @Bean
//    public FilterRegistrationBean<LogTraceIdFilter> logTraceIdFilter() {
//        FilterRegistrationBean<LogTraceIdFilter> bean = new FilterRegistrationBean<>();
//        bean.setFilter(new LogTraceIdFilter());
//        bean.addUrlPatterns("/api/*");
//        bean.setOrder(1);
//        return bean;
//    }

//    @Bean
//    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
//        FilterRegistrationBean<AccessLogFilter> bean = new FilterRegistrationBean<>();
//        bean.setFilter(new AccessLogFilter());
//        bean.addUrlPatterns("/api/*");
//        bean.setOrder(3);
//        return bean;
//    }
}