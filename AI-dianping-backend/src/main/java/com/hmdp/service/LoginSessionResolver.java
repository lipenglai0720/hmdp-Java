package com.hmdp.service;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** HTTP 和 MCP 共用登录校验，身份只来自服务端保存的会话。 */
@Component
@RequiredArgsConstructor
public class LoginSessionResolver {
    private final StringRedisTemplate redis;
    public UserDTO resolve(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String token = authorization.trim().replaceFirst("(?i)^Bearer\\s+", "");
        if (!token.matches("[A-Za-z0-9_-]{16,128}")) return null;
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object,Object> fields = redis.opsForHash().entries(key);
        if (fields.isEmpty()) return null;
        UserDTO user = BeanUtil.fillBeanWithMap(fields, new UserDTO(), false);
        if (user.getId() == null || user.getId() <= 0) return null;
        redis.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return user;
    }
}
