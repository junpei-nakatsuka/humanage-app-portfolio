package jp.co.example.controller;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;
import jp.co.example.service.LockService;
import jp.co.example.service.SalaryService;
import jp.co.example.service.TokenService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
public class PdfController {

	private static final Logger logger = LoggerFactory.getLogger(PdfController.class);
	
	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private SalaryService salaryService;
	
	@Autowired
	private LockService lockService;
	
	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private RestTemplateBuilder restTemplateBuilder;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired
	private ObjectMapper objectMapper;
	
	@Autowired
	private S3Client s3Client;
	
	@Value("${RETRY_INTERVAL_MS:60000}")
    private long retryInterval;

    @Value("${MAX_RETRIES:5}")
    private int maxRetries;
    
    @Value("${S3_BUCKET_NAME}")
    private String bucketName;
    
    @Value("${PDF_STORAGE_PATH:/tmp/pdf_reports}")
    private String pdfStoragePath;
    
    @Autowired
    private HttpSession session;
    
    @PostConstruct
	public void validateAndInitializePdfStorage() {
	    try {
	        logger.info("[INFO] ManagementController.javaのvalidateAndInitializePdfStorageが呼ばれました。");
	        File directory = new File(pdfStoragePath);
	        logger.debug("[DEBUG] 作成したdirectory: {}", directory);
	        if (!directory.exists()) {
	            if (!directory.mkdirs()) {
	            	logger.error("[ERROR] PDF保存ディレクトリの作成に失敗しました");
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
                    throw new RuntimeException("Redis接続エラー after maximum retries:" + e.getMessage(), e);
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
    public void logBaseUrl() {
    	logger.info("[INFO] logBaseUrlが呼ばれました。");
        try {
            String baseUrl = System.getenv("BASE_URL");
            if (baseUrl == null) {
                logger.warn("[WARN] BASE_URLの値は設定されていません。");
            } else {
                logger.info("[INFO] BASE_URLの値: {}", baseUrl);
            }
        } catch (Exception e) {
            logger.error("[ERROR] BASE_URLの取得中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("BASE_URLの取得中に予期しないエラーが発生しました。", e);
        }
    }
        
    @PostConstruct
    public void validatePdfStoragePath() {
        try {
            logger.info("[INFO] validatePdfStoragePathが呼ばれました");
            File storageDir = new File(pdfStoragePath);
            
            logger.debug("[DEBUG] 作成したstorageDirを確認します: {}", storageDir);
            if (!storageDir.exists()) {
                logger.warn("[WARN] PDF保存ディレクトリが存在しません。作成を試みます: {}", pdfStoragePath);
                if (!storageDir.mkdirs()) {
                    logger.error("[ERROR] PDF保存ディレクトリの作成に失敗しました: {}", pdfStoragePath);
                    throw new RuntimeException("PDF保存ディレクトリの作成に失敗しました: " + pdfStoragePath);
                }
            }

            if (!storageDir.canWrite()) {
                logger.error("[ERROR] ディレクトリへの書き込み権限がありません: {}", pdfStoragePath);
                throw new RuntimeException("PDF保存ディレクトリに書き込み権限がありません: " + pdfStoragePath);
            }

            long freeSpace = storageDir.getUsableSpace();
            if (freeSpace < 10 * 1024 * 1024) { // 10MB未満
                logger.error("[ERROR] ディスク容量が不足しています - 空き容量: {} bytes", freeSpace);
                throw new RuntimeException("ディスク容量が不足しています。");
            }

            logger.info("[INFO] PDF保存ディレクトリの確認完了: {}", pdfStoragePath);
        } catch (SecurityException e) {
            logger.error("[ERROR] セキュリティエラー: PDF保存ディレクトリのアクセスに関するエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("セキュリティエラー: PDF保存ディレクトリへのアクセスに失敗しました。", e);
        } catch (Exception e) {
            logger.error("[ERROR] PDF保存ディレクトリの確認中に予期しないエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("予期しないエラーが発生しました。PDF保存ディレクトリの確認に失敗しました。", e);
        }
    }

    @PostConstruct
    public void logPdfStoragePath() {
    	logger.info("[INFO] logPdfStoragePathが呼ばれました");
        logger.info("[INFO] PDF_STORAGE_PATH: {}", pdfStoragePath);
    }
    
    @Bean
    public RestTemplate createRestTemplate() {
        try {
            logger.info("[INFO] createRestTemplateが呼ばれました。");

            if (restTemplateBuilder == null) {
                logger.error("[ERROR] restTemplateBuilderが初期化されていません。");
                throw new RuntimeException("restTemplateBuilderが初期化されていません。");
            }

            // タイムアウト設定
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(4000))
                    .setResponseTimeout(Timeout.ofMilliseconds(4000))
                    .build();
            
            logger.debug("[DEBUG] 設定したrequestConfigを確認します: {}", requestConfig);

            // SSLContextの作成
            SSLContext sslContext = SSLContextBuilder.create().build();
            
            logger.debug("[DEBUG] 設定したsslContextを確認します: {}", sslContext);
            
            // SSLConnectionSocketFactoryの作成
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE // ホスト名検証を無効にする
            );
            
            logger.debug("[DEBUG] 設定したsslSocketFactoryを確認します: {}", sslSocketFactory);
            
            // ソケットファクトリの設定
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("https", sslSocketFactory)
                    .register("http", new SSLConnectionSocketFactory(SSLContextBuilder.create().build())) // HTTP用
                    .build();
            
            logger.debug("[DEBUG] 設定したsocketFactoryRegistryを確認します: {}", socketFactoryRegistry);

            // 接続プールの設定
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(50); // 最大接続数
            connectionManager.setDefaultMaxPerRoute(20); // 各ルートごとの最大接続数
            
            logger.debug("[DEBUG] 設定したconnectionManagerを確認します: {}", connectionManager);

            // HttpClientの作成
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)  // タイムアウト設定を適用
                    .setConnectionManager(connectionManager)
                    .setConnectionManagerShared(true)
                    .build();
            
            logger.debug("[DEBUG] 設定したhttpClientを確認します: {}", httpClient);

            // RestTemplateの作成
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            logger.debug("[DEBUG] 設定したfactoryを確認します: {}", factory);
            return new RestTemplate(factory);

        } catch (Exception e) {
            logger.error("[ERROR] RestTemplateの作成中にエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("RestTemplateの作成中にエラーが発生しました。", e);
        }
    }
	
    private <T> T retryOperation(Callable<T> operation, int maxRetries, long initialRetryInterval) throws S3Exception, SdkClientException {
        logger.info("[INFO] retryOperation が呼び出されました: operation={}, maxRetries={}, initialRetryInterval={}", operation, maxRetries, initialRetryInterval);
        
        int attempt = 0;
        long retryInterval = initialRetryInterval;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                logger.debug("[DEBUG] リトライ操作を実行します: 現在の試行回数: {}", attempt + 1);
                T result = operation.call();
                logger.info("[INFO] リトライ操作が成功しました: 試行回数: {}", attempt + 1);
                return result;
            } catch (Exception e) {
                attempt++;
                lastException = e;
                logger.error("[ERROR] リトライ処理中 ({}回目): エラーが発生しました。operation={}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", attempt, operation, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());

                if (attempt >= maxRetries) {
                    logger.error("[ERROR] 最大リトライ回数に到達しました: maxRetries={}, 最後のエラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", maxRetries, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                    throw new RuntimeException("操作が最大リトライ回数に達しました: " + maxRetries + ", エラー: " + e.getMessage(), e);
                }

                logger.info("[INFO] 再試行の準備中: 次の試行まで {} ms 待機します", retryInterval);
                try {
                    Thread.sleep(retryInterval);
                    retryInterval *= 2; // 再試行間隔を指数的に増加
                    logger.debug("[DEBUG] 再試行間隔を増加: 次の間隔は {} ms です", retryInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("[ERROR] 再試行処理が中断されました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ie.toString(), Arrays.toString(ie.getStackTrace()), ie.getCause() != null ? ie.getCause().toString() : "原因不明", ie.getLocalizedMessage());
                    throw new RuntimeException("再試行処理が中断されました: " + ie.getMessage(), ie);
                }
            }
        }

        logger.error("[ERROR] 操作のリトライが最大回数に達しました: maxRetries={}, 最後のエラーメッセージ: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", maxRetries, lastException.toString(), Arrays.toString(lastException.getStackTrace()), lastException.getCause() != null ? lastException.getCause().toString() : "原因不明", lastException.getLocalizedMessage());
        throw new RuntimeException("操作のリトライが最大回数に達しました: " + maxRetries + ", 最後のエラー: " + lastException.getMessage(), lastException);
    }

	@PostMapping("/api/checkSession")
    public ResponseEntity<Map<String, String>> checkSession(@RequestHeader("X-Auth-Token") String token) {
        logger.info("[INFO] checkSessionが呼ばれました - トークン: {}", token);

        Map<String, String> response = new HashMap<>();
        try {
            if (tokenService.isTokenExpired(token)) {
                logger.error("[ERROR] トークンの有効期限が切れています。再ログインしてください: {}", token);
                response.put("message", "トークンの有効期限が切れています。再ログインしてください。");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String userJson = getTokenFromRedis(token);
            if (userJson == null) {
            	logger.error("[ERROR] セッションが無効です。再ログインしてください。");
                response.put("message", "セッションが無効です。再ログインしてください。");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            logger.debug("[DEBUG] 取得したuserJsonを確認します: {}", userJson);

            logger.info("[INFO] セッションが有効です。トークン: {}", token);
            response.put("message", "セッションは有効です");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("[ERROR] セッション確認エラー: {}", e.getMessage(), e);
            response.put("message", "サーバーでエラーが発生しました。管理者にお問い合わせください。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
	
	@CrossOrigin(origins = "https://humanage-app-1fe93ce442da.herokuapp.com", exposedHeaders = { "Content-Type", "X-Auth-Token", "X-Debug-Info", "X-Debug-Status" })
	@PostMapping("/api/generatePDF")
	public ResponseEntity<String> generatePDF(
			@RequestBody Map<String, Object> requestBody,
			@RequestHeader("X-Auth-Token") String token) {
		logger.info("[INFO] PdfController.javaのgeneratePDFメソッドが呼び出されました - リクエストボディ: {}, token: {}", requestBody, token);

		if (!requestBody.containsKey("userId") || !requestBody.containsKey("salaryId")) {
			logger.error("[ERROR] リクエストボディが不完全です: {}", requestBody);
			return ResponseEntity.badRequest().body("{\"message\":\"userIdおよびsalaryIdは必須です。\"}");
		}

		if (!requestBody.containsKey("paymentMonth")) {
			logger.error("[ERROR] リクエストボディが不完全です - paymentMonthが見つかりません: {}", requestBody);
			return ResponseEntity.badRequest().body("{\"message\":\"paymentMonthは必須です。\"}");
		}

		Integer userId = Integer.parseInt(requestBody.get("userId").toString());
		Integer salaryId = Integer.parseInt(requestBody.get("salaryId").toString());

		Salary salary = salaryService.getSalaryById(salaryId).orElseThrow(() -> new RuntimeException("指定されたIDの給与情報が見つかりませんでした: " + salaryId));

		logger.debug("[DEBUG] 取得したuserId: {}, salaryId: {}, salary: {}", userId, salaryId, salary);

		validatePdfStoragePath();

		String paymentMonth = (salary != null && salary.getPaymentMonth() != null) ? salary.getPaymentMonth().toString() : "unknown";
		logger.debug("[DEBUG] Salaryの支払い月情報 - salaryId: {}, paymentMonth: {}", salaryId, paymentMonth);

		String redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;
		String lockKey = redisKey + ":lock";
		logger.debug("[DEBUG] 取得したredisKey: {}, lockKey: {}", redisKey, lockKey);

		if ("unknown".equals(paymentMonth)) {
			logger.error("[ERROR] 支払い月がunknownです。Redisキー生成に失敗する可能性があります: {}", redisKey);
		}

		requestBody.put("paymentMonth", salary.getPaymentMonth().toString());

		String lockOwner = lockService.acquireLock(lockKey, 40);
		if (lockOwner == null) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("{\"message\":\"現在他のプロセスがPDF生成中です\", \"status\":\"processing\"}");
		}
		
		String refreshedToken = null;
		try {
			refreshedToken = tokenService.refreshTokenIfNecessary(token);
			if (refreshedToken == null) {
				logger.error("[ERROR] トークンが無効または期限切れです: {}", token);
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.body("{\"message\":\"セッションが無効です。再ログインしてください。\"}");
			}

			logger.debug("[DEBUG] 取得したrefreshedToken: {}", refreshedToken);

			//トークンからユーザー情報を取得
			User user = tokenService.verifyTokenAndFetchUser(refreshedToken);
			if (user == null) {
				logger.error("[ERROR] ユーザー認証エラー: トークンが無効または期限切れです - token: {}, refreshedToken: {}", token, refreshedToken);
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.body("{\"message\":\"ユーザー認証に失敗しました。\"}");
			}

			logger.debug("[DEBUG] 取得したuser: {}", user);

			// リクエストされたユーザーIDとセッションのユーザーIDの整合性を確認
			String loggedInUserId = (session.getAttribute("user") != null) ? String.valueOf(((User) session.getAttribute("user")).getId()) : "";
			if (!user.getId().equals(userId) && !user.getRole().equals("人事管理者")) {
				logger.warn("[WARN] ユーザーIDの不一致 - userId: {}, tokenUserId: {}, ログインユーザーの役職: {}, loggedInUserId: {}",userId, user.getId(), user.getRole(), loggedInUserId);
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body("{\"message\":\"他のユーザーの給与明細へのアクセスは許可されていません。\"}");
			}

			// ログにリクエスト情報を出力
			logger.debug("[DEBUG] PDF生成リクエスト - userId: {}, salaryId: {}, paymentMonth: {}, loggedInUserId: {}", userId, salaryId, paymentMonth, loggedInUserId);

			if (!loggedInUserId.equals(String.valueOf(userId)) && !user.getRole().equals("人事管理者")) {
				logger.warn("[WARN] セッションのユーザーIDとリクエストのユーザーIDが一致しません - loggedInUserId: {}, requestUserId: {}", loggedInUserId, userId);
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.body("{\"message\":\"セッションのユーザーIDが一致しません。または権限が不足しています。\", \"status\":\"error\"}");
			}

			String fileName = getFileName(userId, salaryId, paymentMonth);
			logger.debug("[DEBUG] 生成されるファイル名: {}", fileName);

			File directory = new File(pdfStoragePath);
			logger.debug("[DEBUG] 作成したdirectory: {}", directory);
			if (!directory.exists() || !directory.canWrite()) {
				logger.error("[ERROR] PDF保存ディレクトリが不正: 存在しない、または書き込み権限なし - Path: {}", pdfStoragePath);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body("{\"message\":\"PDF保存ディレクトリが正しくありません。書き込み権限を確認してください。\", \"status\":\"error\"}");
			}

			File file = new File(pdfStoragePath, fileName);
			logger.debug("[DEBUG] 作成したfile: {}", file);
			if (!file.exists() || file.length() == 0) {
				logger.warn("[WARN] PDFファイルが存在しないか空です: {}, getPath: {}", file.getAbsolutePath(), file.getPath());
				setRedisStatus(redisKey, "processing");

				// PDF生成プロセスを非同期でトリガー
				logger.info("[INFO] PDFファイルが存在しないか空なのでinitiatePdfGenerationAsyncを呼び出します! userId: {}, salaryId: {}, refreshedToken: {}, redisKey: {}, lockKey: {}, paymentMonth: {}", userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth);
				initiatePdfGenerationAsync(userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth, 0);

				return ResponseEntity.status(HttpStatus.ACCEPTED)
						.body("{\"message\":\"PDF生成を開始しました。しばらくお待ちください。\", \"status\":\"processing\"}");
			} else if (file.exists() && file.length() > 0) {
				logger.info("[INFO] PDFファイルは既に生成されています: {}", file.getAbsolutePath());
				return ResponseEntity.ok("{\"message\":\"PDF生成が完了しています。\", \"status\":\"complete\"}");
			}

			logger.debug("[DEBUG] PDF保存ディレクトリ確認完了: {}", pdfStoragePath);

			if (userId == null || salaryId == null || paymentMonth == null) {
				logger.error("[ERROR] リクエストが不完全です - userId: {}, salaryId: {}, paymentMonth: {}", userId, salaryId, paymentMonth);
				return ResponseEntity.badRequest()
						.body("{\"message\": \"userId, salaryId, paymentMonthは必須です。\", \"status\":\"error\"}");
			}

			// 現在のステータスを取得
			String status = (String) redisTemplate.opsForValue().get(redisKey);
			logger.debug("[DEBUG] generatePDF - 現在のredisKey: {}, status: {}", redisKey, status);

			if (status == null) {
				logger.warn("[WARN] 指定されたRedisキーが見つかりません - Redisキー: {}", redisKey);

				setRedisStatus(redisKey, "processing");
				// 再生成をトリガー
				logger.debug("[DEBUG] initiatePdfGenerationAsyncを呼んで再生成します。userId: {}, salaryId: {}, token: {}, redisKey: {}, lockKey: {}, paymentMonth: {}", userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth);
				initiatePdfGenerationAsync(userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth, 0);
			} else if ("processing".equals(status)) {
				logger.warn("[WARN] PDF生成が進行中ですが、処理が進んでいない可能性があります - redisKey: {}, lockKey: {}, status: {}", redisKey, lockKey, status);
				handleRedisConflict(redisKey, lockKey, refreshedToken); // 競合状態のリセット処理を実行
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body("{\"message\": \"現在PDFが処理中です。\", \"status\":\"processing\"}");
			} else if ("complete".equals(status)) {
				logger.info("[INFO] PDF生成は既に完了しています - Redisキー: {}, ファイル名: {}", redisKey, fileName);
				return ResponseEntity.ok("{\"message\": \"PDF生成が完了しています。\", \"status\":\"complete\"}");
			} else if (status.startsWith("error")) {
				logger.warn("[WARN] PDF生成が失敗した状態です。再生成を試みます - status: {}, Redisキー: {}", status, redisKey);

				String errorReason = status.substring("error:".length()).trim();
				if (errorReason.isEmpty()) {
					errorReason = "不明な理由でエラーが発生しました";
				}
				logger.error("[ERROR] PDF生成失敗理由: {}", errorReason);

				clearRedisCache(redisKey);
				logger.info("[INFO] Redisキーを 'processing' にリセットしました - Redisキー: {}, status: {}", redisKey, status);

				logger.debug("[DEBUG] initiatePdfGenerationAsyncを呼んで再生成します。userId: {}, salaryId: {}, token: {}, redisKey: {}, lockKey: {}, paymentMonth: {}", userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth);
				initiatePdfGenerationAsync(userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth, 0);
				return ResponseEntity.status(HttpStatus.ACCEPTED)
						.body("{\"message\": \"再生成を開始しました。\", \"status\": \"processing\"}");
			} else {
				// 状態が不明な場合の処理
				logger.warn("[WARN] Redisキーのステータスが不明: {}, ステータス: {}", redisKey, status);
				clearRedisCache(redisKey);
				logger.debug("[DEBUG] initiatePdfGenerationAsyncを呼んで再生成します。userId: {}, salaryId: {}, token: {}, redisKey: {}, lockKey: {}, paymentMonth: {}", userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth);
				initiatePdfGenerationAsync(userId, salaryId, refreshedToken, redisKey, lockKey, paymentMonth, 0);
			}
		} catch (Exception e) {
			logger.error("[ERROR] PDF生成中にエラーが発生しました - redisKey: {}, userId: {}, salaryId: {}, paymentMonth: {}, error: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisKey, userId, salaryId, paymentMonth, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());

			if (e instanceof NoSuchKeyException) {
				logger.error("[ERROR] 404エラー: S3でファイルが見つかりません - userId: {}, salaryId: {}, paymentMonth: {}, error: {}", userId, salaryId, paymentMonth, e.getMessage(), e);
				setRedisStatus(redisKey, "error: " + e.getMessage());
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.body("{\"message\":\"PDFファイルが見つかりませんでした\"}");
			}

			setRedisStatus(redisKey, "error: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.body("{\"message\":\"サーバーエラーが発生しました\", \"status\":\"error\"}");
		} finally {
			if (lockOwner != null) {
				boolean lockReleased = lockService.releaseLock(lockKey, lockOwner);
				if (!lockReleased) {
					logger.warn("[WARN] ロック解除に失敗しました - lockKey: {}, lockOwner: {}", lockKey, lockOwner);
				}
			} else {
				logger.warn("[WARN] ロック解除がスキップされました - lockKey: {}", lockKey);
			}
		}

		// 最後に適切なレスポンスを返す
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("X-Debug-Status", "PDF生成中です - userId: " + userId + ", salaryId: " + salaryId + ", paymentMonth: " + paymentMonth + ", redisKey:" + redisKey);

		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.headers(headers)
				.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.header("X-Auth-Token", refreshedToken)
				.body("{\"message\": \"PDF生成が開始されました\", \"status\": \"processing\"}");
	}
	
	public void initiatePdfGenerationAsync(Integer userId, Integer salaryId, String token, String redisKey, String lockKey, String paymentMonth, int attempt) {
	    logger.info("[INFO] initiatePdfGenerationAsyncメソッドが呼ばれました。非同期処理開始 - userId: {}, salaryId: {}, paymentMonth: {}, token: {}, redisKey: {}, lockKey: {}, attempt: {}", userId, salaryId, paymentMonth, token, redisKey, lockKey, attempt);

	    // 入力チェック
	    if (userId == null || salaryId == null || paymentMonth == null || token == null) {
	        logger.error("[ERROR] PDF生成に必要なデータが不足しています。userId: {}, salaryId: {}, paymentMonth: {}, token: {}", userId, salaryId, paymentMonth, token);
	        updateRedisStatusWithError(redisKey, "必要なデータが不足しています", null);
	        return;
	    }

	    // リトライ上限チェック
	    if (attempt > maxRetries) {
	        logger.error("[ERROR] PDF生成のリトライ回数が上限に達しました - userId: {}, salaryId: {}, paymentMonth: {}, redisKey: {}, lockKey: {}", userId, salaryId, paymentMonth, redisKey, lockKey);
	        updateRedisStatusWithError(redisKey, "リトライ回数超過", null);
	        return;
	    }

	    // 非同期処理
	    CompletableFuture.runAsync(() -> {
	        try {
	            logger.debug("[DEBUG] 非同期PDF生成プロセスを開始します - userId: {}, salaryId: {}, token: {}, redisKey: {}, paymentMonth: {}", userId, salaryId, token, redisKey, paymentMonth);

	            // 再試行ロジックを実行
	            retryOperation(() -> {
	                processPdfGeneration(userId, salaryId, token, redisKey, lockKey, paymentMonth);
	                return null;
	            }, maxRetries, retryInterval);

	        } catch (Exception e) {
	            logger.error("[ERROR] 非同期PDF生成中にエラー発生 - redisKey: {}, attempt: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisKey, attempt, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());

	            if (e instanceof S3Exception || e instanceof SdkClientException) {
	                // ネットワーク関連のエラーでリトライを試みる
	                logger.error("[ERROR] ネットワーク関連のエラーが発生しました。リトライ試行 - redisKey: {}, attempt: {}", redisKey, attempt);
	                initiatePdfGenerationAsync(userId, salaryId, token, redisKey, lockKey, paymentMonth, attempt + 1);
	            } else {
	                // 再試行が無意味な場合
	                logger.error("[ERROR] リトライ不可なエラーが発生しました - redisKey: {}, error: {}", redisKey, e.getMessage(), e);
	                updateRedisStatusWithError(redisKey, e.getMessage(), e);
	            }
	        }
	    });
	}
	
	@Async("taskExecutor")
	public void processPdfGeneration(Integer userId, Integer salaryId, String token, String redisKey, String lockKey, String paymentMonth) {
	    logger.info("[INFO] processPdfGenerationが呼ばれました。 - userId: {}, salaryId: {}, redisKey: {}, lockKey: {}, paymentMonth: {}", userId, salaryId, redisKey, lockKey, paymentMonth);

	    // 入力データの検証
	    validateRequestData(userId, salaryId, paymentMonth);

	    String fileName = getFileName(userId, salaryId, paymentMonth);
	    File generatedPdf = new File(pdfStoragePath, fileName);

	    // 保存先ディレクトリの確認
	    if (!ensureDirectoryExistsAndWritable(new File(pdfStoragePath))) {
	        logger.warn("[WARN] 保存ディレクトリが不正です");
	        updateRedisStatusWithError(redisKey, "保存ディレクトリが不正です", null);
	        return;
	    }

	    // 既存のPDFファイルを確認
	    if (generatedPdf.exists() && validateGeneratedPdf(generatedPdf)) {
	        logger.warn("[WARN] PDFが既に生成されています - ファイル: {}", generatedPdf.getAbsolutePath());
	        return;
	    }

	    try {
	        // Redisのステータスを「processing」に設定
	        setRedisStatus(redisKey, "processing");
	        logger.debug("[DEBUG] Redisステータス更新 - redisKey: {}, status: {}", redisKey, "processing");

	        // 再試行ロジックを実行
	        retryOperation(() -> {
	            logger.debug("[DEBUG] callGeneratePDFApiを呼びます。userId: {}, salaryId: {}, paymentMonth: {}, token: {}", userId, salaryId, paymentMonth, token);

	            ResponseEntity<String> response = callGeneratePDFApi(userId, salaryId, paymentMonth, token);
	            logger.debug("[DEBUG] 呼び出し後に取得したresponse: {}", response);

	            if (!response.getStatusCode().is2xxSuccessful()) {
	                logger.error("[ERROR] PDF生成API失敗 - ステータスコード: {}", response.getStatusCode());
	                throw new RuntimeException("PDF生成API失敗 - ステータスコード: " + response.getStatusCode());
	            }

	            return null;
	        }, maxRetries, retryInterval);
	    } catch (Exception e) {
	        // エラーハンドリング
	        logger.error("[ERROR] PDF生成中にエラーが発生しました - redisKey: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisKey, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        updateRedisStatusWithError(redisKey, e.getMessage(), e);
	        logger.info("[INFO] Redisステータス更新 - redisKey: {}, status: error: {}", redisKey, e.getMessage());
	    } finally {
	        // ローカルファイルの削除
	        deleteLocalFile(generatedPdf);
	    }
	}

	@GetMapping("/api/downloadPdf")
	public ResponseEntity<byte[]> downloadPdf(
	    @RequestHeader("X-Auth-Token") String token,
	    @RequestParam("userId") Integer userId,
	    @RequestParam("salaryId") Integer salaryId) {

	    logger.info("[INFO] downloadPdfメソッドが呼ばれました - token: {}, userId: {}, salaryId: {}", token, userId, salaryId);
	    try {
	        Salary salary = salaryService.getSalaryById(salaryId).orElse(null);
	        if (salary == null || salary.getPaymentMonth() == null) {
	            logger.error("[ERROR] 給与データが見つかりません - salaryId: {}", salaryId);
	            return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                    .body("給与データが見つかりませんでした。".getBytes());
	        }
	        String paymentMonth = salary.getPaymentMonth().toString();
	        logger.debug("[DEBUG] 取得したsalary: {}, paymentMonth: {}", salary, paymentMonth);

	        String fileName = getFileName(userId, salaryId, paymentMonth);
	        logger.debug("[DEBUG] 生成したファイル名: {}", fileName);

	        String redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;
	        String lockKey = redisKey + ":lock";
	        String redisStatus = (String) redisTemplate.opsForValue().get(redisKey);
	        logger.debug("[DEBUG] 現在のRedisステータス - redisKey: {}, lockKey: {}, status: {}", redisKey, lockKey, redisStatus);
	  
	        return retryOperation(() -> {
	            if (!isPdfInS3(fileName)) {
	                logger.warn("[WARN] 指定されたPDFが見つかりません: {}", fileName);
	                redisTemplate.opsForValue().set(redisKey, "error: PDFファイルが見つかりません", Duration.ofSeconds(40));
	                return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                        .body("指定されたPDFファイルが見つかりませんでした。再試行してください。".getBytes());
	            }
	            byte[] pdfBytes = getPdfFromS3(bucketName, fileName, redisKey, lockKey, paymentMonth);
	            if (pdfBytes == null) {
	                logger.warn("[WARN] 指定されたPDFが見つかりません: {}", fileName);
	                redisTemplate.opsForValue().set(redisKey, "error: PDFファイルが見つかりません", Duration.ofSeconds(40));
	                return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                    .body("PDFファイルが存在しません。再度確認してください。".getBytes());
	            }
	            
	            logger.debug("[DEBUG] 取得したpdfBytes: {}", pdfBytes);

	            // HTTPレスポンスのヘッダーを設定
	            HttpHeaders headers = new HttpHeaders();
	            headers.setContentType(MediaType.APPLICATION_PDF);
	            headers.setContentDispositionFormData("attachment", fileName);
	            
	            logger.debug("[DEBUG] 取得したheaders: {}, pdfBytes: {}", headers, pdfBytes);

	            return ResponseEntity.ok().headers(headers).body(pdfBytes);
	        }, maxRetries, retryInterval);

	    } catch (Exception e) {
	    	logger.error("[ERROR] PDFダウンロード中にエラーが発生しました - userId: {}, salaryId: {}, token: {}, error: {}", userId, salaryId, token, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .body("サーバーエラーが発生しました。サポートに連絡してください。".getBytes());
	    }
	}
	
	private byte[] getPdfFromS3(String bucketName, String fileName, String redisKey, String lockKey, String paymentMonth) {
	    logger.info("[INFO] getPdfFromS3が呼ばれました - バケット名: {}, ファイル名: {}, redieKey: {}, lockKey: {}, paymentMoonth: {}", bucketName, fileName, redisKey, lockKey, paymentMonth);

	    try {
	        // リトライ付きでS3からPDFを取得
	        return retryOperation(() -> {
	            GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(fileName).build();
	            logger.debug("[DEBUG] S3にリクエストを送信します - Bucket name: {}, File name: {}, request: {}", bucketName, fileName, request);
	            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(request);
	            logger.info("[INFO] S3からファイルの取得に成功しました - バケット名: {}, ファイル名: {}, s3Object: {}", bucketName, fileName, s3Object);
	            return s3Object.readAllBytes();
	        }, maxRetries, retryInterval);
	    } catch (NoSuchKeyException e) {
	        if (!isPdfInS3(fileName)) {
	            logger.error("[ERROR] 指定されたPDFがS3に見つかりません: {}, ERROR: {}, 詳細: {}", fileName, e.getMessage(), e.toString(), e);
	            redisTemplate.opsForValue().set(redisKey, "error: ファイルが見つかりません", Duration.ofSeconds(40));

	            // 再生成をトリガー
	            initiatePdfGenerationAsync(
	                Integer.valueOf(fileName.split("_")[2]),
	                Integer.valueOf(fileName.split("_")[3].replace(".pdf", "")),
	                "", // トークンが空の場合の処理を呼び出し元で確認
	                redisKey,
	                lockKey,
	                paymentMonth,
	                0
	            );

	            // エラーを示す空の `byte[]` を返す
	            return new byte[0];
	        }
	    } catch (S3Exception ex) {
	        logger.error("[ERROR] S3アクセスエラー（サーバー側） - ファイル名: {}, ERROR: {}, メッセージ: {}, 詳細: {}", fileName, ex.getMessage(), ex.awsErrorDetails().errorMessage(), ex.toString(), ex);
	        redisTemplate.opsForValue().set(redisKey, "error", Duration.ofSeconds(40));
	        throw new RuntimeException("S3からPDFを取得できませんでした: " + ex.awsErrorDetails().errorMessage(), ex);
	    } catch (SdkClientException exx) {
	        logger.error("[ERROR] S3アクセスエラー（クライアント側） - ファイル名: {}, ERRPR: {}, 詳細: {}", fileName, exx.getMessage(), exx.toString(), exx);
	        redisTemplate.opsForValue().set(redisKey, "error", Duration.ofSeconds(40));
	        throw new RuntimeException("S3からPDFを取得できませんでした: " + exx.getMessage(), exx);
	    } catch (Exception exxx) {
	        logger.error("[ERROR] 不明なエラー - ファイル名: {}, ERROR: {}, 詳細: {}", fileName, exxx.getMessage(), exxx.toString(), exxx);
	        redisTemplate.opsForValue().set(redisKey, "error", Duration.ofSeconds(40));
	        throw new RuntimeException("S3からPDFを取得できませんでした: " + exxx.getMessage(), exxx);
	    }

	    logger.warn("[WARN] S3からのPDF取得に失敗しましたが、エラーが適切に処理されませんでした - ファイル名: {}", fileName);
	    return new byte[0];
	}

	private boolean isPdfInS3(String fileName) {
		logger.info("[INFO] isPdfInS3が呼ばれました, fileName: {}", fileName);
	    for (int attempt = 0; attempt < maxRetries; attempt++) {
	        try {
	            HeadObjectRequest headRequest = HeadObjectRequest.builder()
	                .bucket(bucketName)
	                .key(fileName)
	                .build();
	            s3Client.headObject(headRequest);
	            logger.debug("[DEBUG] 取得したheadRequest: {}", headRequest);
	            return true;
	        } catch (NoSuchKeyException e) {
	            logger.error("[ERROR] S3にファイルが見つかりません - ファイル名: {}, ERROR: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", fileName, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	            if (attempt < maxRetries - 1) {
	                try {
	                    Thread.sleep(retryInterval);
	                } catch (InterruptedException ie) {
	                    Thread.currentThread().interrupt(); // 中断フラグを設定
	                    logger.error("[ERROR] スレッドが中断されました: {}", ie.getMessage(), ie);
	                    throw new RuntimeException("スレッドが中断されました。エラー: " + ie.getMessage(), ie);
	                }
	                continue;
	            }
	            return false;
	        } catch (S3Exception | SdkClientException ex) {
	            logger.error("[ERROR] S3確認中にエラー発生 - リトライ回数: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", attempt + 1, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
	            if (attempt == maxRetries - 1) {
	                logger.error("[ERROR] S3のリトライが最大回数に達しました。");
	            }
	            try {
	                Thread.sleep(retryInterval);
	            } catch (InterruptedException ie) {
	                Thread.currentThread().interrupt(); // 中断フラグを設定
	                logger.error("[ERROR] スレッドが中断されました: {}", ie.getMessage(), ie);
	                throw new RuntimeException("スレッドが中断されました。エラー: " + ie.getMessage(), ie);
	            }
	        }
	    }
	    return false;
	}
	
	@GetMapping("/api/checkPdfStatus")
	public ResponseEntity<String> checkPdfStatus(@RequestParam String redisKey) {
	    logger.info("[INFO] checkPdfStatusが呼ばれました。redisKey: {}", redisKey);
	    try {
	        // Redisからステータスを取得
	        Object status = redisTemplate.opsForValue().get(redisKey);

	        if (status == null) {
	            // Redisにデータがない場合、404を返す
	            return ResponseEntity.status(404).body("{\"error\": \"指定されたタスクは存在しません\"}");
	        }

	        String statusString;

	        // statusが文字列であれば、そのまま使う
	        if (status instanceof String) {
	            statusString = (String) status;
	        } else {
	            // JSONオブジェクトとして格納されている場合
	            try {
	                // もしstatusがJSON文字列なら、デシリアライズして取得
	                statusString = objectMapper.writeValueAsString(status);
	            } catch (Exception e) {
	                // JSONフォーマットでない場合、そのまま文字列として扱う
	                statusString = String.valueOf(status);
	            }
	        }

	        // 文字列としての比較
	        if ("complete".equals(statusString)) {
	            return ResponseEntity.ok("{\"message\": \"PDF生成が完了しました\"}");
	        } else {
	            return ResponseEntity.status(202).body("{\"message\": \"PDF生成中またはエラー\"}");
	        }

	    } catch (Exception e) {
	        logger.error("[ERROR] checkPdfStatusで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", 
	                     e.toString(), Arrays.toString(e.getStackTrace()), 
	                     e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        return ResponseEntity.status(500).body("{\"error\": \"サーバーエラー: " + e.getMessage() + "\"}");
	    }
	}
	
	@CrossOrigin(origins = "https://humanage-app-1fe93ce442da.herokuapp.com")
	@PostMapping("/api/clearRedisCache")
	public ResponseEntity<String> clearRedisCache(@RequestBody Map<String, String> requestBody) {
		logger.info("[INFO] PdfController.javaの/api/clearRedisCacheエンドポイントが呼ばれました。リクエストボディ: {}", requestBody);
	    String redisKey = requestBody.get("redisKey");
	    logger.debug("[DEBUG] リクエストボディから取得したredisKey = {}, clearRedisCache関数を呼びます。", redisKey);
	    return clearRedisCache(redisKey);
	}

	public ResponseEntity<String> clearRedisCache(String redisKey) {
	    logger.info("[INFO] clearRedisCacheが呼ばれました - 受け取ったredisキー: {}", redisKey);
	    if (redisKey == null) {
	        logger.error("[ERROR] redisKeyが指定されていません。");
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body("{\"error\": \"redisKeyが指定されていません\"}");
	    }
	    try {
	        redisTemplate.delete(redisKey);
	        setRedisStatus(redisKey, "processing");
	        logger.info("[INFO] Redisキャッシュがクリアされ、初期状態にリセットされました。キー: {}", redisKey);
	        return ResponseEntity.ok("{\"message\": \"Redisキャッシュが正常にクリアされました\"}");
	    } catch (Exception e) {
	        logger.error("[ERROR] Redisキャッシュのクリアに失敗しました。エラー: {}", e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("{\"error\": \"Redisキャッシュのクリアに失敗しました\"}");
	    }
	}
	
	@PostMapping("/api/clearCacheIfError")
	public ResponseEntity<String> clearCacheIfError(@RequestBody Map<String, String> requestBody) {
	    String redisKey = requestBody.get("redisKey");  // リクエストボディからredisKeyを取得
	    logger.info("[INFO] clearCacheIfErrorが呼ばれました - キー: {}, リクエストボディ: {}", redisKey, requestBody);

	    if (redisKey == null) {
	    	logger.error("[ERROR] redisKeyパラメーターが必要です");
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body("{\"message\": \"redisKeyパラメーターが必要です\"}");
	    }

	    for (int attempt = 0; attempt < maxRetries; attempt++) {
	        try {
	            String status = (String) redisTemplate.opsForValue().get(redisKey);
	            logger.debug("[DEBUG] 取得したステータス: {}", status);
	            
	            if (status.startsWith("error")) {
	                // Mapに変換してclearRedisCacheメソッドに渡す
	                Map<String, String> clearRequestBody = new HashMap<>();
	                clearRequestBody.put("redisKey", redisKey);
	                clearRedisCache(clearRequestBody);
	                logger.info("[INFO] エラーステータスが確認され、Redisキャッシュがクリアされました。status: {}", status);
	                return ResponseEntity.status(HttpStatus.OK)
	                        .body("{\"message\": \"エラーキャッシュがクリアされました。再試行してください。\"}");
	            } else {
	                logger.info("[INFO] 指定されたキーはエラー状態ではありません。キー: {}, status: {}", redisKey, status);
	                return ResponseEntity.status(HttpStatus.OK)
	                        .body("{\"message\": \"指定されたキーはエラー状態ではありません。キャッシュの削除は行われませんでした。\"}");
	            }
	        } catch (Exception e) {
	            logger.error("[ERROR] キャッシュ状態確認中にエラーが発生しました - キー: {}, エラー: {}", redisKey, e.getMessage(), e);
	            try {
	                Thread.sleep(retryInterval);
	            } catch (InterruptedException ie) {
	                Thread.currentThread().interrupt();  // 現在のスレッドの割り込みステータスを設定
	                logger.error("[ERROR] スリープ中に割り込みが発生しました - 中断します。エラー: {}", ie.getMessage(), ie);
	                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                        .body("{\"message\": \"スリープ中に割り込みが発生しました。\"}");
	            }
	        }
	    }
	    logger.error("[ERROR] キャッシュ状態確認中にエラーが発生しました。");
	    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .body("{\"message\": \"キャッシュ状態確認中にエラーが発生しました。\"}");
	}

	private ResponseEntity<String> callGeneratePDFApi(Integer userId, Integer salaryId, String paymentMonth, String token) {
	    try {
	        logger.info("[INFO] callGeneratePDFApiが呼ばれました - userId: {}, salaryId: {}, paymentMonth: {}, token: {}", userId, salaryId, paymentMonth, token);

	        String baseUrl = System.getenv("BASE_URL");
	        if (baseUrl == null || baseUrl.isEmpty()) {
	            logger.error("[ERROR] BASE_URLが環境変数から取得できませんでしたのでデフォルト値を設定します。");
	            baseUrl = "https://humanage-app-1fe93ce442da.herokuapp.com/";
	        }
	        logger.debug("[DEBUG] 取得したbaseUrl: {}", baseUrl);

	        validateRequestData(userId, salaryId, paymentMonth);
	        
	        String nodePdfServerUrl = baseUrl + "generateSalaryDetailsPDFByPuppeteer";
	        String reportUrl = baseUrl + "salaryDetails?userId=" + userId + "&salaryId=" + salaryId + "&paymentMonth=" + paymentMonth;

	        logger.debug("[DEBUG] Node.js PDF生成API nodePdfServerUrl: {}, reportUrl: {}", nodePdfServerUrl, reportUrl);

	        // リクエストボディの作成
	        Map<String, String> requestBody = new HashMap<>();
	        requestBody.put("userId", String.valueOf(userId));
	        requestBody.put("salaryId", String.valueOf(salaryId));
	        requestBody.put("paymentMonth", paymentMonth);
	        requestBody.put("reportUrl", reportUrl);
	        requestBody.put("timeout", "50000");
	        logger.debug("[DEBUG] 作成したrequestBody: {}", requestBody);

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.set("X-Auth-Token", token);
	        logger.debug("[DEBUG] 作成したheaders: {}", headers);

	        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
	        logger.debug("[DEBUG] 作成したentiry: {}", entity);

	        // Redisキー
	        String redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;
	        checkAndResetRedisKey(redisKey);

	        // PDFファイルの確認
	        String fileName = getFileName(userId, salaryId, paymentMonth);
	        File generatedPdf = new File(pdfStoragePath, fileName);
	        
	        logger.debug("[DEBUG] 取得したfileName: {}, generatedPdf: {}", fileName, generatedPdf);

	        if (!generatedPdf.exists() || generatedPdf.length() == 0) {
	            logger.warn("[WARN] PDFが存在しない、または空です。生成を開始します - ファイル: {}", generatedPdf.getPath());
	            logger.debug("[DEBUG] attemptPdfGenerationを呼びます。nodePdfServerUrl: {}, entity: {}, redisKey: {}, generatedPdf: {}", nodePdfServerUrl, entity, redisKey, generatedPdf);
	            ResponseEntity<String> response = attemptPdfGeneration(nodePdfServerUrl, entity, redisKey, generatedPdf);
	            
	            logger.debug("[DEBUG] callGeneratePDFApiに戻ってきました。取得したresponse: {}", response);
	            
	            return response;
	        } else {
	            logger.info("[INFO] PDFが既に存在します - パス: {}", generatedPdf.getAbsolutePath());
	            setRedisStatus(redisKey, "complete");
	            return ResponseEntity.ok("{\"message\":\"PDF生成が既に完了しています。\", \"status\":\"complete\"}");
	        }
	    } catch (Exception e) {
	        // エラーログを記録し、RuntimeExceptionをスロー
	        logger.error("[ERROR] PDF生成API呼び出し中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        throw new RuntimeException("PDF生成API呼び出し中にエラーが発生しました。", e);
	    }
	}
	
	private void checkAndResetRedisKey(String redisKey) {
	    try {
	        logger.info("[INFO] checkAndResetRedisKeyが呼ばれました。redisKey: {}", redisKey);
	        
	        // Redisからステータスを取得
	        String status = (String) redisTemplate.opsForValue().get(redisKey);
	        
	        logger.debug("[DEBUG] 取得したstatus: {}", status);
	        
	        // ステータスが "processing" の場合、Redisキーを削除
	        if ("processing".equals(status)) {
	            logger.info("[INFO] 古い処理を削除 - Redisキー: {}", redisKey);
	            redisTemplate.delete(redisKey);
	        }
	    } catch (Exception e) {
	        // エラーログを記録
	        logger.error("[ERROR] Redisキーの処理中にエラーが発生しました: redisKey = {}, errorMessage = {}", redisKey, e.getMessage(), e);
	        throw new RuntimeException("Redisキーの処理中にエラーが発生しました。", e);
	    }
	}
	
	private ResponseEntity<String> attemptPdfGeneration(String url, HttpEntity<Map<String, String>> entity, String redisKey, File generatedPdf) {
	    logger.info("[INFO] attemptPdfGenerationが呼ばれました。url: {}, entity: {}, redisKey: {}, generatedPdf: {}", url, entity, redisKey, generatedPdf);

	    for (int attempt = 0; attempt < maxRetries; attempt++) {
	        if (redisKey == null || redisKey.isEmpty()) {
	            logger.error("[ERROR] リトライ時に不正なデータが検出されました - redisKey: {}, generatedPdf: {}", redisKey, generatedPdf);
	            throw new IllegalStateException("リトライ時に不正なデータが検出されました。redisKey: " + redisKey + ", generatedPdf: " + generatedPdf);
	        }

	        try {
	            restTemplate = createRestTemplate();
	            logger.debug("[DEBUG] 作成したrestTemplate: {}", restTemplate);
	            
	            logger.debug("[DEBUG] restTemplate.postForEntity(url, entity, String.class)を実行します。URL: {}, entity: {}", url, entity);
	            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
	            logger.debug("[DEBUG] restTemplate.postForEntity(url, entity, String.class)によって取得したresponse: {}", response);

	            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
	                logger.info("[INFO] PDF生成APIが正常に受け入れられました - ステータスコード: {}", response.getStatusCode());
	                setRedisStatus(redisKey, "processing");
	                return ResponseEntity.ok("{\"message\":\"PDF生成が開始されました。\", \"status\":\"processing\"}");
	            } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
	                logger.error("[ERROR] 409エラー: 他のプロセスが処理中です - redisKey: {}, 試行回数: {}, リトライ間隔: {}ms", redisKey, attempt + 1, retryInterval * (attempt + 1));
	                performRetrySleep(retryInterval * (attempt + 1));
	                continue; // 再試行
	            } else if (response.getStatusCode().is2xxSuccessful()) {
	                logger.info("[INFO] Node.js API呼び出し成功 - ステータスコード: {}", response.getStatusCode());
	                CompletableFuture.runAsync(() -> validateGeneratedPdfAsync(redisKey, generatedPdf));
	                return ResponseEntity.ok("{\"message\":\"PDF生成が開始されました。\", \"status\":\"processing\"}");
	            } else if (response.getStatusCode().is4xxClientError()) {
	                logger.error("[ERROR] Node.js APIエラー - ステータスコード: {}, レスポンス: {}", response.getStatusCode(), response.getBody());
	                return handleError(redisKey, "クライアントエラー", HttpStatus.BAD_REQUEST, response.getBody());
	            } else if (response.getStatusCode().is5xxServerError()) {
	                logger.error("[ERROR] Node.js APIエラー - ステータスコード: {}, レスポンス: {}", response.getStatusCode(), response.getBody());
	                return handleError(redisKey, "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR, response.getBody());
	            } else if (response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT) {
	                logger.error("[ERROR] APIタイムアウト - URL: {}, ステータスコード: {}", url, response.getStatusCode());
	                return handleError(redisKey, "タイムアウト", HttpStatus.REQUEST_TIMEOUT, response.getBody());
	            } else {
	                logger.error("[ERROR] Node.js APIエラー - ステータスコード: {}, レスポンス: {}", response.getStatusCode(), response.getBody());
	                return handleError(redisKey, "APIエラー", HttpStatus.INTERNAL_SERVER_ERROR, response.getBody());
	            }
	        } catch (HttpClientErrorException.Conflict e) {
	            logger.error("[ERROR] 409 Conflict: 他のプロセスが進行中です。リトライを実行します - redisKey: {}, 試行回数: {}, ERROR: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", redisKey, attempt + 1, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	            performRetrySleep(retryInterval * (attempt + 1));
	        } catch (HttpClientErrorException.NotFound ex) {
	            logger.error("[ERROR] Node.js APIエンドポイントが見つかりません (404) - URL: {}, ERROR: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", url, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
	            throw ex;
	        } catch (RestClientException e) {
	            // 他のRestClientException（タイムアウト以外）を処理
	            logger.error("[ERROR] RestTemplateによるAPI呼び出しでエラーが発生しました - 試行回数: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", attempt + 1, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	            if (attempt >= maxRetries - 1) {
	                logger.error("[ERROR] 最大試行回数に達しました。エラー: {}, 詳細: {}", e.getMessage(), e.toString(), e);
	                return handleError(redisKey, "PDF生成失敗", HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
	            }
	            performRetrySleep(retryInterval * (attempt + 1));  // リトライのインターバル
	            continue; // 再試行
	        } catch (Exception e) {
	            logger.error("[ERROR] API呼び出し中にエラーが発生しました - 試行回数: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", attempt + 1, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	            if (attempt >= maxRetries - 1) {
	            	logger.error("[ERROR] 最大試行回数に達しました: {}", e.toString(), e);
	                return handleError(redisKey, "PDF生成失敗", HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
	            }
	        }

	        // 最後のリトライ間隔を設定
	        performRetrySleep(retryInterval);
	    }

	    return handleError(redisKey, "最大リトライ回数を超えました", HttpStatus.SERVICE_UNAVAILABLE, null);
	}

	// 共通のリトライスリープメソッド
	private void performRetrySleep(long sleepTime) {
		logger.info("[INFO] performRetrySleepが呼ばれました。sleepTime: {}", sleepTime);
	    try {
	        Thread.sleep(sleepTime);
	    } catch (InterruptedException ie) {
	        Thread.currentThread().interrupt(); // 割り込みステータスを設定
	        logger.error("[ERROR] リトライ中にスレッドが割り込まれました: {}", ie.getMessage(), ie);
	        throw new RuntimeException("リトライ中にスレッドが割り込まれました。", ie);
	    }
	}

	// 非同期でPDF生成を確認するメソッド
	private void validateGeneratedPdfAsync(String redisKey, File generatedPdf) {
		logger.info("[INFO] validateGeneratedPdfAsyncが呼ばれました。redisKey: {}, generatedPdf: {}", redisKey, generatedPdf);
	    try {
	        Thread.sleep(4000); // ファイル生成を待機
	        if (generatedPdf.exists() && generatedPdf.length() > 0) {
	            setRedisStatus(redisKey, "complete");
	            logger.info("[INFO] PDFが正常に生成されました - ファイル: {}", generatedPdf.getAbsolutePath());
	        } else {
	        	logger.error("[ERROR] PDF生成未完了");
	            handleErrorStatus(redisKey, "PDF生成未完了", HttpStatus.INTERNAL_SERVER_ERROR);
	        }
	    } catch (InterruptedException e) {
	        Thread.currentThread().interrupt();
	        logger.error("[ERROR] 非同期PDF確認中に中断されました - redisKey: {}, ERROR: {}", redisKey, e.getMessage(), e);
	        handleErrorStatus(redisKey, "PDF確認中断", HttpStatus.INTERNAL_SERVER_ERROR);
	    }
	}
	
	private void validateRequestData(Integer userId, Integer salaryId, String paymentMonth) {
		logger.info("[INFO] validateRequestDateが呼ばれました。userId: {}, salaryId: {}, paymentMonth: {}", userId, salaryId, paymentMonth);
	    try {
	        if (userId == null || salaryId == null || paymentMonth == null || paymentMonth.isEmpty()) {
	            logger.error("[ERROR] 必要なデータが不足しています - userId: {}, salaryId: {}, paymentMonth: {}", userId, salaryId, paymentMonth);
	            throw new IllegalArgumentException("PDF生成に必要なデータが不足しています。");
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] validateRequestData中にエラーが発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("validateRequestDataの処理中にエラーが発生しました。", e);
	    }
	}

	private void handleErrorStatus(String redisKey, String errorMessage, HttpStatus status) {
	    try {
	        logger.error("[ERROR] エラー発生 - redisKey: {}, メッセージ: {}, ステータス: {}", redisKey, errorMessage, status);
	        setRedisStatus(redisKey, "error: " + errorMessage);
	    } catch (Exception e) {
	        logger.error("[ERROR] handleErrorStatus中にエラーが発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("handleErrorStatusの処理中にエラーが発生しました。", e);
	    }
	}

	private ResponseEntity<String> handleError(String redisKey, String errorMessage, HttpStatus status, String details) {
	    try {
	        logger.error("[ERROR] {} - redisKey: {}, 詳細: {}, status: {}", errorMessage, redisKey, details, status);
	        setRedisStatus(redisKey, "error: " + errorMessage);
	        return ResponseEntity.status(status)
	                .body("{\"message\":\"" + errorMessage + "\", \"status\":\"error\"}");
	    } catch (Exception e) {
	        logger.error("[ERROR] handleError中にエラーが発生しました: {}", e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("{\"message\":\"エラー処理中に問題が発生しました。\", \"status\":\"error\"}");
	    }
	}

	public String getTokenFromRedis(String token) {
	    logger.info("[INFO] getTokenFromRedisが呼び出されました。token: {}", token);
	    int maxAttempts = 3;
	    int attempt = 0;

	    while (attempt < maxAttempts) {
	        logger.debug("[DEBUG] トークン取得試行 - 現在の試行回数: {}/{}", attempt + 1, maxAttempts);

	        try {
	            logger.debug("[DEBUG] トークン有効期限チェックを開始します。token: {}", token);
	            boolean isTokenExpired = tokenService.isTokenExpired(token);
	            if (isTokenExpired) {
	                logger.warn("[WARN] トークンが期限切れです。token: {}", token);
	                return null;
	            }
	            logger.debug("[DEBUG] トークンは有効です。次にRedisからユーザーデータを取得します。token: {}", token);

	            String redisKey = "token:" + token;
	            logger.debug("[DEBUG] Redisキーを作成しました。redisKey: {}", redisKey);

	            String userJson = (String) redisTemplate.opsForValue().get(redisKey);
	            if (userJson == null) {
	                logger.warn("[WARN] Redisからデータが見つかりませんでした。redisKey: {}", redisKey);
	                return null;
	            }

	            logger.info("[INFO] Redisからユーザーデータを正常に取得しました。redisKey: {}, userJson: {}", redisKey, userJson);
	            return userJson;

	        } catch (Exception e) {
	            attempt++;
	            logger.error("[ERROR] getTokenFromRedisでエラーが発生しました。試行回数: {}, 最大試行回数: {}, エラー内容: {}", attempt, maxAttempts, e.getMessage(), e);

	            if (attempt >= maxAttempts) {
	                logger.error("[ERROR] 最大試行回数に達しました。処理を終了します。token: {}", token);
	                return null;
	            }

	            logger.warn("[WARN] 再試行します。現在の試行回数: {}/{}", attempt, maxAttempts);
	        }
	    }

	    logger.error("[ERROR] 異常な状態です。トークン処理を終了します。token: {}", token);
	    return null;
	}

	@PostMapping("/api/extendToken")
	public ResponseEntity<String> extendTokenExpiration(@RequestHeader("X-Auth-Token") String token) {
		logger.info("[INFO] extendTokenExpirationが呼ばれました。token: {}", token);
		try {
			String newToken = tokenService.extendTokenExpiration(token);
			if (newToken == null) {
				logger.error("[ERROR] トークンの延長に失敗しました。再ログインしてください");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body("{\"message\": \"トークンの延長に失敗しました。再ログインしてください。\"}");
			}
			logger.debug("[DEBUG] 取得したnewToken: {}", newToken);
			return ResponseEntity.ok("{\"newToken\":\"" + newToken + "\"}");
		} catch (Exception e) {
			logger.error("[ERROR] トークン延長エラー: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"message\": \"トークンの延長に失敗しました。\"}");
		}
	}

	@GetMapping("/api/checkToken")
	public ResponseEntity<String> checkToken(@RequestParam String token) {
	    try {
	        logger.info("[INFO] checkTokenが呼ばれました。token: {}", token);
	        if (tokenService.isTokenExpired(token)) {
	            logger.warn("[WARN] トークンの有効期限切れ - トークン: {}", token);
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("トークンが無効です");
	        } else {
	            logger.info("[INFO] トークンは有効です - トークン: {}", token);
	            return ResponseEntity.ok("トークンは有効です");
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] checkToken中にエラーが発生しました: {}", e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("トークンの検証中にエラーが発生しました");
	    }
	}

	@GetMapping("/debug/checkTokenInRedis")
	public ResponseEntity<String> checkTokenInRedis(@RequestParam String token) {
		logger.info("[INFO] checkTokenInRedisが呼ばれました。token: {}", token);
		try {
			String userJson = (String) redisTemplate.opsForValue().get("token:" + token);
			if (userJson != null) {
				logger.debug("[DEBUG] Redisにトークンが保存されています。ユーザーデータ: {}", userJson);
				return ResponseEntity.ok("Redisにトークンが保存されています。ユーザーデータ: " + userJson);
			} else {
				logger.error("[ERROR] Redisにトークンが見つかりません");
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Redisにトークンが見つかりません。");
			}
		} catch (Exception e) {
			logger.error("[ERROR] Redis確認エラー: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Redisチェック中にエラーが発生しました");
		}
	}

	@PostMapping("/api/refreshToken")
	public ResponseEntity<String> refreshToken(@RequestHeader("X-Auth-Token") String token) {
	    logger.info("[INFO] refreshTokenが呼び出されました。トークン: {}", token);

	    int maxRetries = 3;  // リトライ回数の上限
	    int attempt = 0;
	    boolean success = false;
	    String newToken = null;
	    String userJson = null;

	    try {
	        while (attempt < maxRetries && !success) {
	            logger.debug("[DEBUG] リフレッシュ処理の試行 - 現在の試行回数: {}/{}", attempt + 1, maxRetries);

	            // Redisからユーザーデータを取得
	            logger.debug("[DEBUG] Redisからユーザーデータを取得します。キー: {}", "token:" + token);
	            userJson = (String) redisTemplate.opsForValue().get("token:" + token);

	            if (userJson == null) {
	                logger.warn("[WARN] トークンに紐づくデータが見つかりませんでした。リトライを試みます - 試行回数: {}", attempt + 1);
	                attempt++;
	                continue;
	            }

	            // トークンの有効期限を確認
	            logger.debug("[DEBUG] トークンの有効期限を確認します。トークン: {}", token);
	            if (tokenService.isTokenExpired(token)) {
	                logger.warn("[WARN] トークンが期限切れです。リトライを試みます - 試行回数: {}", attempt + 1);
	                attempt++;

	                // トークンの延長を試みる
	                logger.debug("[DEBUG] トークンの有効期限延長を試みます。トークン: {}", token);
	                newToken = tokenService.extendTokenExpiration(token);
	                logger.debug("[DEBUG] 延長されたトークン: {}", newToken);

	                success = newToken != null;
	            } else {
	                logger.debug("[DEBUG] トークンは有効です。新しいトークンを生成します。トークン: {}", token);
	                newToken = tokenService.extendTokenExpiration(token);
	                logger.debug("[DEBUG] 新しく生成されたトークン: {}", newToken);

	                success = newToken != null;
	            }
	        }

	        if (!success || newToken == null || userJson == null) {
	            logger.error("[ERROR] トークンのリフレッシュに失敗しました。再ログインが必要です - トークン: {}", token);
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body("{\"message\": \"トークンのリフレッシュに失敗しました。再ログインしてください。\"}");
	        }

	        // 新しいトークンと有効期限をRedisに保存
	        long newExpirationTime = System.currentTimeMillis() + 30 * 60 * 1000;
	        logger.debug("[DEBUG] 新しいトークンをRedisに保存します。キー: {}, 値: {}", "token:" + newToken, userJson);
	        redisTemplate.opsForValue().set("token:" + newToken, userJson);

	        logger.debug("[DEBUG] 新しいトークンの有効期限をRedisに保存します。キー: {}, 値: {}", "token_expiration:" + newToken, newExpirationTime);
	        redisTemplate.opsForValue().set("token_expiration:" + newToken, String.valueOf(newExpirationTime));

	        // 古いトークンを非同期で削除
	        logger.debug("[DEBUG] 古いトークンを非同期で削除します。トークン: {}", token);
	        CompletableFuture.runAsync(() -> {
	            try {
	                Thread.sleep(2000); // クライアントが新しいトークンを使用するまで待機
	                redisTemplate.delete("token:" + token);
	                redisTemplate.delete("token_expiration:" + token);
	                logger.info("[INFO] 古いトークンがRedisから削除されました。トークン: {}", token);
	            } catch (InterruptedException e) {
	                logger.error("[ERROR] 非同期処理中にエラーが発生しました: {}", e.getMessage(), e);
	                Thread.currentThread().interrupt();
	            }
	        });

	        logger.info("[INFO] 新しいトークンを正常に発行しました。トークン: {}", newToken);
	        return ResponseEntity.ok("{\"newToken\":\"" + newToken + "\"}");
	    } catch (Exception e) {
	        logger.error("[ERROR] トークンのリフレッシュ中にエラーが発生しました。トークン: {}, エラー内容: {}", token, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("{\"message\": \"トークンリフレッシュ中にエラーが発生しました。\"}");
	    }
	}
	
	private String getFileName(Integer userId, Integer salaryId, String paymentMonth) {
	    try {
	        logger.info("[INFO] getFileNameが呼ばれました。userId: {}, salaryId: {}, paymentMonth: {}", userId, salaryId, paymentMonth);
	        String fileName = "pdf_report_" + userId + "_" + salaryId + "_" + paymentMonth + ".pdf";
	        logger.info("[INFO] getFileNameメソッドで生成したファイル名: {}", fileName);
	        return fileName;
	    } catch (Exception e) {
	        logger.error("[ERROR] getFileNameメソッドでエラーが発生しました: {}", e.getMessage(), e);
	        throw new RuntimeException("ファイル名生成中にエラーが発生しました", e);
	    }
	}
		
	private void deleteLocalFile(File pdfFile) {
		logger.info("[INFO] deleteLocalFileが呼ばれました。pdfFile: {}", pdfFile);
	    try {
	        if (pdfFile.exists() && !pdfFile.delete()) {
	            logger.error("[ERROR] ローカルファイルの削除に失敗しました: {}", pdfFile.getPath());
	            redisTemplate.opsForValue().set("error", "ローカルファイル削除失敗", Duration.ofSeconds(40));
	        } else {
	            logger.info("[INFO] ローカルファイルを削除しました: {}", pdfFile.getPath());
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] ローカルファイル削除中にエラーが発生しました: {}", e.getMessage(), e);
	    }
	}
		
	@PostMapping("/api/updateRedisStatus")
	public ResponseEntity<String> updateRedisStatus(@RequestBody Map<String, String> requestBody) {
	    logger.info("[INFO] updateRedisStatusが呼ばれました - リクエストボディ: {}", requestBody);

	    String redisKey = requestBody.get("redisKey");
	    String status = requestBody.get("status");

	    if (redisKey == null || status == null) {
	        logger.error("[ERROR] リクエストボディが不完全 - redisKey: {}, status: {}", redisKey, status);
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\":\"redisKeyとstatusは必須です。\"}");
	    }

	    try {
	        redisTemplate.opsForValue().set(redisKey, status, Duration.ofSeconds(40));

	        logger.info("[INFO] Redisキー '{}' を更新 - ステータス: {}", redisKey, status);
	        return ResponseEntity.ok("{\"message\":\"Redisステータスが更新されました。\"}");
	    } catch (Exception e) {
	        logger.error("[ERROR] Redis更新エラー - redisKey: {}, status: {}, エラー: {}", redisKey, status, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Redisステータスの更新に失敗しました。\"}");
	    }
	}
	
	@GetMapping("/api/getRedisStatus")
	public ResponseEntity<Map<String, String>> getRedisStatus(
	        @RequestParam String key,
	        @RequestHeader(value = "X-Auth-Token", required = false) String token) {
	    logger.info("[INFO] getRedisStatusが呼ばれました。key: {}, token: {}", key, token);

	    Map<String, String> response = new HashMap<>();
	    try {
	        logger.debug("[DEBUG] Redisからのデータ取得開始 - キー: {}", key);
	        // 明示的にStringとして取得
	        String status = (String) redisTemplate.opsForValue().get(key);
	        logger.debug("[DEBUG] Redisから取得したstatus: {}", status);

	        if (status == null || status.isBlank()) {
	            logger.warn("[WARN] 指定されたRedisキーが存在しないため、再生成を試行します - キー: {}", key);
	            setRedisStatus(key, "processing");
	            response.put("status", "processing");
	            response.put("message", "processingをセットし、再生成を開始しました。");
	            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	        }

	        logger.info("[INFO] Redisキー '{}' のステータス: {}", key, status);

	        response.put("status", status);
	        return ResponseEntity.ok(response);
	    } catch (Exception e) {
	        logger.error("[ERROR] Redisキーのステータス取得中にエラーが発生 - キー: {}, エラー: {}", key, e.toString());

	        setRedisStatus(key, "error");

	        response.put("status", "error");
	        response.put("message", "Redisステータスの取得中にエラーが発生しました。");
	        response.put("details", e.getLocalizedMessage());
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	    }
	}
	
	@PostMapping("/api/checkDirectory")
	public ResponseEntity<Map<String, Object>> checkAndCreateDirectory(@RequestBody Map<String, String> request) {
		logger.info("[INFO] checkAndCreateDirectoryが呼ばれました。request: {}", request);
	    String directoryPath = request.get("directoryPath");
	    Map<String, Object> response = new HashMap<>();
	    if (directoryPath == null || directoryPath.isEmpty() || directoryPath.isBlank()) {
	    	logger.error("[ERROR] ディレクトリパスが指定されていません。");
	        response.put("status", "error");
	        response.put("message", "ディレクトリパスが指定されていません");
	        return ResponseEntity.badRequest().body(response);
	    }
	    
	    logger.debug("[DEBUG] 取得したdirectoryPath: {}", directoryPath);
	    
	    try {
	    	File directory = new File(directoryPath);
	    	if (!directory.exists()) {
	    		if (directory.mkdirs()) {
	    			logger.debug("[DEBUG] ディレクトリが正常に作成されました。directory: {}, directoryPath: {}", directory, directoryPath);
	    			response.put("status", "complete");
	    			response.put("message", "ディレクトリが正常に作成されました");
	    		} else {
	    			logger.error("[ERROR] ディレクトリの作成に失敗しました。directory: {}, directoryPath: {}", directory, directoryPath);
	    			response.put("status", "error");
	    			response.put("message", "ディレクトリの作成に失敗しました");
	    			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	    		}
	    	} else if (!directory.canWrite()) {
	    		logger.error("[ERROR] 既存ディレクトリに書き込み権限がありません - directory: {}, Path: {}", directory, directoryPath);
	    		response.put("status", "error");
	            response.put("message", "ディレクトリに書き込み権限がありません");
	            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	        } else {
	        	logger.debug("[DEBUG] ディレクトリは既に存在します。directory: {}, directoryPath: {}", directory, directoryPath);
	    		response.put("status", "complete");
	    		response.put("message", "ディレクトリはすでに存在します");
	    	}

	    	return ResponseEntity.ok(response);
	    } catch (SecurityException e) {
	        logger.error("[ERROR] ディレクトリチェックエラー: {}", e.getMessage(), e);
	        response.put("message", "ディレクトリエラー: " + e.getMessage());
	        response.put("status", "error");
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	    } catch (Exception ex) {
	    	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
	    	response.put("status", "error");
	        response.put("message", "エラー: " + ex.getMessage());
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	    }
	}
	
	@GetMapping("/api/checkFileExists")
	public ResponseEntity<Map<String, String>> checkFileExists(@RequestParam("key") String fileName) {
	    try {
	        logger.info("[INFO] checkFileExistsが呼ばれました。fileName: {}", fileName);
	        File file = new File(pdfStoragePath, fileName);
	        logger.debug("[DEBUG] 取得したfile: {}, pdfStoragePath: {}, fileName: {}", file, pdfStoragePath, fileName);
	        
	        Map<String, String> response = new HashMap<>();

	        if (file.exists()) {
	            logger.info("[INFO] 指定されたPDFファイルは存在します: {}", file.getAbsolutePath());
	            response.put("message", "ファイルが存在します");
	            return ResponseEntity.ok(response);
	        } else {
	            logger.warn("[WARN] 指定されたPDFファイルが見つかりません: {}", file.getAbsolutePath());
	            response.put("message", "ファイルが存在しません");
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] ファイル確認中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        Map<String, String> errorResponse = new HashMap<>();
	        errorResponse.put("message", "ファイル確認中にエラーが発生しました");
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
	        // 文字列として保存
	        redisTemplate.opsForValue().set(redisKey, status, Duration.ofSeconds(40));
	        logger.info("[INFO] Redisキー '{}' を更新しました - ステータス: {}", redisKey, status);
	    } catch (Exception e) {
	        logger.error("[ERROR] Redisステータス設定中に予期しないエラーが発生しました - キー: {}, ステータス: {}, エラー: {}", redisKey, status, e.toString(), e);
	        throw new RuntimeException("Redisステータス設定失敗 - " + redisKey + ", status:" + status, e);
	    }
	}

	private void handleRedisConflict(String redisKey, String lockKey, String token) {
		logger.info("[INFO] handleRedisConflictが呼ばれました。redisKey: {}, lockKey: {}, token: {}", redisKey, lockKey, token);
	    
	    String status = (String) redisTemplate.opsForValue().get(redisKey);
	    logger.debug("[DEBUG] 取得したstatusを確認します: {}", status);
	    if ("processing".equals(status)) {
	    	logger.warn("[WARN] Redis状態が 'processing' のままです。競合状態を解消します - Redisキー: {}, status: {}", redisKey, status);
	        // Redisの状態をエラーに変更
	        setRedisStatus(redisKey, "error: 処理停滞");
	        logger.debug("[DEBUG] 競合状態を解消し、エラーステータスを設定しました - Redisキー: {}, status: {}", redisKey, status);

	        try {
	            // redisKey から userId, salaryId, paymentMonth を抽出
	            String[] parts = redisKey.split(":");
	            if (parts.length == 4) {
	                Integer userId = Integer.parseInt(parts[1]);
	                Integer salaryId = Integer.parseInt(parts[2]);
	                String paymentMonth = parts[3];

	                // initiatePdfGenerationAsync を呼び出して再生成を開始
	                logger.debug("[DEBUG] initiatePdfGenerationAsyncを呼びます。userId: {}, salaryId: {}, token: {}, redisKey: {}, paymentMonth: {}", userId, salaryId, token, redisKey, paymentMonth);
	                initiatePdfGenerationAsync(userId, salaryId, token, redisKey, lockKey, paymentMonth, 0);
	                logger.debug("[DEBUG] PDF生成プロセスを再トリガーしました - userId: {}, salaryId: {}, paymentMonth: {}, redisKey: {}, token: {}", userId, salaryId, paymentMonth, redisKey, token);
	            } else {
	                logger.error("[ERROR] 無効なRedisキー形式: {}", redisKey);
	            }
	        } catch (Exception e) {
	            logger.error("[ERROR] handleRedisConflict中にエラーが発生しました: {}", e.getMessage(), e);
	        }
	    }
	}

	@CrossOrigin(origins = "https://humanage-app-1fe93ce442da.herokuapp.com")
	@PostMapping("/api/createDirectory")
	public ResponseEntity<Map<String, String>> createDirectory(@RequestBody Map<String, String> requestBody) {
	    try {
	        logger.info("[INFO] createDirectoryが呼ばれました。requestBody: {}", requestBody);
	        String directoryPath = requestBody.get("directoryPath");
	        Map<String, String> response = new HashMap<>();

	        if (directoryPath == null || directoryPath.isEmpty()) {
	        	logger.error("[ERROR] ディレクトリパスが指定されていません");
	            response.put("message", "ディレクトリパスが指定されていません");
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	        }
	        
	        logger.debug("[DEBUG] 取得したdirectoryPath: {}", directoryPath);

	        File directory = new File(directoryPath);
	        logger.debug("[DEBUG] 作成したdirectory: {}", directory);
	        if (!directory.exists()) {
	            if (directory.mkdirs()) {
	            	logger.debug("[DEBUG] ディレクトリが作成されました: {}", directory);
	                response.put("message", "ディレクトリが作成されました。");
	                return ResponseEntity.ok(response);
	            } else {
	            	logger.error("[ERROR] ディレクトリの作成に失敗しました。");
	                response.put("message", "ディレクトリの作成に失敗しました");
	                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	            }
	        } else {
	        	logger.info("[INFO] ディレクトリは既に存在します: {}", directory);
	            response.put("message", "ディレクトリは既に存在します");
	            return ResponseEntity.ok(response);
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] createDirectory中にエラーが発生しました: {}", e.getMessage(), e);
	        Map<String, String> errorResponse = new HashMap<>();
	        errorResponse.put("message", "ディレクトリ作成中にエラーが発生しました");
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
	    }
	}

	private void updateRedisStatusWithError(String redisKey, String errorMessage, Exception e) {
	    logger.info("[INFO] updateRedisStatusWithErrorが呼ばれました - Redisエラー更新 - Redisキー: {}, エラーメッセージ: {}", redisKey, errorMessage, e);
	    try {
	        String detailedError = errorMessage + " - 詳細: " + (e != null ? e.getMessage() : "なし");
	        logger.info("[INFO] Redisステータスをエラーに更新 - キー: {}, メッセージ: {}", redisKey, detailedError);
	        redisTemplate.opsForValue().set(redisKey, "error: " + detailedError, Duration.ofSeconds(40));
	    } catch (Exception redisException) {
	        logger.error("[ERROR] Redisステータス更新中にさらにエラーが発生しました - Redisキー: {}, エラー: {}", redisKey, redisException.getMessage(), redisException);
	    }
	}
	
	private boolean ensureDirectoryExistsAndWritable(File directory) {
		logger.info("[INFO] ensureDirectoryExistsAndWritableが呼ばれました。directory: {}", directory);
	    try {
	        if (!directory.exists() && !directory.mkdirs()) {
	            logger.error("[ERROR] 保存ディレクトリの作成に失敗しました - Path: {}", directory.getPath());
	            return false;
	        }
	        if (!directory.canWrite()) {
	            logger.error("[ERROR] 保存ディレクトリに書き込み権限がありません - Path: {}", directory.getPath());
	            return false;
	        }
	        return true;
	    } catch (Exception e) {
	        logger.error("[ERROR] ensureDirectoryExistsAndWritable中にエラーが発生しました - Path: {} - {}", directory.getPath(), e.getMessage(), e);
	        return false;
	    }
	}
	
	private boolean validateGeneratedPdf(File pdfFile) {
	    try {
	        logger.info("[INFO] validateGeneratedPdfが呼ばれました - ファイルパス: {}", pdfFile.getAbsolutePath());
	        
	        if (pdfFile.exists() && pdfFile.isFile() && pdfFile.length() > 0) {
	            logger.info("[INFO] PDFファイルは有効です - ファイルパス: {}, サイズ: {} bytes", pdfFile.getAbsolutePath(), pdfFile.length());
	            return true;
	        } else {
	            if (!pdfFile.exists()) {
	                logger.warn("[WARN] PDFファイルが存在しません - ファイルパス: {}", pdfFile.getAbsolutePath());
	            } else if (pdfFile.length() == 0) {
	                logger.warn("[WARN] PDFファイルが空です - ファイルパス: {}", pdfFile.getAbsolutePath());
	            }
	            return false;
	        }
	    } catch (Exception e) {
	        logger.error("[ERROR] validateGeneratedPdf中にエラーが発生しました - ファイルパス: {} - {}", pdfFile.getAbsolutePath(), e.getMessage(), e);
	        return false;
	    }
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleException(Exception ex) {
	    logger.error("[ERROR] サーバーエラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());

	    if (ex instanceof NoSuchKeyException) {
	        logger.error("[ERROR] S3でキーが見つかりません: {}", ex.getMessage(), ex);
	        return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                .body("{\"message\":\"指定されたリソースが見つかりません。\"}");
	    }

	    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .contentType(MediaType.APPLICATION_JSON)
	            .body("{\"message\":\"システムエラーが発生しました。詳細: " + ex.getMessage() + "\"}");
	}
}
