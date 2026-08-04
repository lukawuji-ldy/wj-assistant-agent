package com.wuji.assistant.memory.retrieve;

import com.wuji.assistant.memory.model.UserProfileMemory;
import com.wuji.assistant.memory.model.UserSemanticHit;
import com.wuji.assistant.memory.repo.UserProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LongTermMemoryRetriever：排序、格式化、触达、语义合并。
 *
 * @author liudy
 */
class LongTermMemoryRetrieverTest {

    @Test
    void formatBlock_containsKeys() {
        UserProfileMemory color = row("preference.favorite_color", "PREFERENCE", "蓝色", 0.9f, 0.8f);
        String block = LongTermMemoryRetriever.formatBlock(List.of(color));
        assertTrue(block.contains("preference.favorite_color=蓝色"));
        assertTrue(block.contains("已知用户长期记忆"));
    }

    @Test
    void score_prefersHigherImportance() {
        MemoryRetrieveOptions opt = new MemoryRetrieveOptions(5, 0, 0.2, 0.2, 0.6, true, 4, 0.55);
        Instant now = Instant.now();
        UserProfileMemory low = row("a", "PREFERENCE", "x", 0.9f, 0.1f);
        UserProfileMemory high = row("b", "PREFERENCE", "y", 0.9f, 0.9f);
        low.setUpdateTime(now);
        high.setUpdateTime(now);
        assertTrue(LongTermMemoryRetriever.score(high, opt, now)
                > LongTermMemoryRetriever.score(low, opt, now));
    }

    @Test
    void retrieveBlock_topKAndTouch() {
        AtomicReference<List<String>> touched = new AtomicReference<>(List.of());
        UserProfileRepository fakeRepo = new UserProfileRepository(null) {
            @Override
            public List<UserProfileMemory> listActive(String userId, Set<String> types) {
                UserProfileMemory low = row("preference.food", "PREFERENCE", "面", 0.5f, 0.2f);
                UserProfileMemory high = row("preference.favorite_color", "PREFERENCE", "蓝色", 0.9f, 0.9f);
                Instant now = Instant.now();
                low.setUpdateTime(now);
                high.setUpdateTime(now);
                return List.of(low, high);
            }

            @Override
            public int touchLastUsed(String userId, java.util.Collection<String> memoryKeys) {
                touched.set(List.copyOf(memoryKeys));
                return memoryKeys.size();
            }
        };
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(new MemoryRouter(), fakeRepo, null);
        String block = retriever.retrieveBlock("u1", "我喜欢什么颜色",
                new MemoryRetrieveOptions(1, 0, 0.2, 0.2, 0.6, true, 4, 0.55));
        assertTrue(block.contains("preference.favorite_color=蓝色"));
        assertEquals(1, touched.get().size());
        assertEquals("preference.favorite_color", touched.get().get(0));
    }

    @Test
    void retrieveBlock_colorQuery_prefersFavoriteColorOverHobbies() {
        AtomicReference<List<String>> touched = new AtomicReference<>(List.of());
        UserProfileRepository fakeRepo = new UserProfileRepository(null) {
            @Override
            public List<UserProfileMemory> listActive(String userId, Set<String> types) {
                Instant now = Instant.now();
                UserProfileMemory color = row("preference.favorite_color", "PREFERENCE", "蓝色", 0.95f, 0.40f);
                UserProfileMemory food = row("preference.food", "PREFERENCE", "喜欢吃山西刀削面", 0.90f, 0.50f);
                UserProfileMemory hobby = row("preference.hobby", "PREFERENCE", "喜欢下象棋", 0.95f, 0.60f);
                UserProfileMemory sport = row("preference.sport", "PREFERENCE", "喜欢踢足球", 0.90f, 0.60f);
                UserProfileMemory badminton = row("preference.hobby.badminton", "PREFERENCE", "喜欢打羽毛球", 0.95f, 0.60f);
                UserProfileMemory pingpong = row("preference.hobby.table_tennis", "PREFERENCE", "喜欢乒乓球", 0.90f, 0.60f);
                UserProfileMemory junk = row("preference.note", "PREFERENCE", "什么颜色", 0.90f, 0.80f);
                for (UserProfileMemory m : List.of(color, food, hobby, sport, badminton, pingpong, junk)) {
                    m.setUpdateTime(now);
                }
                return List.of(color, food, hobby, sport, badminton, pingpong, junk);
            }

            @Override
            public int touchLastUsed(String userId, java.util.Collection<String> memoryKeys) {
                touched.set(List.copyOf(memoryKeys));
                return memoryKeys.size();
            }
        };
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(new MemoryRouter(), fakeRepo, null);
        String block = retriever.retrieveBlock("u1", "我喜欢什么颜色",
                new MemoryRetrieveOptions(5, 0.5, 0.2, 0.2, 0.2, true, 4, 0.55));
        assertTrue(block.contains("preference.favorite_color=蓝色"), block);
        assertTrue(!block.contains("preference.note=什么颜色"), block);
        assertTrue(touched.get().contains("preference.favorite_color"));
    }

    @Test
    void retrieveBlock_mergesSemanticWhenPortPresent() {
        UserProfileRepository fakeRepo = new UserProfileRepository(null) {
            @Override
            public List<UserProfileMemory> listActive(String userId, Set<String> types) {
                return List.of();
            }
        };
        SemanticMemorySearchPort semantic = (userId, query, topK, minScore) -> {
            UserSemanticHit hit = new UserSemanticHit();
            hit.setId("s1");
            hit.setContent("曾在山西大同长大");
            hit.setConfidence(0.9f);
            hit.setImportance(0.8f);
            hit.setScore(0.9);
            hit.setUpdateTime(Instant.now());
            return List.of(hit);
        };
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(new MemoryRouter(), fakeRepo, semantic);
        String block = retriever.retrieveBlock("u1", "你还记得我的经历吗",
                MemoryRetrieveOptions.defaults());
        assertTrue(block.contains("用户语义记忆"), block);
        assertTrue(block.contains("曾在山西大同长大"), block);
        assertFalse(block.contains("已知用户长期记忆"), block);
    }

    @Test
    void retrieveBlock_semanticPortNull_skipsSemanticOnly() {
        UserProfileRepository fakeRepo = new UserProfileRepository(null) {
            @Override
            public List<UserProfileMemory> listActive(String userId, Set<String> types) {
                return List.of();
            }
        };
        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(new MemoryRouter(), fakeRepo, null);
        String block = retriever.retrieveBlock("u1", "你还记得我说过什么吗",
                MemoryRetrieveOptions.defaults());
        assertEquals("", block);
    }

    @Test
    void queryRelevance_colorBoostsFavoriteColor() {
        UserProfileMemory color = row("preference.favorite_color", "PREFERENCE", "蓝色", 0.9f, 0.4f);
        UserProfileMemory hobby = row("preference.hobby", "PREFERENCE", "喜欢下象棋", 0.9f, 0.9f);
        assertTrue(LongTermMemoryRetriever.queryRelevance(color, "我喜欢什么颜色")
                > LongTermMemoryRetriever.queryRelevance(hobby, "我喜欢什么颜色"));
    }

    @Test
    void isQuestionEchoJunk_filtersNote() {
        assertTrue(LongTermMemoryRetriever.isQuestionEchoJunk(
                row("preference.note", "PREFERENCE", "什么颜色", 0.9f, 0.8f)));
        assertTrue(!LongTermMemoryRetriever.isQuestionEchoJunk(
                row("preference.favorite_color", "PREFERENCE", "蓝色", 0.9f, 0.4f)));
    }

    private static UserProfileMemory row(String key, String type, String value,
                                         float confidence, float importance) {
        UserProfileMemory m = new UserProfileMemory();
        m.setMemoryKey(key);
        m.setMemoryType(type);
        m.setMemoryValue(value);
        m.setConfidence(confidence);
        m.setImportance(importance);
        return m;
    }
}
