package com.wuji.assistant.memory.retrieve;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MemoryRouter 规则分支。
 *
 * @author liudy
 */
class MemoryRouterTest {

    private final MemoryRouter router = new MemoryRouter();

    @Test
    void route_favoriteColor_needsPreference() {
        MemoryRouteDecision d = router.route("我喜欢什么颜色");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("PREFERENCE"));
    }

    @Test
    void route_whoAmI_needsProfile() {
        MemoryRouteDecision d = router.route("我是谁");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("PROFILE"));
    }

    @Test
    void route_redis_skips() {
        MemoryRouteDecision d = router.route("什么是 Redis");
        assertFalse(d.needMemory());
        assertEquals(Set.of(), d.memoryTypes());
    }

    @Test
    void route_rememberExperience_needsSemantic() {
        MemoryRouteDecision d = router.route("你还记得我说过的经历吗");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("SEMANTIC"));
    }

    @Test
    void route_blank_skips() {
        assertFalse(router.route("").needMemory());
        assertFalse(router.route(null).needMemory());
    }
}
