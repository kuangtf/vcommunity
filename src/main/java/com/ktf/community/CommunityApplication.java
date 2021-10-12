package com.ktf.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;

/**
 * @author github.com/kuangtf
 * @date 2021/9/4 19:33
 */
@SpringBootApplication
public class CommunityApplication {

    /**
     * 解决 Elasticsearch 和 Redis 底层的 Netty 启动冲突问题
     */
    @PostConstruct
    public void init() {
        System.setProperty("es.set.netty.runtime.available.processors", "false");
    }

    public static void main(String[] args) {
        SpringApplication.run(CommunityApplication.class, args);
    }

}
