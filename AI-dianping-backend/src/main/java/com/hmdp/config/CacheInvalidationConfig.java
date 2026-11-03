package com.hmdp.config;

import com.hmdp.utils.CacheClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.nio.charset.StandardCharsets;

@Configuration
public class CacheInvalidationConfig {
    @Bean
    public RedisMessageListenerContainer cacheInvalidationListener(RedisConnectionFactory connectionFactory, CacheClient cache) {
        RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(connectionFactory);
        listener.addMessageListener((message, pattern) -> cache.invalidateLocal(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(CacheClient.INVALIDATION_CHANNEL));
        return listener;
    }
}
