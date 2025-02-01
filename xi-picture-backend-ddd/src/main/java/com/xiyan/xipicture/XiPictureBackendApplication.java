package com.xiyan.xipicture;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

//@SpringBootApplication
@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})  // 启动类排除依赖，关闭分库分表
@EnableAsync  // 使用@Async，要开启异步任务
@MapperScan("com.xiyan.xipicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)  // 通过 Spring AOP 提供对当前代理对象的访问
public class XiPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiPictureBackendApplication.class, args);
    }

}
