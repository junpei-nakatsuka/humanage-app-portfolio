package jp.co.example.controller;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAllExceptions(Exception ex, Model model) {
    	StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    	String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        logger.error("[ERROR] 予期しないエラーが発生しました。呼び出し元: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", callingMethodName, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        model.addAttribute("errorMessage", "予期しないエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
    
    @ExceptionHandler(BeanCreationNotAllowedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleBeanCreationNotAllowedException(BeanCreationNotAllowedException ex, Model model) {
        logger.error("[ERROR] Beanの作成が許可されていません: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "アプリケーションのライフサイクルに関連するエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoHandlerFoundException ex, Model model) {
        logger.error("[ERROR] ページが見つかりません: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "お探しのページは見つかりません。URLが正しいか確認してください。");
        return "error_page"; // error_page.jsp へリダイレクト
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFoundException(NoResourceFoundException ex, Model model, HttpServletRequest request) {
        // スタックトレースを取得
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        // スタックトレースの情報をログに出力
        StringBuilder stackTraceLog = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            stackTraceLog.append(element.toString()).append("\n");
        }

        // スタックトレースをログに出力
        logger.error("[ERROR] リソースが見つかりません: {}", request.getRequestURL(), ex);
        logger.error("[ERROR] 呼び出しスタック:\n{}", stackTraceLog.toString());

        model.addAttribute("errorMessage", "お探しのリソースが見つかりません。");
        return "error_page";
    }
}
