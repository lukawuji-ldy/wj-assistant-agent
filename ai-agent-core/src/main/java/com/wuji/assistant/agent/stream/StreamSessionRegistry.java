package com.wuji.assistant.agent.stream;

import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.common.util.IdGenerator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 流会话注册表：登记、查找、TTL 清理。
 *
 * @author liudy
 */
@Component
public class StreamSessionRegistry {

    private final ConcurrentHashMap<String, StreamSession> sessions = new ConcurrentHashMap<>();
    private final WujiAgentProperties agentProperties;

    public StreamSessionRegistry(WujiAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 创建并登记新流会话。
     *
     * @param userId 用户
     * @return 会话
     */
    public StreamSession create(String userId) {
        purgeExpired();
        String streamId = IdGenerator.nextBizId("s_");
        int max = agentProperties.getStream().getResumeBufferEvents();
        StreamSession session = new StreamSession(streamId, userId, max);
        sessions.put(streamId, session);
        return session;
    }

    /**
     * 使用指定 streamId 登记（与 prepare 生成的 id 对齐时可用）。
     *
     * @param streamId 流 id
     * @param userId   用户
     * @return 会话
     */
    public StreamSession register(String streamId, String userId) {
        purgeExpired();
        int max = agentProperties.getStream().getResumeBufferEvents();
        StreamSession session = new StreamSession(streamId, userId, max);
        sessions.put(streamId, session);
        return session;
    }

    /**
     * 查找未过期且归属正确的会话。
     *
     * @param streamId 流 id
     * @param userId   用户
     * @return Optional
     */
    public Optional<StreamSession> findActive(String streamId, String userId) {
        purgeExpired();
        if (streamId == null || userId == null) {
            return Optional.empty();
        }
        StreamSession session = sessions.get(streamId);
        if (session == null) {
            return Optional.empty();
        }
        Duration ttl = agentProperties.getStream().getResumeTtl();
        if (session.isExpired(ttl) || !userId.equals(session.getUserId())) {
            sessions.remove(streamId, session);
            return Optional.empty();
        }
        session.touch();
        return Optional.of(session);
    }

    /**
     * 移除会话。
     *
     * @param streamId 流 id
     */
    public void remove(String streamId) {
        if (streamId != null) {
            sessions.remove(streamId);
        }
    }

    /**
     * 清理过期会话。
     */
    public void purgeExpired() {
        Duration ttl = agentProperties.getStream().getResumeTtl();
        Iterator<Map.Entry<String, StreamSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StreamSession> e = it.next();
            if (e.getValue().isExpired(ttl)) {
                it.remove();
            }
        }
    }

    /**
     * 当前登记数量（测试用）。
     *
     * @return size
     */
    public int size() {
        return sessions.size();
    }
}
