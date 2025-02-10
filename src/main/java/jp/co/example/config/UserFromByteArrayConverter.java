package jp.co.example.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import jp.co.example.entity.User;

@Component
public class UserFromByteArrayConverter implements Converter<byte[], User> {
	
	private static final Logger logger = LoggerFactory.getLogger(UserFromByteArrayConverter.class);
	
	private final ObjectMapper objectMapper;
	
	public UserFromByteArrayConverter (ObjectMapper objectMapper) {
		logger.info("[INFO] 引数ありUserFromByteArrayConverterコンストラクタが呼ばれました。objectMapper: {}", objectMapper);
		this.objectMapper = objectMapper;
	}
	
	@Override
    public User convert(byte[] source) {
		
		String callerMethod  = getCallerMethod();
        logger.info("[INFO] UserFromByteArrayConverter.javaのconvert()が呼ばれました。byte[]: {}, 呼び出し元: {}", Arrays.toString(source), callerMethod);
        try {
            logger.debug("[DEBUG] byte[]をuserにデシリアライズ中: byte[]長さ: {}", source.length);
            
            String sourceString = new String(source, StandardCharsets.UTF_8);
            logger.debug("[DEBUG] byte[]の内容: {}", sourceString);
            
            User user = objectMapper.readValue(source, User.class);
            logger.debug("[DEBUG] デシリアライズ成功: {}", user);
            return user;
        } catch (InvalidFormatException e) {
        	logger.error("[ERROR] byte[] をユーザーにデシリアライズする際に無効な形式が発生しました: {}, 呼び出し元: {}", e.getMessage(), callerMethod, e);
        	throw new RuntimeException("byte[] をユーザーにデシリアライズする際に無効な形式が発生しました: " + e.getMessage(), e);
        } catch (JsonProcessingException ex) {
            logger.error("[ERROR] byte[]からUserオブジェクトへの変換中にJsonProcessingExceptionが発生しました。bytes: {}, Error: {}, 呼び出し元: {}", Arrays.toString(source), ex.getMessage(), callerMethod, ex);
            throw new ConversionFailedException(
                    TypeDescriptor.valueOf(byte[].class),
                    TypeDescriptor.valueOf(User.class),
                    source,
                    ex
                );
        } catch (Exception exx) {
            logger.error("[ERROR] ユーザーのデシリアライズ中に予期しないエラーが発生しました: bytes: {}, Error: {}, 呼び出し元: {}", Arrays.toString(source), exx.getMessage(), callerMethod, exx);
            throw new RuntimeException("ユーザーのデシリアライズ中に予期しないエラーが発生しました: " + exx.getMessage(), exx);
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
