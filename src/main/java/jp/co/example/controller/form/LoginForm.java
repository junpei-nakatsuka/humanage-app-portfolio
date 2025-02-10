package jp.co.example.controller.form;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginForm {
	
	private static final Logger logger = LoggerFactory.getLogger(LoginForm.class);
	
    private String username;
    private String password;
    
    public LoginForm () {
    	logger.info("[INFO] LoginForm.javaの引数なしコンストラクタが呼ばれました。");
    }

    public String getUsername() {
        if (username == null || username.isEmpty()) {
            logger.warn("[WARN] usernameがnullか空です。");
        }
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        logger.info("[INFO] usernameがセットされました: {}", username);
    }

    public String getPassword() {
    	if (password == null || password.isEmpty()) {
    		logger.warn("[WARN] passwordがnullか空です。");
    	}
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}