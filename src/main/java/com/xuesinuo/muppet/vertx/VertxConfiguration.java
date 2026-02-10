package com.xuesinuo.muppet.vertx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * Vertx配置
 * 
 * @author xuesinuo
 */
@Configuration
public class VertxConfiguration {
    @Bean
    Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    WebClient webClient(Vertx vertx) {
        return WebClient.create(vertx);
    }
}
