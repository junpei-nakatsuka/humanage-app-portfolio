package jp.co.example.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.example.entity.Contract;
import jp.co.example.entity.User;

@Configuration
public class RedisConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);	
	
	@Autowired
	private GenericConversionService genericConversionService;
	
	private final ObjectMapper objectMapper;
	
	public RedisConfig (ObjectMapper objectMapper) {
		logger.info("[INFO] 引数ありRedisConfigコンストラクタが呼ばれました。objectMapper: {}", objectMapper);
		this.objectMapper = objectMapper;
	}
	
	@Bean
    public RedisConnectionFactory redisConnectionFactory() throws URISyntaxException {
        logger.info("[INFO] RedisConfig.javaのredisConnectionFactoryが呼ばれました");

        try {
            String redisTlsUrl = System.getenv("REDIS_TLS_URL");
            if (redisTlsUrl == null) {
            	logger.error("[ERROR] REDIS_TLS_URL環境変数が設定されていません");
                throw new IllegalArgumentException("REDIS_TLS_URL環境変数が設定されていません。");
            }

            URI redisUri = new URI(redisTlsUrl); //ここでURISyntaxExceptionが発生する可能性がある
            String host = redisUri.getHost();
            int port = redisUri.getPort();
            String password = redisUri.getUserInfo() != null ? redisUri.getUserInfo().split(":", 2)[1] : null;
            
            logger.debug("[DEBUG] redisUri: {}, host: {}, port: {}, password: [REDACTED]", redisUri, host, port);
            
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(host);
            standaloneConfig.setPort(port);
            standaloneConfig.setPassword(password);
            
            logger.debug("[DEBUG] Redis接続設定: {}", standaloneConfig);

            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .useSsl()
                    .disablePeerVerification()
                    .build();

            LettuceConnectionFactory factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
            factory.setValidateConnection(true);
            
            logger.info("[INFO] RedisConnectionFactoryを正常に構築しました");
            return factory;

        } catch (URISyntaxException e) {
            logger.error("[ERROR] REDIS_TLS_URLの解析に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw e;
        } catch (IllegalArgumentException ex) {
            logger.error("[ERROR] 環境変数REDIS_TLS_URLが設定されていません: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw ex;
        } catch (RedisConnectionFailureException exx) {
            logger.error("[ERROR] Redisサーバーへの接続に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", exx.toString(), Arrays.toString(exx.getStackTrace()), exx.getCause() != null ? exx.getCause().toString() : "原因不明", exx.getLocalizedMessage());
            throw exx;
        } catch (Exception exxx) {
        	logger.error("[ERROR] RedisConnectionFactoryの構築中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", exxx.toString(), Arrays.toString(exxx.getStackTrace()), exxx.getCause() != null ? exxx.getCause().toString() : "原因不明", exxx.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + exxx.getMessage(), exxx);
        }
    }

	@Bean
	public RedisCustomConversions redisCustomConversions() {
	    logger.info("[INFO] RedisCustomConversionsの初期化開始");
	    try {
	        RedisCustomConversions conversions = new RedisCustomConversions(Arrays.asList(
	            new UserToByteArrayConverter(objectMapper),
	            new UserFromByteArrayConverter(objectMapper)
	        ));

	        logger.info("[INFO] RedisCustomConversionsの初期化が正常に完了しました");
	        return conversions;
	    } catch (Exception e) {
	        logger.error("[ERROR] RedisCustomConversionsの初期化中にエラーが発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("RedisCustomConversionsの初期化に失敗しました: " + e.getMessage(), e);
	    }
	}
	
	@Bean
    public RedisTemplate<String, User> userRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
		logger.info("[INFO] RedisConfig.javaのuserRedisTemplateが呼ばれました。RedisConnectionFactory: {}, genericConversionService: {}", redisConnectionFactory, genericConversionService);
		try {
	        logger.debug("[DEBUG] userRedisTemplateの初期化を開始しました。");
	        RedisTemplate<String, User> template = new RedisTemplate<>();
	        template.setConnectionFactory(redisConnectionFactory);
	        logger.debug("[DEBUG] RedisConnectionFactoryを設定しました。");

	        template.setKeySerializer(new StringRedisSerializer());
	        logger.debug("[DEBUG] StringRedisSerializerをキーシリアライザとして設定しました。");

	        template.setValueSerializer(new UserSerializer(genericConversionService)); 
	        logger.debug("[DEBUG] UserSerializerを値のシリアライザとして設定しました。ConversionService: {}", genericConversionService);
	        
	        template.setHashKeySerializer(new StringRedisSerializer());
	        logger.debug("[DEBUG] StringRedisSerializerをハッシュキーのシリアライザとして設定しました。");

	        template.setHashValueSerializer(new UserSerializer(genericConversionService));
	        logger.debug("[DEBUG] UserSerializerをハッシュ値のシリアライザとして設定しました。ConversionService: {}", genericConversionService);
	        
	        template.afterPropertiesSet();
	        logger.info("[INFO] userRedisTemplateの初期化が正常に完了しました。");
	        return template;
	    } catch (SerializationException e) {
            logger.error("[ERROR] 値のシリアライゼーションまたはデシリアライゼーション中にエラーが発生しました: {}", e.getMessage(), e);
            throw e;
        } catch (Exception ex) {
	        logger.error("[ERROR] userRedisTemplateの初期化中にエラーが発生しました: {}", ex.getMessage(), ex);
	        throw new RuntimeException("userRedisTemplateの初期化に失敗しました: " + ex.getMessage(), ex);
	    }
    }

    @Bean
    public RedisTemplate<String, Object> genericRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
    	logger.info("[INFO] RedisConfig.javaのgenericRedisTemplateが呼ばれました。RedisConnectionFactory: {}", redisConnectionFactory);
    	try {
            logger.debug("[DEBUG] genericRedisTemplateの初期化を開始しました。");
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(redisConnectionFactory);
            logger.debug("[DEBUG] RedisConnectionFactoryを設定しました。");

            template.setKeySerializer(new StringRedisSerializer());
            logger.debug("[DEBUG] StringRedisSerializerをキーシリアライザとして設定しました。");

            template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
            logger.debug("[DEBUG] GenericJackson2JsonRedisSerializerを値のシリアライザとして設定しました。ObjectMapper: {}", objectMapper);
            
            template.setHashKeySerializer(new StringRedisSerializer());
            logger.debug("[DEBUG] StringRedisSerializerをハッシュキーシリアライザとして設定しました。");
            
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
            logger.debug("[DEBUG] GenericJacson2JsonRedisSerializerをハッシュ値のシリアライザとして設定しました。ObjectMapper: {}", objectMapper);
                        
            template.afterPropertiesSet();
            logger.info("[INFO] genericRedisTemplateの初期化が正常に完了しました。");
            return template;
        } catch (SerializationException e) {
            logger.error("[ERROR] 値のシリアライゼーションまたはデシリアライゼーション中にエラーが発生しました: {}", e.getMessage(), e);
            throw e;
        } catch (Exception ex) {
            logger.error("[ERROR] genericRedisTemplateの初期化中にエラーが発生しました: {}", ex.getMessage(), ex);
            throw new RuntimeException("genericRedisTemplateの初期化に失敗しました: " + ex.getMessage(), ex);
        }
    }
    
    @Bean
    public RedisTemplate<String, Contract> contractRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Contract> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Contract.class));

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Contract.class));

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {

        logger.info("[INFO] RedisTemplateが呼ばれました。redisConnectionFactory: {}", redisConnectionFactory);
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        try {
            template.setConnectionFactory(redisConnectionFactory);

            // String型の値にはJSONシリアライザではなく、文字列シリアライザを使う
            template.setDefaultSerializer(new StringRedisSerializer());  // デフォルトのシリアライザ

            // キーとハッシュキーにはStringRedisSerializerを使用
            template.setKeySerializer(new StringRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());

            // 値にはObjectでも文字列として扱いたいのでStringRedisSerializerを設定
            template.setValueSerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new StringRedisSerializer());

            template.afterPropertiesSet();

            logger.info("[INFO] RedisTemplate作成完了");
            return template;
        } catch (Exception e) {
            logger.error("[ERROR] RedisTemplateの初期化中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("RedisTemplateの初期化に失敗しました: " + e.getMessage(), e);
        }
    }
}
