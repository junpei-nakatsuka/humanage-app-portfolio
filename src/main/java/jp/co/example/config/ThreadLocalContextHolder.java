package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLocalContextHolder {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreadLocalContextHolder.class);
    
    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isEvictingCache = ThreadLocal.withInitial(() -> false);

    public static boolean isAlreadyProcessing() {
        logger.info("[INFO] ThreadLocalContextHolder.javaのisAlreadyProcessingが呼ばれました。");
        try {
            boolean result = isProcessing.get();
            logger.debug("[DEBUG] isAlreadyProcessingの結果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] isAlreadyProcessingメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのisAlreadyProcessingでエラーが発生しました", e);
        }
    }

    public static void setProcessing(boolean processing) {
        logger.info("[INFO] ThreadLocalContextHolder.javaのsetProcessingが呼ばれました。boolean: {}", processing);
        try {
            isProcessing.set(processing);
            logger.debug("[DEBUG] setProcessingで値が設定されました: {}", processing);
        } catch (Exception e) {
            logger.error("[ERROR] setProcessingメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのsetProcessingでエラーが発生しました", e);
        }
    }

    public static void clear() {
        logger.info("[INFO] ThreadLocalContextHolder.javaのclearが呼ばれました。");
        try {
            isProcessing.remove();
            logger.debug("[DEBUG] clearでisProcessingが削除されました。");
        } catch (Exception e) {
            logger.error("[ERROR] clearメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのclearでエラーが発生しました", e);
        }
    }
    
    public static boolean isEvictingCache() {
        logger.info("[INFO] ThreadLocalContextHolder.javaのisEvictingCacheが呼ばれました。");
        try {
            boolean result = isEvictingCache.get();
            logger.debug("[DEBUG] isEvictingCacheの結果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] isEvictingCacheメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのisEvictingCacheでエラーが発生しました", e);
        }
    }

    public static void setEvictingCache(boolean value) {
        logger.info("[INFO] ThreadLocalContextHolder.javaのsetEvictingCacheが呼ばれました。boolean: {}", value);
        try {
            isEvictingCache.set(value);
            logger.debug("[DEBUG] setEvictingCacheで値が設定されました: {}", value);
        } catch (Exception e) {
            logger.error("[ERROR] setEvictingCacheメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのsetEvictingCacheでエラーが発生しました", e);
        }
    }

    public static void clearEvictingCache() {
        logger.info("[INFO] ThreadLocalContextHolder.javaのclearEvictingCacheが呼ばれました。");
        try {
            isEvictingCache.remove();
            logger.debug("[DEBUG] clearEvictingCacheでisEvictingCacheが削除されました。");
        } catch (Exception e) {
            logger.error("[ERROR] clearEvictingCacheメソッドでエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ThreadLocalContextHolderのclearEvictingCacheでエラーが発生しました", e);
        }
    }
}
