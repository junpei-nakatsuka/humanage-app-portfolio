package jp.co.example.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import io.lettuce.core.RedisConnectionException;

@Service
public class LockService {
	
	private static final Logger logger = LoggerFactory.getLogger(LockService.class);
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	@Value("${RETRY_INTERVAL_MS:120000}")
    private long retryInterval;

    @Value("${MAX_RETRIES:5}")
    private int maxRetries;
	
    public String acquireLock(String lockKey, long lockTimeoutSeconds) {
    	StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    	String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        logger.info("[INFO] acquireLockが呼ばれました。lockKey: {}, lockTimeoutSeconds: {}, 呼び出し元: {}", lockKey, lockTimeoutSeconds, callingMethodName);
        int attempts = 0;
        String lockOwner = UUID.randomUUID().toString();
        String creatorInfo = String.format("thread:%s, function:%s", Thread.currentThread().getName(), new Throwable().getStackTrace()[1].getMethodName());
        logger.debug("[DEBUG] 生成されたlockOwner: {}, creatorInfo: {}", lockOwner, creatorInfo);
        
        while (attempts < maxRetries) {
            try {
                // ロックを取得
                Boolean isLockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockOwner + "@" + creatorInfo, Duration.ofSeconds(lockTimeoutSeconds));
                if (Boolean.TRUE.equals(isLockAcquired)) {
                    logger.info("[INFO] ロック取得成功 - lockKey: {}, thread: {}, owner: {}", lockKey, Thread.currentThread().getName(), lockOwner);
                    return lockOwner;
                }

                // 既存のロック所有者の詳細を取得
                String existingOwner = (String) redisTemplate.opsForValue().get(lockKey);
                if (existingOwner != null) {
                    logger.warn("[WARN] ロック取得失敗。他のプロセスがロックを保持しています - lockKey: {}, thread: {}, existingOwner: {}", lockKey, Thread.currentThread().getName(), existingOwner);
                }

                //期限切れロックの削除
                Long expireTime = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
                if (expireTime == null || expireTime <= 0) {
                    redisTemplate.delete(lockKey);
                    logger.warn("[WARN] 期限切れロックを削除しました - lockKey: {}, thread: {}", lockKey, Thread.currentThread().getName());
                }

                // 🔽 修正: 指数バックオフ方式でリトライ間隔を設定
                long waitTime = (long) (Math.pow(2, attempts) * 500 + Math.random() * 100);
                logger.warn("[WARN] ロック取得失敗 - リトライ: {}回目, lockKey: {}, thread: {}, 再試行まで待機: {}ms", attempts + 1, lockKey, Thread.currentThread().getName(), waitTime);
                Thread.sleep(waitTime);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 割り込み処理を確実に行う
                logger.error("[ERROR] acquireLockで割り込みエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                throw new RuntimeException("ロック取得中に割り込まれました - lockKey: " + lockKey, e);
            } catch (Exception ex) {
                logger.error("[ERROR] acquireLockでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            }
        }
        logger.error("[ERROR] ロック取得に失敗 - 最大リトライ回数に到達しました - lockKey: {}, thread: {}", lockKey, Thread.currentThread().getName());
        return null;
    }
	
    public boolean releaseLock(String lockKey, String lockOwner) {
    	StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    	String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        logger.info("[INFO] releaseLockが呼び出されました。lockKey: {}, lockOwner: {}, 呼び出し元: {}", lockKey, lockOwner, callingMethodName);
        try {
            String currentOwner = (String) redisTemplate.opsForValue().get(lockKey);
            logger.info("[INFO] 取得したlockOwner: {}, currentOwner: {}", lockOwner, currentOwner);
            if (currentOwner == null) {
                logger.warn("[WARN] ロック解除失敗。ロックが存在しません - lockKey: {}, thread: {}, 呼び出し元: {}", lockKey, Thread.currentThread().getName(), callingMethodName);
                return false;
            }
            if (lockOwner.equals(currentOwner)) {
                redisTemplate.delete(lockKey);
                logger.info("[INFO] ロック解除成功 - lockKey: {}, thread: {}, owner: {}, 呼び出し元: {}", lockKey, Thread.currentThread().getName(), lockOwner, callingMethodName);
                return true;
            } else {
                logger.warn("[WARN] ロック解除失敗。現在の所有者が異なります - lockKey: {}, thread: {}, currentOwner: {}, attemptedOwner: {}, 呼び出し元: {}", lockKey, Thread.currentThread().getName(), currentOwner, lockOwner, callingMethodName);
                return false;
            }
        } catch (RedisConnectionException e) {
            logger.error("[ERROR] Redis接続エラー: ロック解除中にRedis接続が失敗しました - lockKey: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", lockKey, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return false;
        } catch (Exception ex) {
            logger.error("[ERROR] ロック解除中にエラーが発生しました - lockKey: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", lockKey, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            return false;
        }
    }
}
