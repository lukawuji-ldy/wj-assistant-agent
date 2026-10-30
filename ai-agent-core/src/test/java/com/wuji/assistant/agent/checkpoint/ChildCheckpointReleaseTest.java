package com.wuji.assistant.agent.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 子线程尚未落 checkpoint 时，release 的失败留在 Saver 里。
 *
 * @author liudy
 */
class ChildCheckpointReleaseTest {

    @Test
    void missingThread_isSwallowed() throws Exception {
        BaseCheckpointSaver saver = mock(BaseCheckpointSaver.class);
        RunnableConfig config = RunnableConfig.builder().threadId("kb:missing").build();
        doThrow(new IllegalStateException("active Thread 'kb:missing' not found"))
                .when(saver).release(config);
        ChildCheckpointRelease.releaseQuietly(saver, config);
        verify(saver).release(config);
    }
}
