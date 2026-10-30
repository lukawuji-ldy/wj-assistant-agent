package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.common.token.OpenAiTokenCounters;
import com.wuji.assistant.common.token.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 启动时按主备 CHAT 行登记文本 token 词表。某一行失败只跳过该行。
 *
 * @author liudy
 */
@Component
public class ModelTokenRegistryBinder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelTokenRegistryBinder.class);

    private final LlmConfigRepository llmConfigRepository;
    private final WujiModelProperties modelProperties;

    public ModelTokenRegistryBinder(LlmConfigRepository llmConfigRepository,
                                    WujiModelProperties modelProperties) {
        this.llmConfigRepository = llmConfigRepository;
        this.modelProperties = modelProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String primaryId = modelProperties.getPrimaryConfigId();
        Set<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(primaryId)) {
            ids.add(primaryId.trim());
        }
        if (modelProperties.getFallbackConfigIds() != null) {
            for (String fallbackId : modelProperties.getFallbackConfigIds()) {
                if (StringUtils.hasText(fallbackId)) {
                    ids.add(fallbackId.trim());
                }
            }
        }
        boolean primaryRegistered = false;
        for (String configId : ids) {
            try {
                LlmConfigRecord row = llmConfigRepository.requireActive(configId, LlmConfigRecord.KIND_CHAT);
                OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select(
                        row.getModel(), row.getTokenEncoding());
                String tokenizerId = selection.encoding() + ":" + row.getModel();
                TokenEstimator.register(configId, tokenizerId, OpenAiTokenCounters.counter(selection.encoding()));
                if (configId.equals(primaryId == null ? null : primaryId.trim())) {
                    TokenEstimator.setPrimaryConfigId(configId);
                    primaryRegistered = true;
                }
                if (!selection.exactMatch()) {
                    log.info("token encoding uses ChatGPT default {} for configId={} model={} tokenEncoding={}",
                            selection.encoding(), configId, row.getModel(), row.getTokenEncoding());
                }
            } catch (Exception ex) {
                log.warn("token registry skipped configId={}: {}", configId, ex.toString());
            }
        }
        if (!primaryRegistered) {
            TokenEstimator.setPrimaryConfigId(null);
            log.warn("primary chat model unavailable, token count uses cl100k_base");
        }
    }
}
