package jp.co.example.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;


@Component
public class CacheInitializer implements CommandLineRunner {
	
	private static final Logger logger = LoggerFactory.getLogger(CacheInitializer.class);

    @Autowired
    private CacheManager cacheManager;

    @Override
    public void run(String... args) {
    	logger.info("[INFO] CacheInitializerが呼ばれました");
        try {
            // キャッシュ名のリスト
            String[] cacheNames = {"attendanceCache", "salaryCache", "userCache"};

            for (String cacheName : cacheNames) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    logger.info("[INFO] {} キャッシュをクリアしています...", cacheName);
                    cache.clear();
                    logger.info("[INFO] {} キャッシュをクリアしました。", cacheName);
                } else {
                    logger.warn("[WARN] {} キャッシュが見つかりませんでした。", cacheName);
                }
            }
        } catch (Exception e) {
            logger.error("[ERROR] キャッシュのクリア中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("キャッシュのクリアに失敗しました: " + e.toString(), e);
        }
    }
}
