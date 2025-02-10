package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.example.entity.User;

@Component
public class UserToByteArrayConverter implements Converter<User, byte[]> {
	
	private static final Logger logger = LoggerFactory.getLogger(UserToByteArrayConverter.class);
	
	private final ObjectMapper objectMapper;

	public UserToByteArrayConverter(ObjectMapper objectMapper) {
		logger.info("[INFO] 引数ありUserToByteArrayConverterコンストラクタが呼ばれました。objectMapper: {}", objectMapper);
	    this.objectMapper = objectMapper;
	}

	@Override
    public byte[] convert(User user) {
		
		String callerMethod = getCallerMethod();
        logger.info("[INFO] UserToByteArrayConverter.javaのconvert()が呼ばれました。user: {}, 呼び出し元: {}", user, callerMethod);
        try {
            logger.debug("[DEBUG] Userをbyte[]にシリアライズ中: {}", user);
            // ここでユーザーをシリアライズ
            byte [] serializedUser = objectMapper.writeValueAsBytes(user);
            
            logger.debug("[DEBUG] シリアライズ成功: {}", serializedUser);
            return serializedUser;
        } catch (JsonProcessingException e) {
            logger.error("[ERROR] Userオブジェクトからbyte[]への変換中にJsonProcessingExceptionが発生しました。User: {}, 呼び出し元: {}, Error: {}", user, callerMethod, e.getMessage(), e);
            throw new ConversionFailedException(
                TypeDescriptor.valueOf(User.class),
                TypeDescriptor.valueOf(byte[].class),
                user,
                e
            );
        } catch (Exception ex) {
            logger.error("[ERROR] ユーザーのシリアライズ中に予期しないエラーが発生しました。User: {}, 呼び出し元: {}, Error: {}", user, callerMethod, ex.getMessage(), ex);
            throw new RuntimeException("Userオブジェクトからbyte[]への変換中に予期しないエラーが発生しました: " 
                                        + "User ID: " + (user != null ? user.getId() : "N/A") 
                                        + ", Error: " + ex.getMessage(), ex);
        }
    }
	
	// 呼び出し元メソッドを取得するヘルパーメソッド
    private String getCallerMethod() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // スタックトレースの2番目の要素が呼び出し元メソッド
        if (stackTrace.length > 2) {
            StackTraceElement caller = stackTrace[2];
            return caller.getClassName() + "." + caller.getMethodName() + "@" + caller.getLineNumber();
        }
        return "不明な呼び出し元";
    }
}
