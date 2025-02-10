package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableRedisHttpSession
public class RedisHttpSessionConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(RedisHttpSessionConfig.class);
	
	public RedisHttpSessionConfig() {
		logger.info("[INFO] RedisHttpSessionConfig.javaの引数なしコンストラクタが呼ばれました。");
	}
}
