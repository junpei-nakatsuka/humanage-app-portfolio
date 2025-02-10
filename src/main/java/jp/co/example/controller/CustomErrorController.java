package jp.co.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    // 予期せぬエラーを処理するためのエンドポイント
    @GetMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
    	logger.info("[INFO] CustomErrorController.javaのhandleErrorが呼ばれました。request: {}, model: {}", request, model);
        try {
            Object statusCode = request.getAttribute("jakarta.servlet.error.status_code");

            // ステータスコードに応じて異なるページを表示
            if (statusCode != null) {
                int status = Integer.parseInt(statusCode.toString());

                if (status == 404) {
                    return "error_page"; // 404エラーはerror_page.jspにリダイレクト
                } else {
                    model.addAttribute("errorCode", status);
                    return "error";
                }
            }
        } catch (NumberFormatException e) {
            logger.error("[ERROR] ステータスコードの解析に失敗しました。エラーメッセージ: {}", e.getMessage(), e);
            model.addAttribute("errorCode", "UNKNOWN");
        } catch (Exception ex) {
            logger.error("[ERROR] handleError メソッド内で予期しないエラーが発生しました。エラーメッセージ: {}", ex.getMessage(), ex);
            model.addAttribute("errorCode", "UNKNOWN");
        }

        return "error";
    }
}
