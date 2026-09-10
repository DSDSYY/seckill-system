package com.example.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀系统启动类
 *
 * <p>@MapperScan：扫描 Mapper 接口所在包，MyBatis 会为每个接口动态生成代理实现，
 * 这样业务代码只依赖接口，不写 Mapper XML 也能完成单表 CRUD。
 */
@SpringBootApplication
@MapperScan("com.example.seckill.mapper")
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}