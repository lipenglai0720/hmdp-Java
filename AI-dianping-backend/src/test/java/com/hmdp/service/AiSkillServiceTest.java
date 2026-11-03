package com.hmdp.service;

import com.hmdp.dto.AiSkillRunRequest;
import com.hmdp.dto.AiSkillRunResponse;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AiSkill;
import com.hmdp.entity.AiSkillRun;
import com.hmdp.mapper.AiSkillMapper;
import com.hmdp.mapper.AiSkillRunMapper;
import com.hmdp.service.impl.AiSkillServiceImpl;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSkillServiceTest {
    private AiSkillServiceImpl service;
    private AiSkillRunMapper runs;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ObjectProvider<OpenAiChatModel> models = mock(ObjectProvider.class);
        AiSkillMapper skills = mock(AiSkillMapper.class);
        runs = mock(AiSkillRunMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn(1L);
        AiSkill skill = new AiSkill().setCode("blog_draft").setName("探店笔记")
                .setEnabled(true).setPromptTemplate("介绍 ${keywords}");
        when(skills.selectList(any())).thenReturn(List.of(skill));
        service = new AiSkillServiceImpl(models);
        ReflectionTestUtils.setField(service, "baseMapper", skills);
        ReflectionTestUtils.setField(service, "aiSkillRunMapper", runs);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void fallbackAndUnsavedHistoryAreExposedToTheFrontend() {
        when(runs.insert(any(AiSkillRun.class))).thenThrow(new RuntimeException("database unavailable"));
        AiSkillRunRequest request = new AiSkillRunRequest();
        request.setInput(Map.of("keywords", "咖啡"));
        var result = service.runSkill("blog_draft", request);
        assertTrue(result.getSuccess());
        AiSkillRunResponse response = (AiSkillRunResponse) result.getData();
        assertTrue(response.getFallback());
        assertFalse(response.getHistorySaved());
        assertEquals(true, response.getOutput().get("fallback"));
        assertNotNull(response.getContent());
    }

    @Test
    void aHistoryDatabaseFailureIsNotReportedAsAnEmptyList() {
        when(runs.selectList(any())).thenThrow(new RuntimeException("database unavailable"));
        var result = service.queryMyRuns(null);
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("无法加载"));
    }

    @Test
    void rejectsEmptyOrOversizedInputBeforeModelInvocation() {
        AiSkillRunRequest request = new AiSkillRunRequest();
        assertFalse(service.runSkill("blog_draft", request).getSuccess());
        request.setInput(Map.of("keywords", "x".repeat(10001)));
        assertFalse(service.runSkill("blog_draft", request).getSuccess());
        verifyNoInteractions(runs);
    }

    @Test
    void ignoresAnUntrustedUserIdAndPersistsTheAuthenticatedOwner() {
        AiSkillRunRequest request = new AiSkillRunRequest();
        request.setInput(Map.of("keywords", "咖啡"));
        request.setUserId(999L);
        assertTrue(service.runSkill("blog_draft", request).getSuccess());
        var saved = org.mockito.ArgumentCaptor.forClass(AiSkillRun.class);
        verify(runs).insert(saved.capture());
        assertEquals(1L, saved.getValue().getUserId());
    }
}
