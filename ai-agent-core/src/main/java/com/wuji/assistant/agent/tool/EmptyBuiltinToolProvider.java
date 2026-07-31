package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置工具空壳：首期无副作用工具。
 *
 * @author liudy
 */
@Component
public class EmptyBuiltinToolProvider implements BuiltinToolProvider {

    @Override
    public List<ToolCallback> getTools() {
        return List.of();
    }
}
