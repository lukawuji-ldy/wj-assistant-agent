package com.wuji.assistant.agent.checkpoint;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * CheckpointSaverFactory 单元测试。
 *
 * @author liudy
 */
class CheckpointSaverFactoryTest {

    @Test
    void memoryType_returnsMemorySaver() {
        WujiAgentProperties props = new WujiAgentProperties();
        props.getCheckpoint().setType("memory");
        CheckpointSaverFactory factory = new CheckpointSaverFactory(props);
        assertInstanceOf(MemorySaver.class, factory.getSaver());
    }
}
