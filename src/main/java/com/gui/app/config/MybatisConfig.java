package com.gui.app.config;

import com.gui.app.interceptor.SqlLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * MyBatis 配置类
 */
@Configuration
public class MybatisConfig {

    @Autowired
    private SqlLoggingInterceptor sqlLoggingInterceptor;

    @PostConstruct
    public void addInterceptor() {
        // MyBatis-Plus 会自动识别 @Component 注解的拦截器
        // 这里只是确保拦截器被正确初始化
    }
}
