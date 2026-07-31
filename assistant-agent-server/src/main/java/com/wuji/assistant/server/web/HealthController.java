package com.wuji.assistant.server.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 健康检查接口。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 返回服务存活状态。
     *
     * @return status=UP
     */
    @GetMapping("/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP", "service", "assistant-agent-server"));
    }
}
