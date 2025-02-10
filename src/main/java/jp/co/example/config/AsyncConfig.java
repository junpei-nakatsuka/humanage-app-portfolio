package jp.co.example.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

	@Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        try {
            logger.info("[INFO] AsyncConfig.javaのtaskExecutorが呼ばれました");
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(5);
            executor.setMaxPoolSize(10);
            executor.setQueueCapacity(500);
            executor.setThreadNamePrefix("PDFGenerator-");
            executor.initialize();
            logger.info("[INFO] ThreadPoolTaskExecutorが正常に初期化されました。");
            return executor;
        } catch (Exception e) {
            logger.error("[ERROR] ThreadPoolTask​​Executorの初期化エラー: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadPoolTask​​Executorの初期化エラー", e);
        }
    }
}
