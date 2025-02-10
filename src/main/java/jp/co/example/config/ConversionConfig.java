package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.example.entity.User;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ConversionConfig {

    private static final Logger logger = LoggerFactory.getLogger(ConversionConfig.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    @Primary
    public Converter<User, byte[]> userToByteArrayConverterBean() {
    	logger.info("[INFO] ConversionConfig.javaにてuserToByteArrayConverterBeanが呼ばれました。");
        try {
            logger.info("[INFO] UserToByteArrayConverterを初期化しています。");
            return new UserToByteArrayConverter(objectMapper); //Userからbyte[]への変換
        } catch (Exception e) {
            logger.error("{ERROR} UserToByteArrayConverterの初期化に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("UserToByteArrayConverterの初期化に失敗しました: " + e.getMessage(), e);
        }
    }
    
	@Bean
	@Primary
	public Converter<byte[], User> byteArrayToUserConverterBean() {
		logger.info("[INFO] ConversionConfig.javaにてbyteArrayToUserConverterBeanが呼ばれました。");
		try {
			logger.info("[INFO] UserFromByteArrayConverterを初期化しています。");
			return new UserFromByteArrayConverter(objectMapper); //byte[]からUserへの変換
		} catch (Exception e) {
			logger.error("[ERROR] UserFromByteArrayConverterの初期化に失敗しました: {}", e.getMessage(), e);
			throw new RuntimeException("UserFromByteArrayConverterの初期化に失敗しました: " + e.getMessage(), e);
		}
	}

    @Bean
    @Primary
    public GenericConversionService genericConversionService(@Qualifier("userToByteArrayConverterBean") Converter<User, byte[]> userToByteArrayConverter,
    														 @Qualifier("byteArrayToUserConverterBean") Converter<byte[], User> byteArrayToUserConverter) {
    	logger.info("[INFO] ConversionConfig.javaにてgenericConversionServiceが呼ばれました。userToByteArrayConverter: {}, byteArrayToUserConverter: {}", userToByteArrayConverter, byteArrayToUserConverter);
        try {
            logger.info("[INFO] GenericConversionServiceの作成中");
            GenericConversionService genericConversionService = new GenericConversionService();
                        
            genericConversionService.addConverter(userToByteArrayConverter);  // User -> byte[]
            logger.info("[INFO] UserToByteArrayConverter を GenericConversionService に正常に登録しました。");
            
            genericConversionService.addConverter(byteArrayToUserConverter);  // byte[] -> User
            logger.info("[INFO] byteArrayToUserConverter を GenericConversionService に正常に登録しました。");
            
            logger.info("[INFO] カスタムコンバーターが GenericConversionService に正常に登録されました。");
            
            return genericConversionService;
        } catch (Exception e) {
            logger.error("[ERROR] GenericConversionServiceの作成に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("GenericConversionServiceの作成に失敗しました: " + e.getMessage(), e);
        }
    }
}
