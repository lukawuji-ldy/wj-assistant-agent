package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 内置工具提供者（首期可为空）。
 *
 * @author liudy
 */
public interface BuiltinToolProvider {

    /**
     * @return 工具回调列表
     */
    List<ToolCallback> getTools();
}
