package com.wuji.assistant.agent.tool;

import java.util.List;

/**
 * 向 Registry 提供 ACTIVE MCP Server 端点（token 已解密）。
 * 零 ACTIVE 时实现方可回落 yml 单端点。
 *
 * @author liudy
 */
public interface McpServerEndpointSource {

    /**
     * @return ACTIVE 端点列表（可空则 Registry 用 yml）
     */
    List<McpServerEndpoint> listActiveEndpoints();
}
