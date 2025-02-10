package jp.co.example.controller;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;
import jp.co.example.service.SalaryService;
import jp.co.example.service.TokenService;
import jp.co.example.service.UserService;

@Controller
public class ManagementController {

	private static final Logger logger = LoggerFactory.getLogger(ManagementController.class);

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserService userService;

	@Autowired
	private SalaryService salaryService;

	@Autowired
	private HttpSession session;

	@Autowired
	private SessionRepository<?> sessionRepository;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
    @Qualifier("genericConversionService")
    private GenericConversionService genericConversionService;
	
	@Autowired
	private ObjectMapper objectMapper;

	@Value("${node.pdf.server.url}")
	private String nodePdfServerUrl;

	@Value("${RETRY_INTERVAL_MS:60000}")
	private long retryInterval;

	@Value("${MAX_RETRIES:5}")
	private int maxRetries;
	
	@Value("${PDF_STORAGE_PATH:/tmp/pdf_reports}") // デフォルト値を明示的に設定
    private String pdfStoragePath;

	@Autowired
	public ManagementController(TokenService tokenService, UserService userService, SalaryService salaryService,
	                             HttpSession session, SessionRepository<?> sessionRepository,
	                             RedisTemplate<String, Object> redisTemplate,
	                             RestTemplate restTemplate) {
	    this.tokenService = tokenService;
	    this.userService = userService;
	    this.salaryService = salaryService;
	    this.session = session;
	    this.sessionRepository = sessionRepository;
	    this.redisTemplate = redisTemplate;
	    this.restTemplate = restTemplate;
	}
	
	@PostConstruct
	public void validateAndInitializePdfStorage() {
	    try {
	        logger.info("[INFO] ManagementController.javaのvalidateAndInitializePdfStorageが呼ばれました。");
	        File directory = new File(pdfStoragePath);
	        logger.debug("[DEBUG] 作成したdirectory: {}", directory);

	        if (!directory.exists()) {
	            if (!directory.mkdirs()) {
	            	logger.error("[ERROR] PDF保存ディレクトリの作成に失敗しました。");
	                throw new RuntimeException("PDF保存ディレクトリの作成に失敗しました。");
	            }
	        }

	        if (!directory.canWrite()) {
	        	logger.error("[ERROR] PDF保存ディレクトリへの書き込み権限がありません。");
	            throw new RuntimeException("PDF保存ディレクトリへの書き込み権限がありません。");
	        }
	    } catch (SecurityException e) {
	        logger.error("[ERROR] セキュリティエラー: PDFストレージの初期化中に書き込み権限の問題が発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("セキュリティエラー: 書き込み権限がありません。", e);
	    } catch (RuntimeException ex) {
	        logger.error("[ERROR] PDFストレージの初期化中にエラーが発生しました: {}", ex.getMessage(), ex);
	        throw ex;
	    } catch (Exception exx) {
	        logger.error("[ERROR] PDFストレージの初期化中に予期しないエラーが発生しました: {}", exx.getMessage(), exx);
	        throw new RuntimeException("予期しないエラーが発生しました。PDFストレージの初期化に失敗しました。", exx);
	    }
	}

	@PostConstruct
    public void checkRedisConnection() {
		logger.info("[INFO] ManagementController.javaのcheckRedisConnectionが呼ばれました");
        for (int i = 0; i < maxRetries; i++) {
            try {
                String pingResponse = redisTemplate.getConnectionFactory().getConnection().ping();
                logger.debug("[DEBUG] Redis接続がアクティブです: {}", pingResponse);
                return;
            } catch (Exception e) {
                logger.error("[ERROR] Redis接続エラーが発生しました (attempt {} of {}): {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", i + 1, maxRetries, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                if (i == maxRetries - 1) {
                	logger.error("[ERROR] Redis接続エラー。最大試行回数に達しました: {}", e.getMessage(), e);
                    throw new RuntimeException("Redis接続エラー。最大試行回数に達しました: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    logger.error("[ERROR] Redis接続が中断されました: {}", interruptedException.getMessage(), interruptedException);
                    throw new RuntimeException("Redis接続が中断されました", interruptedException);
                }
            }
        }
    }
	
	@PostConstruct
	public void checkPdfStoragePath() {
	    logger.info("[INFO] checkPdfStoragePathが呼ばれました");
	    File directory = new File(pdfStoragePath);
	    logger.debug("[DEBUG] 作成したdirectory: {}", directory);
	    Map<String, Object> response = new HashMap<>();
	    
	    try {
	    	if (!directory.exists()) {
	    		if (directory.mkdirs()) {
	    			logger.info("[INFO] PDF保存ディレクトリを作成しました: {}, directory: {}", pdfStoragePath, directory);
	    			response.put("exists", true);
	    			response.put("message", "ディレクトリが作成されました");
	    		} else {
	    			logger.error("[ERROR] PDF保存ディレクトリの作成に失敗しました: {}, directory: {}", pdfStoragePath, directory);
	    			response.put("exists", false);
	    			response.put("message", "ディレクトリ作成失敗");
	    			throw new RuntimeException("PDF保存ディレクトリの作成に失敗しました: " + pdfStoragePath);
	    		}
	    	} else if (!pdfStoragePath.startsWith("/tmp")) {
	    		logger.error("[ERROR] Heroku環境では /tmp 以下にしか書き込めません: {}, directory: {}", pdfStoragePath, directory);
	    		throw new RuntimeException("PDF保存ディレクトリが無効です。Herokuでは /tmp のみが書き込み可能です。");
	    	} else {
	    		logger.info("[INFO] PDF保存ディレクトリは既に存在します: {}, directory: {}", pdfStoragePath, directory);
	    		response.put("exists", true);
	    		response.put("message", "ディレクトリは既に存在します");
	    	}

	    	if (!directory.canWrite()) {
	    		logger.error("[ERROR] PDF保存ディレクトリに書き込み権限がありません: {}, directory: {}", pdfStoragePath, directory);
	    		response.put("exists", false);
	    		response.put("message", "書き込み権限なし");
	    		throw new RuntimeException("PDF保存ディレクトリに書き込み権限がありません: " + pdfStoragePath);
	    	}

	    	logger.info("[INFO] ディレクトリ確認が完了しました - レスポンス: {}, directory: {}, ストレージパス: {}", response, directory, pdfStoragePath);
	    } catch (Exception e) {
	        logger.error("[ERROR] PDF保存ディレクトリ確認中にエラーが発生しました: {}", e.getMessage(), e);
	        throw e;
	    }
	}
	
	@PostConstruct
	public void checkAndInitializePdfStorage() {
		logger.info("[INFO] checkAndInitializePdfStorageが呼ばれました。");
	    try {
	        File directory = new File(pdfStoragePath);
	        logger.debug("[DEBUG] 作成したdirectory: {}", directory);

	        if (!directory.exists() || !directory.isDirectory()) {
	            if (!directory.mkdirs()) {
	                logger.error("[ERROR] PDF保存ディレクトリの作成に失敗しました: {}, directory: {}", pdfStoragePath, directory);
	                throw new RuntimeException("PDF保存ディレクトリの作成に失敗しました");
	            }
	        }

	        if (!directory.canWrite()) {
	            logger.error("[ERROR] PDF保存ディレクトリに書き込み権限がありません: {}, directory: {}", pdfStoragePath, directory);
	            throw new RuntimeException("PDF保存ディレクトリの書き込み権限エラー");
	        }

	        if (!pdfStoragePath.startsWith("/tmp")) {
	        	logger.error("[ERROR] Herokuでは/tmp以下のみが書き込み可能です。");
	            throw new RuntimeException("Herokuでは/tmp以下のみが書き込み可能です。");
	        }
	    } catch (SecurityException e) {
	        logger.error("[ERROR] セキュリティエラー: PDF保存ディレクトリにアクセスする際に問題が発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("セキュリティエラー: PDF保存ディレクトリへのアクセスに失敗しました", e);
	    } catch (RuntimeException ex) {
	        logger.error("[ERROR] PDF保存ディレクトリに関連するエラー: {}", ex.getMessage(), ex);
	        throw ex;
	    } catch (Exception exx) {
	        logger.error("[ERROR] PDF保存ディレクトリの初期化中に予期しないエラーが発生しました: {}", exx.getMessage(), exx);
	        throw new RuntimeException("予期しないエラーが発生しました。PDF保存ディレクトリの初期化に失敗しました", exx);
	    }
	}

	private String refreshTokenWithRetry(String token) {
		logger.info("[INFO] refreshTokenWithRetryが呼ばれました。トークン: {}", token);
	    int attempts = 0;
	    long interval = retryInterval;

	    while (attempts < maxRetries) {
	    	try {
	            String newToken = tokenService.checkAndExtendToken(token);
	            if (newToken != null) {
	            	logger.debug("[DEBUG] 取得したnewTokenを返します: {}", newToken);
	                return newToken;
	            }
	        } catch (Exception e) {
	        	if (e instanceof IllegalArgumentException || e instanceof AccessDeniedException) {
	                logger.error("[ERROR] リトライ対象外の致命的なエラーが発生しました: {}", e.getMessage(), e);
	                throw e; // リトライせずに即座に中断
	            }
	            logger.error("[ERROR] トークンリフレッシュ失敗 (attempt={}): {}", attempts, e.getMessage(), e);
	        }
	        attempts++;
	        logger.info("[INFO] トークンの再取得を試行中 - attempts {}", attempts);

	        // 再試行までの待機時間を増加させる
	        interval *= 2;

	        try {
	            Thread.sleep(interval);
	        } catch (InterruptedException e) {
	        	logger.error("[ERROR] 割り込み例外が発生しました: {}", e.getMessage(), e);
	            Thread.currentThread().interrupt();
	            break;
	        }
	    }
	    logger.error("[ERROR] トークンのリトライ上限に達しました。");
	    return null;
	}

	// 給与管理セクション画面
	@GetMapping("/payroll")
	public String showPayrollManagement(@RequestParam(value = "selectedMonth", required = false) String selectedMonth,
			@RequestParam(value = "employeeSearch", required = false) String employeeSearch, Model model) {
		logger.info("[INFO] showPayrollManagementが呼ばれました。selectedMonth: {}, employeeSearch: {}", selectedMonth, employeeSearch);
		try {
			User loggedInUser = (User) session.getAttribute("user"); // ログインユーザーを取得
			logger.debug("[DEBUG] ログインユーザーを取得しました: {}", loggedInUser);

			YearMonth month = (selectedMonth != null && !selectedMonth.isEmpty()) ? YearMonth.parse(selectedMonth) : YearMonth.now();
			logger.debug("[DEBUG] 取得したmonthを確認します: {}", month);
			
			List<User> users;

			if ("人事管理者".equals(loggedInUser.getRole())) {
				// 人事管理者の場合、全てのユーザーを取得
				if (employeeSearch != null && !employeeSearch.isEmpty()) {
					users = userService.searchUsersByUsername(employeeSearch);
					logger.info("[INFO] 従業員検索結果: {}", employeeSearch);
				} else {
					users = userService.getAllUsersSortedByUsername();
				}
			} else {
				// 非人事管理者の場合、自分自身のみをリストに追加
				users = List.of(loggedInUser);
			}
			
			logger.debug("[DEBUG] 取得したusersを確認します: {}", users);

			List<Salary> salaries = salaryService.getSalariesByMonth(month);
			if (salaries == null || salaries.isEmpty()) {
				logger.debug("[DEBUG] 給与データはnullまたは空です。");
			} else {
				logger.debug("[DEBUG] 給与データが取得されました: " + salaries.size() + "件");
			}
			
			for (Salary salary : salaries) {
			    logger.debug("[DEBUG] Salary データ: {}", salary);
			}

			logger.debug("[DEBUG] {}月の給与{}を取得しました。", month, salaries);
			
			BigDecimal totalDeductions = null;
			for (int i = 0; i < salaries.size(); i++) {
			    Salary salary = salaries.get(i);

			    if (salary == null) {
			        logger.error("[ERROR] salaryがnullです。インデックス: {}", i);
			        continue; // そのデータをスキップして次のループへ
			    }

			    try {
			    	logger.debug("[DEBUG] 総控除を取得するためにSalaryService.javaのcalculateDeductionsを呼びます。salary: {}", salary);
			        totalDeductions = salaryService.calculateDeductions(salary);
			        salary.setDeductions(totalDeductions);
			    } catch (Exception e) {
			        logger.error("[ERROR] salaryの控除計算中にエラーが発生しました。salaryの詳細: {} エラー: {}", salary, e.getMessage(), e);
			    }
			}
			
			logger.debug("[DEBUG] 取得したtotalDeductionsを確認します: {}", totalDeductions);
			
			logger.debug("[DEBUG] usersを確認します: {}", users);
			model.addAttribute("users", users);
			
			logger.debug("[DEBUG] salariesを確認します: {}", salaries);
			model.addAttribute("salaries", salaries);
			
			logger.debug("[DEBUG] selectedMonthを確認します: {}", month.toString());
			model.addAttribute("selectedMonth", month.toString());
			
			logger.debug("[DEBUG] employeeSearchを確認します: {}", employeeSearch);
			model.addAttribute("employeeSearch", employeeSearch);

			return "payroll_management";
		} catch (Exception e) {
			logger.error("[ERROR] 給与管理画面の表示中にエラーが発生しました: {}", e.getMessage(), e);
			model.addAttribute("errorMessage", "給与管理画面の表示中にエラーが発生しました。");
			return "error_page";
		}
	}

	@PostMapping("/calculateSalaryWithOvertime")
	public String calculateSalaryWithOvertime(@RequestParam("userId") Integer userId,
			@RequestParam("year") int year,
			@RequestParam("month") int month,
			RedirectAttributes redirectAttributes) {
		logger.info("[INFO] ManagementController.javaのcalculateSalaryWithOvertime関数が呼ばれました。userId: {}, Year: {}, Month: {}, redirectAttributes: {}", userId, year, month, redirectAttributes);
		User user = null;
		try {
			user = userService.getUserById(userId);
			if (user == null) {
				logger.warn("[WARN] Userが見つかりませんでした: {}", userId);
				redirectAttributes.addFlashAttribute("errorMessage", "指定されたユーザーが見つかりませんでした");
				return "redirect:/salaryManagement";
			}
			logger.debug("[DEBUG] 取得したuser: {}", user);

			//直接残業時間を計算し、給与情報を更新
			YearMonth yearMonth = YearMonth.of(year, month);
			logger.debug("[DEBUG] SalaryService.javaのcalculateSalaryWithOvertime関数を呼びます。user: {}, yearMonth: {}", user, yearMonth);
			salaryService.calculateSalaryWithOvertime(user, yearMonth);
			logger.info("[INFO] SalaryService.javaのcalculateSalaryWithOvertimeが完了し、ManagementController.javaのcalculateSalaryWithOvertimeに戻ってきました。給与計算が成功しました。userId: {} for Year: {}, Month: {}", userId, year, month);

			return "redirect:/salaryManagement";
		} catch (Exception e) {
			logger.error("[ERROR] 給与の計算中にエラーが発生しました。userId: {}, user: {}, Year: {}, Month: {}. Error: {}", userId, user, year, month, e.getMessage(), e);
			redirectAttributes.addFlashAttribute("errorMessage", "給与の計算中にエラーが発生しました。");
			return "redirect:/salaryManagement";
		}
	}

	@GetMapping(value = "/salaryDetails", produces = "text/html; charset=UTF-8")
	public String showSalaryDetails(@RequestParam(value = "userId", required = false) Integer userId,
			@RequestParam(value = "paymentMonth", required = false) String paymentMonth, Model model) {
		logger.info("[INFO] ManagementController.javaのshowSalaryDetailsメソッドが呼び出されました - userId: {}, paymentMonth: {}, model: {}", userId, paymentMonth, model);
		
		model.addAttribute("maxRetries", maxRetries);
	    model.addAttribute("retryInterval", retryInterval);
	    
		try {
			User sessionUser = (User) session.getAttribute("user");
	        logger.debug("[DEBUG] セッションから取得したユーザー情報 - sessionUser: {}", sessionUser);

	        if (sessionUser == null) {
	        	logger.error("[ERROR] セッションが存在しません。再ログインしてください。");
	            model.addAttribute("errorMessage", "セッションが存在しません。再ログインしてください。");
	            return "redirect:/login";
	        }
	        
	        logger.debug("[DEBUG] 現在のユーザーID: {}", userId);
	        logger.debug("[DEBUG] 現在の支払月: {}", paymentMonth);
	        logger.debug("[DEBUG] キャッシュキー: {}", userId + "_" + paymentMonth);

			if (userId == null || paymentMonth == null) {
		        model.addAttribute("errorMessage", "ユーザーIDまたは支払月が指定されていません。");
		        logger.error("[ERROR] 必要なパラメータが不足しています");
		        return "error_page";
		    }
			
			try {
	            User user = userService.getUserById(userId);
	            if (user == null) {
	            	logger.error("[ERROR] ユーザーが見つかりません。ID=" + userId);
	                model.addAttribute("errorMessage", "ユーザーが見つかりません。ID=" + userId);
	                return "error_page";
	            }

	            logger.debug("[DEBUG] 受け取ったユーザーと支払い月を解析。user: {}, paymentMonth: {}", user, paymentMonth);
	            YearMonth month;
	            try {
	                month = (paymentMonth != null) ? YearMonth.parse(paymentMonth) : YearMonth.now();
	            } catch (DateTimeParseException e) {
	                model.addAttribute("errorMessage", "無効な支払月のフォーマットです。YYYY-MM形式で指定してください。");
	                logger.error("[ERROR] 無効な支払月のフォーマットです: {}, ERROR: {}", paymentMonth, e.getMessage(), e);
	                return "error_page";
	            }
	            
	            logger.info("[INFO] ユーザーと月の給与データを取得しています: userId={}, month={}", userId, month);
	            logger.debug("[DEBUG] SalaryService.javaのgetSalaryByUserAndPaymentMonthを呼びます。");
	            Optional<Salary> salaryOpt = salaryService.getSalaryByUserAndPaymentMonth(userId, month);
	            
	            if (salaryOpt.isEmpty()) {
	                logger.error("[ERROR] ユーザーID: {}、支払月: {} に対応する給与情報が見つかりませんでした。", userId, paymentMonth);
	                model.addAttribute("errorMessage", "指定された月の給与情報が見つかりません。ユーザーID: " + userId + "、支払月: " + paymentMonth);
	                return "error_page";
	            }
	            
	            logger.debug("[DEBUG] SalaryService.javaのgetSalaryByUserAndPaymentMonthが完了し、ManagementController.javaのshowSalaryDetailsに戻ってきました。取得したsalaryOptを確認します: {}", salaryOpt);

	            Salary salary = salaryOpt.get();
	            Integer salaryId = salary.getId();
	            model.addAttribute("salary", salary);
	            model.addAttribute("user", user);
	            model.addAttribute("userId", user.getId());
	            model.addAttribute("salaryId", salary.getId());

	            if (user == null || salary == null) {
	                logger.error("[ERROR] ユーザーまたは給与情報が見つかりませんでした。userId: {}, paymentMonth: {}, ユーザー: {}, 給与: {}", userId, paymentMonth, user, salary);
	                model.addAttribute("errorMessage", "ユーザーまたは給与情報が見つかりません。");
	                return "error_page";
	            }

	            logger.debug("[DEBUG] ユーザーID: {}", userId);
	            logger.debug("[DEBUG] 給与ID: {}", salaryId);
	            logger.debug("[DEBUG] ユーザーID: {}", (user != null ? user.getId() : "null"));
	            logger.debug("[DEBUG] 給与ID: {}", (salary != null ? salary.getId() : "null"));
	            logger.debug("[DEBUG] ユーザー: {}", user);
	            logger.debug("[DEBUG] 給与: {}", salary);

	            logger.info("[INFO] ユーザーと給与データが正常に取得されました。userId={}, paymentMonth={}, month={}", userId, paymentMonth, month);
	            return "salary_details";
	        } catch (DateTimeParseException e) {  
	            logger.error("[ERROR] DateTimeParseExceptionが発生しました。詳細: {}", e.getMessage(), e);
	            model.addAttribute("errorMessage", "日付の形式が正しくありません。");
	            return "error_page";
	        } catch (NullPointerException ex) {
	            logger.error("[ERROR] NullPointerExceptionが発生しました。詳細: {}", ex.getMessage(), ex);
	            model.addAttribute("errorMessage", "必須項目が不足しています。管理者に問い合わせてください。");
	            return "error_page";
	        } catch (Exception exx) {
	            logger.error("[ERROR] ユーザーまたは給与情報の取得中に予期しないエラーが発生しました: {}", exx.getMessage(), exx);
	            model.addAttribute("errorMessage", "システムエラーが発生しました。管理者にお問い合わせください。");
	            return "error_page";
	        }
		} catch (Exception exxx) {
			logger.error("[ERROR] ユーザーまたは給与情報の取得中に予期しないエラーが発生しました: {}", exxx.getMessage(), exxx);
	        model.addAttribute("errorMessage", "システムエラーが発生しました。管理者にお問い合わせください。");
	        return "error_page";
		}
	}

	@GetMapping("salaryHistory")
	public String showSalaryHistory() {
		try {
			return "salary_history";
		} catch (Exception e) {
			logger.error("[ERROR] showSalaryHistoryでエラーが発生しました: {}", e.getMessage(), e);
			return "error_page";
		}
	}

	@GetMapping("/salaryManagement")
	public String showSalaryManagement(@RequestParam(value = "employeeSearch", required = false) String employeeSearch,
	        @RequestParam(value = "selectedMonth", required = false) String selectedMonth,
	        Model model) {
		
		logger.info("[INFO] showSalaryManagementが呼ばれました。employeeSearch: {}, selectedMonth: {}, model: {}", employeeSearch, selectedMonth, model);

	    try {
	        
	        // selectedMonthがnullの場合、現在の月を使用
	        YearMonth month = (selectedMonth != null && !selectedMonth.isEmpty()) ? YearMonth.parse(selectedMonth)
	                : YearMonth.now();

	        List<Salary> salaries;

	        //検索条件がある場合は名前で検索し、username順でソート
	        if (employeeSearch != null && !employeeSearch.isEmpty()) {
	            salaries = salaryService.getSalariesByUsernameAndMonthSortedByUsername(employeeSearch, month);
	            logger.debug("[DEBUG] employeeSearch{}とmonth{}によりsalaries{}を取得しました。", employeeSearch, month, salaries);
	        } else {
	            //ない場合は月ごとの全ての給与情報をusername順で表示
	            salaries = salaryService.getSalariesByMonthSortedByUsername(month);
	            logger.debug("[DEBUG] 検索条件がないので月ごとの全ての給与情報をusername順で表示します。month: {}, salaries: {}", month, salaries);
	        }
	        	        
	        //各給与情報に対して控除を計算してセット
	        BigDecimal totalDeductions  = null;
	        for (Salary salary : salaries) {
	            totalDeductions = salaryService.calculateDeductions(salary); // 各給与に対して控除を計算
	            salary.setDeductions(totalDeductions); // 控除額をセット
	        }
	        
	        logger.debug("[DEBUG] 総控除額: {}", totalDeductions);

	        // モデルにデータを追加
	        logger.debug("[DEBUG] salariesを確認します: {}", salaries);
	        model.addAttribute("salaries", salaries);
	        
	        logger.debug("[DEBUG] employeeSearchを確認します: {}", employeeSearch);
	        model.addAttribute("employeeSearch", employeeSearch); // 入力した検索ワードを保持
	        
	        logger.debug("[DEBUG] selectedMonthを確認します: {}", selectedMonth);
	        model.addAttribute("selectedMonth", month.toString());

	        return "salary_management";
	    } catch (Exception e) {
	        logger.error("[ERROR] 給与管理画面の表示中にエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", "給与管理画面の表示中にエラーが発生しました。");
	        return "error_page";
	    }
	}

	// 新規給与情報追加画面の表示
	@GetMapping("/addSalary")
	public String showAddSalaryForm(Model model) {
		logger.info("[INFO] addSalaryが呼ばれました。model: {}", model);
	    try {
	        // ユーザー一覧を取得してフォームに渡す
	        List<User> users = userService.getAllUsers();
	        logger.debug("[DEBUG] 取得したusers: {}", users);
	        model.addAttribute("users", users);
	        return "add_salary";
	    } catch (DataAccessException e) {
	        logger.error("[ERROR] ユーザー一覧の取得中にデータベースエラーが発生しました: {}", e.getMessage(), e);
	        model.addAttribute("error", "ユーザー一覧の取得中にエラーが発生しました。");
	        return "error_page"; // エラーページに遷移
	    } catch (Exception ex) {
	        logger.error("[ERROR] ユーザー一覧の取得中に予期しないエラーが発生しました: {}", ex.getMessage(), ex);
	        model.addAttribute("error", "予期しないエラーが発生しました。");
	        return "error_page"; // エラーページに遷移
	    }
	}

	// 新規給与情報の追加処理
	@PostMapping("/addSalary")
	public String addSalary(
	        @RequestParam("userId") Integer userId,
	        @RequestParam("basicSalary") BigDecimal basicSalary,
	        @RequestParam("allowances") BigDecimal allowances,
	        @RequestParam(value = "overtimeHours", defaultValue = "0") int overtimeHours,
	        @RequestParam(value = "overtimeMinutes", defaultValue = "0") int overtimeMinutes,
	        @RequestParam("paymentMonth") String paymentMonth,
	        RedirectAttributes redirectAttributes) {
		
		logger.info("[INFO] addSalaryが呼ばれました。userId: {}, basicSalary: {}, allowances: {}, overtimeHours: {}, overtimeMinutes: {}, paymentMonth: {}, redirectAttributes: {}", userId, basicSalary, allowances, overtimeHours, overtimeMinutes, paymentMonth, redirectAttributes);

	    try {
	        User user = userService.getUserById(userId);
	        
	        logger.debug("[DEBUG] 取得したuser: {}", user);

	        if (user != null) {
	            YearMonth yearMonth = YearMonth.parse(paymentMonth);
	            
	            // 同じ従業員・同じ月の給与情報が既に存在するか確認
	            logger.debug("[DEBUG] getSalaryByUserAndPaymentMonthを呼びます。userId: {}, yearMonth: {}", userId, yearMonth);
	            Optional<Salary> existingSalary = salaryService.getSalaryByUserAndPaymentMonth(userId, yearMonth);
	            if (existingSalary.isPresent()) {
	            	logger.debug("[DEBUG] この従業員の{}の給与情報は既に登録されています。", yearMonth);
	                redirectAttributes.addFlashAttribute("errorMessage", "この従業員の" + yearMonth + "の給与情報は既に登録されています。");
	                return "redirect:/addSalary";
	            }
	            
	            logger.debug("[DEBUG] 取得したexistingSalary: {}", existingSalary);

	            Salary salary = new Salary();
	            salary.setUser(user);
	            salary.setBasicSalary(basicSalary != null ? basicSalary : BigDecimal.ZERO);
	            salary.setAllowances(allowances != null ? allowances : BigDecimal.ZERO);
	            salary.setOvertimeHours(overtimeHours);
	            salary.setOvertimeMinutes(overtimeMinutes);

	            // 残業時間を計算
	            double totalOvertimeHours = overtimeHours + (overtimeMinutes / 60.0);

	            // 控除額は自動計算
	            BigDecimal totalDeductions = BigDecimal.ZERO;
	            try {
	                totalDeductions = salaryService.calculateDeductions(salary);
	                if (totalDeductions == null) {
	                	logger.error("[ERROR] 計算された控除額がnullです。");
	                    throw new RuntimeException("計算された控除額がnullです。");
	                }
	                salary.setDeductions(totalDeductions);
	            } catch (Exception e) {
	                logger.error("[ERROR] 控除額の計算中にエラーが発生しました: {}", e.getMessage(), e);
	                redirectAttributes.addFlashAttribute("errorMessage", "控除額の計算中にエラーが発生しました。");
	                return "redirect:/addSalary";
	            }

	            // 残業代を計算
	            BigDecimal overtimePay = salaryService.calculateOvertimePay(salary, totalOvertimeHours);
	            logger.debug("[DEBUG] 計算された残業代を確認します: {}", overtimePay);
	            salary.setOvertimePay(overtimePay);

	            // null チェックを追加
	            BigDecimal basicSalaryValue = basicSalary != null ? basicSalary : BigDecimal.ZERO;
	            BigDecimal allowancesValue = allowances != null ? allowances : BigDecimal.ZERO;
	            BigDecimal deductionsValue = totalDeductions != null ? totalDeductions : BigDecimal.ZERO;
	            BigDecimal overtimePayValue = overtimePay != null ? overtimePay : BigDecimal.ZERO;

	            // 総支給額を計算
	            BigDecimal totalSalary = basicSalaryValue.add(allowancesValue).subtract(deductionsValue)
	                    .add(overtimePayValue);
	            salary.setTotalSalary(totalSalary);

	            // 支払月と支払日を設定
	            salary.setPaymentMonth(YearMonth.parse(paymentMonth));
	            salary.setPaymentDate(LocalDate.now());
	            
	            logger.debug("[DEBUG] セットした保存前のsalary: {}", salary);

	            // 給与情報の保存
	            try {
	                salaryService.saveSalary(salary);
	            } catch (Exception e) {
	                logger.error("[ERROR] 給与情報の保存中にエラーが発生しました: {}", e.getMessage(), e);
	                redirectAttributes.addFlashAttribute("errorMessage", "給与情報の保存中にエラーが発生しました。");
	                return "redirect:/addSalary";
	            }
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] 給与情報の追加中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "予期しないエラーが発生しました。");
	        return "redirect:/addSalary";
	    }

	    return "redirect:/salaryManagement";
	}

	@PostMapping("/updatePaymentStatus")
	public String updatePaymentStatus(@RequestParam("salaryId") Integer salaryId, RedirectAttributes redirectAttributes) {
		logger.info("[INFO] updatePaymentStatusが呼ばれました。salaryId: {}, redirectAttributes: {}", salaryId, redirectAttributes);
	    try {
	        Salary salary = salaryService.getSalaryById(salaryId).orElse(null);
	        logger.debug("[DEBUG] 取得したsalary: {}", salary);
	        
	        if (salary != null) {
	            try {
	                salaryService.saveSalary(salary);
	            } catch (Exception e) {
	                logger.error("[ERROR] 給与情報の更新中にエラーが発生しました: {}", e.getMessage(), e);
	                redirectAttributes.addFlashAttribute("errorMessage", "給与情報の更新中にエラーが発生しました。");
	                return "redirect:/salaryManagement";
	            }
	        } else {
	            logger.warn("[WARN] 指定された給与IDに該当する給与情報が見つかりません: {}", salaryId);
	            redirectAttributes.addFlashAttribute("errorMessage", "指定された給与情報が見つかりません。");
	            return "redirect:/salaryManagement";
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] 給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "予期しないエラーが発生しました。給与情報の取得に失敗しました。");
	        return "redirect:/salaryManagement";
	    }

	    return "redirect:/salaryManagement"; // 給与管理ページにリダイレクト
	}

	@PostMapping("/updateSalary")
	public String updateSalary(
	        @RequestParam("salaryId") Integer salaryId,
	        @RequestParam("basicSalary") BigDecimal basicSalary,
	        @RequestParam("allowances") BigDecimal allowances,
	        @RequestParam(value = "overtimeHours", defaultValue = "0") int overtimeHours, // 残業時間
	        @RequestParam(value = "overtimeMinutes", defaultValue = "0") int overtimeMinutes, //残業分
	        @RequestParam("paymentMonth") String paymentMonth,
	        RedirectAttributes redirectAttributes) {
		logger.info("[INFO] updateSalaryが呼ばれました。salaryId: {}, basicSalary: {}, allowances: {}, overtimeHours: {}, overtimeMinutes: {}, paymentMonth: {}, redirectAttributes: {}", salaryId, basicSalary, allowances, overtimeHours, overtimeMinutes, paymentMonth, redirectAttributes);
	    try {
	        Salary salary = salaryService.getSalaryById(salaryId).orElse(null);
	        logger.debug("[DEBUG] 取得したsalary: {}", salary);
	        
	        if (salary != null) {
	            // 給与情報の更新
	            salary.setBasicSalary(basicSalary);
	            salary.setAllowances(allowances);
	            salary.setOvertimeHours(overtimeHours);
	            salary.setOvertimeMinutes(overtimeMinutes);
	            salary.setPaymentMonth(YearMonth.parse(paymentMonth));

	            // 残業時間を計算
	            double totalOvertimeHours = overtimeHours + (overtimeMinutes / 60.0);

	            // 控除額を再計算
	            BigDecimal totalDeductions = salaryService.calculateDeductions(salary);
	            if (totalDeductions == null) {
	                totalDeductions = BigDecimal.ZERO; // もしnullなら0を設定
	            }
	            salary.setDeductions(totalDeductions);

	            // 残業代を計算
	            BigDecimal overtimePay = salaryService.calculateOvertimePay(salary, totalOvertimeHours);
	            if (overtimePay == null) {
	                overtimePay = BigDecimal.ZERO; // null の場合は 0 を設定
	            }
	            salary.setOvertimePay(overtimePay);

	            // 総支給額を再計算
	            salary.setTotalSalary(
	                    salary.getBasicSalary().add(salary.getAllowances()).subtract(totalDeductions).add(overtimePay));
	            
	            logger.debug("[DEBUG] セットした保存する前のsalary: {}", salary);

	            // 給与情報を保存
	            salaryService.saveSalary(salary);
	        } else {
	            logger.warn("[WARN] 給与ID {} に該当する給与情報が見つかりません", salaryId);
	            redirectAttributes.addFlashAttribute("errorMessage", "指定された給与情報が見つかりません。");
	            return "redirect:/salaryManagement?selectedMonth=" + paymentMonth;
	        }
	    } catch (NumberFormatException e) {
	        logger.error("[ERROR] 支払月のパース中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "支払月の形式が無効です。");
	        return "redirect:/salaryManagement?selectedMonth=" + paymentMonth;
	    } catch (Exception ex) {
	        logger.error("[ERROR] 給与情報の更新中にエラーが発生しました: {}", ex.getMessage(), ex);
	        redirectAttributes.addFlashAttribute("errorMessage", "給与情報の更新中にエラーが発生しました。");
	        return "redirect:/salaryManagement?selectedMonth=" + paymentMonth;
	    }

	    return "redirect:/salaryManagement?selectedMonth=" + paymentMonth; // 給与管理ページにリダイレクト
	}

	@GetMapping("/salaryHistoryByMonth")
	public String showSalaryHistoryByMonth(@RequestParam("month") String month, Model model, RedirectAttributes redirectAttributes) {
		logger.info("[INFO] showSalaryHistoryByMonthが呼ばれました。month: {}, model: {}, redirectAttributes: {}", month, model, redirectAttributes);
	    try {
	        YearMonth yearMonth = YearMonth.parse(month); // 月をYearMonth型に変換
	        List<Salary> salariesForMonth = salaryService.getSalariesByMonth(yearMonth); // 月ごとの給与履歴を取得
	        logger.debug("[DEBUG] 取得したyearMonth: {}, salaryForMonth: {}", yearMonth, salariesForMonth);
	        model.addAttribute("salaries", salariesForMonth);
	        return "salary_history";
	    } catch (DateTimeParseException e) {
	        logger.error("[ERROR] 月のパース中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "無効な月の形式です。正しい形式で入力してください。");
	        return "redirect:/salaryManagement"; // エラー発生時は給与管理ページにリダイレクト
	    } catch (Exception e) {
	        logger.error("[ERROR] 給与履歴の取得中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "給与履歴の取得中にエラーが発生しました。");
	        return "redirect:/salaryManagement"; // エラー発生時は給与管理ページにリダイレクト
	    }
	}

	@GetMapping("/copySalaryForNextMonth")
	public String copySalaryForNextMonth(RedirectAttributes redirectAttributes) {
	    try {
	        logger.info("[INFO] copySalaryForNextMonthが呼ばれました: {}", redirectAttributes);
	        salaryService.copySalaryForNextMonth(); // 次月の給与データをコピー

	        redirectAttributes.addFlashAttribute("message", "次月の給与データが正常にコピーされました");
	        return "redirect:/salaryManagement"; // 正常にコピーされた場合は給与管理ページにリダイレクト
	    } catch (Exception e) {
	        logger.error("[ERROR] 次月の給与データのコピー中にエラーが発生しました: {}", e.getMessage(), e);
	        redirectAttributes.addFlashAttribute("errorMessage", "次月の給与データのコピー中にエラーが発生しました。");
	        return "redirect:/salaryManagement"; // エラー発生時は給与管理ページにリダイレクト
	    }
	}

	//手動実行用のエンドポイント
	@PostMapping("/manualPayrollProcess")
	public String manualPayrollProcess(RedirectAttributes redirectAttributes) {
		logger.info("[INFO] manualPayrollProcessが呼ばれました。redirectAttributes: {}", redirectAttributes);
		try {
			// 手動で月次給与処理を実行
			salaryService.processMonthlyPayroll();
			redirectAttributes.addFlashAttribute("successMessage", "給与処理が正常に完了しました");
		} catch (Exception e) {
			logger.error("[ERROR] 給与処理中にエラーが発生しました: {}", e.getMessage(), e);
			redirectAttributes.addFlashAttribute("errorMessage", "給与処理中にエラーが発生しました: " + e.getMessage());
		}
		// リダイレクト先のページ
		return "redirect:/payroll"; // 給与管理画面など、適切なページにリダイレクト
	}
	
	@CrossOrigin(origins = "https://humanage-app-1fe93ce442da.herokuapp.com", methods = { RequestMethod.POST, RequestMethod.OPTIONS }, exposedHeaders = { "Content-Type", "X-Auth-Token", "X-Debug-Info", "X-Debug-Status" })
	@PostMapping("/generateSalaryDetailsPDFByPuppeteer")
	public ResponseEntity<String> generateSalaryDetailsPDFByPuppeteer(
	        @RequestBody Map<String, Object> requestBody,
	        @RequestHeader("X-Auth-Token") String token) {

	    logger.info("[INFO] generateSalaryDetailsPDFByPuppeteerが呼ばれました。リクエストボディ: {}, トークン: {}", requestBody, token);

	    if (nodePdfServerUrl == null || nodePdfServerUrl.isEmpty()) {
	        String errorMessage = "[ERROR] 設定エラー: Node.jsサーバーのURLが設定されていません";
	        logger.error(errorMessage);
	        throw new RuntimeException(errorMessage);
	    }

	    logger.debug("[DEBUG] Node.js PDFサーバーURL: {}", nodePdfServerUrl);
	    
	    //Step 1: ヘッダーの検証
	    if (token == null || token.isEmpty()) {
	        logger.error("[ERROR] X-Auth-Token ヘッダーが指定されていません");
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"X-Auth-Token ヘッダーが不足しています。\"}");
	    }

	    // Step 2: リクエストボディの検証
	    if (!requestBody.containsKey("userId") || !requestBody.containsKey("salaryId")) {
	        logger.error("[ERROR] リクエストボディに必要なフィールドが不足しています: {}", requestBody);
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"リクエストボディに userId または salaryId が含まれていません。\"}");
	    }

	    Integer userId = extractInteger(requestBody, "userId");
	    Integer salaryId = extractInteger(requestBody, "salaryId");
	    
	    logger.debug("[DEBUG] 取得したuserId: {}, salaryId: {}", userId, salaryId);

	    Salary salary = salaryService.getSalaryById(salaryId).orElse(null);
	    if (salary == null || salary.getPaymentMonth() == null) {
	        logger.error("[ERROR] 指定されたIDの給与情報または支払い月が見つかりませんでした: salaryId={}, paymentMonth={}", salaryId, salary != null ? salary.getPaymentMonth() : "null");
	        return ResponseEntity.badRequest()
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"給与情報または支払い月が見つかりません。\"}");
	    }

	    logger.debug("[DEBUG] 取得した給与情報: {}", salary);

	    String paymentMonth = (String) requestBody.get("paymentMonth");
	    logger.debug("[DEBUG] 給与支払月を確認: {}", paymentMonth);
	    requestBody.put("paymentMonth", paymentMonth);
	    
	    String redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;

	    if (userId == null || salaryId == null || paymentMonth == null) {
	        logger.error("[ERROR] 無効な入力パラメータ: userId={}, salaryId={}, paymentMonth={}", userId, salaryId, paymentMonth);
	        setRedisStatus(redisKey, "error");
	        return ResponseEntity.badRequest()
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"userId または salaryId または paymentMonth が指定されていません。\"}");
	    }
	    
	    if (userId == null || salaryId == null) {
	        logger.error("[ERROR] リクエストボディ内のフィールドが無効です: userId={}, salaryId={}", userId, salaryId);
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"userId または salaryId が無効です。\"}");
	    }

	    if (paymentMonth == null || paymentMonth.isEmpty()) {
	        logger.error("[ERROR] リクエストボディ内の paymentMonth が無効です: {}", paymentMonth);
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"paymentMonth が無効です。\"}");
	    }

	    String retryKey = redisKey + ":retries";
	    String lockKey = generateLockKey(userId, salaryId, paymentMonth);
	    logger.debug("[DEBUG] 生成されたRedisキー: {}, Retryキー: {}, lockKey: {}", redisKey, retryKey, lockKey);
	    
	    try {
	        // トークンをリフレッシュ
	        String newToken = refreshTokenWithRetry(token);
	        if (newToken == null) {
	            logger.error("[ERROR] トークンが無効または期限切れです: {}", token);
	            setRedisStatus(redisKey, "error: 無効なトークン");
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body("{\"status\":\"error\", \"message\":\"トークンが無効です。再ログインしてください。\"}");
	        }

	        logger.debug("[DEBUG] PDF生成のためのuserId: {}, salaryId: {}, paymentMonth: {}, newToken: {}", userId, salaryId, paymentMonth, newToken);

	        // 非同期処理の開始
	        CompletableFuture.runAsync(() -> retryAsync(requestBody, newToken, redisKey, lockKey, retryKey, userId, salaryId, paymentMonth, 0))
	                .exceptionally(ex -> {
	                    logger.error("[ERROR] 非同期タスクでエラーが発生しました - redisKey={}, エラー={}, スタックトレース={}, 原因={}, ローカライズメッセージ={}", redisKey, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
	                    setRedisStatus(redisKey, "error: " + ex.getMessage());
	                    return null;
	                });

	        // レスポンスの設定
	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.add("X-Debug-Info", "PDF生成リクエスト受け付けました - userId: " + userId + ", salaryId: " + salaryId + ", paymentMonth: " + paymentMonth);

	        return ResponseEntity.status(HttpStatus.ACCEPTED)
	                .headers(headers)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .header("X-Auth-Token", newToken)
	                .body("{\"message\": \"PDF生成が開始されました\"}");

	    } catch (Exception e) {
	        // エラー処理
	        logger.error("[ERROR] generateSalaryDetailsPDFByPuppeteerでエラーが発生: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        setRedisStatus(redisKey, "error");
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\": \"PDF生成中にエラーが発生しました: " + e.getMessage() + "\"}");
	    }
	}

	private CompletableFuture<Void> retryAsync(Map<String, Object> requestBody, String token, String redisKey,
			String lockKey, String retryKey, Integer userId, Integer salaryId, String paymentMonth, int attempts) {
		try {
			logger.info("[INFO] retryAsyncが呼ばれました。requestBody: {}, token: {}, redisKey: {}, lockKey: {}, retryKey: {}, userId: {}, salaryId: {}, paymentMonth: {}, attempts: {}", requestBody, token, redisKey, lockKey, retryKey, userId, salaryId, paymentMonth, attempts);

			logger.info("[INFO] リトライ試行中 - attempt: {}, lockKey: {}, thread: {}", attempts, lockKey, Thread.currentThread().getName());

			if (attempts >= maxRetries) {
				logger.error("[ERROR] リトライ上限に達しました - redisKey: {}, lockKey: {}, maxRetries: {}", redisKey, lockKey, maxRetries);
				setRedisStatus(redisKey, "error: リトライ上限到達");
				return CompletableFuture.completedFuture(null);
			}

			// Node.js API呼び出し
			return generateSalaryDetailsPDFByNode(requestBody, token)
					.thenCompose(response -> {
						try {
							if (response.getStatusCode().is2xxSuccessful()) {
								logger.info("[INFO] generateSalaryDetailsPDFByNodeの呼び出しが成功しました - requestBody: {}, redisKey: {}, lockKey: {}", requestBody, redisKey, lockKey);
								setRedisStatus(redisKey, "processing");
								return CompletableFuture.completedFuture(null);
							} else {
								logger.warn("[WARN] Node.js APIの呼び出し中にエラーが発生しました - responseCode: {}, responseBody: {}, retryCount: {}", response.getStatusCode(), response.getBody(), attempts);
								setRedisStatus(redisKey, "error: " + response.getBody());
								return retryAsyncWithDelay(requestBody, token, redisKey, lockKey, retryKey, userId, salaryId, paymentMonth, attempts + 1);
							}
						} catch (Exception ex) {
							logger.error("[ERROR] thenComposeブロック内で例外が発生しました - redisKey={}, lockKey={}, 詳細={}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisKey, lockKey, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
							setRedisStatus(redisKey, "error: " + ex.getMessage());
							return CompletableFuture.completedFuture(null);
						}
					}).exceptionally(ex -> {
						logger.error("[ERROR] 非同期タスクでエラーが発生しました - redisKey={}, lockKey={}, エラー={}, スタックトレース={}, 原因={}, ローカライズメッセージ={}", redisKey, lockKey, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
						setRedisStatus(redisKey, "error: " + ex.getMessage());
						return null;
					});
		} catch (Exception ex) {
			logger.error("[ERROR] retryAsyncメソッドで予期しないエラーが発生しました - redisKey={}, lockKey={}, 詳細={}, スタックトレース: {}, 原因: {}, ローカルメッセージ: {}", redisKey, lockKey, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
			setRedisStatus(redisKey, "error: " + ex.getMessage());
			return CompletableFuture.completedFuture(null);
		}
	}
	
	private CompletableFuture<Void> retryAsyncWithDelay(Map<String, Object> requestBody, String token, String redisKey, String lockKey, String retryKey, Integer userId, Integer salaryId, String paymentMonth, int attempts) {
		logger.info("[INFO] retryAsyncWithDelayが呼ばれました。リクエストボディ: {}, token: {}, redisKey: {}, lockKey: {}, retryKey: {}, userId: {}, salaryId: {}, paymentMonth: {}, attempts: {}", requestBody, token, redisKey, lockKey, retryKey, userId, salaryId, paymentMonth, attempts);
		logger.info("[INFO] リトライ試行中 - attempt: {}, lockKey: {}, thread: {}", attempts, lockKey, Thread.currentThread().getName());

		if (attempts >= maxRetries) {
	        logger.error("[ERROR] リトライ上限に到達 - redisKey: {}", redisKey);
	        setRedisStatus(redisKey, "error: リトライ上限到達");
	        return CompletableFuture.completedFuture(null);
	    }

	    long delay = Math.min(retryInterval * (attempts + 1), 60000); // 最大遅延60秒
	    return CompletableFuture.runAsync(() -> {
	        try {
	            Thread.sleep(delay);
	        } catch (InterruptedException e) {
	            Thread.currentThread().interrupt();
	            logger.error("[ERROR] リトライ待機中に割り込み - redisKey: {}, ERROR: {}", redisKey, e.getMessage(), e);
	        }
	    }).thenCompose(ignored -> retryAsync(requestBody, token, redisKey, lockKey, retryKey, userId, salaryId, paymentMonth, attempts + 1));
	}
	
	@CrossOrigin(origins = "https://humanage-app-1fe93ce442da.herokuapp.com", methods = { RequestMethod.POST, RequestMethod.OPTIONS }, exposedHeaders = {"Content-Type", "X-Auth-Token"})
	@PostMapping("/generateSalaryDetailsPDFByNode")
	public CompletableFuture<ResponseEntity<String>> generateSalaryDetailsPDFByNode(
	        @RequestBody Map<String, Object> requestBody,
	        @RequestHeader("X-Auth-Token") String token) {

	    logger.info("[INFO] generateSalaryDetailsPDFByNodeが呼ばれました。リクエストボディ: {}, トークン: {}", requestBody, token);
	    
	    requestBody.put("token", token);
	    logger.debug("[DEBUG] リクエストボディのトークンを追加しました。リクエストボディ: {}", requestBody);

	    if (nodePdfServerUrl == null || nodePdfServerUrl.isEmpty()) {
	        String errorMessage = "[ERROR] 設定エラー: Node.jsサーバーのURLが設定されていません";
	        logger.error(errorMessage);
	        throw new RuntimeException(errorMessage);
	    }

	    Integer userId = extractInteger(requestBody, "userId");
	    Integer salaryId = extractInteger(requestBody, "salaryId");

	    if (userId == null || salaryId == null) {
	        logger.error("[ERROR] 無効な userId または salaryId: userId={}, salaryId={}", userId, salaryId);
	        return CompletableFuture.completedFuture(ResponseEntity.badRequest()
	                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                .body("{\"status\":\"error\", \"message\":\"userId または salaryId が指定されていません。\"}"));
	    }
	    
	    String paymentMonth = (String) requestBody.getOrDefault("paymentMonth", "unknown");
        String redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;

	    logger.debug("[DEBUG] Node.js PDF生成を非同期で処理開始 - userId: {}, salaryId: {}, paymentMonth: {}, redisKey: {}", userId, salaryId, paymentMonth, redisKey);

	    return CompletableFuture.supplyAsync(() -> {
	        try {	            
	            // HTTPリクエストを作成
	            HttpHeaders headers = new HttpHeaders();
	            headers.set("X-Auth-Token", token);
	            headers.setContentType(MediaType.APPLICATION_JSON);
	            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
	            
	            logger.debug("[DEBUG] 取得したリクエストボディ: {}, headers: {}, request: {}", requestBody, headers, request);
	            
	            String port = System.getenv("PORT");
	           
	            logger.debug("[DEBUG] System.getenv(\"PORT\")によってherokuから取得したポート番号: {}", port);
	            
	            String url = nodePdfServerUrl + "/api/generatePDF/node";
	            logger.debug("[DEBUG] Node.js PDFサーバーURL: {}", nodePdfServerUrl);

	            logger.debug("[DEBUG] /node-pdf-server/にあるindex.jsのNode.jsサーバーにリクエスト送信 - URL: {}", url);
	            logger.debug("[DEBUG] リクエストURL: " + url);
	            logger.debug("[DEBUG] リクエストボディ: " + request.toString());
	            logger.debug("[DEBUG] リクエストヘッダー: " + headers.toString());
	            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
	            logger.debug("[DEBUG] Node.js APIレスポンス - status: {}, body: {}, response: {}", response.getStatusCode(), response.getBody(), response);
	            
	            if (response.getStatusCode().is2xxSuccessful()) {
	                logger.info("[INFO] Node.js APIの呼び出しが成功しました - redisKey: {}, status: {}, body: {}", redisKey, response.getStatusCode(), response.getBody());
	                //setRedisStatus(redisKey, "complete");
	                return ResponseEntity.ok()
	                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
	                        .body("{\"status\":\"processing\", \"message\":\"Node.js APIの呼び出しが成功しました。\"}");
	            } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
	                logger.error("[ERROR] Node.js APIエラー - リソースが見つかりません: URL={}, ステータスコード: {}, body={}", url, response.getStatusCode(), response.getBody());
	                setRedisStatus(redisKey, "error: APIエラー" + response.getBody());
	                throw new RuntimeException("リソースが見つかりません: " + url);
	            } else {
	                logger.error("[ERROR] Node.js APIエラー - ステータス: {}, ボディ: {}", response.getStatusCode(), response.getBody());
	                setRedisStatus(redisKey, "error: APIエラー" + response.getBody());
	                throw new RuntimeException("Node.js APIエラー: " + response.getBody());
	            }
	        } catch (Exception e) {
	            logger.error("[ERROR] Node.js API通信エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	            setRedisStatus(redisKey, "error");
	            throw new CompletionException(e);
	        }
	    });
	}
	
	@PostMapping("/api/generatePDF/node")
    public ResponseEntity<?> enqueuePdfGeneration(@RequestBody Map<String, Object> requestBody) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        logger.info("[INFO] enqueuePdfGenerationが呼ばれました。リクエストボディ: {}, 呼び出し元: {}", requestBody, callingMethodName);
        
        if (!requestBody.containsKey("token")) {
            logger.error("[ERROR] トークンが不足しています");
            return ResponseEntity.badRequest().body("トークンが必要です");
        }

        try {
            // Redisにタスクを追加
            String redisQueueKey = "pdf:tasks";
            String jsonRequestBody = objectMapper.writeValueAsString(requestBody);
            logger.debug("[DEBUG] Redisのキュー({})にタスクを追加中。JSON化したリクエストボディ: {}", redisQueueKey, jsonRequestBody);
            redisTemplate.opsForList().leftPush(redisQueueKey, jsonRequestBody);

            logger.info("[INFO] タスクがRedisキューに正常に追加されました: {}", jsonRequestBody);
            return ResponseEntity.ok("PDF生成タスクが正常にキューに追加されました");
        } catch (Exception e) {
            logger.error("[ERROR] タスクのキュー追加中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return ResponseEntity.status(500).body("タスクのキュー追加中にエラーが発生しました: " + e.toString());
        }
    }

	private Integer extractInteger(Map<String, Object> requestBody, String key) {
	    logger.info("[INFO] extractIntegerが呼ばれました。リクエストボディ: {}, key: {}", requestBody, key);
	    try {
	        Object value = requestBody.get(key);

	        // 値が null の場合
	        if (value == null) {
	            logger.warn("[WARN] キー '{}' の値が null です。リクエストボディ: {}", key, requestBody);
	            return null;
	        }

	        // 値の型情報を記録
	        logger.info("[INFO] キー '{}' に対応する値の型: {}, 値: {}", key, value.getClass().getName(), value);

	        // 値が Integer の場合
	        if (value instanceof Integer) {
	            logger.info("[INFO] キー '{}' の値は Integer 型です。そのまま返します。値: {}", key, value);
	            return (Integer) value;
	        }

	        // 値が Long の場合
	        if (value instanceof Long) {
	            logger.info("[INFO] キー '{}' の値は Long 型です。int に変換して返します。値: {}", key, value);
	            return ((Long) value).intValue();
	        }

	        // 値が Double の場合
	        if (value instanceof Double) {
	            logger.info("[INFO] キー '{}' の値は Double 型です。int に変換して返します。値: {}", key, value);
	            return ((Double) value).intValue();
	        }

	        // その他の型の場合（文字列など）
	        logger.info("[INFO] キー '{}' の値はその他の型（{}）です。文字列として扱い、整数に変換を試みます。値: {}", key, value.getClass().getName(), value);
	        return Integer.parseInt(value.toString());

	    } catch (Exception e) {
	        // 例外発生時のログ
	        logger.error("[ERROR] キー '{}' の値を整数に変換できませんでした。リクエストボディ: {}, エラーメッセージ: {}", key, requestBody, e.getMessage(), e);
	        return null;
	    }
	}

	@PostMapping("/saveToRedis")
	public ResponseEntity<String> saveTokenToRedis(@RequestParam String token) {
		logger.info("[INFO] aveTokenToRedisが呼ばれました。トークン: {}", token);
		try {
			User user = (User) session.getAttribute("user");
			if (user != null) {
				// Redisにトークンとユーザー情報を保存
				redisTemplate.opsForValue().set("token:" + token, user);
				logger.info("[INFO] トークンをRedisに保存しました: user: {}, userId={}, token={}", user, user.getId(), token);
				return ResponseEntity.ok("トークンがRedisに保存されました");
			} else {
				logger.warn("[WARN] セッションにユーザー情報が見つかりません。user: {}", user);
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("ユーザーがセッションに存在しません");
			}
		} catch (Exception e) {
			logger.error("[ERROR] トークンのRedis保存エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("トークンの保存に失敗しました");
		}
	}

	// Redisからトークンとユーザー情報を取得して確認
	@GetMapping("/getFromRedis")
	public ResponseEntity<String> getTokenFromRedis(@RequestParam String token) {
		logger.info("[INFO] getTokenFromRedisが呼ばれました。token: {}", token);
		try {
			User user = (User) redisTemplate.opsForValue().get("token:" + token);
			if (user != null) {
				logger.info("[INFO] Redisからトークンに関連するユーザーを取得しました: user: {}, userId={}, token={}", user, user.getId(), token);
				return ResponseEntity.ok("トークンが有効です");
			} else {
				logger.warn("[WARN] Redisにトークンが存在しません: user: {}, token={}", user, token);
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("トークンが見つかりません");
			}
		} catch (Exception e) {
			logger.error("[ERROR] Redisからトークン取得エラー: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("トークン取得中にエラーが発生しました");
		}
	}

	// セッションの削除時のログ出力を追加
	@PostMapping("/deleteSession")
	public ResponseEntity<String> deleteSession(@RequestHeader("X-Auth-Token") String token) {
		logger.info("[INFO] deleteSessionが呼ばれました。token: {}", token);
		if (tokenService.isTokenExpired(token)) {
			logger.warn("[WARN] トークンが無効です。再ログインしてください。");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body("{\"message\": \"トークンが無効です。再ログインしてください。\"}");
		}
		try {
			sessionRepository.deleteById(token);
			logger.info("[INFO] セッションを削除しました。トークン: {}", token);
			return ResponseEntity.ok("セッションが削除されました");
		} catch (Exception e) {
			logger.error("[ERROR] セッション削除エラー: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("セッション削除中にエラーが発生しました");
		}
	}
	
	private void setRedisStatus(String redisKey, String status) {
	    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
	    String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
	    logger.info("[INFO] setRedisStatusが呼ばれました - キー: {}, ステータス: {}, 呼び出し元: {}", redisKey, status, callingMethodName);

	    if (status == null || status.isEmpty()) {
	        logger.error("[ERROR] 無効なステータスをRedisに設定しようとしています - キー: {}, ステータス: {}", redisKey, status);
	        throw new IllegalArgumentException("無効なステータスが渡されました: " + redisKey + ", status: " + status);
	    }

	    try {
	        redisTemplate.opsForValue().set(redisKey, status, Duration.ofSeconds(40));
	        logger.info("[INFO] Redisキー '{}' を更新しました - ステータス: {}", redisKey, status);
	    } catch (Exception e) {
	        logger.error("[ERROR] Redisステータス設定中に予期しないエラーが発生しました - キー: {}, ステータス: {}, エラー: {}", redisKey, status, e.toString(), e);
	        throw new RuntimeException("Redisステータス設定失敗 - " + redisKey + ", status:" + status, e);
	    }
	}
	
	private String generateLockKey(Integer userId, Integer salaryId, String paymentMonth) {
		logger.info("[INFO] generateLockKeyが呼ばれました。userId: {}, salaryId: {}, paymentMonth: {}", userId, salaryId, paymentMonth);
	    try {
	        // 引数がnullの場合に処理を行う前にチェック
	        if (userId == null || salaryId == null || paymentMonth == null) {
	        	logger.error("[ERROR] 引数にnullが含まれています。userId, salaryId, paymentMonth はすべて必須です");
	            throw new IllegalArgumentException("引数にnullが含まれています。userId, salaryId, paymentMonth はすべて必須です。");
	        }

	        // フォーマットしてロックキーを生成
	        return String.format("pdf_status:%d:%d:%s:lock", userId, salaryId, paymentMonth);
	    } catch (IllegalArgumentException e) {
	        logger.error("[ERROR] generateLockKeyメソッドで引数に問題が発生しました: {}", e.getMessage(), e);
	        throw e;  // IllegalArgumentException はそのまま投げる
	    } catch (Exception ex) {
	        logger.error("[ERROR] generateLockKeyメソッドで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
	        throw new RuntimeException("ロックキー生成中に予期しないエラーが発生しました。", ex);
	    }
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, Model model) {
		logger.error("[ERROR] データの整合性に問題があります: {}", ex.getMessage(), ex);
		model.addAttribute("errorMessage", "データの整合性に問題があります。");
		return "error_page";
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
		logger.error("[ERROR] アクセスが拒否されました。必要な権限がありません: {}", ex.getMessage(), ex);
		model.addAttribute("errorMessage", "アクセスが拒否されました。必要な権限がありません。");
		return "error_page";
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public String handleGeneralException(Exception ex, Model model) {
	    logger.error("[ERROR] システムエラー: {}", ex.getMessage(), ex);
	    model.addAttribute("errorMessage", "エラーが発生しました。詳細: " + ex.getMessage());
	    return "error_page";
	}
	
	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
	    logger.error("[ERROR] システムエラー: {}", ex.getMessage(), ex);
	    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .body("{\"status\":\"error\", \"message\":\"システムエラーが発生しました。\"}");
	}
}
