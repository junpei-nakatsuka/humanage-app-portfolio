package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);

    @Bean
    public RestTemplate restTemplate() {
        logger.info("[INFO] RestTemplateConfig.javaのrestTemplateが呼ばれました。");
        try {
            return new RestTemplate();
        } catch (Exception e) {
            logger.error("[ERROR] RestTemplateの作成中にエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("RestTemplateの作成に失敗しました: " + e.getMessage(), e);
        }
    }
}
