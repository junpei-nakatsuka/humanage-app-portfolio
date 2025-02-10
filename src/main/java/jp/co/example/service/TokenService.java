package jp.co.example.service;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.example.entity.User;
import jp.co.example.repository.UserRepository;

@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;

    public User verifyTokenAndFetchUser(String token) {
        logger.info("[INFO] TokenService.javaのverifyTokenAndFetchUserが呼ばれました - token: {}", token);
        String userJson = (String) redisTemplate.opsForValue().get("token:" + token);
        if (userJson == null) {
            logger.warn("[WARN] トークンが無効または期限切れです。トークン: {}", token);
            return null;
        }
        
        logger.debug("[DEBUG] Redisから取得したユーザー情報: {}", userJson);
        
        try {
            User user = objectMapper.readValue(userJson, User.class);
            logger.debug("[DEBUG] トークンから取得したUserオブジェクト: {}", user);
            
            if (user != null) {
            	Integer userId = user.getId();
                logger.debug("[DEBUG] 取得したuserId: {}", userId);
            } else {
                logger.warn("[WARN] Userオブジェクトがnullです - token: {}", token);
            }
            
            return user;
        } catch (Exception e) {
            logger.error("[ERROR] ユーザー情報の変換エラー:{}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null;
        }
    }

    public boolean isTokenExpired(String token) {
        logger.info("[INFO] TokenService.javaのisTokenExpiredが呼ばれました。トークン: {}", token);
        String expirationTimeStr = (String) redisTemplate.opsForValue().get("token_expiration:" + token);
        
        if (expirationTimeStr == null) {
            logger.warn("[WARN] トークンの有効期限がRedisに保存されていません: {}", token);
            return true;
        }
        
        try {
            Long expirationTime = Long.parseLong(expirationTimeStr);
            Long currentTime = System.currentTimeMillis();
            boolean isExpired = currentTime > expirationTime;
            
            // **日時フォーマットを適用**
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN);
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            
            String expirationDate = sdf.format(new Date(expirationTime));
            String currentDate = sdf.format(new Date(currentTime));
            
            logger.info("[INFO] トークンの有効期限: {} ({}), 現在時刻: {} ({}), 有効かどうか: {}", 
                expirationTime, expirationDate, currentTime, currentDate, !isExpired);
            
            return isExpired;
        } catch (NumberFormatException e) {
            logger.error("[ERROR] 有効期限のパースに失敗しました。値: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", expirationTimeStr, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return true;
        } catch (Exception ex) {
            logger.error("[ERROR] isTokenExpiredで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public String extendTokenExpiration(String token) {
        logger.info("[INFO] TokenService.javaのextendTokenExpirationが呼ばれました - トークン: {}", token);
        try {
            String userJson = (String) redisTemplate.opsForValue().get("token:" + token);
            if (userJson == null) {
                logger.warn("[WARN] トークンがRedisに存在しないか、期限が切れています: {}", token);
                return null;
            }
            
            logger.debug("[DEBUG] 取得したuserJsonを確認します。userJson: {}", userJson);
            
            //SupabaseとRedis間でデータ整合性をチェック
            User user = objectMapper.readValue(userJson, User.class);
            Integer userId = user.getId();
            logger.debug("[DEBUG] 取得したuserとuserIdを確認します。user: {}, userId: {}", user, userId);
            
            Optional<User> supabaseUser = userRepository.findById(userId);
            logger.debug("[DEBUG] 取得したsupabaseUserを確認します。supabaseUser: {}", supabaseUser);
            if (!supabaseUser.isPresent() || !supabaseUser.get().equals(user)) {
                logger.warn("[WARN] SupabaseとRedisのデータが一致しません - userId: {}, user: {}, supabaseUser: {}", userId, user, supabaseUser);
                return null;
            }

            String newToken = UUID.randomUUID().toString();
            long newExpirationTime = System.currentTimeMillis() + 30 * 60 * 1000;

            redisTemplate.opsForValue().set("token:" + newToken, userJson, Duration.ofMinutes(30));
            redisTemplate.opsForValue().set("token_expiration:" + newToken, String.valueOf(newExpirationTime));

            redisTemplate.delete("token:" + token);
            redisTemplate.delete("token_expiration:" + token);

            logger.info("[INFO] トークンが延長されました。新しいトークン: {}", newToken);
            return newToken;
        } catch (Exception e) {
            logger.error("[ERROR] トークン延長処理でエラーが発生しました。エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null;
        }
    }
    
    public String refreshTokenIfNecessary(String token) {
        try {
            logger.info("[INFO] TokenService.javaのrefreshTokenIfNecessaryが呼ばれました: {}", token);
            
            if (isTokenExpired(token)) {
                try {
                    redisTemplate.delete("token:" + token);
                    redisTemplate.delete("token_expiration:" + token);
                    logger.debug("[DEBUG] 古いトークンとその期限を削除しました: {}", token);
                } catch (Exception e) {
                    logger.error("[ERROR] トークン削除中にエラーが発生しました - トークン: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", token, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                    throw new RuntimeException("トークン削除中に問題が発生しました", e);
                }

                String newToken = extendTokenExpiration(token);
                if (newToken == null) {
                    logger.warn("[WARN] 新しいトークンの生成に失敗しました。");
                    return null;
                }
                logger.info("[INFO] 生成したnewToken: {}", newToken);
                return newToken;
            }
            
            return token;
        } catch (Exception e) {
            logger.error("[ERROR] refreshTokenIfNecessary中にエラーが発生しました - トークン: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", token, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null; // 必要に応じてエラーハンドリングのロジックを追加
        }
    }
    
    public String checkAndExtendToken(String token) {
        try {
            logger.info("[INFO] TokenService.javaのcheckAndExtendTokenが呼ばれました: {}", token);
            
            if (isTokenExpired(token)) {
                String newToken = extendTokenExpiration(token);
                if (newToken == null) {
                    logger.warn("[WARN] トークンの延長に失敗しました - トークン: {}", token);
                    return null;
                }
                logger.debug("[DEBUG] 延長したnewToken: {}", newToken);
                return newToken;
            }
            return token;
        } catch (Exception e) {
            logger.error("[ERROR] トークンのチェックまたは延長中にエラーが発生しました - トークン: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", token, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null; // 必要に応じてエラーハンドリングのロジックを追加
        }
    }
    
    public String refreshToken(String token) {
        logger.info("[INFO] refreshToken メソッドが呼び出されました: token={}", token);

        try {
            // トークンが有効期限切れか確認
            if (isTokenExpired(token)) {
                logger.warn("[WARN] トークンが期限切れのため、null を返します: token={}", token);
                return null;
            }
            logger.debug("[DEBUG] トークンは有効期限内です: token={}", token);

            // Redisからトークンに紐づくユーザー情報を取得
            String userJson = (String) redisTemplate.opsForValue().get("token:" + token);
            if (userJson == null) {
                logger.warn("[WARN] トークンに対応するユーザー情報が見つかりません: token={}", token);
                return null;
            }
            logger.debug("[DEBUG] Redisから取得したユーザー情報: userJson={}", userJson);

            // 新しいトークンを生成
            String newToken = UUID.randomUUID().toString();
            long newExpirationTime = System.currentTimeMillis() + 30 * 60 * 1000; // 30分後

            logger.debug("[DEBUG] 新しいトークンを生成しました: newToken={}, newExpirationTime={}", newToken, newExpirationTime);

            // 新しいトークンをRedisに保存
            redisTemplate.opsForValue().set("token:" + newToken, userJson, Duration.ofMinutes(30));
            redisTemplate.opsForValue().set("token_expiration:" + newToken, String.valueOf(newExpirationTime));
            logger.debug("[DEBUG] 新しいトークンと有効期限をRedisに保存しました: newToken={}, expirationTime={}", newToken, newExpirationTime);

            // 古いトークンをRedisから削除
            redisTemplate.delete("token:" + token);
            redisTemplate.delete("token_expiration:" + token);
            logger.debug("[DEBUG] 古いトークンをRedisから削除しました: token={}", token);

            // 新しいトークンを返却
            logger.info("[INFO] トークンのリフレッシュが完了しました: newToken={}", newToken);
            return newToken;
        } catch (Exception e) {
            // 例外の詳細をログに記録
            logger.error("[ERROR] トークンのリフレッシュ中にエラーが発生しました: token={}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", token, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null;
        }
    }
}
