package com.hmdp.service;

import com.google.common.hash.*;
import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CacheClientTest {
    CacheClient cache; StringRedisTemplate redis; ValueOperations<String,String> values;
    RedissonClient redisson; BloomFilter<Long> bloom;
    @BeforeEach @SuppressWarnings("unchecked") void init() {
        redis=mock(StringRedisTemplate.class);values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
        redisson=mock(RedissonClient.class);RLock lock=mock(RLock.class);when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);
        bloom=BloomFilter.create(Funnels.longFunnel(),100,0.001);bloom.put(1L);
        cache=new CacheClient(redis);ReflectionTestUtils.setField(cache,"bloomFilter",bloom);ReflectionTestUtils.setField(cache,"redissonClient",redisson);
    }
    Shop query(Long id,Function<Long,Shop> fallback) {return cache.queryWithPassThroughAndMutex("cache:shop:","lock:shop:",id,Shop.class,fallback,30L,TimeUnit.MINUTES,2L,TimeUnit.MINUTES);}
    @Test void bloomFilterRejectsUnknownIdsWithoutRedisOrDatabaseReads() {
        assertNull(query(999L,id->{throw new AssertionError("database must not run");}));verifyNoInteractions(redis,redisson);
    }
    @Test void doubleCheckAfterLockAvoidsAnUnnecessaryDatabaseQuery() {
        when(values.get("cache:shop:1")).thenReturn(null,"{\"id\":1,\"name\":\"shop\"}");
        assertEquals("shop",query(1L,id->{throw new AssertionError("database must not run");}).getName());
    }
    @Test void localCacheDoesNotOutliveRedisExpiry() throws Exception {
        when(values.get("key")).thenReturn("first","second");when(redis.getExpire("key",TimeUnit.MILLISECONDS)).thenReturn(1L);
        assertEquals("first",cache.get("key",30L,TimeUnit.MINUTES));Thread.sleep(30);
        assertEquals("second",cache.get("key",30L,TimeUnit.MINUTES));
    }
}
