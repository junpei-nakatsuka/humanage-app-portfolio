package jp.co.example.config;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableCaching
public class AppConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    
	@Bean
    public ModelMapper modelMapper() {
        try {
            logger.debug("[DEBUG] AppConfig.javaのmodelMapperが呼ばれました。");
            return new ModelMapper();
        } catch (Exception e) {
            logger.error("[ERROR] ModelMapperの初期化中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ModelMapperの初期化中にエラーが発生しました: " + e.getMessage(), e);
        }
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
        try {
            logger.info("[INFO] AppConfig.javaのlettuceClientConfigurationBuilderCustomizerが呼ばれました。");
            return clientConfigurationBuilder -> {
                try {
                    if (clientConfigurationBuilder.build().isUseSsl()) {
                        clientConfigurationBuilder.useSsl().disablePeerVerification();
                    }
                } catch (Exception e) {
                    logger.error("[ERROR] LettuceクライアントのSSL設定の構成中にエラーが発生しました: {}", e.getMessage(), e);
                    throw new RuntimeException("LettuceクライアントのSSL設定の構成中にエラーが発生しました", e);
                }
            };
        } catch (Exception e) {
            logger.error("[ERROR] LettuceClientConfigurationBuilderCustomizerの初期化中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("LettuceClientConfigurationBuilderCustomizerの初期化中にエラーが発生しました", e);
        }
    }
}
