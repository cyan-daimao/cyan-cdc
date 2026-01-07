package com.cyan.cdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 启动类
 *
 * @author cy.Y
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.cyan"})
@EnableFeignClients(basePackages = "com.cyan.cdc.client") // 指向EmployeeRPCClient所在包
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}