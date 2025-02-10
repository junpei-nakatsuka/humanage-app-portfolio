package jp.co.example.controller;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jp.co.example.controller.form.EmploymentForm;
import jp.co.example.entity.Employment;
import jp.co.example.entity.User;
import jp.co.example.exception.UserNotFoundException;
import jp.co.example.service.EmploymentService;
import jp.co.example.service.UserService;

@Controller
public class EmploymentController {
	
	private static final Logger logger = LoggerFactory.getLogger(EmploymentController.class);

    @Autowired
    private EmploymentService employmentService;

    @Autowired
    private UserService userService;
        
    // 新規登録ページの表示
    @GetMapping("/employments/new/{userId}")
    public String showEmploymentForm(@PathVariable("userId") Integer userId, Model model) {
        try {
            User user = userService.getUserById(userId);
            if (user == null){
                model.addAttribute("errorMessage", "無効なユーザーID: " + userId);
                return "error_page";
            }
            EmploymentForm employmentForm = new EmploymentForm();
            employmentForm.setUserId(user.getId());
            employmentForm.setUserName(user.getUsername());
            
            model.addAttribute("employmentForm", employmentForm);
            model.addAttribute("user", user);
            return "employment_form";
        } catch (Exception e) {
            logger.error("[ERROR] 新規登録フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "新規登録フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    @PostMapping("/employments/save")
    public String saveEmployment(@ModelAttribute EmploymentForm employmentForm, Model model, RedirectAttributes redirectAttributes) {
        try {
            if (employmentForm.getUserId() == null) {
                model.addAttribute("errorMessage", "ユーザーIDが必要ですが提供されませんでした。");
                return "error_page";
            }
            
            User user = userService.getUserById(employmentForm.getUserId());

            if (user == null) {
                model.addAttribute("errorMessage", "無効なユーザーID: " + employmentForm.getUserId());
                return "error_page";
            }
            
            // 既に「在職中」のレコードが存在するかチェック
            List<Employment> employments = employmentService.getEmploymentsByUserId(user.getId());
            boolean hasActiveEmployment = employments.stream().anyMatch(e -> "在職中".equals(e.getStatus()) && !e.getId().equals(employmentForm.getEmploymentId()));
            
            if (hasActiveEmployment && "在職中".equals(employmentForm.getStatus())) {
                if (employmentForm.getEmploymentId() == null) {
                    // 新規追加の場合
                    redirectAttributes.addFlashAttribute("errorMessage", "既に「在職中」のエントリーが存在するため、新規追加できません。");
                    return "redirect:/employments/new/" + employmentForm.getUserId();
                } else {
                    // 編集の場合
                    redirectAttributes.addFlashAttribute("errorMessage", "既に「在職中」のエントリーが存在するため、更新できません。");
                    return "redirect:/employments/edit/" + employmentForm.getEmploymentId();
                }
            }

            Employment employment;
            boolean isNewEntry = employmentForm.getEmploymentId() == null;

            if (!isNewEntry) {
                // 既存レコードの編集
                employment = employmentService.getEmploymentById(employmentForm.getEmploymentId());
                if (employment == null) {
                	logger.error("[ERROR] employmentがnullです。ElementId: {}", employmentForm.getEmploymentId());
                    model.addAttribute("errorMessage", "無効な雇用ID: " + employmentForm.getEmploymentId());
                    return "error_page";
                }

                // 「退職済み」から「在職中」に変更する場合、退職日をクリア
                if ("在職中".equals(employmentForm.getStatus())) {
                	logger.info("[INFO] 退職済みから在職中に変更したので退職日をクリアします。");
                    employment.setResignationDate(null);
                }
            } else {
                // 新規登録
                employment = new Employment();
                employment.setUser(user);
            }

            employment.setHireDate(employmentForm.getHireDate());
            employment.setResignationDate(employmentForm.getResignationDate());
            employment.setStatus(employmentForm.getStatus());
            
            logger.debug("[DEBUG] controllerのsaveEmploymentからserviceのsaveEmploymentを呼びます。employment: {}", employment);
            employmentService.saveEmployment(employment);

            // 「在職中」または「退職済み」の場合のみユーザーのステータスを更新
            if ("在職中".equals(employmentForm.getStatus()) || "退職済み".equals(employmentForm.getStatus())) {
                userService.updateUserStatus(employmentForm.getUserId(), employmentForm.getStatus());
            }

            return "redirect:/users/" + employmentForm.getUserId() + "/employment";
        } catch (Exception e) {
            logger.error("[ERROR] 雇用情報の保存中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            model.addAttribute("errorMessage", "雇用情報の保存中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    // 特定ユーザーの入社日・退職日管理画面の表示
    @GetMapping("/users/{userId}/employment")
    public String viewEmploymentManagement(@PathVariable("userId") Integer userId, Model model) {
        try {
            User user = userService.getUserById(userId);
            List<Employment> employments = employmentService.getEmploymentsByUserId(userId);
            
            model.addAttribute("user", user);
            model.addAttribute("employments", employments);
            
            //最新のレコードを特定してモデルに追加
            employments.sort(Comparator.comparing(Employment::getHireDate).reversed());
            if (!employments.isEmpty()) {
                Employment latestEmployment = employments.get(0);
                model.addAttribute("latestEmploymentId", latestEmployment.getId());
            }
            
            return "employ_management";  // employ_management.jsp へのビュー名
        } catch (Exception e) {
            logger.error("[ERROR] 入社・退職管理画面の表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "入社・退職管理画面の表示中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    @GetMapping("/employments/edit/{employmentId}")
    public String showEditForm(@PathVariable("employmentId") Integer employmentId, Model model) {
        try {
            Employment employment = employmentService.getEmploymentById(employmentId);
            
            if (employment == null) {
                model.addAttribute("errorMessage", "無効な雇用ID: " + employmentId);
                return "error_page";
            }

            EmploymentForm employmentForm = new EmploymentForm();
            employmentForm.setEmploymentId(employment.getId());  // Employment IDをセット
            employmentForm.setUserId(employment.getUser().getId());
            employmentForm.setUserName(employment.getUser().getUsername());
            employmentForm.setHireDate(employment.getHireDate());
            employmentForm.setResignationDate(employment.getResignationDate());
            employmentForm.setStatus(employment.getStatus());
            
            model.addAttribute("employmentForm", employmentForm);
            return "employment_edit";
        } catch (Exception e) {
            logger.error("[ERROR] 編集フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "編集フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
    }

    // 入社日・退職日の削除
    @GetMapping("/employments/delete/{userId}")
    public String deleteEmployment(@PathVariable("userId") Integer userId, Model model) {
        try {
            employmentService.deleteEmploymentByUserId(userId);
            return "redirect:/users/" + userId + "/employment";
        } catch (Exception e) {
            logger.error("[ERROR] 入社日・退職日の削除中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "入社日・退職日の削除中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    @PostMapping("/employments/delete/{id}")
    public ResponseEntity<String> deleteEmployment(@PathVariable("id") Integer id, @RequestParam("password") String password, HttpSession session) {
        try {
            User currentUser = (User) session.getAttribute("user");
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("ユーザーがログインしていません。");
            }

            if (!userService.isPasswordCorrect(currentUser, password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("パスワードが正しくありません。");
            }

            employmentService.deleteEmploymentById(id);
            return ResponseEntity.ok("削除が完了しました。");
        } catch (Exception e) {
            logger.error("[ERROR] 入社日・退職日の削除中にエラーが発生しました: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("入社日・退職日の削除中にエラーが発生しました。");
        }
    }
    
    // 全ユーザーの入社日・退職日管理画面の表示
    @GetMapping("/employments")
    public String listEmployments(Model model) {
        try {
            List<Employment> employments = employmentService.getAllEmployments();
            
            model.addAttribute("employments", employments);
            return "employ_management";
        } catch (Exception e) {
            logger.error("[ERROR] 全ユーザーの入社日・退職日管理画面の表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "全ユーザーの入社日・退職日管理画面の表示中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUserNotFoundException(UserNotFoundException ex, Model model) {
        logger.error("[ERROR] ユーザーを見つけることが出来ませんでした: {}", ex.getMessage(), ex);
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
