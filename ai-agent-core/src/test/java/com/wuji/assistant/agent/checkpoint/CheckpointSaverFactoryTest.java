package com.wuji.assistant.agent.checkpoint;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void schemaProbeSql_coversLowercaseTableAndIndex() {
        String sql = CheckpointSaverFactory.SCHEMA_PROBE_SQL;
        assertTrue(sql.contains("public.graphcheckpoint"));
        assertTrue(sql.contains("public.\"GraphCheckpoint\""));
        assertTrue(sql.contains("idx_lg4jcheckpoint_thread_id"));
    }

    @Test
    void createTables_defaultsToFalse() {
        assertFalse(new WujiAgentProperties().getCheckpoint().isCreateTables());
    }

    @Test
    void graphSerializer_isSpringAiJackson() {
        assertSame(StateGraph.DEFAULT_JACKSON_SERIALIZER, CheckpointSaverFactory.graphSerializer());
        assertInstanceOf(SpringAIJacksonStateSerializer.class, CheckpointSaverFactory.graphSerializer());
    }
}
