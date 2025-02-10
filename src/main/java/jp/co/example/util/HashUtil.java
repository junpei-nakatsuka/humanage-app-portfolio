package jp.co.example.util;

import java.util.Arrays;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashUtil {
	
	private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);
	
	public static String hashPassword(String password) {
	    try {
	        // すでにハッシュ化されたパスワードは再ハッシュ化しない
	        if (password != null && (password.startsWith("$2a$") || password.startsWith("$2b$"))) {
	            logger.debug("[DEBUG] すでにハッシュ化されたパスワードが渡されたため、再ハッシュ化は行いません。ハッシュ値: {}", password);
	            return password;  // ハッシュ化されているパスワードはそのまま返す
	        }

	        logger.debug("[DEBUG] パスワードをハッシュ化します。ハッシュ化前の入力されたパスワード: {}", password);
	        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
	        logger.debug("[DEBUG] パスワードのハッシュ化に成功しました。ハッシュ後のパスワード(ハッシュ値): {}", hashedPassword);
	        return hashedPassword;
	    } catch (Exception e) {
	        logger.error("[ERROR] パスワードのハッシュ化に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        throw new RuntimeException("パスワードのハッシュ化に失敗しました: " + e.getMessage(), e);
	    }
	}
    
	public static boolean checkPassword(String rawPassword, String hashedPassword) {
	    try {
	        logger.debug("[DEBUG] パスワードをチェックします: rawPassword=[****], hashedPassword=[****]");
	        logger.trace("[TRACE] パスワードをチェックします: rawPassword=[{}], hashedPassword=[{}]", rawPassword, hashedPassword);
	        
	        boolean result = BCrypt.checkpw(rawPassword, hashedPassword);
	        logger.trace("[TRACE] パスワード一致チェック結果: isMatch={}", result);
	        if (!result) {
	        	logger.debug("[DEBUG] 一致しなかったのでfalseを返します。");
	            return false;
	        }
	        return result;
	    } catch (Exception e) {
	        logger.error("[ERROR] パスワードのチェック中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        throw new RuntimeException("パスワードのチェック中にエラーが発生しました: " + e.getMessage(), e);
	    }
	}
}
