package com.xiyan.xipicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.xiyan.xipicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)  // 通过 Spring AOP 提供对当前代理对象的访问
public class XiPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiPictureBackendApplication.class, args);
    }

}
