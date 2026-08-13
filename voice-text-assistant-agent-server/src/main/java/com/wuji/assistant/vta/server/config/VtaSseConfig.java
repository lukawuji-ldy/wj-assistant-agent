package com.wuji.assistant.vta.server.config;

import com.wuji.assistant.vta.server.sse.VtaStreamSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@Configuration
public class VtaSseConfig {

    @Bean
    public VtaStreamSessionRegistry vtaStreamSessionRegistry(
            @Value("${wuji.vta.sse.resume-ttl:10m}") Duration ttl,
            @Value("${wuji.vta.sse.resume-buffer-events:200}") int maxBufferEvents
    ) {
        return new VtaStreamSessionRegistry(ttl, maxBufferEvents);
    }
}

