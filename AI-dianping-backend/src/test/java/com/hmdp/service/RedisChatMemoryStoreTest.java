package com.hmdp.service;

import com.hmdp.utils.RedisChatMemoryStore;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisChatMemoryStoreTest {
    @SuppressWarnings("unchecked")
    @Test
    void aNewConversationCanWriteItsFirstMessage() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String key = "chat:memory:7:session";
        when(values.get(key)).thenReturn(null);
        var memory = MessageWindowChatMemory.builder().id(key).maxMessages(20)
                .chatMemoryStore(new RedisChatMemoryStore(redis)).build();
        memory.add(UserMessage.from("hello"));
        verify(values).set(eq(key), contains("hello"), eq(1L), eq(TimeUnit.DAYS));
    }
}
