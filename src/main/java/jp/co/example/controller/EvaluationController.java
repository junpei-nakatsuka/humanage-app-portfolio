package jp.co.example.controller;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.servlet.http.HttpSession;
import jp.co.example.entity.Evaluation;
import jp.co.example.entity.User;
import jp.co.example.exception.UserNotFoundException;
import jp.co.example.service.EvaluationService;
import jp.co.example.service.UserService;

@Controller
@RequestMapping("/evaluationManagement")
public class EvaluationController {
	
	private static final Logger logger = LoggerFactory.getLogger(EvaluationController.class);
	
	@Autowired
	private UserService userService;
	
    @Autowired
    private EvaluationService evaluationService;

    // 評価一覧の表示
    @GetMapping
    public String showEvaluations(Model model) {
        try {
            List<Evaluation> evaluations = evaluationService.getAllEvaluations();
            model.addAttribute("evaluations", evaluations);
            return "evaluation_management";
        } catch (Exception e) {
            logger.error("[ERROR] 評価一覧の表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "評価一覧の表示中にエラーが発生しました。");
            return "error_page";
        }
    }

    // 新規評価フォームを表示
    @GetMapping("/new")
    public String showNewEvaluationForm(Model model) {
        try {
            model.addAttribute("evaluation", new Evaluation());
            
            // 全てのユーザーを取得
            List<User> users = userService.getAllUsers();
            model.addAttribute("users", users);
            return "evaluation_form";
        } catch (Exception e) {
            logger.error("[ERROR] 新規評価フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "新規評価フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
    }

    // 評価の保存（新規登録または更新）
    @PostMapping("/save")
    public String saveEvaluation(@ModelAttribute("evaluation") Evaluation evaluation, Model model) {
        try {
            evaluationService.saveEvaluation(evaluation);
            return "redirect:/evaluationManagement";
        } catch (Exception e) {
            logger.error("[ERROR] 評価の保存中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "評価の保存中にエラーが発生しました。");
            return "error_page";
        }
    }

    // 評価編集フォームを表示
    @GetMapping("/edit/{id}")
    public String showEditEvaluationForm(@PathVariable("id") Integer id, Model model) {
        try {
            Optional<Evaluation> evaluation = evaluationService.getEvaluationById(id);
            if (evaluation.isPresent()) {
                model.addAttribute("evaluation", evaluation.get());
                
                // 全てのユーザーを取得
                List<User> users = userService.getAllUsers();
                model.addAttribute("users", users);
                return "evaluation_form";
            } else {
                model.addAttribute("errorMessage", "指定された評価が見つかりませんでした。");
                return "error_page";
            }
        } catch (Exception e) {
            logger.error("[ERROR] 評価編集フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "評価編集フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    //評価の削除
    @PostMapping("/delete/{id}")
    public ResponseEntity<String> deleteEvaluation(@PathVariable("id") int evaluationId, @RequestParam("password") String password, HttpSession session) {
        try {
            User loggedInUser = (User) session.getAttribute("user");

            if (userService.isPasswordCorrect(loggedInUser, password)) {
                evaluationService.deleteEvaluation(evaluationId);
                return ResponseEntity.ok("評価が削除されました。");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("パスワードが正しくありません。");
            }
        } catch (Exception e) {
            logger.error("[ERROR] 評価の削除中にエラーが発生しました: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("評価の削除中にエラーが発生しました。");
        }
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUserNotFoundException(UserNotFoundException ex, Model model) {
        logger.error("[ERROR] ユーザーを見つけることができませんでした: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage());
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
