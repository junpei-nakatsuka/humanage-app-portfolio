package jp.co.example.controller;

import java.beans.PropertyEditorSupport;
import java.nio.file.AccessDeniedException;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jp.co.example.controller.form.UserForm;
import jp.co.example.entity.Contract;
import jp.co.example.entity.Department;
import jp.co.example.entity.User;
import jp.co.example.exception.InvalidDepartmentException;
import jp.co.example.exception.UserNotFoundException;
import jp.co.example.service.ContractService;
import jp.co.example.service.DepartmentService;
import jp.co.example.service.UserService;

@Controller
public class UserController {
	
	private static final Logger logger = LoggerFactory.getLogger(UserController.class);
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private DepartmentService departmentService;
		
	@Autowired
	private ModelMapper modelMapper;
	
	@Autowired
	private HttpSession session;
	
	@Autowired
	private ContractService contractService;
		
	@InitBinder
	public void initBinder(WebDataBinder binder) {
	    logger.info("[INFO] initBinderが呼ばれました: {}", binder);

	    try {
	        // Department のエディタを登録
	        binder.registerCustomEditor(Department.class, new DepartmentEditor(departmentService));

	        // postalCode のフォーマット変換エディタを登録
	        binder.registerCustomEditor(String.class, "postalCode", new PropertyEditorSupport() {
	            @Override
	            public void setAsText(String text) {
	                if (text != null && text.matches("^\\d{7}$")) {
	                    setValue(text.substring(0, 3) + "-" + text.substring(3));
	                    logger.info("[INFO] 郵便番号のフォーマットを修正: {} -> {}", text, text.substring(0, 3) + "-" + text.substring(3));
	                } else {
	                    setValue(text);
	                }
	            }
	        });

	    } catch (Exception e) {
	        logger.error("[ERROR] initBinderメソッドでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", 
	            e.toString(), 
	            Arrays.toString(e.getStackTrace()), 
	            e.getCause() != null ? e.getCause().toString() : "原因不明", 
	            e.getLocalizedMessage()
	        );
	    }
	}

	@GetMapping("/users")
	public String listUsers(@RequestParam(value = "query", required = false) String query, Model model) {
		logger.info("[INFO] listUsersが呼ばれました。query: {}, model: {}", query, model);
	    try {
	        List<User> users;

	        if (query != null && !query.isEmpty()) {
	            users = userService.searchUsers(query);
	        } else {
	            users = userService.getAllUsers();
	        }

	        Collator collator = Collator.getInstance(Locale.JAPANESE);
	        users.sort(Comparator.comparing(User::getUsername, collator));

	        model.addAttribute("users", users);
	        return "user_list";

	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザーリストの取得中にエラーが発生しました - メッセージ: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "ユーザーリストの取得中にエラーが発生しました。");
	        return "error_page"; // エラーページを表示
	    }
	}

	@GetMapping("/register")
	public String registerUser(Model model) {
		logger.info("[INFO] Getのregisterが呼ばれました。model: {}", model);
	    try {
	        List<Department> departments = departmentService.getAllDepartments();
	        model.addAttribute("departments", departments);
	        return "user_register";
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー登録ページの部門情報取得中にエラーが発生しました - メッセージ: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページを表示
	    }
	}
	
	@PostMapping("/register")
	public String registerUser(@Valid @ModelAttribute("userForm") UserForm form, BindingResult result, Model model) {
		try {
			logger.info("[INFO] PostのregisterUserが呼ばれました。userForm: {}, BindingResult: {}, model: {}", form, result, model);

			// バリデーションエラーの処理
			if (result.hasErrors()) {
				logger.error("[ERROR] バリデーションエラーが発生しました: {}", result.getAllErrors());
				try {
					model.addAttribute("departments", departmentService.getAllDepartments());
				} catch (Exception e) {
					logger.error("[ERROR] 部門情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
					model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
					return "user_register";
				}
				return "user_register";
			}
			
			if (form.getDepartmentId() == null || form.getDepartmentId().isEmpty()) {
		        result.rejectValue("departmentId", "error.userForm", "所属部署を選択してください。");
		        model.addAttribute("departments", departmentService.getAllDepartments());
		        return "user_register";
		    }

			// メールアドレスの重複チェック
			if (userService.emailExists(form.getEmail())) {
				logger.warn("[WARN] Emailは既に存在します: {}", form.getEmail());
				model.addAttribute("errorMessage", "指定されたメールアドレスは既に存在します。");
				return "user_register";
			}
			
			User user = modelMapper.map(form, User.class);

			try {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				user.setDob(LocalDate.parse(form.getDob(), formatter));
			} catch (DateTimeParseException e) {
				logger.error("[ERROR] 生年月日の形式が正しくありません: {}, ERROR: {}", form.getDob(), e.getMessage(), e);
				model.addAttribute("errorMessage", "生年月日の形式が正しくありません。");
				return "user_register";
			}

			logger.debug("[DEBUG] 生年月日を解析します: {}", user.getDob());
			logger.debug("[DEBUG] フォームから取得した部門: {}", form.getDepartment());

			user.setStatus("在職中");

			Department department = null;
			try {
			    Integer departmentId = Integer.parseInt(form.getDepartmentId()); // String を Integer に変換
			    department = departmentService.getDepartmentById(departmentId);
			    
			    if (department == null) {
			        logger.error("[ERROR] 選択した部門が存在しません。");
			        model.addAttribute("errorMessage", "選択した部門が存在しません。");
			        return "user_register";
			    }
			} catch (NumberFormatException e) {
			    logger.error("[ERROR] 部門IDの形式が不正です: {}", form.getDepartmentId(), e);
			    model.addAttribute("errorMessage", "無効な部門IDです。");
			    return "user_register";
			} catch (Exception e) {
			    logger.error("[ERROR] 部門情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
			    model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
			    return "user_register";
			}
			
			user.setDepartment(department);
			
			logger.info("[INFO] 取得した部門情報: {}", department);

			if (department.getDepartmentName() == null) {
				logger.error("[ERROR] 部門情報が不完全です: {}", department);
			    model.addAttribute("errorMessage", "部門情報が不完全です。");
			    return "user_register";
			}

			if ("部門長".equals(user.getRole())) {
				if (department.getManager() != null) {
					logger.warn("[WARN] 部門長が既に存在しています。部門長は一つの部門につき一人です: {}", department.getId());
					model.addAttribute("errorMessage", "部門長は一つの部門につき一人です。");
					return "user_register";
				}
			}

			try {
				logger.debug("[DEBUG] saveUserを呼びます。user: {}", user);
				userService.saveUser(user);
				logger.info("[INFO] ユーザー情報を保存しました: {}", user);
			} catch (Exception e) {
				logger.error("[ERROR] ユーザー情報の保存中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
				model.addAttribute("errorMessage", "ユーザー情報の保存中にエラーが発生しました。");
				return "user_register";
			}

			if ("部門長".equals(user.getRole())) {
				try {
			        logger.debug("[DEBUG] Managerをセットします。user: {}", user);
			        department.setManager(user);  // 部門長の設定

			        logger.debug("[DEBUG] 部門情報が既に存在しているのでマージを試みます。");
			        department = departmentService.mergeDepartment(department);  // 部門のマージ処理

			    } catch (Exception e) {
					logger.error("[ERROR] 部門情報の保存中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
					model.addAttribute("errorMessage", "部門情報の保存中にエラーが発生しました。");
					return "user_register";
				}
			}

			logger.info("[INFO] /usersにリダイレクトします");
			return "redirect:/users";
		} catch (Exception e) {
			logger.error("[ERROR] registerで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
			throw new RuntimeException("registerで予期しないエラーが発生しました: " + e.getMessage(), e);
		}
	}

	@GetMapping("/users/{id}")
	public String getUserDetail(@PathVariable("id") Integer id, Model model) {
	    User user = null;
	    try {
	        user = userService.getUserById(id);
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "ユーザー情報の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }

	    // 生年月日をString形式でフォーマットして渡す
	    String formattedDob = "";
	    try {
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
	        formattedDob = user.getDob().format(formatter);
	    } catch (Exception e) {
	        logger.error("[ERROR] 生年月日のフォーマット中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "生年月日のフォーマット中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }

	    // 最終更新日時をフォーマット
	    String formattedLastModifiedAt = "";
	    try {
	        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");
	        formattedLastModifiedAt = user.getLastModifiedAt() != null ? user.getLastModifiedAt().format(dateTimeFormatter) : "未設定";
	    } catch (Exception e) {
	        logger.error("[ERROR] 最終更新日時のフォーマット中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "最終更新日時のフォーマット中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }

	    // ユーザーの最新の契約情報を取得
	    Contract latestContract = null;
	    try {
	        latestContract = contractService.getLatestContractByUserId(id);
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザーの契約情報取得中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "契約情報の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }

	    // モデルにユーザー情報を追加
	    model.addAttribute("user", user);
	    model.addAttribute("formattedDob", formattedDob);
	    model.addAttribute("latestContract", latestContract);
	    model.addAttribute("formattedLastModifiedAt", formattedLastModifiedAt);

	    // user_detail.jspを返す
	    return "user_detail";
	}

	@GetMapping("/users/edit/{id}")
	public String showEditUserForm(@PathVariable("id") Integer id, Model model) {
	    User user = null;
	    try {
	        user = userService.getUserById(id);
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "ユーザー情報の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }
	    model.addAttribute("user", user);

	    List<Department> departments = null;
	    try {
	        departments = departmentService.getAllDepartments();
	    } catch (Exception e) {
	        logger.error("[ERROR] 部門情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページにリダイレクト
	    }
	    model.addAttribute("departments", departments);

	    return "user_edit";
	}
	
	@PostMapping("/users/update")
	public String updateUser(@Valid @ModelAttribute("user") UserForm userForm, BindingResult result, @RequestParam("password") String enteredPassword, Model model) {
	    // ログインしているユーザーを取得
	    User loggedInUser = (User) session.getAttribute("user");

	    // 入力されたパスワードがログインユーザーのパスワードと一致するか確認
	    if (!userService.isPasswordCorrect(loggedInUser, enteredPassword)) {
	        // パスワードが一致しない場合はエラーメッセージを表示して、編集ページに戻る
	        model.addAttribute("errorMessage", "パスワードが正しくありません。");
	        model.addAttribute("departments", departmentService.getAllDepartments());
	        return "user_edit";  // エラーメッセージを表示してuser_edit.jspに戻る
	    }

	    // バリデーションエラーがある場合は、編集ページに戻す
	    if (result.hasErrors()) {
	    	logger.error("[ERROR] バリデーションエラーがあります。");
	        model.addAttribute("departments", departmentService.getAllDepartments());
	        model.addAttribute("result", result);
	        return "user_edit";
	    }

	    User user = modelMapper.map(userForm, User.class);

	    // 生年月日のパース処理にtry-catchを追加
	    try {
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	        user.setDob(LocalDate.parse(userForm.getDob(), formatter));
	    } catch (DateTimeParseException e) {
	    	logger.error("[ERROR] 生年月日の形式が正しくありません: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "生年月日の形式が正しくありません。");
	        model.addAttribute("departments", departmentService.getAllDepartments());
	        return "user_edit";
	    }

	    // 部門IDを使って部門を取得し、存在しない場合はエラーメッセージ
	    Department department = null;
	    try {
	        department = departmentService.getDepartmentById(userForm.getDepartment().getId());
	        if (department == null) {
	        	logger.error("[ERROR] 指定された部門が存在しません。");
	            model.addAttribute("errorMessage", "指定された部門が存在しません。");
	            model.addAttribute("departments", departmentService.getAllDepartments());
	            return "user_edit";
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] 部門情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "部門情報の取得中にエラーが発生しました。");
	        model.addAttribute("departments", departmentService.getAllDepartments());
	        return "user_edit"; // エラーページにリダイレクト
	    }

	    // 役職が「部門長」の場合に、既に部門長が存在するか確認
	    if ("部門長".equals(user.getRole())) {
	        if (department.getManager() != null && !department.getManager().getId().equals(user.getId())) {
	            // 他のユーザーが既に部門長として設定されている場合
	        	logger.error("[ERROR] 部門長は一つの部門につき一人です。");
	            model.addAttribute("errorMessage", "部門長は一つの部門につき一人です。");
	            model.addAttribute("departments", departmentService.getAllDepartments());
	            return "user_edit"; // エラーを表示して、再度編集ページを表示
	        }
	    }

	    // 部門長でない場合、部門のmanagerフィールドをクリア
	    if (!"部門長".equals(user.getRole()) && department.getManager() != null && department.getManager().getId().equals(user.getId())) {
	        department.setManager(null);  // 部門長解除
	    }

	    // 部門長として設定する場合
	    if ("部門長".equals(user.getRole())) {
	        department.setManager(user);
	        try {
	            departmentService.saveDepartment(department);
	        } catch (Exception e) {
	            logger.error("[ERROR] 部門情報の保存中にエラーが発生しました: {}", e.getMessage(), e);
	            model.addAttribute("errorMessage", "部門情報の保存中にエラーが発生しました。");
	            model.addAttribute("departments", departmentService.getAllDepartments());
	            return "user_edit"; // エラーページにリダイレクト
	        }
	    }

	    // ユーザー情報の更新処理
	    try {
	        userService.updateUser(user, loggedInUser);
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー情報の更新中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        model.addAttribute("errorMessage", "ユーザー情報の更新中にエラーが発生しました。");
	        model.addAttribute("departments", departmentService.getAllDepartments());
	        return "user_edit"; // エラーページにリダイレクト
	    }

	    return "redirect:/users";
	}
	
	@PostMapping("/users/delete/{id}")
	@ResponseBody
	public ResponseEntity<String> deleteUser(@PathVariable("id") int userId, @RequestParam("password") String password, HttpSession session) {
		logger.info("[INFO] postのdeleteUserが呼ばれました。userId: {}, password: [****], session: {}", userId, session);
	    try {
	        User loggedInUser = (User) session.getAttribute("user");
	        logger.debug("[DEBUG] 取得したloggedInUser: {}", loggedInUser);

	        // パスワードの検証
	        if (userService.isPasswordCorrect(loggedInUser, password)) {
	            // ユーザー削除
	            userService.deleteUser(userId);
	            logger.info("[INFO] ユーザーが正常に削除されました。");
	            return ResponseEntity.ok("ユーザーが正常に削除されました。");
	        } else {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("パスワードが間違っています。");
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー削除中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ユーザー削除中にエラーが発生しました。");
	    }
	}

	@GetMapping("/users/{id}/details")
	@ResponseBody
	public ResponseEntity<User> getUserDetails(@PathVariable Integer id) {
	    try {
	        User user = userService.getUserById(id);
	        if (user != null) {
	            return ResponseEntity.ok(user);
	        } else {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // ユーザーが見つからない場合
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] ユーザー詳細取得中にエラーが発生しました: {}", e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // エラー発生時
	    }
	}
	
	@ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUserNotFoundException(UserNotFoundException ex, Model model) {
        logger.error("[ERROR] ユーザーが見つかりませんでした: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error_page";
    }

    @ExceptionHandler(InvalidDepartmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidDepartmentException(InvalidDepartmentException ex, Model model) {
        logger.error("[ERROR] 不当な部門例外: {}", ex.getMessage(), ex);
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
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)  // HTTP 409 Conflict エラー
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, Model model) {
        logger.error("[ERROR] データベースの整合性エラーが発生しました。入力内容を確認してください: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "データベースの整合性エラーが発生しました。入力内容を確認してください。");
        return "error_page";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)  // HTTP 403 Forbidden エラー
    public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
        logger.error("[ERROR] アクセスが拒否されました。必要な権限がありません: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "アクセスが拒否されました。必要な権限がありません。");
        return "error_page";
    }
    
    public void setModelMapper(ModelMapper modelMapper) {
    	logger.info("[INFO] setModelMapperが呼ばれました: {}", modelMapper);
        this.modelMapper = modelMapper;
    }

}
