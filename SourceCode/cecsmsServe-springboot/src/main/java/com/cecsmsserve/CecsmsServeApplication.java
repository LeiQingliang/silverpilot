package com.cecsmsserve;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cecsmsserve.mapper")
@SpringBootApplication
public class CecsmsServeApplication {

    public static void main(String[] args) {

        SpringApplication.run(CecsmsServeApplication.class, args);
    }
}
