package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import jp.co.example.entity.User;

@Component
public class UserSerializer implements RedisSerializer<User> {
	
	private static final Logger logger = LoggerFactory.getLogger(UserSerializer.class);
	
	private final GenericConversionService genericConversionService;

	public UserSerializer(@Qualifier("genericConversionService") GenericConversionService genericConversionService) {
		logger.info("[INFO] UserSerializer.javaの引数ありコンストラクタにてConversionServiceをインジェクションしています。GenericConversionService: {}", genericConversionService);
        this.genericConversionService = genericConversionService;
    }
    
    @Override
    public byte[] serialize(User user) throws SerializationException {
    	StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
    	logger.info("[INFO] UserSerializer.javaのserializeが呼ばれました。User: {}, 呼び出し元: {}", user, callingMethodName);
        if (user == null) {
            logger.warn("[WARN] Userオブジェクトがnullです。nullを返します。");
            return null;
        }
        
        user.setAttendances(null);
        user.setSalaries(null); 
        user.setDepartment(null);
        user.setContracts(null);
        
        logger.debug("[DEBUG] Userオブジェクトのシリアライズを開始します。User: {}", user);
        try {
            byte[] result = genericConversionService.convert(user, byte[].class);  // User -> byte[]
            logger.info("[INFO] Userオブジェクトのシリアライズが正常に完了しました。");
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] Userオブジェクトのシリアライズに失敗しました。User: {}, ByteClass: {}, Error: {}", user, byte[].class, e.getMessage(), e);
            throw new SerializationException("Failed to serialize User: " + e.getMessage(), e);
        }
    }

    @Override
    public User deserialize(byte[] bytes) throws SerializationException {
    	StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
    	logger.info("[INFO] UserSerializer.javaのdeserializeが呼ばれました。Byte: {}, 呼び出し元: {}", bytes, callingMethodName);
        if (bytes == null || bytes.length == 0) {
            logger.warn("[WARN] バイト配列がnullまたは空です。nullを返します。");
            return null;
        }
        
        logger.debug("[DEBUG] バイト配列からUserオブジェクトのデシリアライズを開始します。bytesの長さ: {}", bytes.length);
        try {
            User result = genericConversionService.convert(bytes, User.class);  // byte[] -> User
            logger.info("[INFO] バイト配列からUserオブジェクトのデシリアライズが正常に完了しました。");
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] バイト配列からUserオブジェクトのデシリアライズに失敗しました。bytesの長さ: {}, UserClass: {}, error: {}", bytes.length, User.class, e.getMessage(), e);
            throw new SerializationException("Failed to deserialize User: " + e.getMessage(), e);
        }
    }
}

