package jp.co.example.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class CacheConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

	@Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        try {
            logger.info("[INFO] CacheConfig.javaのcacheManagerが呼ばれました。", redisConnectionFactory);

            // RedisCacheManagerを使用してRedisのキャッシュ管理を行う
            RedisCacheManager cacheManager = RedisCacheManager.builder(redisConnectionFactory)
                .initialCacheNames(Set.of("attendanceCache", "salaryCache")) // 使用するキャッシュ名を指定
                .build();

            return cacheManager;
        } catch (Exception e) {
            logger.error("[ERROR] CacheManagerの初期化中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("CacheManagerの初期化に失敗しました。", e);
        }
    }
}
