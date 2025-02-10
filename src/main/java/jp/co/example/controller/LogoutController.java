package jp.co.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class LogoutController {
	
	private static final Logger logger = LoggerFactory.getLogger(LogoutController.class);
	
	@Autowired
	private HttpSession session;
	
	@GetMapping("/logout")
	public String logout(Model model) {
		logger.info("[INFO] logoutが呼ばれました: {}", model);
	    try {
	        session.invalidate();
	    } catch (Exception e) {
	    	logger.error("[ERROR] ログアウト処理中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "ログアウト処理中にエラーが発生しました。");
	        return "error_page"; // エラーページを表示
	    }
	    return "logout";
	}
}
