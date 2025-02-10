package jp.co.example.filter;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class LoginFilter implements Filter {
    
	@Autowired
    private RedisTemplate<String, Object> redisTemplate;
	
    private static final Logger logger = LoggerFactory.getLogger(LoginFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            logger.info("[INFO] LoginFilter.javaのdoFilterが呼ばれました。リクエスト: {}, レスポンス: {}, チェーン: {}", request, response, chain);

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String remoteIp = httpRequest.getRemoteAddr();
            String remoteHost = httpRequest.getRemoteHost();
            String userAgent = httpRequest.getHeader("User-Agent");
            
            //スタックトレースを取得して呼び出し元のメソッド名を取得
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
            
            logger.debug("[DEBUG] リクエスト元 - IP: {}, ホスト: {}, ユーザーエージェント: {}, 関数名: {}", remoteIp, remoteHost, userAgent, callingMethodName);
            
            String token = httpRequest.getHeader("X-Auth-Token");
            HttpSession session = httpRequest.getSession(false);
            
            // セッションが存在する場合の処理
            if (session != null && session.getAttribute("user") != null) {
                logger.debug("[DEBUG] ユーザーはログインしています: {}", session.getAttribute("user"));
                chain.doFilter(request, response);
                return;
            }

            String requestURI = httpRequest.getRequestURI();
            String contextPath = httpRequest.getContextPath();
            
            logger.debug("[DEBUG] リクエストURI: {}, コンテキストパス: {}", requestURI, contextPath);
            logger.debug("[DEBUG] セッション: {}", (session != null) ? session.getId() : "セッションなし");
            logger.debug("[DEBUG] トークン: {}", (token != null) ? token : "トークンなし");

            // ログイン、ログアウト、faviconリクエストのスキップ処理
            if (requestURI.equals(contextPath + "/login") || requestURI.equals(contextPath + "/logout") || requestURI.equals(contextPath + "/favicon.ico")) {
                logger.info("[INFO] ログインまたはログアウトまたはfavicon.icoリクエスト: 処理をスキップします");
                chain.doFilter(request, response);
                return;
            }

            // 特定のURLパターンに対する処理
            boolean loginRequest = requestURI.equals(contextPath + "/login");
            boolean logoutRequest = requestURI.equals(contextPath + "/logout");
            boolean passwordResetRequest = requestURI.equals(contextPath + "/requestPasswordReset");
            boolean passwordReset = requestURI.equals(contextPath + "/resetPassword");
            boolean passwordResetPage = requestURI.equals(contextPath + "/password_reset");
            boolean resourceRequest = requestURI.startsWith(contextPath + "/css/")
                    || requestURI.startsWith(contextPath + "/js/")
                    || requestURI.endsWith(".js");
            boolean apiRequest = requestURI.startsWith(contextPath + "/api/");
            boolean pdfRequest = requestURI.equals(contextPath + "/generateSalaryDetailsPDFByPuppeteer")
                    || requestURI.equals(contextPath + "/api/generatePDF")
                    || requestURI.equals(contextPath + "/generate-pdf");

            logger.debug("[DEBUG] X-Auth-Token: {}", token);
            logger.debug("[DEBUG] リクエスト URI: {}", requestURI);

            // 特定のリクエストに対してフィルタをスキップ
            if (loginRequest || logoutRequest || passwordResetRequest || passwordReset || passwordResetPage || resourceRequest || apiRequest || pdfRequest) {
                chain.doFilter(request, response);
                return;
            }

            // セッションが存在する場合の再確認
            if (session != null && session.getAttribute("user") != null) {
                logger.debug("[DEBUG] ユーザーはセッションにログインしています: {}", session.getAttribute("user"));
                chain.doFilter(request, response);
                return;
            }

            // トークンが有効な場合の処理
            if (token != null && isValidToken(token)) {
                logger.info("[INFO] 有効なトークンを確認しました: {}", token);
                chain.doFilter(request, response);
                return;
            }

            logger.warn("[WARN] 無効なトークンまたはセッションなしでのリクエスト: {}", requestURI);
            httpResponse.sendRedirect(contextPath + "/login");
            
        } catch (Exception e) {
            // 例外処理
            logger.error("[ERROR] LoginFilter.javaでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new ServletException("LoginFilterでエラーが発生しました: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
    	logger.info("[INFO] LoginFilter.javaのdestroyが呼ばれました。");
    	logger.info("[INFO] LoginFilter: Destroyed");
    }

    private boolean isValidToken(String token) {
    	logger.info("[INFO] LoginFilter.javaのisValidTokenが呼ばれました。トークン: {}", token);
    	if (token == null || token.isEmpty()) {
            logger.warn("[WARN] 検証のためにトークンを受け取りましたがトークンが空です。");
            return false;
        }
    	try {
            String userJson = (String) redisTemplate.opsForValue().get("token:" + token);
            logger.debug("[DEBUG] Redisから取得したユーザーデータ: {}", userJson);
            if (userJson == null) {
                logger.warn("[WARN] トークンが無効です（ユーザーデータが存在しません）: {}", token);
                return false;
            }

            String expirationTimeStr = (String) redisTemplate.opsForValue().get("token_expiration:" + token);
            if (expirationTimeStr == null || System.currentTimeMillis() > Long.parseLong(expirationTimeStr)) {
                logger.warn("[WARN] トークンの有効期限が見つかりません: {}", token);
                redisTemplate.delete("token:" + token);
                redisTemplate.delete("token_expiration:" + token);
                return false;
            }

            long expirationTime;
            try {
                expirationTime = Long.parseLong(expirationTimeStr);
                logger.debug("[DEBUG] トークンの有効期限を確認: token={}, expirationTime={}", token, expirationTime);
            } catch (NumberFormatException e) {
                logger.error("[ERROR] トークンの有効期限の解析に失敗しました: {}, error: {}", expirationTimeStr, e.getMessage(), e);
                return false;
            }

            if (System.currentTimeMillis() > expirationTime) {
                logger.warn("[WARN] トークンの有効期限が切れています: {}", token);
                return false;
            }

            logger.info("[INFO] 有効なトークンです: {}", token);
            return true;
        } catch (Exception e) {
            logger.error("[ERROR] トークン検証中にエラーが発生しました: {}", e.getMessage(), e);
            return false;
        }
    }
}
