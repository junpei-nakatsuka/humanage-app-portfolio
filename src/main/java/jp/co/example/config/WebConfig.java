package jp.co.example.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);
	
	@Bean
    public LocaleResolver localeResolver() {
        try {
            logger.info("[INFO] WebConfig.javaのlocaleResolverが呼ばれました。");
            FixedLocaleResolver localeResolver = new FixedLocaleResolver();
            localeResolver.setDefaultLocale(Locale.JAPAN);
            logger.info("[INFO] デフォルトロケールが日本に設定されました。");
            return localeResolver;
        } catch (Exception e) {
            logger.error("[ERROR] localeResolverの設定中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("localeResolverの設定に失敗しました。", e);
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        try {
            logger.info("[INFO] WebConfig.javaのaddCorsMappingsが呼ばれました。registry: {}", registry);
            registry.addMapping("/**")
                    .allowedOrigins(
                        "https://humanage-app-1fe93ce442da.herokuapp.com",
                        "https://humanage-app-1fe93ce442da.herokuapp.com:5000",
                        "https://smfpqnakjetusngjujyy.supabase.co",
                        "http://localhost:5000"
                    )
                    .allowedMethods("HEAD", "GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("X-Auth-Token", "Authorization", "Content-Type", "Accept", "X-Debug-Info", "X-Debug-Status")
                    .allowCredentials(true)
                    .maxAge(3600);
            logger.info("[INFO] CORS設定が正常に完了しました。registy: {}", registry);
        } catch (Exception e) {
            logger.error("[ERROR] CORS設定中にエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("CORS設定に失敗しました。", e);
        }
    }

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		try {
			logger.info("[INFO] WebConfig.javaのconfigurePathMatchが呼ばれました: {}", configurer);
			configurer.setPatternParser(new PathPatternParser());
			logger.info("[INFO] PathPatternParserが正常に設定されました。");
		} catch (Exception e) {
			logger.error("[ERROR] PathMatch設定中にエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
			throw new RuntimeException("PathMatch設定に失敗しました。", e);
		}
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		logger.info("[INFO] addResourceHandlersが呼ばれました: {}", registry);
		try {
			registry.addResourceHandler("/static/**", "/api/generatePDF/node/**")
					.addResourceLocations("classpath:/static/")
					.setCachePeriod(0);
		} catch (Exception e) {
			logger.error("[ERROR] 予期しないエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
			throw new RuntimeException("予期しないエラーが発生しました: " + e.getMessage(), e);
		}
	}
}
