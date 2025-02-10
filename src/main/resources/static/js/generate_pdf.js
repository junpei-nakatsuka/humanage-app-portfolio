import puppeteer from 'puppeteer-core';
import fs from 'fs';
import { S3Client, PutObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import path from 'path';
let redisClient;
console.log("カレントディレクトリ:", process.cwd());
let logsDirectory = '/tmp/pdf_reports';
import Redis from 'ioredis';
let retryAttempt = 0;
let res = {};
let cookiesFilePath = './cookies.json';
let retryInterval = parseInt(process.env.RETRY_INTERVAL_MS) || 30000;
let maxRetries = parseInt(process.env.MAX_RETRIES) || 5;
import express from 'express';
import cors from 'cors';
let requiredEnvVars = [
	'ADMIN_USERNAME',
	'ADMIN_PASSWORD',
	'REDIS_TLS_URL',
	'AWS_ACCESS_KEY_ID',
	'AWS_SECRET_ACCESS_KEY',
	'AWS_REGION',
	'S3_BUCKET_NAME',
	'BASE_URL'
];
requiredEnvVars.forEach((varName) => {
	if (!process.env[varName] || process.env[varName].trim() === '') {
		console.error("[ERROR] 環境変数" + varName + "が設定されていないか無効です。。");
		process.exit(1);
	}
});

if (typeof process === 'undefined' || !process.env) {
    console.error(`[ERROR] processが未定義です。Node.js環境外で実行されている可能性があります。`);
    throw new Error("process is not defined");
}
requiredEnvVars.forEach((varName) => {
    if (!process.env[varName]) {
        console.error(`[ERROR] 環境変数 ${varName} が設定されていません`);
        process.exit(1);
    }
});

const baseUrl = process.env.BASE_URL;

let retries = 5;

while (retries > 0) {
    try {
		console.debug("[DEBUG] generate_pdf.jsにてredisClientを初期化します。getRedisClientを呼んでいます。");
        redisClient = await getRedisClient();
        if (redisClient.status === 'ready') {
            break;
        }
    } catch (error) {
        console.error(`[ERROR] Redis初期化失敗 (${5 - retries + 1}回目): `, error.message, error.stack, error);
    }
    retries--;
    if (retries === 0) {
        console.error("[ERROR] Redis初期化に失敗しました。Workerを停止します。");
        process.exit(1);
    }
    await new Promise((resolve) => setTimeout(resolve, 5000));
}

console.info("[INFO] loginAndGeneratePDF is:", loginAndGeneratePDF);
if (!loginAndGeneratePDF) {
	console.error("[ERROR] loginAndGeneratePDFが未定義です。");
    throw new Error("loginAndGeneratePDFが未定義です。");
}

let app = express();

app.use(express.json());

app.use(cors({
    origin: 'https://humanage-app-1fe93ce442da.herokuapp.com',
    methods: ['GET', 'POST', 'OPTIONS', "HEAD"],
    allowedHeaders: ['Content-Type', 'X-Auth-Token'],
    credentials: true
}));

app.options('/api/generatePDF/node', (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', 'https://humanage-app-1fe93ce442da.herokuapp.com');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS', 'HEAD');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Auth-Token');
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    res.sendStatus(200);
});

const processTask = async (task) => {
  console.info("[INFO] Redisキューから受信したタスクを処理中: ", task);
  try {
    // Redisから取得したデータをデコード
    const rawTask = task[1]; // 生データ
    console.debug("[DEBUG] 生タスクデータ(rawTask): ", rawTask);
	
	let parsedTask;
	try {
    	// 二重にエスケープされている場合、まず一度アンエスケープ
    	parsedTask = JSON.parse(rawTask);
    	if (typeof parsedTask === "string") {
			console.debug("[DEBUG] parsedTaskは文字列です。再度JSON.parseを実行します: ", parsedTask);
        	console.debug("[DEBUG] parsedTaskの内容: ", parsedTask);
        	
    		parsedTask = JSON.parse(parsedTask);
		}
    	console.debug("[DEBUG] パース済みタスクデータ(parsedTask): ", parsedTask);
	} catch (error) {
		console.error("[ERROR] JSONのパースに失敗しました: ", error.message, error);
		return;
	}
    const { userId, salaryId, paymentMonth, token } = parsedTask;

    // 必須パラメータが不足していないか確認
    if (!userId || !salaryId || !paymentMonth || !token) {
      console.error("[ERROR] 必須パラメータが不足しています。", { userId, salaryId, paymentMonth, token });
      return;
    }
    
    console.debug("[DEBUG] userId: ", userId, ", salaryId: ", salaryId, ", paymentMonth: ", paymentMonth, ", token: ", token);

    const redisKey = `pdf_status:${userId}:${salaryId}:${paymentMonth}`;
    const outputPath = `/tmp/pdf_reports/report_${userId}_${salaryId}_${paymentMonth}.pdf`;
    const fileName = `pdf_report_${userId}_${salaryId}_${paymentMonth}.pdf`;
    const outputDir = "/tmp/pdf_reports";

    console.debug("[DEBUG] redisKey: ", redisKey, ", outputPath: ", outputPath, ", fileName: ", fileName, ", outputDir: ", outputDir);

    await ensureDirectoryExists(outputDir);
	
	console.debug("[DEBUG] loginAndGeneratePDF関数を呼びます。");
	
    await loginAndGeneratePDF(
      `https://humanage-app-1fe93ce442da.herokuapp.com/salaryDetails?userId=${userId}&salaryId=${salaryId}&paymentMonth=${paymentMonth}`,
      outputPath,
      token,
      redisKey,
      fileName,
      process.env.S3_BUCKET_NAME,
      paymentMonth,
      userId,
      salaryId,
      res
    );

    console.info("[INFO] PDF生成に成功しました。outputPath: ", outputPath);
  } catch (err) {
    console.error("[ERROR] タスク処理中にエラーが発生しました: ", err.message, err);
  }
};

const startPolling = async () => {
  console.info("[INFO] Redisキューからタスクをポーリングしています...");
  while (true) {
    try {
      const task = await redisClient.brpop("pdf:tasks", 0); // ioredisのネイティブメソッドを使用
      if (task) {
        await processTask(task);
      }
    } catch (err) {
      console.error("[ERROR] ポーリング中にエラーが発生しました: ", err.message, err);
    }
  }
};

startPolling().catch((err) => {
  console.error("[ERROR] ポーリングの起動中にエラーが発生しました: ", err.message, err);
  process.exit(1);
});

app.get('/api/checkFileExists', async (req, res) => {
	console.info("[INFO] generate_pdf.jsの/api/checkFileExistsが呼ばれました。");
    const { bucket, key } = req.query;
    if (!bucket || !key) {
		console.error("[ERROR] bucketとkeyは必須です。");
        return res.status(400).json({ message: "bucket と key は必須です。" });
    }

    try {
        await s3Client.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
        return res.status(200).json({ exists: true });
    } catch (error) {
        if (error.name === "NotFound") {
			console.error("[ERROR] ファイルが見つかりません: ", error.message, error);
            return res.status(404).json({ exists: false, message: "ファイルが見つかりません。" });
        }
        console.error("[ERROR] S3チェック中にエラーが発生しました: ", error.message, error);
        return res.status(500).json({ exists: false, message: "S3チェック中にエラーが発生しました。", details: error.message });
    }
});

app.use((err, req, res, next) => {
    console.error("[ERROR] 未処理のエラー ミドルウェア:", err.stack || err.message);
    console.error("[ERROR] 未処理エラー:", err.message, err.stack);
    console.error(`[ERROR] ${new Date().toISOString()}] 未処理エラー:`, err);
    
    console.log("[DEBUG] ヘッダーがすでに送信されているかどうかを確認しています...");
    if (!res.headersSent) {
		console.error("[ERROR] 未処理のエラーに対する 500 応答を送信しています");
        res.status(err.status || 500).json({
            error: {
                message: err.message || "Internal Server Error",
                stack: process.env.NODE_ENV === 'development' ? err.stack : undefined,
            },
        });
    } else {
		console.warn("[WARN] ヘッダーはすでに送信されているため、エラー応答はスキップされます。");
		return next(err);
    }
});

const s3Client = new S3Client({
    region: process.env.AWS_REGION || 'ap-northeast-1',
    credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
    }
});

process.on('SIGTERM', async () => {
    console.info('[INFO] SIGTERM を受け取りました。 サーバーを閉じています...');
    try {
		if (redisClient){
        	await redisClient.quit();
        	console.info('[INFO] Redis接続が閉じられました。');
        }
        process.exit(0);
    } catch (error) {
        console.error('[ERROR] シャットダウン中にエラーが発生しました:', error.message, error);
        process.exit(1);
    }
});

const DEFAULT_PORT = 6000;
let port = DEFAULT_PORT;

const server = app.listen(port, () => {
    console.info(`[INFO] generate_pdf.js PDF生成サーバーはポート${port}で実行されています`);
}).on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.warn(`[WARN] ポート${port}が使用中のため、別のポートを試します`);
        port += 1; // 次のポートを試す
        app.listen(port, () => {
            console.info(`[INFO] generate_pdf.js PDF生成サーバーはポート${port}で実行されています`);
        });
    } else {
        console.error('[ERROR] サーバーエラー:', err.message, err);
        process.exit(1);
    }
});

setInterval(() => {
    const memoryUsage = process.memoryUsage();
    console.debug("[DEBUG] メモリ使用状況:", memoryUsage);
}, 60000);

if (!process.env.ADMIN_USERNAME || !process.env.ADMIN_PASSWORD) {
	console.error("[ERROR] 環境変数ADMIN_USERNAMEまたはADMIN_PASSWORDが設定されていません。");
	process.exit(1);
}

if (!fs.existsSync(logsDirectory)) {
    try {
        fs.mkdirSync(logsDirectory);
        console.info("[INFO] ログディレクトリを作成しました:" + logsDirectory);
    } catch (error) {
        console.error("[ERROR] ログディレクトリの作成に失敗しました:" + error.message, error);
    }
}

function safeWriteToFile(filePath, data) {
	console.info("[INFO] generate_pdf.jsのsafeWriteToFileが呼ばれました。", filePath, data);
    try {
        fs.appendFileSync(filePath, data);
    } catch (error) {
        console.error("[ERROR] ファイルへの書き込みに失敗しました:" + filePath);
        console.error("[ERROR] エラーメッセージ:" + error.message, error);
        console.error("[ERROR] 書き込むデータ:" + data);
    }
}

async function loginAndGeneratePDF(url, outputPath, token, redisKey, fileName, bucketName, paymentMonth, userId, salaryId, res) {
	console.info("[INFO] Node.jsのgenerate_pdf.jsのloginAndGeneratePDFが呼び出されました - URL:", url, ", outputPath:", outputPath, ", token:", token, "redisKey:", redisKey, "fileName:", fileName, "bucketName", bucketName, "paymentMonth", paymentMonth, "userId", userId, "salaryId", salaryId);
	console.debug("[DEBUG] 現在のRedisステータスを取得 - Redisキー:", redisKey, "ステータス:", await redisClient.get(redisKey));
	console.debug("[DEBUG] PDF generation URL:", url);
	console.debug("[DEBUG] Starting PDF generation for:", { userId, salaryId, paymentMonth });

	if (!userId || !salaryId || !paymentMonth) {
		console.error("[ERROR] 必須パラメータが不足しています。userId, salaryId, paymentMonthを確認してください。");
		throw new Error("必須パラメータが不足しています。userId, salaryId, paymentMonthを確認してください。");
	}

	const outputDir = process.env.PDF_STORAGE_PATH || path.join(__dirname, "pdf_reports");
	console.debug("[DEBUG] PDF保存先ディレクトリ:", outputDir);
	await ensureDirectoryExists(outputDir);
	await ensureDirectoryExists(logsDirectory);

	const lockKey = await acquireLock(redisKey);
	let browser;

	try {
		console.debug("[DEBUG] 現在のRedisステータスを取得 - Redisキー:", redisKey, "ステータス:", await redisClient.get(redisKey));
		await setRedisStatus(redisKey, 'processing');
		console.debug("[DEBUG] Redisステータスを 'processing' に設定しました:", redisKey);
		console.debug("[DEBUG] RedisにPDF生成ステータスを設定: processing");
		console.debug("[DEBUG] Redis保存キー:", redisKey, "保存ステータス: processing");
		const status = await redisClient.get(redisKey);
		console.debug("[DEBUG] 保存直後のRedisキー:", redisKey, "ステータス:", status);

		const pdfGenerated = await retryOperation(async () => {
			console.info("[INFO] generate_pdf.jsでPuppeteerでPDF生成開始... URL:" + url + "Output Path:" + outputPath);
			try {
				browser = await puppeteer.launch({
					executablePath: process.env.CHROME_BIN || '/usr/bin/chromium',
					headless: "new",
					ignoreHTTPSErrors: true,
					args: [
						"--disable-extensions", "--no-sandbox", "--disable-dev-shm-usage", "--disable-setuid-sandbox",
						"--disable-notifications", "--ignore-certificate-errors", "--disable-gpu",
						"--remote-debugging-port=9222", "--disable-software-rasterizer",
						"--disable-background-timer-throttling", "--disable-backgrounding-occluded-windows",
						"--disable-canvas-aa", "--disable-accelerated-2d-canvas", "--disable-background-networking", "--mute-audio",
						"--disable-features=IsolateOrigins,site-per-process", "--disable-cache",
						"--disable-accelerated-video-decode"
					],
					timeout: 50000,
					protocolTimeout: 50000
				});
			} catch (error) {
				console.error("[ERROR] generate_pdf.jsでPuppeteerの起動に失敗しました: Name:", error.name, "Message:", error.message, "Stack:", error.stack, "Code:", error.code, "File:", error.fileName, "Line:", error.lineNumber, "Column:", error.columnNumber, "ToString:", error.toString(), "Cause:", error.cause);
				throw new Error("generate_pdf.jsでPuppeteerの起動に失敗しました: " + error.message + error.stack + error.toString(), error);
			}

			console.debug("[DEBUG] [Puppeteer] generate_pdf.jsでブラウザが正常に開始されました。");

			if (!browser.connected) {
				console.error("[ERROR] Puppeteerのブラウザインスタンスが切断されています。");
				return;
			}

			let page;
			try {
				try {
					console.debug("[DEBUG] 新しいページを作成...");
					console.debug("[DEBUG] browser.newPage() 呼び出し前");
					const newPagePromise = browser.newPage();
					const timeoutPromise = new Promise((_, reject) =>
						setTimeout(() => reject(new Error("browser.newPage() がタイムアウトしました")), 10000)
					);
					page = await Promise.race([newPagePromise, timeoutPromise]);
					console.debug("[DEBUG] browser.newPage() 呼び出し後");
					page.setDefaultTimeout(50000);
					console.debug("[DEBUG] [Puppeteer] 新しいページが作成されました。");
				} catch (error) {
					console.error("[ERROR] page.newPage() でエラーが発生しました:", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause);
				}

				if (token) {
					await page.setExtraHTTPHeaders({
						'Authorization': 'Bearer ' + token
					});
					console.debug("[DEBUG] トークンがheadersに追加されました。");
				}

				page.on('console', msg => console.log('PAGE LOG:', msg.text()));

				await page.evaluate(() => {
					return document.fonts ? document.fonts.ready.catch(() => { }) : null;
				});

				page.on('response', response => {
					console.info('[INFO] HTTPレスポンス:', response.status(), response.url());
				});

				process.env.DEBUG = 'puppeteer:*';

				page.setRequestInterception(true);
				page.on('request', (request) => {
					console.info("[INFO] リクエスト: " + request.url() + ", タイプ: " + request.resourceType());
					if (['image', 'font', 'media'].includes(request.resourceType())) {
						request.abort();
					} else {
						request.continue();
					}
				});

				console.debug("[DEBUG] 既存クッキーの確認...");
				if (fs.existsSync(cookiesFilePath)) {
					const cookies = JSON.parse(fs.readFileSync(cookiesFilePath, 'utf-8'));
					await page.setCookie(...cookies);
					console.debug("[DEBUG] クッキーをロードし、セッションを確認します...");
					const isLoggedIn = await checkSession(page);
					console.debug("[DEBUG] ログイン状態確認:", isLoggedIn);

					if (!isLoggedIn) {
						fs.unlinkSync(cookiesFilePath);
						console.warn("[WARN] セッションが切れたため再ログインします...");
						await performLogin(page);
					} else {
						console.debug("[DEBUG] セッション確認済み。");
					}
				} else {
					console.warn("[WARN] クッキーなし。ログインを実行します...");
					await performLogin(page);
				}

				console.debug("[DEBUG] セッションを確認中...");
				await checkSession(page);

				page.on('console', (msg) => console.log('[PAGE LOG] ' + msg.text()));
				page.on('response', (response) => console.log('[PAGE RESPONSE] ' + response.status() + ' - ' + response.url()));
				page.on('requestfailed', (request) => console.error('[PAGE REQUEST FAILED] ' + request.failure().errorText + ' - ' + request.url()));

				try {
					console.info("[INFO] PDF生成対象ページへ移動します: " + url + "Output Path:" + outputPath);
					await retryOperation(async () => {
						try {
							if (!url.startsWith('https://humanage-app-')) {
								console.error("[ERROR] 無効なURLが指定されました: ", url);
								throw new Error("無効なURLが指定されました: " + url);
							}
							await page.setCacheEnabled(false);
							const response = await page.goto(url, { waitUntil: 'networkidle2', timeout: 50000, });
							if (!response || !response.ok() || response.status() === 404) {
								console.error("[ERROR] ページアクセス失敗: ステータスコード ${response?.status()}, URL ${url}");
								throw new Error(`ページアクセス失敗: ステータスコード ${response?.status()}, URL ${url}`);
							}
							const responseStatus = response.status();
							console.debug("[DEBUG] APIレスポンスステータス:" + responseStatus);
							if (responseStatus >= 400) {
								console.error("[ERROR] PDF生成API失敗: ステータスコード: ", responseStatus);
								throw new Error("PDF生成API失敗: ステータスコード" + responseStatus);
							}
						} catch (error) {
							console.error("[ERROR] ページへのアクセス失敗:", url, error.message, error.stack, error);
							await setRedisStatus(redisKey, 'error');
							throw new Error("ページアクセス失敗");
						}
					}, maxRetries, retryInterval);
					console.debug("[DEBUG] ページロード完了 - URL:", page.url());
					console.debug("[DEBUG] ページにアクセスしました: " + url);

					await page.evaluate(() => {
						return new Promise((resolve, reject) => {
							const checkCSS = setInterval(() => {
								if (document.styleSheets.length > 0) {
									clearInterval(checkCSS);
									resolve(true);
								}
							}, 100);
						});
					});
					console.debug("[DEBUG] CSSが適用されるのを待ちました。");
				} catch {
					console.error("[ERROR] ページへのアクセスに失敗しました: URL=" + url + ", エラー=" + error.toString() + ", スタック=" + error.stack + ", 原因=" + error.cause, error);
					throw new Error("ページアクセス失敗: " + error.toString(), error);
				}

				if (!await page.evaluate(() => document.documentElement.outerHTML.includes('</html>'))) {
					console.warn("[WARN] ページ内容が不完全です。");
				} else {
					console.debug("[DEBUG] ページ内容が有効です。");
				}

				const contentType = await page.evaluate(() => document.contentType);
				console.debug("[DEBUG] [Puppeteer] ページのContent-Type:", contentType);
				if (!['application/pdf', 'text/html'].includes(contentType)) {
					console.warn("[WARN] 予期しないContent-Typeを受信しましたが、処理を続行します: ", contentType);
				}

				console.debug("[DEBUG] generate_pdf.jsにてページ移動完了。PDFの生成を開始します - 出力パス: " + outputPath);
				console.debug("[DEBUG] generate_pdf.jsにてPDF生成を開始します - " + new Date().toISOString());

				try {
					await new Promise(resolve => setTimeout(resolve, 1000));
					const pdfGenerationPromise = page.pdf({ path: outputPath, format: 'A4', printBackground: true });
					console.debug("[DEBUG] PDF生成オプション:" + pdfGenerationPromise);
					const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('PDF生成タイムアウト')), 50000));
					await Promise.race([pdfGenerationPromise, timeoutPromise]).catch(error => {
						console.error("[ERROR] PDF生成中にタイムアウト:", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause);
						throw error;
					});

					console.debug("[DEBUG] PDF生成完了。ファイルの存在確認中..." + outputPath);
					console.debug("[DEBUG] Redisステータスを確認 - 現在のステータス:", await redisClient.get(redisKey));
					if (!fs.existsSync(outputPath) || fs.statSync(outputPath).size === 0) {
						console.error("[ERROR] PDF生成失敗 - ファイルが存在しないか空です: ", outputPath);
						safeWriteToFile(
							path.join(logsDirectory, 'error_log.log'),
							new Date().toISOString() + " - PDF生成失敗: ファイルが空または存在しない - Path: " + outputPath + "\n"
						);
						await setRedisStatus(redisKey, 'error');

						const retries = 3;
						for (let i = 0; i < retries; i++) {
							console.debug("[DEBUG] PDF再試行(" + (i + 1) + "/" + retries + ")...");
							await loginAndGeneratePDF(
								url,
								outputPath,
								token,
								redisKey,
								fileName,
								process.env.S3_BUCKET_NAME,
								paymentMonth,
								userId,
								salaryId,
								res
							);
							if (fs.existsSync(outputPath) && fs.statSync(outputPath).size > 0) {
								console.info("[INFO] PDF生成が成功しました。再試行後のファイルパス: " + outputPath);
								break;
							}
						}

						if (!fs.existsSync(outputPath) || fs.statSync(outputPath).size === 0) {
							console.error("[ERROR] PDF生成を再試行しましたが失敗しました。PDFファイルが空かサイズが０です: ", outputPath);
							throw new Error("PDF生成を再試行しましたが失敗しました。PDFファイルが空か、サイズが０です:", outputPath);
						}
					} else {
						console.info("[INFO] PDF生成成功 - ファイルパス:", outputPath);
						await setRedisStatus(redisKey, "complete");
					}
				} catch (error) {
					console.error("[ERROR] PDF生成中にエラーが発生しました: ", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause);
					safeWriteToFile(
						path.join(logsDirectory, 'error_log.log'),
						new Date().toISOString() + " - PDF生成エラー: " + error.stack + "\n"
					);
					await setRedisStatus(redisKey, 'error');
					throw new Error("Puppeteerエラー: " + error.message);
				}

				console.debug("[DEBUG] PDFが正常に生成されました - ファイルパス:", outputPath);
				console.debug("[DEBUG] PDF生成成功 - 出力先:" + outputPath + ":" + new Date().toISOString());
				console.debug("[DEBUG] PDF生成完了後のRedisステータス - Redisキー:", redisKey, "ステータス:", await redisClient.get(redisKey));

				const fileExists = await checkIfS3FileExists(bucketName, fileName);

				if (fileExists) {
					console.debug("[DEBUG] S3に既に同じファイルが存在しますが、上書きします。fileName: ", fileName);
				}

				if (!fs.existsSync(outputPath)) {
					console.error("[ERROR] PDFファイルが見つかりません - outputPath: " + outputPath);
					await setRedisStatus(redisKey, 'error');
					throw new Error("PDFが生成されませんでした");
				} else {
					console.debug("[DEBUG] PDFファイルがoutputPathに見つかったため、redisステータスをcompleteにします。outputPath: ", outputPath);
					try {
						await setRedisStatus(redisKey, "complete");
						console.info(`[INFO] Status updated to 'complete' for ${redisKey}`);
					} catch (error) {
						console.error(`[ERROR] Error setting Redis status for ${redisKey}:`, error);
					}
					console.info("[INFO] PDFが正常に生成されました:" + outputPath);
					return true;
				}
			} catch (error) {
				console.error("[ERROR] generate_pdf.jsでPDF生成中にエラーが発生しました。エラー内容:", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause);
				if (error.message.includes('Session expired')) {
					await setRedisStatus(redisKey, 'error');
				} else if (error.message.includes('Cookie error')) {
					await setRedisStatus(redisKey, 'error');
				} else if (error.message.includes('Network error')) {
					await setRedisStatus(redisKey, 'error');
				} else if (error.message.includes('timeout')) {
					console.error("[ERROR] PDF生成中にタイムアウトエラーが発生しました。");
					await setRedisStatus(redisKey, 'error');
				} else {
					console.error("[ERROR] PDF生成中に不明なエラーが発生しました。" + error.message + error.toString(), error);
					safeWriteToFile(
						path.join(logsDirectory, 'error_log.log'),
						new Date().toISOString() + " - PDF生成エラー: " + error.stack + "\n"
					);
					await setRedisStatus(redisKey, 'error');
				}
				throw error;
			} finally {
				if (page) {
					await page.close();
					console.debug("[DEBUG] Puppeteer ページを確実に閉じました。");
				}
				if (browser) {
					await browser.close();
					console.debug("[DEBUG] Puppeteer ブラウザを確実に閉じました。");
				} else {
					console.warn("[WARN] ブラウザインスタンスが見つかりませんでした。強制終了します。");
					process.exit(1);
				}
			}
		}, maxRetries, retryInterval) || false;

		if (pdfGenerated === true) {
			console.info("[INFO] PDF生成完了 - S3へのアップロードを開始します");
			await uploadToS3(outputPath, bucketName, fileName, redisKey);
			try {
				await setRedisStatus(redisKey, "complete");
				console.info(`[INFO] Status updated to 'complete' for ${redisKey}`);
			} catch (error) {
				console.error(`[ERROR] Error setting Redis status for ${redisKey}:`, error);
			}
			checkPdfCompletionAndProceed(redisKey);
			console.info("[INFO] PDF生成成功: Redisステータスを 'complete' に更新しました");
		} else {
			console.error("[ERROR] PDF生成が不完全 - Redisステータスを 'error' に更新します");
			await setRedisStatus(redisKey, 'error');
		}
		return pdfGenerated;
	} catch (error) {
		console.error("[ERROR] loginAndGeneratePDF関数内でエラーが発生しました。handleErrorを呼びます: ", error.toString());
		handleError(res, error);
	} finally {
		console.debug("[DEBUG] loginAndGeneratePDF関数で確実にロックを解除します。");
		await releaseLock(lockKey);
		if (fs.existsSync(outputPath)) {
			setTimeout(() => fs.unlinkSync(outputPath), 5000);
			console.debug("[DEBUG] 一時ファイル削除 - パス: " + outputPath);
		}
	}
}

async function checkSession(page) {
	console.info("[INFO] checkSessionが呼ばれました。", page);
	try {
		if (!fs.existsSync(cookiesFilePath)) {
			console.warn("[WARN] セッション用クッキーが見つかりません。再ログインを試みます...");
			await performLogin(page);
			return true;
		}

		const cookies = JSON.parse(fs.readFileSync(cookiesFilePath, 'utf-8'));
		await page.setCookie(...cookies);
		console.debug("[DEBUG] セッション確認用ページに移動中...");
		await page.goto('https://humanage-app-1fe93ce442da.herokuapp.com/top', { waitUntil: 'networkidle2', timeout: 30000 });
		const sessionExists = await page.evaluate(() => !!document.querySelector('header p'));

		if (!sessionExists) {
			console.warn("[WARN] セッションが無効です。ログインを試みます...");
			await performLogin(page);
		}

		return sessionExists;
	} catch (error) {
		console.error("[ERROR] セッション確認中にエラーが発生しました。エラー詳細:" + error.message, error);
		return false;
	}
}

async function performLogin(page) {
	console.info("[INFO] performLoginが呼ばれました。", page);
	try {
		await retryOperation(async () => {
			console.debug("[DEBUG] ログインページに移動中...");
			await page.goto('https://humanage-app-1fe93ce442da.herokuapp.com/login', { waitUntil: 'networkidle2' });
			await page.type('#username', process.env.ADMIN_USERNAME);
			await page.type('#password', process.env.ADMIN_PASSWORD);
			console.debug("[DEBUG] ログイン情報入力完了。ログインボタンクリック中...");
			await Promise.all([
				page.click('#login-submit-button'),
				page.waitForNavigation({ waitUntil: 'networkidle2' })
			]);
			const cookies = await page.cookies();
			fs.writeFileSync(cookiesFilePath, JSON.stringify(cookies));
			console.debug("[DEBUG] ログイン成功、クッキーを保存しました。");
		}, maxRetries, retryInterval);
	} catch (error) {
		console.error("[ERROR] performLoginでエラー発生: " + error.message, error);
		throw error;
	}
}

async function retryOperation(operation, maxRetries, baseRetryInterval, timeout = 50000) {
    console.info("[INFO] retryOperationが呼ばれました - operation:", operation.name, ", maxRetries:", maxRetries, ", baseRetryInterval:", baseRetryInterval);
    const startTime = Date.now();
    let attempt = 0;
    retryInterval = baseRetryInterval;

    while (attempt < maxRetries && (Date.now() - startTime) < timeout) {
        try {
            console.debug("[DEBUG] retryOperation - Attempt:" + (attempt + 1));
            const result = await operation(); // ここで operation の戻り値を取得
            console.info("[INFO] Operationが成功しました。attempt: " + (attempt + 1));
            return result; // 結果を明示的に return
        } catch (error) {
            console.warn("[WARN] リトライ " + (attempt + 1) + " 失敗。次の試行まで " + retryInterval + "ms 待機中... エラー内容: " + error.message, error);
            if (error.message.includes("権限不足") || error.message.includes("ディスク容量不足")) {
                console.error("[ERROR] リトライ不可エラーが発生しました:", error.message, error);
                throw error;
            }
            safeWriteToFile(
                path.join(logsDirectory, 'error_log.log'),
                new Date().toISOString() + " - retryOperationエラー: " + error.stack + "\n"
            );
            if (attempt === maxRetries - 1) {
                console.error("[ERROR] 最大リトライ回数に達しました。エラー: " + error.message, error);
                throw new Error("最大リトライ回数を超えました: " + error.message, error);
            }

            await new Promise(resolve => setTimeout(resolve, retryInterval));
            retryInterval *= 2;
            attempt++;
        }
    }
    console.error("[ERROR] 全てのリトライが失敗しました - Operation: " + operation.name);
    throw new Error("リトライ回数が最大に達しました: " + operation.name);
}

async function checkIfS3FileExists(bucketName, fileName) {
	console.info("[INFO] checkIfS3FileExistsが呼ばれました: ", bucketName, fileName);
	const s3Client = new S3Client({
		region: process.env.AWS_REGION || 'ap-northeast-1',
		credentials: {
			accessKeyId: process.env.AWS_ACCESS_KEY_ID,
			secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
		}
	});
	try {
		await s3Client.send(new HeadObjectCommand({ Bucket: bucketName, Key: fileName }));
		console.info("[INFO] S3にファイルが存在します - fileName: " + fileName);
		return true;
	} catch (error) {
		if (error.name === 'NotFound') {
			console.error("[ERROR] S3にファイルが存在しません - fileName: ", fileName, ", エラー: ", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause);
			return false;
		}
		console.error("[ERROR] S3ファイルの存在確認中にエラーが発生しました: ", error);
		throw error;
	}
}

async function uploadToS3(filePath, bucketName, fileName, redisKey) {
    console.info("[INFO] generate_pdf.jsのuploadToS3が呼ばれました - filePath: " + filePath + ", bucketName: " + bucketName + ", fileName: " + fileName + ", redisKey: " + redisKey);
    console.debug("[DEBUG] S3にアップロード開始 - バケット: " + bucketName + ", ファイル名: " + fileName);

    const s3Client = new S3Client({
        region: process.env.AWS_REGION || "ap-northeast-1",
        credentials: {
            accessKeyId: process.env.AWS_ACCESS_KEY_ID,
            secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
        }
    });

    // 既存ファイルのチェック（ログ出力のみ）
    let fileExists = await checkIfS3FileExists(bucketName, fileName);
    if (fileExists) {
        console.info(`[INFO] S3に既に同じファイルが存在します: ${fileName} → 上書きします`);
    }

    if (!fs.existsSync(filePath)) {
        console.error("[ERROR] アップロードするファイルが見つかりません: " + filePath);
        throw new Error("ファイルが存在しません。");
    }

    let fileStream;
    try {
        fileStream = fs.createReadStream(filePath);
        const command = new PutObjectCommand({
            Bucket: bucketName,
            Key: fileName,  // 変更なしでアップロード（上書き）
            Body: fileStream
        });

        // S3にアップロード
        await retryOperation(() => s3Client.send(command), maxRetries, retryInterval);

        // アップロード確認
        const uploadSuccess = await retryOperation(async () => {
            return await checkIfS3FileExists(bucketName, fileName);
        }, maxRetries, retryInterval);

        if (!uploadSuccess) {
            console.error("[ERROR] S3アップロード後のファイル存在確認に失敗しました。");
            throw new Error("S3アップロード後のファイル存在確認に失敗しました");
        }

        // Redisにアップロード成功を記録
        await setRedisStatus(redisKey, "complete");
        console.info("[INFO] S3へのアップロードが成功しました: " + fileName);
    } catch (error) {
        console.error("[ERROR] S3アップロード中にエラー: " + error.toString() + ", スタック: " + error.stack + ", 原因: ", error.cause);
        handleS3UploadError(fileName, error.message);
        await setRedisStatus(redisKey, "error");
        throw error;
    } finally {
        if (fileStream) {
            console.debug("[DEBUG] ファイルストリームをクローズします: " + filePath);
            fileStream.destroy();
        }

        if (fs.existsSync(filePath)) {
            console.debug("[DEBUG] ローカルファイルを削除します: " + filePath);
            fs.unlinkSync(filePath);
        } else {
            console.warn("[WARN] 削除しようとしたファイルが見つかりませんでした: " + filePath);
        }
    }
}

function handleS3UploadError(fileName, message) {
	console.error("[ERROR] handleS3UploadErrorが呼ばれました。S3アップロードエラー - ファイル名: " + fileName + ", メッセージ: " + message);
	safeWriteToFile(
		path.join(logsDirectory, 's3_errors.log'),
		new Date().toISOString() + " - ファイル名: " + fileName + ", メッセージ: " + message + "\n"
	);
}

const checkPdfCompletionAndProceed = async (redisKey) => {
	console.info("[INFO] checkPdfCompletionAndProceedが呼ばれました: ", redisKey);
    const response = await fetch(baseUrl + `api/checkPdfStatus?redisKey=${redisKey}`);
    const data = await response.json();

    if (data === "PDF生成が完了しました") {
        console.info("[INFO] PDF生成完了:", data);
    } else {
        console.warn("[WARN] PDF生成がまだ進行中です。再度確認してください。");
    }
};

function gracefulShutdown() { 
	console.info("[INFO] サーバー停止プロセスが開始されました...");

	if (redisClient) {
		redisClient.quit().then(() => {
			console.info("[INFO] Redis接続を終了しました。");
			process.exit(0);
		}).catch((error) => {
			console.error("[ERROR] Redis接続終了中にエラーが発生しました:", error.message, error);
			process.exit(1);
		});
	} else {
		process.exit(0);
	}
}

async function setRedisStatus(redisKey, status) {
	console.info("[INFO] generate_pdf.jsのsetRedisStatusが呼ばれました。", redisKey, status);
    const validStatuses = ['processing', 'complete', 'error', 'not_found'];
    if (!validStatuses.includes(status) && !status.startsWith('error')) {
        console.error("[ERROR] 無効なステータスを設定しようとしています: " + status);
        throw new Error("無効なステータス: " + status);
    }
    
    if (!redisKey || redisKey.trim() === '' || typeof redisKey !== 'string') {
        console.error("[ERROR] redisKeyが空または未定義です。");
        throw new Error("redisKeyが無効です。" + redisKey);
    }
    
    try {
        await retryOperation(async () => {
			console.debug("[DEBUG] [Redis] ステータスを設定中 - キー:", redisKey, "ステータス:", status);
            await redisClient.set(redisKey, status, 'EX', 40);
        }, 5, 5000);
        console.info("[INFO] Redisステータス設定完了: " + redisKey + " => " + status);
    } catch (error) {
    	console.error("[ERROR] Redisステータス設定中にエラー発生 - Redisキー: " + redisKey + ", ステータス: " + status + ", エラー: " + error.message, error);
    	const errorDetails = "Redisステータス設定中にエラー発生:\n" +
        	"メッセージ: " + error.message + "\n" +
        	"スタックトレース: " + error.stack + "\n";
    	safeWriteToFile(
        	'/tmp/pdf_reports/error_log.log',
        	new Date().toISOString() + " - RedisKey: " + redisKey + ", エラー: " + errorDetails + "\n"
    	);
    	await redisClient.set(redisKey, `error:${error.message || 'Unknown error'}`, 'EX', 40);
    	throw error;
	}
}

async function acquireLock(redisKey) {
    console.info("[INFO] generate_pdf.jsのacquireLockが呼ばれました。ロックします", redisKey);
    const lockKey = "lock:" + redisKey;
    
    for (let attempt = 0; attempt < 5; attempt++) {
        try {
            const lockAcquired = await redisClient.set(lockKey, 'locked', 'NX', 'EX', 40);
            if (lockAcquired) {
                console.info("[INFO] ロック取得成功: " + lockKey);
                return lockKey;
            }

            const ttl = await redisClient.ttl(lockKey);
            console.warn(`[WARN] ロック取得に失敗 (試行 ${attempt + 1}/5): ${lockKey}, 残りTTL: ${ttl}s`);

            // 🔽 期限切れロックを削除
            if (ttl === -2) {
                console.warn("[WARN] 期限切れロックを検出。削除してリトライします - キー:" + lockKey);
                await redisClient.del(lockKey);
                console.info("[INFO] 期限切れロックを削除しました: " + lockKey);
            }

            // 🔽 修正: 指数バックオフ方式でリトライ
            const waitTime = Math.min(1000 * Math.pow(2, attempt), 10000);
            await new Promise(resolve => setTimeout(resolve, waitTime));

        } catch (error) {
            console.error(`[ERROR] Redisロック取得失敗 (試行 ${attempt + 1}/5): ${error}`);
        }
    }

    throw new Error("Redisロック取得失敗 - キー: " + lockKey);
}

async function releaseLock(lockKey) {
	console.info("[INFO] generate_pdf.jsのreleaseLockが呼ばれました。ロックを解除します", lockKey);
    try {
        const deleted = await redisClient.del(lockKey);
        if (deleted) {
            console.info("[INFO] [Lock] ロックを解除しました - キー:", lockKey);
        } else {
            console.warn("[WARN] [Lock] ロックが既に解除されているか存在しません - キー:", lockKey);
        }
    } catch (error) {
        console.error("[ERROR] ロック解除に失敗しました: " + lockKey + ", エラー: " + error.message, error);
        for (let attempt = 1; attempt <= 3; attempt++) {
            try {
                console.debug("[DEBUG] ロック解除リトライ (" + attempt + "/3) - ロックキー: " + lockKey);
                await redisClient.del(lockKey);
                console.info("[INFO] ロック解除成功: " + lockKey);
                return;
            } catch (retryError) {
                console.error("[ERROR] ロック解除リトライ失敗 (" + attempt + "/3): " + retryError.message, redisError);
            }
        }
        console.error("[ERROR] ロック解除が最終的に失敗しました: " + lockKey + "エラーメッセージ:" + error.message, error);
        throw error;
    }
}

async function cleanupRedisKeys() {
	console.info("[INFO] cleanupRedisKeysが呼ばれました");
    const keys = await redisClient.keys("pdf_status:*");
    for (const key of keys) {
        const ttl = await redisClient.ttl(key);
        if (ttl === -2) {
            console.info("[INFO] 削除済みキー: " + key);
            continue;
        }
        if (ttl === -1) {
            await redisClient.del(key);
            console.info("[INFO] 有効期限のないキーを削除: " + key);
        }
    }
}

async function ensureDirectoryExists(dirPath) {
	console.info("[INFO] ensureDirectoryExistsが呼ばれました: ", dirPath);
    if (!fs.existsSync(dirPath)) {
        try {
            fs.mkdirSync(dirPath, { recursive: true });
            console.info("[INFO] ディレクトリ作成成功:", dirPath);
        } catch (error) {
            console.error("[ERROR] ディレクトリ作成失敗:", dirPath, error.message, error);
            throw new Error("ディレクトリ作成エラー: " + error.message, error);
        }
    }
    try {
        fs.accessSync(dirPath, fs.constants.W_OK);
    } catch (error) {
        console.error("[ERROR] ディレクトリ書き込み権限エラー:", dirPath, error.message, error);
        throw new Error("ディレクトリ書き込み権限エラー: " + error.message, error);
    }
}

function handleError(res, error) {
    const mockRes = (res && typeof res.status === "function") ? res : {
        status: (code) => ({
            json: (message) => console.log(`[STATUS ${code}]`, message),
        }),
    };

    console.info("[INFO] handleErrorが呼ばれました:", mockRes, error);
    console.error("[ERROR] エラー発生:", error.message, error);

    if (mockRes.headersSent) {
        console.warn("[WARN] レスポンスヘッダーは既に送信されています。エラーハンドリングをスキップします。");
        return;
    }

    try {
        mockRes.status(500).json({
            status: 'error',
            message: error.message || "Internal Server Error",
        });
    } catch (sendError) {
        console.error("[ERROR] エラーレスポンス送信中にエラー発生:", sendError.message, sendError);
    }
}


async function waitForRedisReady(client, retries = 10, interval = 7000) {
    console.info("[INFO] generate_pdf.jsのwaitForRedisReady関数が呼ばれました。");

    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            if (client.status === 'ready') {
                console.info('[INFO] generate_pdf.js Redisは準備完了です。');
                return;
            }

            console.warn(`[WARN] generate_pdf.js Redis未準備 (${retries - attempt + 1}回の試行残り)`);

            await new Promise((resolve, reject) => {
                const handleReady = () => {
                    client.off('ready', handleReady);
                    resolve();
                };

                client.once('ready', handleReady);
                setTimeout(resolve, interval);
            });
        } catch (error) {
            console.error(`[ERROR] generate_pdf.js Redisの準備中にエラーが発生しました: ${error.message}`, error.stack, error.toString(), error);
        }
    }
	
	console.error("[ERROR] Redisが準備完了状態になりませんでした。");
    throw new Error("Redisが準備完了状態になりませんでした。");
}

export async function getRedisClient() {
	console.info("[INFO] getRedisClient関数が呼ばれました。");
	const redisUrl = process.env.REDIS_TLS_URL;

	if (!redisUrl) {
		console.error("[ERROR] 環境変数 'REDIS_TLS_URL' が設定されていません。");
		throw new Error("REDIS_TLS_URL 環境変数が設定されていません。");
	}

	if (redisClient && ['connecting', 'ready'].includes(redisClient.status)) {
		console.info(`[INFO] 既存のRedisクライアントを再利用します。状態: ${redisClient.status}`);
		await waitForRedisReady(redisClient);
		return redisClient;
	}

	console.info("[INFO] generate_pdf.jsにて新しいRedisクライアントを作成します - URL:", redisUrl);
	try {
		redisClient = new Redis(redisUrl, {
			tls: { rejectUnauthorized: false },
			maxRetriesPerRequest: null,
			enableReadyCheck: false,
			reconnectOnError: (err) => {
				if (err.message.includes("ECONNREFUSED") || err.message.includes("ETIMEDOUT")) {
					console.error("[ERROR] Redisエラー (再接続可能):", err.message, err.stack, err.toString(), err);
					return true;
				}
				return false;
			},
			retryStrategy: (times) => {
				const maxRetryAttempts = parseInt(process.env.REDIS_MAX_RETRIES || "10", 10);
				retryAttempt = times;
				if (times > maxRetryAttempts) {
					console.error(`[ERROR] [${new Date().toISOString()}] Redisリトライが最大試行回数(${maxRetryAttempts})に達しました。接続を停止します。`);
					return new Error("Redisの接続試行が最大回数に達しました");
				}
				const delay = Math.min(times * 1000, 3000);
				console.warn(`[WARN] [${new Date().toISOString()}] retryStrategyによりRedis再接続中 (試行: ${times}, 次回遅延: ${delay}ms)`);
				return delay;
			},
		});
		console.info("[INFO] Redisクライアントの接続状態:", redisClient.status);
		setupEventHandlers(redisClient);
		await waitForRedisReady(redisClient);
	} catch (error) {
		console.error("[ERROR] Redisクライアントの作成中にエラーが発生:", error.message, error.stack, error.toString(), error);
		throw error;
	}

	return redisClient;
}

function setupEventHandlers(redisClient) {
    console.info("[INFO] generate_pdf.jsによるsetupEventHandlers関数が呼ばれました。");

    redisClient.on('connect', () => {
        console.info(`[INFO] [${new Date().toISOString()}] generate_pdf.jsによるRedisに接続成功 状態: ${redisClient.status}`);
        retryAttempt = 0;
    });

    redisClient.on('ready', () => console.info(`[INFO] [${new Date().toISOString()}] generate_pdf.jsによるRedisクライアントが準備完了です。`));

    redisClient.on('error', (err) => {
        console.error("[ERROR] generate_pdf.jsによるRedisエラー:", err.toString(), ", 原因: ", err.cause);
        console.error("[ERROR] generate_pdf.jsによるエラースタック:", err.stack, err);
    });

    redisClient.on('reconnecting', (delay) => {
        console.warn(`[WARN] [${new Date().toISOString()}] generate_pdf.jsによるReconnecting to Redis in ${delay} ms... (attempt ${retryAttempt})`);
    });

    redisClient.on('end', () => {
        console.warn(`[WARN] [${new Date().toISOString()}] generate_pdf.jsによるRedis接続が切断されました。再接続を試みます...`);
    });
}

setInterval(cleanupRedisKeys, 3600000);

process.on('unhandledRejection', (reason, promise) => {
	console.error("[ERROR] Unhandled Rejection at:", promise, "reason:", reason);
});

process.on('uncaughtException', (error) => {
	console.error("[ERROR] generate_pdf.jsの未処理例外:", error.message, error.stack, error);
	console.error("[ERROR] Uncaught Exception:", error);
	safeWriteToFile('/tmp/pdf_reports/error_log.log', `[${new Date().toISOString()}] 未処理例外: ${error.stack}\n`);
	process.exit(1);
});

process.on('SIGTERM', gracefulShutdown);
process.on('SIGINT', gracefulShutdown);

