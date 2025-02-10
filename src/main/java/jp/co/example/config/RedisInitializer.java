package jp.co.example.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jp.co.example.entity.User;
import jp.co.example.service.UserService;

@Component
public class RedisInitializer {

    private static final Logger logger = LoggerFactory.getLogger(RedisInitializer.class);
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @PostConstruct
    public void initialize() {
    	logger.info("[INFO] RedisInitializer.javaのinitializeが呼ばれました。Redisの初期化とSupabaseデータの同期を開始します...");
    	try {
    		retryWithDelay(this::validateRedisConnection, "Redis接続検証");
            retryWithDelay(this::clearRedisCache, "Redisキャッシュのクリア");
            testSupabaseConnection();
            validateSupabaseConnection();
			syncSupabaseToRedis();
		} catch (Exception e) {
			logger.error("[FATAL] Redisの初期化中に致命的なエラーが発生しました: {}", e.getMessage(), e);
			throw new RuntimeException("Redisの初期化に失敗しました:" + e.getMessage(), e);
		}
    }
    
    private void validateSupabaseConnection() {
        logger.info("[INFO] RedisInitializer.javaのvalidateSupabaseConnectionが呼ばれました。Supabase接続を検証中...");
        
        String supabaseUrl = System.getenv("SUPABASE_URL");
        String databaseUrl = System.getenv("DATABASE_URL");
        String databaseUserName = System.getenv("DATABASE_USERNAME");
        String supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY");
        
        if (supabaseUrl == null || databaseUrl == null || databaseUserName == null || supabaseAnonKey == null) {
        	logger.error("[ERROR] supabase接続に必要な環境変数が適切に設定されていません");
        	throw new IllegalArgumentException("supabase接続に必要な環境変数が適切に設定されていません");
        }
        
        try {
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                logger.warn("[WARN] Supabaseにはユーザーデータが存在しません。");
            } else {
                logger.info("[INFO] Supabaseから{}件のユーザーデータを取得しました。", users.size());
            }
        } catch (IllegalArgumentException e) {
        	logger.error("[ERROR] supabase接続に必要な環境変数が適切に設定されていません: {}", e.getMessage(), e);
        	throw e;
        } catch (Exception ex) {
            logger.error("[ERROR] Supabase接続中にエラーが発生しました: {}", ex.getMessage(), ex);
            throw new RuntimeException("Supabase接続エラー: " + ex.getMessage(), ex);
        }
    }
    
    private void testSupabaseConnection() {
    	logger.info("[INFO] RedisInitializer.javaのtestSupabaseConnectionが呼ばれました。");
        try {
        	logger.debug("[DEBUG] テストでgetAllUsersを呼びます。");
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                logger.warn("[WARN] Supabaseにはユーザーデータが存在しません。接続に問題があるかもしれません。");
            } else {
                logger.info("[INFO] Supabaseからユーザーデータを正常に取得しました。");
            }
        } catch (Exception e) {
            logger.error("[ERROR] Supabase接続に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("Supabase接続失敗: " + e.getMessage(), e);
        }
    }

    private void syncSupabaseToRedis() {
        logger.info("[INFO] RedisInitializer.javaのsyncSupabaseToRedisが呼ばれました。SupabaseデータをRedisに同期中...");
        try {
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                logger.warn("[WARN] Supabaseにはユーザーデータが存在しません。同期処理をスキップします。");
                return;
            }

            for (User user : users) {
            	logger.debug("[DEBUG] ユーザー: {} をRedisに保存します...", user.getUsername());
                saveUserToRedis(user);
            }
            logger.info("[INFO] SupabaseからRedisに{}件のユーザーデータを同期しました。", users.size());
        } catch (RuntimeException e) {
            logger.error("[ERROR] Supabaseデータ同期中にランタイム例外が発生しました: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("[ERROR] Supabaseデータ同期中に予期しないエラーが発生しました: {}", e.getMessage(), e);
        }
    }

    private void saveUserToRedis(User user) {
    	if (user == null) {
    		logger.error("[ERROR] 保存するユーザーデータがnullです。");
    	    throw new IllegalArgumentException("保存するユーザーデータがnullです。");
    	}
    	
        logger.info("[INFO] RedisInitializer.javaのsaveUserToRedisが呼ばれました。ユーザーデータをRedisに保存中: {}", user.getUsername());
        try {
        	String redisKey = "user:" + user.getUsername();
            String userJson = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(redisKey, userJson);
            logger.debug("[DEBUG] ユーザーデータをRedisに保存しました: {}", user.getUsername());
        } catch (IllegalArgumentException e) {
            logger.error("[ERROR] 無効なデータ形式のためユーザーデータを保存できませんでした: {}, Error: {}", user.getUsername(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("[ERROR] ユーザーデータ保存中に予期しないエラーが発生しました: {}", e.getMessage(), e);
        }
    }

    private void validateRedisConnection() {
        logger.info("[INFO] RedisInitializer.javaのvalidateRedisConnectionが呼ばれました。Redis接続検証を開始します...");
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            if (connection == null) {
            	logger.error("[ERROR] Redis接続がnullです。設定を確認してください。");
                throw new RuntimeException("Redis接続がnullです。設定を確認してください。");
            }
            String pingResponse = connection.ping();
            if (!"PONG".equalsIgnoreCase(pingResponse)) {
            	logger.error("[ERROR] Redis接続検証失敗。Ping応答が不正: {}", pingResponse);
                throw new RuntimeException("Redis接続検証失敗: PING応答が不正 (" + pingResponse + ")");
            }
            logger.info("[INFO] Redis接続は正常です。");
        } catch (RuntimeException e) {
            logger.error("[ERROR] Redis接続検証中にランタイム例外が発生しました: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("[ERROR] Redis接続検証中に予期しないエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("Redis接続検証中にエラーが発生しました: " + e.getMessage(), e);
        }
    }

    private void clearRedisCache() {
        logger.info("[INFO] RedisInitializer.javaのclearRedisCacheが呼ばれました。Redisキャッシュのクリアを開始します...");
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
            logger.info("[INFO] Redisキャッシュを正常にクリアしました。");
        } catch (UnsupportedOperationException e) {
            logger.error("[ERROR] Redisキャッシュクリアはサポートされていません: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("[ERROR] Redisキャッシュクリア中に予期しないエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("Redisキャッシュクリア中にエラーが発生しました: " + e.getMessage(), e);
        }
    }

    private void retryWithDelay(Runnable action, String actionName) {
        logger.info("[INFO] RedisInitializer.javaのretryWithDelayが呼ばれました。{} を試行します (最大{}回のリトライが許可されています)...", actionName, MAX_RETRIES);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                action.run();
                logger.info("[INFO] {} が成功しました。", actionName);
                return;
            } catch (RuntimeException e) {
                logger.warn("[WARN] {} の試行 {}/{} が失敗しました: {}", actionName, attempt, MAX_RETRIES, e.getMessage(), e);
                if (attempt == MAX_RETRIES) {
                    logger.error("[ERROR] {} が最大試行回数に達しました。プロセスを中断します。", actionName);
                    throw e;
                }
            } catch (Exception e) {
                logger.error("[ERROR] {} 中に予期しないエラーが発生しました: {}", actionName, e.getMessage(), e);
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException(actionName + " 中にエラーが発生しました: 最大試行回数を超えました。", e);
                }
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                logger.error("[ERROR] リトライ待機中に割り込みが発生しました: {}", interruptedException.getMessage(), interruptedException);
                throw new RuntimeException("リトライ待機中に割り込みが発生しました。", interruptedException);
            }
        }
    }
}
