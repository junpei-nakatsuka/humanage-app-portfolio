package jp.co.example.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import jp.co.example.controller.form.UserForm;
import jp.co.example.entity.Department;
import jp.co.example.entity.User;
import jp.co.example.exception.InvalidDepartmentException;
import jp.co.example.service.DepartmentService;
import jp.co.example.service.UserService;
import jp.co.example.util.HashUtil;

@Controller
@RequestMapping("/departments")
public class DepartmentController {
	
	private static final Logger logger = LoggerFactory.getLogger(DepartmentController.class);

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserService userService;

    @GetMapping
    public String listDepartments(Model model) {
        try {
            List<Department> departments = departmentService.getAllDepartments();
            
            // 各部門に関連付けられた部門長を設定
            for (Department department : departments) {
                if (department.getManager() != null) {
                    User manager = userService.getUserById(department.getManager().getId());
                    department.setManager(manager);
                }
            }
            model.addAttribute("departments", departments);
        } catch (Exception e) {
            logger.error("[ERROR] 部門情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
            return "error_page";
        }
        return "department_management";
    }

    @GetMapping("/new")
    public String showDepartmentForm(Model model) {
        try {
            model.addAttribute("department", new Department());
            List<User> users = userService.getAllUsers();
            model.addAttribute("users", users);
        } catch (Exception e) {
            logger.error("[ERROR] 部門登録フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "部門登録フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
        return "department_register";
    }

    @PostMapping("/save")
    public String saveDepartment(@ModelAttribute Department department) {
        try {
            if (department.getManager() != null && department.getManager().getId() != null) {
                User manager = userService.getUserById(department.getManager().getId());
                department.setManager(manager);
            } else {
                department.setManager(null);
            }
            departmentService.saveDepartment(department);
        } catch (InvalidDepartmentException e) {
            logger.error("[ERROR] 無効な部門: {}", e.getMessage(), e);
            return "redirect:/departments?error=invalid_department";
        } catch (DataIntegrityViolationException ex) {
            logger.error("[ERROR] データ整合性違反: {}", ex.getMessage(), ex);
            return "redirect:/departments?error=data_integrity";
        } catch (Exception exx) {
            logger.error("[ERROR] 部門の保存に失敗: {}", exx.getMessage(), exx);
            return "redirect:/departments?error=unknown_error";
        }
        return "redirect:/departments";
    }

    @GetMapping("/edit/{id}")
    public String showEditDepartmentForm(@PathVariable("id") Integer id, Model model) {
        try {
            Department department = departmentService.getDepartmentById(id);
            List<User> users = userService.getAllUsers();
            model.addAttribute("department", department);
            model.addAttribute("users", users);
        } catch (Exception e) {
            logger.error("[ERROR] 部門編集フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "部門編集フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
        return "department_edit";
    }

    @PostMapping("/update")
    public String updateDepartment(@ModelAttribute Department department) {
        try {
            departmentService.updateDepartment(department);
        } catch (InvalidDepartmentException e) {
            logger.error("[ERROR] 無効な部門の更新: {}", e.getMessage(), e);
            return "redirect:/departments?error=invalid_department";
        } catch (Exception ex) {
            logger.error("[ERROR] 部門の更新中にエラーが発生しました: {}", ex.getMessage(), ex);
            return "redirect:/departments?error=unknown_error";
        }
        return "redirect:/departments";
    }
    
    @PostMapping("/delete/{id}")
    public ResponseEntity<String> deleteDepartment(@PathVariable("id") Integer id, @RequestParam("password") String password, HttpSession session) {
        try {
            User loggedInUser = (User) session.getAttribute("user");
            String storedHashedPassword = loggedInUser.getPassword();

            if (!HashUtil.checkPassword(password, storedHashedPassword)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("パスワードが間違っています。削除に失敗しました。");
            }

            departmentService.deleteDepartment(id);
            return ResponseEntity.ok("部門が削除されました。");
        } catch (Exception e) {
            logger.error("[ERROR] 部門の削除中にエラーが発生しました: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("部門の削除中にエラーが発生しました。");
        }
    }
    
    @GetMapping("/register")
    public String showUserRegisterForm(Model model) {
        try {
            List<Department> departments = departmentService.getAllDepartments();
            model.addAttribute("departments", departments);
            model.addAttribute("userForm", new UserForm());
        } catch (Exception e) {
            logger.error("[ERROR] ユーザー登録フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "ユーザー登録フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
        return "user_register";
    }

    @ExceptionHandler(InvalidDepartmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidDepartmentException(InvalidDepartmentException ex, Model model) {
        logger.error("[ERROR] 無効な部門例外: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error_page";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, Model model) {
        logger.error("[ERROR] データ整合性のエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "データ整合性のエラーが発生しました。");
        return "error_page";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "予期しないエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
}
