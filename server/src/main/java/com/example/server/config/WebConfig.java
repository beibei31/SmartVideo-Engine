package com.example.server.config;

import com.example.server.interceptor.AiTokenQuotaInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AiTokenQuotaInterceptor aiTokenQuotaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiTokenQuotaInterceptor)
                .addPathPatterns("/debug/ai", "/debug/ai-direct", "/debug/transcribe");
    }


    //新增全局跨域配置 (解决 Network Error)，放弃本地调用
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                //允许所有前端来源
                .allowedOriginPatterns("*")
                //允许 GET, POST, DELETE 等所有方法
                .allowedMethods("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
