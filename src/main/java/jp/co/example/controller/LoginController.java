package jp.co.example.controller;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import jp.co.example.controller.form.LoginForm;
import jp.co.example.entity.User;
import jp.co.example.service.UserService;

@Controller
public class LoginController {
	
	private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
	
	@Autowired
	private UserService userService;
	
	@Autowired
    private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired
    private ObjectMapper objectMapper;
	
	@PostConstruct
	public void checkRedisConnection() {
	    logger.info("[INFO] LoginController.javaのcheckRedisConnectionによりRedis接続を確認しています...");
	    for (int i = 0; i < 5; i++) {
	        try {
	            String pingResponse = redisTemplate.getConnectionFactory().getConnection().ping();
	            logger.debug("[DEBUG] Redis接続がアクティブです: {}", pingResponse);
	            return;
	        } catch (Exception e) {
	            logger.warn("[WARN] Redis接続試行失敗 ({}回目): {}", i + 1, e.getMessage(), e);
	            if (i == 4) {
	            	logger.error("[ERROR] Redis接続失敗: 最大試行回数を超えました: {}", e.getMessage(), e);
	                throw new RuntimeException("Redis接続失敗: 最大試行回数を超えました:" + e.getMessage(), e);
	            }
	            try {
	                Thread.sleep(2000);
	            } catch (InterruptedException interruptedException) {
	                Thread.currentThread().interrupt();
	                logger.error("[ERROR] Redis接続が中断されました: {}", interruptedException.getMessage(), interruptedException);
	                throw new RuntimeException("Redis接続が中断されました:" + interruptedException.getMessage(), interruptedException);
	            }
	        }
	    }
	}
	
	@PreDestroy
    public void cleanupResources() {
        logger.info("[INFO] アプリケーション終了時にリソースを解放します...");
        // 必要に応じてリソースを解放
    }

	@GetMapping("/login")
	public String loginPage(Model model) {
	    try {
	        logger.info("[INFO] LoginController.javaのloginがGetで呼ばれました。受信したログイン要求: ユーザー名。model: {}", model);
	        model.addAttribute("loginForm", new LoginForm());
	        return "login";
	    } catch (Exception e) {
	        logger.error("[ERROR] loginPage メソッド内で予期しないエラーが発生しました。エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        model.addAttribute("errorMessage", "ログインページの読み込み中にエラーが発生しました。");
	        return "error";  // エラーページを表示
	    }
	}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginForm loginForm, HttpSession session) {
    	
    	logger.info("[INFO] LoginController.javaのloginがPostで呼ばれました。RequestBody={}, session={}", loginForm, session);
    	logger.debug("[DEBUG] LoginForm received: username={}, password=[****]", loginForm.getUsername());
    	
        String username = loginForm.getUsername();
        String password = loginForm.getPassword();

        logger.debug("[DEBUG] 受信したログイン要求: username={}", username);

        try {
        	logger.debug("[DEBUG] UserService.javaのauthenticateを呼びます。username: {}, password: [****]", username);
            User user = userService.authenticate(username, password);
            if (user != null) {
                session.setAttribute("user", user);
                logger.debug("[DEBUG] 認証成功。セッションにユーザーを保存しました: {}", user);
                session.setAttribute("role", user.getRole());

                logger.debug("[DEBUG] セッションがRedisに保存されました。セッションID: {}", session.getId());

                String token = generateTokenForUser(user);
                logger.debug("[DEBUG] トークン生成成功: {}", token);
                
                Integer userId = user.getId();
                logger.debug("[DEBUG] 取得したユーザーID: {}", userId);

                try {
                    String userJson = objectMapper.writeValueAsString(user);
                    redisTemplate.opsForValue().set("token:" + token, userJson);
                    logger.debug("[DEBUG] トークンを生成しRedisに保存しました: user: {}, userId={}, userJson: {}, token={}", user, userId, userJson, token);
                } catch (Exception redisEx) {
                    logger.error("[ERROR] Redisへの保存に失敗しました。エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisEx.toString(), Arrays.toString(redisEx.getStackTrace()), redisEx.getCause() != null ? redisEx.getCause().toString() : "原因不明", redisEx.getLocalizedMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("{\"error\":\"Redisエラーが発生しました。\"}");
                }

                return ResponseEntity.ok()
                        .header("X-Auth-Token", token)
                        .body("{\"message\":\"ログイン成功\"}");
            } else {
                Optional<User> optionalUser = userService.findByUsername(username);
                if (optionalUser.isPresent() && "退職済み".equals(optionalUser.get().getStatus())) {
                    logger.warn("[WARN] 退職済みのユーザーのログイン試行: username={}", username);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("{\"error\":\"退職済みのユーザーです。\"}");
                } else {
                    logger.warn("[WARN] ユーザー名またはパスワードが正しくありません: username={}, password: [****]", username);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("{\"error\":\"ユーザー名またはパスワードが正しくありません。\"}");
                }
            }
        } catch (DataAccessException e) {
            logger.error("[ERROR] POSTのloginにてデータベースエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"システムエラーが発生しました。後ほどお試しください。\"}");
        } catch (Exception ex) {
            logger.error("[ERROR] POSTのloginにて予期せぬエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"予期せぬエラーが発生しました。\"}");
        }
    }
    
    public String generateTokenForUser(User user) {
    	logger.info("[INFO] LoginController.javaのgenerateTokenForUserが呼ばれました: {}", user);
        String token = UUID.randomUUID().toString();
        try {
            String userJson = objectMapper.writeValueAsString(user);
            logger.debug("[DEBUG] userJsonを確認します: {}", userJson);
            redisTemplate.opsForValue().set("token:" + token, userJson);
            
            long expirationTime = System.currentTimeMillis() + 30 * 60 * 1000; // 30分
            redisTemplate.opsForValue().set("token_expiration:" + token, String.valueOf(expirationTime));
            
            logger.debug("[DEBUG] トークン生成とRedis保存完了: userId={}, token={}, expirationTime={}", user.getId(), token, expirationTime);
        } catch (Exception e) {
            logger.error("[ERROR] Redis保存エラー: {}", e.getMessage(), e);
            throw new RuntimeException("トークンの保存に失敗しました: " + e.getMessage(), e);
        }
        return token;
    }

    @GetMapping("/top")
    public String topPage() {
        try {
            return "top";
        } catch (Exception e) {
            logger.error("[ERROR] topPage メソッド内で予期しないエラーが発生しました。エラーメッセージ: {}", e.getMessage(), e);
            return "error";  // エラーページを表示
        }
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
        logger.error("[ERROR] アクセス権限がありません: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "アクセス権限がありません。");
        return "error_page";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "システムエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
}
