<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ page import="jp.co.example.entity.User"%>
<%@ page import="jp.co.example.entity.Salary"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>給与明細</title>
<link rel="stylesheet"
	href="${pageContext.request.contextPath}/css/payroll_management.css">
<script>
	console.debug("[DEBUG] JSPで設定されたuserId:", "<%=request.getAttribute("user") != null ? ((User) request.getAttribute("user")).getId() : "null"%>");
    console.debug("[DEBUG] JSPで設定されたsalaryId:", "<%=request.getAttribute("salary") != null ? ((Salary) request.getAttribute("salary")).getId() : "null"%>");
    let isPolling = false;
	let isGeneratingPDF = false;
	let isGenerating = false;
	let hasDownloaded = false;
	const GENERATE_TIMEOUT = 40000;
	let attempts = 0;
	let retryCount = 0;
	const maxRetries = 10;
	let retryInterval = 40000;
	let baseUrl = "<%=System.getenv("BASE_URL") != null ? System.getenv("BASE_URL") : "https://humanage-app-1fe93ce442da.herokuapp.com/" %>";
	if (!baseUrl) {
		console.debug("[DEBUG] 環境変数からbaseUrlが取得できなかったので代入します。");
		baseUrl = "https://humanage-app-1fe93ce442da.herokuapp.com/";
	}
	console.debug("[DEBUG] Base URL:", baseUrl);
	let userId = "<%=request.getAttribute("user") != null ? ((User) request.getAttribute("user")).getId() : "null"%>";
	let salaryId = "<%=request.getAttribute("salary") != null ? ((Salary) request.getAttribute("salary")).getId() : "null"%>";
	let paymentMonth = "<%=request.getAttribute("salary") != null ? ((Salary) request.getAttribute("salary")).getPaymentMonth() : "null"%>";
	console.debug("[DEBUG] userId: " + userId + ", salaryId: " + salaryId + ", paymentMonth: " + paymentMonth);

	if (!userId || userId === 'null' || !salaryId || salaryId === 'null' || !paymentMonth || paymentMonth === 'null') {
	    console.error("[ERROR] ユーザーID、給与ID、支払月が正しく設定されていません: userId=" + userId + ", salaryId=" + salaryId + ", paymentMonth=" + paymentMonth);
	    displayErrorMessage("ユーザーID、給与ID、または支払月が不足しています。userId=" + userId + ", salaryId=" + salaryId + ", paymentMonth=" + paymentMonth);
	    throw new Error("必要なデータが不足しています。");
	}
	
	const redisKey = "pdf_status:" + userId + ":" + salaryId + ":" + paymentMonth;
    console.debug("[DEBUG] Redisキー: " + redisKey);
    
    async function refreshAuthTokenIfNeeded(retryCount) {
        console.info("[INFO] refreshAuthTokenIfNeededが呼ばれました - retryCount:", retryCount);
        try {
            const token = localStorage.getItem('X-Auth-Token');
            const expirationTime = Number(localStorage.getItem('X-Auth-Token-Expiration'));

            console.debug("[DEBUG] 取得したトークン情報 - token:", token, "expirationTime:", expirationTime);

            if (retryCount > maxRetries) {
                console.error("[ERROR] トークンのリフレッシュ試行回数が上限に達しました - maxRetries:", maxRetries);
                throw new Error("トークンのリフレッシュ試行回数が上限に達しました。");
            }

            if (!token) {
                console.warn("[WARN] トークンが存在しません。更新が必要です。");
            } else if (Date.now() >= expirationTime - 5 * 60 * 1000) {
                console.info("[INFO] トークンの有効期限が近づいています。更新が必要です。");
            } else {
                console.info("[INFO] トークンは有効です。更新は不要です。");
                return token;
            }

            console.info("[INFO] トークンの更新処理を開始します。");
            try {
                const newToken = await refreshAuthToken();
                if (newToken) {
                    localStorage.setItem('X-Auth-Token', newToken);
                    localStorage.setItem('X-Auth-Token-Expiration', Date.now() + 30 * 60 * 1000);
                    console.info("[INFO] 新しいトークンを取得しました - newToken:", newToken);
                    return newToken;
                } else {
                    console.warn("[WARN] トークン更新が失敗しました。");
                    if (retryCount < maxRetries) {
                        console.warn("[WARN] トークン更新をリトライします - retryCount:", retryCount + 1);
                        return await refreshAuthTokenIfNeeded(retryCount + 1);
                    } else {
                        console.error("[ERROR] トークン更新が最大リトライ回数に達しました。再ログインが必要です。");
                        throw new Error("トークン更新に失敗しました。再ログインが必要です。");
                    }
                }
            } catch (refreshError) {
                console.error("[ERROR] トークンの更新中にエラーが発生しました - message:", refreshError.message, refreshError);
                throw refreshError;
            }
        } catch (error) {
            console.error("[ERROR] トークンの更新処理全体でエラーが発生しました - message:", error.message, error);
            displayErrorMessage("トークンの更新に失敗しました。再ログインしてください。" + error.message);
            redirectToLogin("トークンの更新に失敗しました。再ログインしてください。");
            return null;
        }
    }
	
    async function refreshAuthToken() {
        console.info("[INFO] refreshAuthTokenが呼ばれました");

        try {
            const currentToken = localStorage.getItem('X-Auth-Token');
            if (!currentToken) {
                console.warn("[WARN] 現在のトークンが存在しません。トークンの更新を試みますが、エラーになる可能性があります。");
            } else {
                console.debug("[DEBUG] 現在のトークン: ", currentToken);
            }

            const url = baseUrl + "api/refreshToken";
            console.info("[INFO] トークン更新リクエストのURL: ", url);

            try {
                const response = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'X-Auth-Token': currentToken,
                        'Content-Type': 'application/json'
                    }
                });

                console.info("[INFO] トークン更新リクエストが完了しました - HTTPステータスコード: ", response.status);

                if (response.ok) {
                    try {
                        const data = await response.json();
                        console.debug("[DEBUG] トークン更新成功 - レスポンスデータ: ", data);

                        if (data && data.newToken) {
                            console.info("[INFO] 新しいトークンを取得しました - newToken: ", data.newToken);
                            return data.newToken;
                        } else {
                            console.warn("[WARN] レスポンスデータに新しいトークンが含まれていません: ", data);
                            return null;
                        }
                    } catch (parseError) {
                        console.error("[ERROR] レスポンスのJSON解析中にエラーが発生しました - message: ", parseError.message, parseError);
                        throw parseError;
                    }
                } else {
                    console.error("[ERROR] トークン更新リクエストが失敗しました - HTTPステータスコード: ", response.status);

                    try {
                        const errorBody = await response.text();
                        console.error("[ERROR] レスポンスエラーの内容: ", errorBody);
                    } catch (readError) {
                        console.error("[ERROR] エラーレスポンスの読み取り中にエラーが発生しました - message: ", readError.message, readError);
                    }

                    throw new Error("トークン更新リクエストが失敗しました - HTTPステータスコード: " + response.status);
                }
            } catch (networkError) {
                console.error("[ERROR] トークン更新リクエスト送信中にネットワークエラーが発生しました - message: ", networkError.message, networkError);
                throw networkError;
            }
        } catch (error) {
            console.error("[ERROR] トークン更新処理全体でエラーが発生しました - message: ", error.message, error);
            return null;
        }

        console.debug("[DEBUG] トークン更新処理が終了しましたが、新しいトークンは取得できませんでした。nullを返します。");
        return null;
    }
	
    function redirectToLogin(message) {
        console.info("[INFO] redirectToLoginが呼ばれました。メッセージ: ", message);

        try {
            const errorMessageElement = document.getElementById("error-message");
            if (!errorMessageElement) {
                console.error("[ERROR] エラーメッセージ要素が見つかりません。");
                throw new Error("エラーメッセージ要素が見つかりません。");
            }

            errorMessageElement.textContent = message;
            console.info("[INFO] エラーメッセージを更新しました: ", message);

            setTimeout(() => {
                try {
                    console.info("[INFO] 5秒後にログインページへリダイレクトします。URL: /login");
                    window.location.href = "/login";
                } catch (redirectError) {
                    console.error("[ERROR] ログインページへのリダイレクト中にエラーが発生しました。", redirectError.message, redirectError);
                }
            }, 5000);
        } catch (error) {
            console.error("[ERROR] redirectToLogin内でエラーが発生しました。", error.message, error);
        }
    }

    function displaySuccessMessage(message) {
        console.info("[INFO] displaySuccessMessageが呼ばれました。メッセージ: ", message);

        try {
            const successMessageElement = document.getElementById("success-message");
            if (!successMessageElement) {
                console.error("[ERROR] 成功メッセージ要素が見つかりません。");
                throw new Error("成功メッセージ要素が見つかりません。");
            }

            successMessageElement.textContent = message;
            successMessageElement.style.display = "block";
            console.info("[INFO] 成功メッセージを設定し、表示しました。メッセージ: ", message);

            setTimeout(() => {
                try {
                    successMessageElement.style.display = "none";
                    console.info("[INFO] 成功メッセージを非表示にしました。");
                } catch (hideError) {
                    console.error("[ERROR] 成功メッセージの非表示処理中にエラーが発生しました。", hideError.message, hideError);
                }
            }, 5000);
        } catch (error) {
            console.error("[ERROR] displaySuccessMessage内でエラーが発生しました。", error.message, error);
        }
    }
	
    function displayErrorMessage(message) {
        console.info("[INFO] displayErrorMessageが呼ばれました。メッセージ: ", message);

        try {
            const errorMessageElement = document.getElementById("error-message");

            if (!errorMessageElement) {
                console.error("[ERROR] エラーメッセージ要素が見つかりません。メッセージ: ", message);
                throw new Error("エラーメッセージ要素が見つかりません。");
            }

            console.debug("[DEBUG] エラーメッセージ要素の現在の内容: ", errorMessageElement.textContent);

            if (errorMessageElement.textContent === message) {
                console.warn("[WARN] 同じエラーメッセージが既に表示されています。スキップします。メッセージ: ", message);
                return;
            }

            // メッセージのクリア
            errorMessageElement.textContent = "";
            console.debug("[DEBUG] メッセージをクリアしました。");

            // 新しいメッセージの設定と表示
            errorMessageElement.textContent = message;
            errorMessageElement.style.display = "block";
            console.info("[INFO] 新しいメッセージを設定し、表示しました。メッセージ: ", message);
			
            setTimeout(() => {
                try {
                	errorMessageElement.style.display = "none";
                	console.info("[INFO] メッセージを非表示にしました。");
                } catch (error) {
                	console.error("[ERROR] displayErrorMessage内でエラーが発生しました: ", error.toString(), error);
                }
            }, 10000);
            
        } catch (error) {
            console.error("[ERROR] displayErrorMessage内でエラーが発生しました。メッセージ: ", message, " エラー詳細: ", error.message, error);
        }
    }
	
    async function checkAndCreateDirectory(directoryPath) {
        console.info("[INFO] checkAndCreateDirectoryが呼ばれました。ディレクトリの存在を確認中 - directoryPath: ", directoryPath);

        try {
            // APIリクエストでディレクトリの存在を確認
            console.debug("[DEBUG] ディレクトリ存在確認のAPIリクエストを送信します。ディレクトリパス: ", directoryPath);
            const response = await fetch(baseUrl + "api/checkDirectory", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Auth-Token": localStorage.getItem("X-Auth-Token"),
                },
                body: JSON.stringify({ directoryPath }),
            });

            console.debug("[DEBUG] APIレスポンス取得完了。responseオブジェクト: ", response);

            // レスポンスの状態を確認
            if (!response.ok) {
                const errorText = await response.text();
                if (response.status === 403) {
                    console.error("[ERROR] ディレクトリの書き込み権限が不足しています。ステータス: ", response.status, "エラーテキスト: ", errorText);
                } else {
                    console.error("[ERROR] ディレクトリ確認に失敗しました。ステータス: ", response.status, "エラーテキスト: ", errorText);
                }
                return false;
            }

            // レスポンスをJSONとして取得
            const result = await response.json();
            console.debug("[DEBUG] レスポンスJSONデータ: ", result);

            // エラーまたは権限不足の場合
            if (result.status === "error" || (result.message && result.message.includes("権限が不足"))) {
                console.error("[ERROR] ディレクトリ権限不足またはエラー。directoryPath: ", directoryPath, " メッセージ: ", result.message);
                displayErrorMessage("ディレクトリ権限不足。管理者に問い合わせてください。" + result.message);
                return false;
            }

            // ディレクトリが存在する場合
            if (result.status === "complete") {
                console.info("[INFO] ディレクトリが正常に存在します。directoryPath: ", directoryPath);
                return true;
            }

            // ディレクトリが存在しない場合、作成を試みる
            console.info("[INFO] ディレクトリが存在しないため、作成を試みます。directoryPath: ", directoryPath);
            try {
                await createDirectory(directoryPath);
            } catch (creationError) {
                console.error("[ERROR] ディレクトリ作成中にエラーが発生しました: ", creationError.message, creationError);
                displayErrorMessage("ディレクトリ作成中にエラーが発生しました: " + creationError.message);
                return false;
            }

            // 再確認のため、もう一度ディレクトリの存在を確認
            console.info("[INFO] ディレクトリ作成後の再確認を開始します。directoryPath: ", directoryPath);
            const retryResponse = await fetch(baseUrl + "api/checkDirectory", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Auth-Token": localStorage.getItem("X-Auth-Token"),
                },
                body: JSON.stringify({ directoryPath }),
            });

            console.debug("[DEBUG] 再確認のレスポンス取得完了。retryResponse: ", retryResponse);

            const retryResult = await retryResponse.json();
            console.debug("[DEBUG] 再確認の結果JSON: ", retryResult);

            if (retryResult.status === "complete") {
                console.info("[INFO] ディレクトリが正常に作成され、利用可能です。directoryPath: ", directoryPath);
                return true;
            } else {
                console.error("[ERROR] ディレクトリ作成後もエラーが発生しました。directoryPath: ", directoryPath, " メッセージ: ", retryResult.message || "不明なエラー");
                displayErrorMessage("ディレクトリ作成に失敗しました: " + retryResult.message || "不明なエラー");
                return false;
            }
        } catch (error) {
            console.error("[ERROR] ディレクトリ確認中に予期しないエラーが発生しました: ", error.message, error);
            displayErrorMessage("ディレクトリ確認中にエラーが発生しました: " + error.message);
            return false;
        }
    }
	
    async function createDirectory(directoryPath) {
        console.info("[INFO] createDirectoryが呼ばれました。directoryPath: ", directoryPath);

        try {
            // APIリクエスト送信前のログ
            console.debug("[DEBUG] ディレクトリ作成APIリクエストを送信します。directoryPath: ", directoryPath);

            const createResponse = await fetch(baseUrl + "api/createDirectory", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Auth-Token": localStorage.getItem("X-Auth-Token"),
                },
                body: JSON.stringify({ directoryPath }),
            });

            // レスポンスオブジェクトのログ
            console.debug("[DEBUG] ディレクトリ作成APIレスポンスを取得しました。responseオブジェクト: ", createResponse);

            // レスポンスの状態を確認
            if (createResponse.ok) {
                console.info("[INFO] ディレクトリが正常に作成されました。directoryPath: ", directoryPath);
                return true;
            } else {
                // エラー時のレスポンス本文取得
                const errorText = await createResponse.text();
                console.error("[ERROR] ディレクトリ作成中にエラーが発生しました。ステータス: ", createResponse.status, 
                              "エラーテキスト: ", errorText);
                displayErrorMessage("ディレクトリ作成に失敗しました: " + errorText);
                return false;
            }
        } catch (error) {
            // 予期しないエラー処理
            console.error("[ERROR] ディレクトリ作成中に予期しないエラーが発生しました。エラーメッセージ: ", error.message, "エラーオブジェクト: ", error);
            displayErrorMessage("ディレクトリ作成中にエラーが発生しました: " + error.message);
            return false;
        } finally {
            // 実行完了後のログ
            console.info("[INFO] createDirectoryの処理が終了しました。directoryPath: ", directoryPath);
        }
    }
	
	async function generatePdf(userId, salaryId, token, paymentMonth, retryCount) {
	    console.info("[INFO] generatePdfが呼ばれました。userId:", userId, "salaryId:", salaryId, "token:", token, "paymentMonth:", paymentMonth);
		
	    validateInputData(userId, salaryId, paymentMonth);
	    	    
	    const directoryPath = "/tmp/pdf_reports/";
	    
	    if (!directoryPath || typeof directoryPath !== 'string') {
	    	console.error("[ERROR] ディレクトリパスが無効です。管理者にお問い合わせください。");
	        displayErrorMessage("ディレクトリパスが無効です。管理者にお問い合わせください。");
	        throw new Error("Invalid directory path");
	    }
	    
	    console.debug("[DEBUG] 取得したdirectoryPath: ", directoryPath);
	    
	    const fileName = "pdf_report_" + userId + "_" + salaryId + "_" + paymentMonth + ".pdf";
	    console.debug("[DEBUG] 生成するファイル名:", fileName);
	   
        const directoryExists = await checkAndCreateDirectory(directoryPath);
        if (!directoryExists) {
            console.error("[ERROR] ディレクトリが無効です。PDF生成を中止します。", directoryPath);
            throw new Error("ディレクトリ確認に失敗しました。");
        }
        
        console.debug("[DEBUG] 取得したdirectoryExists: ", directoryExists);

	    if (retryCount >= maxRetries) {
	        console.error("[ERROR] リトライ上限 (" + maxRetries + "回) に達しました。");
	        displayErrorMessage("PDF生成がタイムアウトしました。再試行回数: " + retryCount);
	        await setRedisStatus(redisKey, 'error: Max retries exceeded');
	        return "error";
	    }

	    if (isGeneratingPDF) {
	        console.warn("[WARN] PDF生成はすでに進行中です。新しいリクエストをキャンセルしました。");
	        displayErrorMessage("PDF生成が進行中です。しばらくお待ちください。");
	        return;
	    }

	    let timeout;
	    try {
	    	console.info("[INFO] isGeneratingPDFをtrueにします");
	        isGeneratingPDF = true;
	        
	        timeout = setTimeout(() => {
	            console.error("[ERROR] PDF生成がタイムアウトしました。");
	            displayErrorMessage("PDF生成がタイムアウトしました。再試行してください。");
	            isGeneratingPDF = false;
	        }, GENERATE_TIMEOUT);

	        const existingStatus = await getRedisStatus(redisKey);
	        console.debug("[DEBUG] 取得したRedisステータス:", existingStatus);
	        
	        let switchStatus = existingStatus;
	        try {
	        	switchStatus = JSON.parse(existingStatus);
	        	switchStatus = switchStatus.status || existingStatus;
	        } catch (e) {
	        	switchStatus = existingStatus;
	        }

	        switch (switchStatus) {
            	case "not_found":
                	console.debug("[DEBUG] 初回生成処理を開始");
                	break;
            	case "processing":
            		console.warn("[WARN] PDF生成が進行中です - Redisキー:", redisKey, "status: ", switchStatus);
                    displayErrorMessage("PDF生成が進行中です...");
                    break;
            	case "complete":
                	console.info("[INFO] PDF生成がすでに完了しています - Redisキー:", redisKey);
                	displaySuccessMessage("PDFがすでに生成されています。");
                	await setRedisStatus(redisKey, "complete");
                	return "complete";
            	case null:
            		console.error("[ERROR] Redisステータスがnullです。処理を中止します。");
            		throw new Error("Redisステータスがnullです。");
                case undefined:
                    console.error("[ERROR] Redisステータスがundefinedです。処理を中止します。");
                    throw new Error("Redisステータスがundefinedです。");
            	default:
                	if (switchStatus && switchStatus.startsWith("error")) {
                    	console.error("[ERROR] PDF生成エラーが発生しています:", switchStatus);
                    	displayErrorMessage("PDF生成に失敗しました。エラー内容: " + switchStatus);
                	} else {
                    	console.warn("[WARN] 未知のRedisステータス:", switchStatus);
                	}
                	await clearRedisCache(userId, salaryId, paymentMonth);
                	return "error";
        	}

	        console.debug("[DEBUG] PDF生成リクエストを送信します...");
	        
	        if (!navigator.onLine) {
	            console.warn("[WARN] ネットワークがオフラインです。復旧待機中...");
	            await waitForOnline();
	        }
	        
	        console.debug("[DEBUG] リクエストURL:", baseUrl + "api/generatePDF");
	        console.debug("[DEBUG] リクエストボディ:", { userId, salaryId, paymentMonth });
	        
        	const response = await fetch(baseUrl + "api/generatePDF", {
	            method: 'POST',
	            headers: {
	                'Content-Type': 'application/json',
	                'X-Auth-Token': token
	            },
	            body: JSON.stringify({ userId, salaryId, paymentMonth })
	        });
        	
        	console.debug("[DEBUG] 取得したresponse: ", response);

	        if (!response.ok) {
	            const errorText = await response.text();
	            switch (response.status) {
	                case 400:
	                    console.error("[ERROR] 400エラー。リクエストに問題があります。詳細:", errorText);
	                    displayErrorMessage("リクエストが無効です。入力データを確認してください。" + errorText);
	                    break;
	                case 404:
	                    console.error("[ERROR] 404エラー。指定されたファイルが見つかりません:", fileName, "詳細:", errorText);
	                    displayErrorMessage("ファイルが見つかりません。再試行してください。");
	                    break;
	                case 409:
	                	console.warn("[WARN] 409エラー。PDF生成プロセスが競合しています。60秒後に再試行します...");
	                	displayErrorMessage("他のリクエストが処理中です。60秒後に再試行してください。");
	                    setTimeout(() => generatePdf(userId, salaryId, token, paymentMonth, retryCount + 1), 45000); // 45秒後に再試行
	                    return;
	                case 503:
	                    console.warn("[WARN] 503エラー。サーバー利用不可のエラー。再試行します...");
	                    await clearRedisCache(userId, salaryId, paymentMonth);
	                    isGeneratingPDF = false;
	                    return await generatePdf(userId, salaryId, token, paymentMonth, retryCount + 1);
	                case 500:
	                    console.error("[ERROR] 500エラー。サーバーエラー:", response.status, "詳細:", errorText);
	                    displayErrorMessage("サーバーエラーが発生しました。再試行してください。");
	                    break;
	                default:
	                    console.error("[ERROR] 予期しないエラー:", response.status, "詳細:", errorText);
	                    displayErrorMessage("予期しないエラーが発生しました。" + errorText);
	            }
	            await setRedisStatus(redisKey, 'error: ' + errorText);
	            throw new Error("PDF生成リクエスト失敗: " + errorText);
	        }
	        
	        const result = await response.json();
	        console.debug("[DEBUG] 取得したAPIレスポンス:", result);

	        if (result && result.message && result.message.includes("PDF生成を開始しました")) {
	            console.debug("[DEBUG] APIメッセージから正常ステータスと判断します。", result, result.message);
	            result.status = "processing";
	        } else if (!result || !result.status || typeof result.status !== 'string') {
	            console.error("[ERROR] APIレスポンスのステータスが無効です:", result);
	            displayErrorMessage("APIから無効なレスポンスを受信しました。");
	            throw new Error("APIレスポンスが無効です:" + JSON.stringify(result));
	        }
	        
	        const isDevelopment = false;
	        if (isDevelopment) {
	            console.debug("[DEBUG] デバッグログ: generatePdfが呼ばれました。", { userId, salaryId, token, paymentMonth });
	        }
	        
	        switch (result.status) {
	        case "complete":
	            console.info("[INFO] PDF生成が完了しました！", fileName);
	            displaySuccessMessage("PDF生成が完了しました。");
	            const bucket = "humanage-reports";
	            const key = "pdf_report_" + userId + "_" + salaryId + "_" + paymentMonth + ".pdf";
	            if (!bucket || !key) {
	                console.error("[ERROR] バケット名またはキーが不正です:", bucket, key);
	                throw new Error("バケット名またはキーが不正です。");
	            }
	            const fileExists = await isFileExists(bucket, key);
	            if (fileExists) {
	                console.info("[INFO] ファイルは既に存在しています:", bucket, key);
	                displaySuccessMessage("PDFが既に生成されています。");
	            } else {
	            	console.warn("[WARN] ファイルが存在しません。再生成を試みます...");
	                await setRedisStatus(redisKey, "processing");
	                return await generatePdf(userId, salaryId, token, paymentMonth, retryCount + 1);
	            }
	            return "complete";
	        case "processing":
	            console.info("[INFO] PDF生成が進行中です。ポーリングを開始します...");

	            const status = await pollPdfStatus(userId, salaryId, paymentMonth);
	            if (status === "error") {
	            	console.error("[ERROR] pollPdfStatusからerrorが返ってきたので終了します。");
	                return "error";  // ここで終了
	            }
	            return status;
	        case "not_found":
	            console.warn("[WARN] Redisキーが見つかりません。再生成を試みます...");
	            await setRedisStatus(redisKey, "processing");
	            await generatePdf(userId, salaryId, token, paymentMonth, retryCount + 1);
	            return "processing";
	        default:
	            if (result.status.startsWith("error")) {
	                console.error("[ERROR] PDF生成エラーが発生:", result.message || "詳細なし");
	                displayErrorMessage("PDF生成に失敗しました: " + (result.message || "詳細なし"));
	            } else {
	                console.error("[ERROR] 不明なステータス:", result.status, "メッセージ:", result.message);
	                displayErrorMessage("予期しないステータス: " + result.status);
	            }
	            throw new Error("予期しないステータス: " + result.status);
	    	}
	    } catch (error) {
	        console.error("[ERROR] generatePdf中のエラー:", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause, error);
	        displayErrorMessage("generatePdf中に問題が発生しました。詳細: " + error.message);
	        await setRedisStatus(redisKey, "error:" + error.message);
	        return error;
	    } finally {
	        if (timeout) clearTimeout(timeout);
	        console.debug("[DEBUG] isGeneratingPDFをfalseにします");
	        isGeneratingPDF = false; // フラグをリセット
	    }
	}
	
	async function pollPdfStatus(userId, salaryId, paymentMonth) {
	    console.info("[INFO] pollPdfStatusが呼ばれました。UserId: " + userId + ", SalaryId: " + salaryId + ", PaymentMonth: " + paymentMonth);
	    
	    if (isPolling) {
	        console.warn("[WARN] pollPdfStatus がすでに実行中のため、新しいリクエストはスキップされました。");
	        return;
	    }
	    isPolling = true;
	    
	    validateInputData(userId, salaryId, paymentMonth);

	    const maxAttempts = 10;
	    const interval = 30000; // 30秒間隔
	    let attempts = 0;

	    let token;
	    try {
	        console.info("[INFO] トークンのリフレッシュを試みます...");
	        token = await refreshAuthTokenIfNeeded();
	        if (!token) {
	            console.error("[ERROR] トークン取得に失敗しました。再ログインしてください。");
	            displayErrorMessage("トークン取得に失敗しました。再ログインしてください。");
	            return 'error';
	        }
	        console.info("[INFO] トークンが正常に取得されました。");
	    } catch (error) {
	        console.error("[ERROR] トークンの取得中にエラーが発生:", error.message, error);
	        displayErrorMessage("トークンの取得に失敗しました。再試行してください。");
	        return 'error';
	    }

	    while (attempts < maxAttempts) {
	        attempts++;
	        try {
	        	if (!navigator.onLine) {
	                console.warn("[WARN] ネットワークがオフラインです。復旧待機中...");
	                await waitForOnline();
	            }
	        	
	            console.info("[INFO] getRedisStatusを呼び出します。リトライ回数: " + attempts + "/" + maxAttempts);
	            const status = await getRedisStatus(redisKey);
				
	            console.debug("[DEBUG] pollPdfStatusに戻ってきました。 - 取得したステータス(" + attempts + "/" + maxAttempts + "):, getRedisStatusの結果: ", status);
	            
	            if (!status || status.trim() === '') {
	                console.error("[ERROR] getRedisStatusが空の値を返しました。");
	                displayErrorMessage("ステータスを取得できませんでした。再試行してください。");
	                return 'error';
	            }
	            
	            const validStatuses = ["processing", "complete", "error", "not_found"];

	         	// JSON 形式のステータスかどうかチェック
	         	let parsedStatus = status;
	         	try {
	            	parsedStatus = JSON.parse(status); // JSONとしてパースできる場合
	            	// parsedStatus.statusが存在すればそのステータスを使う
	            	parsedStatus = parsedStatus.status || status;
	         	} catch (e) {
	            	// JSON形式でない場合、元のstatusを使う
	            	console.warn("[WARN] json形式でないため、元のstatusを使用します: ", status, ", エラー内容: ", e.toString(), ", スタック: ", e.stack, ", 原因: ", e.cause);
	         		parsedStatus = status
	         	}
	         	
	         	console.debug("[DEBUG] pollPdfStatusにて取得したparsedStatus: ", parsedStatus);

	         	if (!validStatuses.some(validStatus => parsedStatus.includes(validStatus)) && !parsedStatus.startsWith("error")) {
	            	console.error("[ERROR] 無効なステータス:", status);
	            	displayErrorMessage("予期しないエラーが発生しました: " + status);
	            	await setRedisStatus(redisKey, 'error: invalid status');
	            	return "error";
	         	}


	            if (parsedStatus === "complete") {
	                console.info("[INFO] PDF生成が完了しました。PDFファイルの存在を確認します...");
	                const bucket = "humanage-reports";
	                const key = "pdf_report_" + userId + "_" + salaryId + "_" + paymentMonth + ".pdf";

	                try {
	                    if (await isFileExists(bucket, key)) {
	                        console.info("[INFO] PDFファイルが存在します。");
	                        displaySuccessMessage("PDF生成が完了しました。");
	                        await downloadPdfWithRetry(userId, salaryId, paymentMonth);
	                        return "complete";
	                    } else {
	                        console.error("[ERROR] PDFファイルが存在しません。ステータスを'processing'に変更し、再生成を試みます...");
	                        displayErrorMessage("PDF生成が失敗しました。ファイルが見つかりません。再試行します...");
	                        await setRedisStatus(redisKey, "processing");
	                        await clearRedisCache(userId, salaryId, paymentMonth);
	                        return "error";
	                    }
	                } catch (fileError) {
	                    console.error("[ERROR] PDFファイルの存在確認中にエラーが発生:", fileError.message);
	                    displayErrorMessage("PDFファイルの確認中にエラーが発生しました。");
	                    return "error";
	                }
	            } else if (parsedStatus === "processing") {
	                console.info("[INFO] PDF生成が進行中(" + attempts + "/" + maxAttempts + ")。リトライを待機します...");
	                if (attempts === maxAttempts) {
	                    console.error("[ERROR] 最大試行回数に到達しましたが、ステータスが 'complete' に変わりません。");
	                    displayErrorMessage("PDF生成がタイムアウトしました。しばらくしてから再試行してください。");
	                    await setRedisStatus(redisKey, "error");
	                    return "error";
	                }
	                await new Promise((resolve) => setTimeout(resolve, interval));
	                continue;
	            } else if (parsedStatus.startsWith("error")) {
	                const errorDetail = status.split(':')[1] || "詳細なし";
	                console.error("[ERROR] PDF生成中にエラーが発生:", errorDetail);
	                displayErrorMessage("PDF生成に失敗しました: " + errorDetail);
	                await clearRedisCache(userId, salaryId, paymentMonth);
	                return "error";
	            } else if (parsedStatus === "not_found") {
	                console.warn("[WARN] Redisステータスが見つかりません。再試行します...");
	                displayErrorMessage("PDF生成ステータスが見つかりません。生成プロセスを再試行します...");
	                await clearRedisCache(userId, salaryId, paymentMonth);
	                return await generatePdf(userId, salaryId, token, paymentMonth, 0);
	            } else {
	                console.error("[ERROR] 予期しないステータス:", parsedStatus);
	                displayErrorMessage("PDF生成中に予期しないエラーが発生しました。");
	                await clearRedisCache(userId, salaryId, paymentMonth);
	                return await generatePdf(userId, salaryId, token, paymentMonth, 0);
	            }
	        } catch (error) {
	            console.error("[ERROR] pollPdfStatusでエラーが発生:", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause, error);
	            if (attempts >= maxAttempts) {
	                displayErrorMessage("PDF生成の試行回数が上限に達しました。");
	                await setRedisStatus(redisKey, "error");
	                return 'error';
	            }
	        } finally {
	            isPolling = false;
	        }

	        await new Promise(resolve => setTimeout(resolve, interval));
	    }

	    console.error("[ERROR] PDF生成がタイムアウトしました。リトライ回数:", maxAttempts);
	    displayErrorMessage("PDF生成がタイムアウトしました。しばらくしてから再試行してください。");
	    await setRedisStatus(redisKey, "error");
	    //return 'error';
	    throw new Error("PDF生成がタイムアウトしました");
	}
	
	async function handleResponseError(response) {
		console.info("[INFO] handleResponseErrorが呼ばれました。", response);
		if (!response || !response.headers) {
	        console.error("[ERROR] レスポンスまたはヘッダーが不正です。");
	        displayErrorMessage("レスポンスヘッダーがありません。");
	        return;
	    }

	    let attempt = 0;
		
	    const contentType = response && response.headers ? response.headers.get('Content-Type') : "不明なContent-Type";
	    console.debug("[DEBUG] handleResponseErrorが呼ばれました - Content-Type: " + contentType);
	    console.debug("[DEBUG] ステータスコード: " + response.status + ", Content-Type: " + contentType);
	    
	    if (!contentType || contentType === "不明なContent-Type") {
	    	console.error("[ERROR] コンテンツタイプがundefinedです。APIレスポンスが期待されていない形式で返ってきました。", contentType);
	        displayErrorMessage("APIレスポンスが無効です。Content-Typeが不明です。再試行してください。");
	        return;
	    }
	    
	    if (!contentType.includes('application/json')) {
	        console.error("[ERROR] APIレスポンスが無効です。Content-Type:", contentType);
	        displayErrorMessage("予期しないエラーが発生しました。管理者に問い合わせてください。");
	        return;
	    }
	    
	    if (response.status === 202) {
	        // ステータス202の場合は処理中なので、エラーメッセージは表示しない
	        console.info("[INFO] PDF生成が進行中です。ステータスチェックを継続します。");
	        await checkPdfStatus(userId, salaryId, paymentMonth, 0);
	        return;
	    }
	    
	    if (response.status >= 400) {
	        console.error("[ERROR] レスポンスエラー: " + response.status);
	        await setRedisStatus(redisKey, "error: response status " + response.status);
	        displayErrorMessage("エラー: " + response.status);
	    }

	    if (response.status === 404) {
	        console.error("[ERROR] 404エラー: 指定されたPDFが見つかりません - URL: " + baseUrl + "api/checkPdfStatus" + ":URL-" + url);
	        displayErrorMessage("PDFが生成されていないか、リソースが存在しない可能性があります。再度生成をお試しください。");
	        await clearRedisCache(userId, salaryId, paymentMonth);
	        return 'not_found';
	    } else if (response.status === 409) {
	        while (attempt < maxRetries) {
	        	console.warn("[WARN] 409エラー。現在、他のリクエストが処理中です。少し待ってから再試行します。");
	            displayErrorMessage("現在、他のリクエストが処理中です。少し待ってから再試行します。");
	            await new Promise(resolve => setTimeout(resolve, retryInterval));
	            attempt++;
	        }
	        if (attempt >= maxRetries) {
	        	console.error("[ERROR] 他のリクエストが競合しているため、PDF生成に失敗しました。");
	            displayErrorMessage("他のリクエストが競合しているため、PDF生成に失敗しました。");
	            return;
	        }
	    } else if (response.status === 503) {
	    	console.error("[ERROR] 503エラー。サーバーが一時的に利用できません。しばらくしてから再試行してください。");
	        displayErrorMessage("サーバーが一時的に利用できません。しばらくしてから再試行してください。");
	        await clearRedisCache(userId, salaryId, paymentMonth);
	    } else if (contentType.includes('application/json')) {
	        const errorData = await response.json();
	        console.error("[ERROR] PDF生成エラー: " + (errorData.message || "詳細不明のエラー") + ", ステータスコード: " + response.status);
	        displayErrorMessage("PDF生成に失敗しました: " + (errorData.message || "詳細不明のエラー"));
	    } else {
	        const errorText = await response.text();
	        console.error("[ERROR] PDF生成エラー - HTMLレスポンス: " + errorText + ", ステータスコード: " + response.status);
	        displayErrorMessage("PDF生成中にサーバーエラーが発生しました。");
	    }
	    
	    try {
	        console.debug("[DEBUG] レスポンスヘッダー:");
	        for (const [key, value] of response.headers.entries()) {
	            console.debug(key + ": " + value);
	        }
	        console.debug("[DEBUG] Content-Type: " + contentType + ", ステータス: " + response.status + ", ステータステキスト: " + response.statusText);
	    } catch (error) {
	        console.error("[ERROR] レスポンスヘッダー処理中にエラーが発生しました: ", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause, error);
	    }

	    if (contentType.includes('application/json')) {
	        const errorData = await response.json();
	        console.error("[ERROR] エラーデータ: " + (errorData.message || "詳細不明のエラー"));
	        displayErrorMessage(errorData.message || "予期しないエラーが発生しました。");
	    } else if (contentType.includes('text/html')) {
	        const errorText = await response.text();
	        console.error("[ERROR] HTMLエラーメッセージ: " + errorText);
	        displayErrorMessage("サーバーエラーが発生しました。詳細: " + errorText);
	    } else {
	    	console.error("[ERROR] 予期しないエラーが発生しました。ステータスコード: ", response.status);
	        displayErrorMessage("予期しないエラーが発生しました。ステータスコード: " + response.status);
	    }

	    displayErrorMessage(errorMessage);
	    console.error("[ERROR] エラー: " + errorMessage);
	}
	
	async function handleErrorResponse(response) {
	    console.info("[INFO] handleErrorResponseが呼ばれました。", response);
	    const contentType = response.headers ? response.headers.get('content-type') : "不明なContent-Type";
	    let errorMessage = "[ERROR] 予期しないエラーが発生しました: ステータスコード " + response.status + "ステータステキスト:" + response.statusText;

	    try {
	        if (contentType && contentType.includes('application/json')) {
	            const errorData = await response.json();
	            errorMessage = errorData.message || errorMessage;
	        } else {
	            const errorText = await response.text();
	            errorMessage = errorText || errorMessage;
	        }
	    } catch (error) {
	        console.error("[ERROR] エラーレスポンスの処理中にエラーが発生しました: " + error.message, error);
	    }
	    console.error("[ERROR] エラー: " + errorMessage);
	    displayErrorMessage(errorMessage);
	}
	
	async function checkPdfStatus(userId, salaryId, paymentMonth, retryCount) {
	    console.info("[INFO] checkPdfStatus関数が呼び出されました - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	    
	    if (!userId || userId === "" || !salaryId || salaryId === "" || !paymentMonth || paymentMonth === "") {
	        displayErrorMessage("ユーザーIDまたは給与IDまたは支払月が設定されていません。");
	        console.error("[ERROR] ユーザーIDまたは給与IDまたは支払月が無効です - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	        return;
	    }

	    const statusUrl = baseUrl + "api/checkPdfStatus?userId=" + userId + "&salaryId=" + salaryId + "&paymentMonth=" + paymentMonth;
	    const downloadUrl = baseUrl + "api/downloadPdf?userId=" + userId + "&salaryId=" + salaryId + "&paymentMonth=" + paymentMonth;
	    const clearErrorUrl = baseUrl + "api/clearCacheIfError";
	    const maxAttempts = 3;
	    const interval = 10000;
	    let response;
	    
	    console.debug("[DEBUG] ステータスチェック URL:" + statusUrl + ", downloadUrl: " + downloadUrl + ", clearErrorUrl: " + clearErrorUrl);
	    console.debug("[DEBUG] ステータスチェック パラメータ - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	    
	    if (retryCount > maxAttempts) {
	    	console.error("[ERROR] PDF生成ステータスの確認が失敗しました。しばらくしてから再試行してください");
	    	displayErrorMessage("PDF生成ステータスの確認が失敗しました。しばらくしてから再試行してください。");
            return;
        }
	    
	    const token = await refreshAuthTokenIfNeeded();
	    if (!token) return;
	    console.debug("[DEBUG] 取得したtoken: ", token);
	    for (let attempt = 0; attempt < maxRetries; attempt++) {
	        try {
	        	const response = await apiRequest(statusUrl, 'GET');
	        	console.debug("[DEBUG] 取得したresponse: ", response);
	 
	            const contentType = response.headers.get('content-type');
	            console.debug("[DEBUG] checkPdfStatus - 応答のContent-Type:", contentType);
	            
	            if (contentType && contentType.includes('text/html')) {
	                // HTMLが返ってきた場合はエラーを表示
	                displayErrorMessage("コンテンツタイプエラー。サーバーエラーが発生しました。しばらくしてから再試行してください。");
	                console.error("[ERROR] コンテンツタイプエラー内容:", await response.text());
	                return;
	            }

	            if (response.ok) {
	                const data = await response.json();
	                console.log("checkPdfStatus応答データ:", data);
	                if (data.status === "not_found") {
	                    console.warn("[WARN] PDFステータスが 'not_found' です。PDF生成を開始します...");
	                    await clearRedisCache(userId, salaryId, paymentMonth);
	                    await generatePdf(userId, salaryId, token, paymentMonth, 0);
	                    return "processing";
	                } else if (data.status === "complete") {
	                	await downloadPdfWithRetry(userId, salaryId, paymentMonth);
	                	console.info("[INFO] PDF生成が完了しました。");
	                    document.getElementById("success-message").textContent = "PDF生成が完了しました。";
	                    return "complete";
	                } else if (data.status === "processing") {
	                    console.debug("[DEBUG] PDF生成が進行中です。再試行します...");
	                    await new Promise(resolve => setTimeout(resolve, interval));
	                    await checkPdfStatus(userId, salaryId, paymentMonth, retryCount + 1);
	                } else if (data.status && data.status.startsWith("error")) {
	                    console.error("[ERROR] PDF生成にエラーが発生しました。キャッシュをクリアします。");
	                    displayErrorMessage("PDF生成に失敗しました。再度生成リクエストをお試しください。");
	                    await clearRedisCache(userId, salaryId, paymentMonth);
	                    await setRedisStatus(redisKey, "error");
	                    return "error";
	                } else {
	                	console.info("[INFO] statusが不明です。", data.status);
	                    setTimeout(() => checkPdfStatus(userId, salaryId, paymentMonth, retryCount + 1), interval);
	                }
	            } else {
	                if (response.status === 404) {
	                	console.error("[ERROR] 404エラー: 指定されたPDFが見つかりません");
	                    displayErrorMessage("PDFが見つかりません。再度生成してください。");
	                    await clearRedisCache(userId, salaryId, paymentMonth);
	                    return;
	                } else if (response.status === 202) {
	                    // ステータス202の場合は処理中なので、再試行
	                    console.debug("[DEBUG] PDF生成が進行中です。リトライします...");
	                    await new Promise(resolve => setTimeout(resolve, interval));
	                } else if (response.status === 503 && retryCount < maxAttempts) {
	                    console.error("[ERROR] 503エラーが発生しました。リトライを待機します...");
	                    await new Promise(resolve => setTimeout(resolve, interval));
	                    return checkPdfStatus(userId, salaryId, paymentMonth, retryCount + 1);
	                } else {
	                	console.error("[ERROR] PDF生成ステータスの取得に失敗しました。");
	                    displayErrorMessage("PDF生成ステータスの取得に失敗しました。");
	                }
	            }
	        } catch (error) {
	            console.error("[ERROR] PDF生成ステータスの取得中にエラーが発生しました:", error.message, error);
	            displayErrorMessage("サーバーエラーが発生しました。しばらくしてから再試行してください。" + error.message);
	            return 'error';
	        }
	    }
	    console.error("[ERROR] PDFのダウンロードリンクを取得できませんでした。しばらくしてから再試行してください");
	    displayErrorMessage("PDFのダウンロードリンクを取得できませんでした。しばらくしてから再試行してください。");
	}
	
	async function clearRedisCache(userId, salaryId, paymentMonth) {
		console.info("[INFO] clearRedisCacheが呼ばれました: userId=" + userId + ", salaryId=" + salaryId + ", paymentMonth=" + paymentMonth);
	    const token = await refreshAuthTokenIfNeeded();
	    if (!token) {
	        console.error("[ERROR] トークン取得に失敗しました。キャッシュをクリアできません。");
	        displayErrorMessage("トークン取得に失敗しました。再ログインしてください。");
	        return;
	    }
	    
	    console.debug("[DEBUG] 取得したtoken: ", token);

	    retryInterval = 2000; // 2秒の待機
	    const maxAttempts = 3;
	    
	    while (attempts < maxAttempts){
	    	try {
	        	const response = await fetch(baseUrl + "api/clearRedisCache", {
	            	method: 'POST',
	            	headers: {
	                	'X-Auth-Token': token,
	                	'Content-Type': 'application/json'
	            	},
	            	body: JSON.stringify({ redisKey })
	        	});
	        	console.debug("[DEBUG] 取得したresponse: ", response);
	        
	        	const contentType = response.headers.get('content-type');
	        	console.debug("[DEBUG] 取得したcontentType: ", contentType);

	        	if (response.ok) {
	            	try {
	                	if (contentType && contentType.includes('application/json')) {
	                    	const data = await response.json();
	                    	console.debug("[DEBUG] Redisキャッシュが正常にクリアされました:", data.message, data);
	                    	document.getElementById("success-message").textContent = "キャッシュが正常にクリアされました";
	                    	return;
	                	} else {
	                    	const successText = await response.text();
	                    	console.debug("[DEBUG] Redisキャッシュが正常にクリアされました:", successText);
	                    	document.getElementById("success-message").textContent = "キャッシュが正常にクリアされました";
	                    	return;
	                	}
	            	} catch (jsonError) {
	                	const errorText = await response.text();
	                	console.error("[ERROR] JSONの解析中にエラーが発生しました:", errorText, jsonError);
	                	displayErrorMessage("レスポンスデータの処理中にエラーが発生しました。" + jsonError);
	            	}
	        	} else {
	        		console.error("[ERROR] Redisキャッシュのクリアに失敗しました:", await response.text());
	        		displayErrorMessage("Redisキャッシュのクリアに失敗しました");
	        		await handleErrorResponse(response);
	        	}
	        	console.debug("[DEBUG] Redisキーをprocessingに再設定します...");
	        	await setRedisStatus(redisKey, 'processing');
	    	} catch (error) {
	    		console.error("[ERROR] Redisキャッシュクリア中にエラーが発生しました:", error.message, error);
            	if (attempts === maxAttempts - 1) {
                	displayErrorMessage("キャッシュのクリアに失敗しました。サーバー管理者にお問い合わせください。" + error.message);
                	return;
            	}
            	await new Promise(resolve => setTimeout(resolve, retryInterval)); // リトライ待機
        	}
        	attempts++;
	    }
	    displayErrorMessage("Redisキャッシュのクリアに失敗しました。管理者に問い合わせてください。");
	}
	
	function formatTimestampToReadableDate(timestamp) {
		console.debug("[DEBUG] formatTimestampToReadableDateが呼ばれました: ", timestamp);
	    const date = new Date(timestamp);
	    console.debug("[DEBUG] 設定したdate: ", date);
	    return date.toLocaleString(); // 日本のロケールに応じた日時形式で表示
	}

	async function apiRequest(url, method, body, maxRetries, retryInterval) {
	    console.info("[INFO] apiRequestが呼ばれました - URL:", url, "メソッド:", method, "ボディ:", JSON.stringify(body));
	    
	    const controller = new AbortController();
	    const timeout = setTimeout(() => controller.abort(), 30000);
	    let attempt = 0;

	    while (attempt < maxRetries) {
	        try {
	        	const response = await retryWithBackoff(async () => {
	                const token = await refreshAuthTokenIfNeeded();
	                if (!token) throw new Error("トークン取得エラー");
	                console.debug("[DEBUG] 取得したtoken: ", token);

	                return await fetch(url, {
	                    method,
	                    headers: {
	                        'Content-Type': 'application/json',
	                        'X-Auth-Token': token,
	                    },
	                    body: body ? JSON.stringify(body) : null,
	                    signal: controller.signal,
	                    mode: 'cors',
	                });
	            }, maxRetries, retryInterval);
	            
	            console.debug("[DEBUG] fetchリクエストの応答:", response);

	            console.debug("[DEBUG] ステータスコード:", response.status, "ステータステキスト:", response.statusText);

	            if (!response || !response.headers) {
	            	console.error("[ERROR] fetchリクエストが失敗し、responseまたはheadersがundefinedです。");
	            	throw new Error("fetchリクエストが失敗しました。");
	            } else {
	                console.debug("[DEBUG] apiRequest - レスポンスオブジェクト:", response);
	                console.debug("[DEBUG] apiRequest - レスポンスヘッダー全体:", response.headers);
	            }

	            if (response.status === 202) {
	                console.debug("[DEBUG] リクエストが処理中です（ステータスコード202）。再試行します...");
	                attempt++;
	                await new Promise(resolve => setTimeout(resolve, retryInterval));
	                continue; // リトライのため次のループへ
	            }

	            if (!response.ok) {
	                console.error("[ERROR] リクエストが失敗しました。ステータスコード:", response.status);
	                if (response.status === 400) {
	                    displayErrorMessage("無効なリクエストです。リクエスト内容を確認してください。");
	                } else if (response.status === 401) {
	                    displayErrorMessage("トークンが無効です。再ログインしてください。");
	                    redirectToLogin("トークンが無効です。再ログインしてください。");
	                    return null; 
	                } else if (response.status === 404) {
	                    displayErrorMessage("リソースが見つかりません。エンドポイントを確認してください。");
	                } else if (response.status === 409) {
	                    displayErrorMessage("現在PDF生成が進行中です。しばらくお待ちください。");
	                    return null;
	                } else if (response.status === 500) {
	                    displayErrorMessage("サーバーエラーが発生しました。しばらくしてから再試行してください。");
	                } else if (response.status === 503) {
	                    displayErrorMessage("サーバーが一時的に利用できません。しばらくしてから再試行してください。");
	                } else {
	                    displayErrorMessage("APIリクエストが失敗しました。ステータスコード: " + response.status);
	                }
	                throw new Error("APIリクエストが失敗しました。ステータスコード: " + response.status);
	            }

	            const contentType = response.headers.get('Content-Type');
	            
	          	console.debug("[DEBUG] 取得したcontentType: ", contentType);
	            
	            if (contentType.includes('text/html')) {
	                console.error("[ERROR] HTMLレスポンスを受信しました。サーバーエラーの可能性があります。", contentType);
	                displayErrorMessage("PDF生成中に問題が発生しました。");
	                return 'error';
	            }
	            
	            if (!contentType || !contentType.includes('application/json')) {
	                displayErrorMessage("予期しないレスポンス形式が返されました。管理者にお問い合わせください。");
	                console.error("[ERROR] Content-Typeエラー:", contentType);
	                return 'error';
	            }
	            
	            clearTimeout(timeout);
	            return response;
	        } catch (error) {
	            if (error instanceof TypeError && error.message === "Failed to fetch") {
	                console.error("[ERROR] ネットワークエラーまたはCORSエラーの可能性があります:", error.message, error);
	                displayErrorMessage("APIリクエストに失敗しました。ネットワークまたはCORS設定を確認してください。" + error.message, error);
	            } else {
	                console.error("[ERROR] APIリクエスト中にエラーが発生:", error.message, error);
	            }
	            throw error;
	        } finally {
	            clearTimeout(timeout); // タイムアウトクリア
	        }
	    }
	    displayErrorMessage("リトライの最大回数に達しました。しばらくしてから再試行してください。");
	    throw new Error("リトライの最大回数に達しました。");
	}
	
	async function retryWithBackoff(apiCall, maxRetries, baseInterval = 2000) {
		console.info("[INFO] retryWithBackoffが呼ばれました。", apiCall);
	    let attempt = 0;
	    let interval = baseInterval;

	    while (attempt < maxRetries) {
	        try {
	            return await apiCall();
	        } catch (error) {
	            console.error("[ERROR] リトライ中のエラー (" + (attempt + 1) + "/" + maxRetries + "): " + error.message, error);
	            if (attempt === maxRetries - 1) throw error;
	            await new Promise(resolve => setTimeout(resolve, interval));
	            interval *= 2; // 次のリトライまでの待機時間を増やす
	            attempt++;
	        }
	    }
	}

	async function checkDownloadLink(userId, salaryId, paymentMonth) {
	    console.info("[INFO] checkDownloadLink関数が呼ばれました - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	    const token = await refreshAuthTokenIfNeeded(); // トークンを事前に取得する
	    if (!token) {
	    	console.error("[ERROR] トークンの取得に失敗しました。");
	        displayErrorMessage("トークンの取得に失敗しました。");
	        return;
	    }
	    console.debug("[DEBUG] 取得したtoken: ", token);

	    const downloadUrl = baseUrl + "api/downloadPdf?userId=" + userId + "&salaryId=" + salaryId + "&paymentMonth=" + paymentMonth;
	    let attempt = 0;
	    const maxAttempts = 3;
	    const interval = 5000;
	    console.debug("[DEBUG] 取得したdownloadUrl: ", downloadUrl);

	    while (attempt < maxAttempts) {
	        try {
	            const response = await fetch(downloadUrl, {
	                method: 'GET',
	                headers: {
	                    'X-Auth-Token': token
	                }
	            });
	            console.debug("[DEBUG] 取得したresponse: ", response);

	            if (response.ok) {
	                setDownloadLink(downloadUrl);
	                console.debug("[DEBUG] PDFが生成されました。");
	                document.getElementById("success-message").textContent = "PDFが生成されました。ダウンロードリンクをクリックしてPDFをダウンロードしてください。";
	                return;
	            } else if (response.status === 404) {
	            	console.error("[ERROR] 指定されたPDFが見つかりません。再試行してください。");
	                displayErrorMessage("指定されたPDFが見つかりません。再試行してください。");
	                return;
	            } else {
	                console.warn("[WARN] PDFがまだ生成されていません。再試行します...");
	            }
	        } catch (error) {
	            console.error("[ERROR] PDFリンクのチェック中にエラーが発生しました:", error.message, error);
	            displayErrorMessage("PDFダウンロード中にエラーが発生しました。しばらくしてから再試行してください。" + error.message);
	        }
	        attempt++;
	        await new Promise(resolve => setTimeout(resolve, interval));
	    }
	    displayErrorMessage("PDFのダウンロードリンクを取得できませんでした。しばらくしてから再試行してください。");
	}
	
	async function retryDownload(url, headers, maxRetries, baseRetryInterval = 10000) {
	    console.info("[INFO] retryDownloadが呼ばれました。", url, headers);
	    let attempt = 0;
	    retryInterval = baseRetryInterval;

	    while (attempt < maxRetries) {
	        try {
	            const response = await fetch(url, { method: 'GET', headers });
	            console.debug("[DEBUG] 取得したresponse: ", response);
	            if (response.ok) {
	                return await response.blob(); // 成功時
	            } else if (response.status === 503) {
	                console.error("[ERROR] 503エラー発生。リトライ待機中... (" + (attempt + 1) + "/" + maxRetries + ")");
	                await new Promise(resolve => setTimeout(resolve, retryInterval));
	                retryInterval *= 2; // バックオフ戦略
	            } else {
	            	console.error("[ERROR] 予期しないエラーレスポンスが返されました: ", response.status);
	                throw new Error("Unexpected response: " + response.status);
	            }
	        } catch (error) {
	            console.error("[ERROR] リトライ中のエラー (" + (attempt + 1) + "/" + maxRetries + "):" + error.message, error);
	            if (attempt === maxRetries - 1) {
	                throw error;
	            }
	            await new Promise(resolve => setTimeout(resolve, retryInterval));
	        }
	        attempt++;
	    }
	    console.error("[ERROR] PDFダウンロードに失敗しました: 最大リトライ回数を超過しました");
	    throw new Error("PDFダウンロードに失敗しました: 最大リトライ回数を超過しました");
	}

	async function downloadPdfWithRetry(userId, salaryId, paymentMonth) {
		if (hasDownloaded) {
	        console.warn("[WARN] PDFはすでにダウンロード済みです。処理をスキップします。");
	        return;
	    }
		
	    console.info("[INFO] downloadPdfWithRetryが呼ばれました。", userId, salaryId, paymentMonth);
	    const headers = {
	        'X-Auth-Token': localStorage.getItem('X-Auth-Token'),
	        'Content-Type': 'application/json'
	    };

	    const downloadUrl = baseUrl + "api/downloadPdf?userId=" + userId + "&salaryId=" + salaryId + "&paymentMonth=" + paymentMonth;

	    try {
	        console.debug("[DEBUG] ダウンロード処理を開始します。取得したheaders: " + headers + ", downloadUrl: " + downloadUrl);
	        const blob = await retryDownload(downloadUrl, headers, 5, 10000); // リトライ回数: 5
	        const blobUrl = URL.createObjectURL(blob);
	        const downloadLink = document.createElement('a');
	        downloadLink.href = blobUrl;
	        downloadLink.download = "pdf_report_" + userId + "_" + salaryId + "_" + paymentMonth + ".pdf";
	        downloadLink.click();
	        URL.revokeObjectURL(blobUrl);
	        
	        hasDownloaded = true;
	        console.info("[INFO] PDFのダウンロードが成功しました");
	    } catch (error) {
	        console.error("[ERROR] PDFダウンロード失敗: ", error.toString(), ", スタック: ", error.stack, ", 原因: ", error.cause, error);
	        displayErrorMessage("PDFのダウンロードに失敗しました。詳細: " + error.message);
	    }
	}
	
	async function updateRedisStatus(redisKey, status) {
	    console.info("[INFO] updateRedisStatusが呼ばれました - redisKey:", redisKey, ", status:", status);

	    try {
	        const url = baseUrl + "api/updateRedisStatus";
	        console.debug("[DEBUG] 生成されたURL:", url, "リクエストボディ:", { redisKey, status });

	        // **JSON変換を削除し、そのまま送信**
	        const requestBody = JSON.stringify({ redisKey, status });

	        console.info("[INFO] Redisステータス更新リクエストを送信。リクエストボディ: ", requestBody);

	        const response = await fetch(url, {
	            method: 'POST',
	            headers: {
	                'Content-Type': 'application/json',
	                'X-Auth-Token': localStorage.getItem('X-Auth-Token'),
	            },
	            body: requestBody,
	        });

	        console.debug("[DEBUG] レスポンスステータス:", response.status, ", レスポンス:", response);

	        const responseBody = await response.text();
	        console.debug("[DEBUG] レスポンスボディ:", responseBody);

	        if (!response.ok) {
	            console.error("[ERROR] Redisステータス更新エラー:", responseBody);
	            throw new Error("Redisステータス更新に失敗しました: " + response.statusText + " レスポンスボディ:" + responseBody);
	        }

	        console.debug("[DEBUG] Redisステータスを更新しました: ", status);

	    } catch (error) {
	        console.error("[ERROR] Redisステータス更新中にエラーが発生しました: ", error.toString());
	        throw error;
	    }
	}
	
	async function setRedisStatus(redisKey, status) {
	    console.info("[INFO] setRedisStatusが呼ばれました - redisKey:", redisKey, ", status:", status);
	    
	    const validStatuses = ["processing", "complete", "error", "not_found"];

	    if (!validStatuses.includes(status) && !status.startsWith('error')) {
	        console.error("[ERROR] 無効なステータス:", status);
	        displayErrorMessage("予期しないエラーが発生しました: " + status);
	        await setRedisStatus(redisKey, 'error: invalid status');
	        return 'error';
	    }

	    if (!redisKey || redisKey.trim() === '') {
	        console.error("[ERROR] redisKeyが空または未定義です。");
	        throw new Error("redisKeyが無効です。");
	    }

	    try {
	        await updateRedisStatus(redisKey, status);
	        console.info("[INFO] Redisステータス更新成功:", status);
	    } catch (error) {
	        console.error("[ERROR] Redisステータス更新中にエラーが発生しました:", error);
	        displayErrorMessage("Redisステータス更新例外: " + error.message);
	        return 'error';
	    }
	}

	async function getRedisStatus(redisKey) {
	    console.info("[INFO] getRedisStatusが呼び出されました - redisKey:", redisKey);

	    if (!redisKey || typeof redisKey !== 'string') {
	        console.error("[ERROR] Redisキーが指定されていません。無効なキー:", redisKey);
	        displayErrorMessage("Redisキーが指定されていません。");
	        throw new Error("無効なリクエスト: Redisキーが空または無効です。");
	    }

	    for (let attempt = 0; attempt < maxRetries; attempt++) {
	        try {
	            if (!navigator.onLine) {
	                console.warn("[WARN] ネットワークがオフラインです。復旧待機中...");
	                await waitForOnline();
	            }
	            
	            const url = baseUrl + "api/getRedisStatus?key=" + redisKey;
	            console.debug("[DEBUG] リクエストURL:", url);

	            const response = await fetch(url, {
	                method: 'GET',
	                headers: {
	                    'Content-Type': 'application/json',
	                    'X-Auth-Token': localStorage.getItem('X-Auth-Token'),
	                },
	            });

	            console.debug("[DEBUG] APIレスポンス:", response);

	            if (!response.ok) {
	                console.error("[ERROR] Redisステータス取得エラー:", response.status);
	                throw new Error("Redisステータス取得エラー: " + response.status);
	            }

	            const data = await response.json();
	            console.debug("[DEBUG] Redisステータス取得成功。APIレスポンスデータ:", data);

	            if (!data || typeof data.status !== "string" || !data.status) {
	                console.warn("[WARN] Redisから無効な値が返されました。デフォルト値を使用します。", data);
	                await setRedisStatus(redisKey, "not_found");
	                return "not_found";
	            }

	            console.debug("[DEBUG] Redisステータス取得成功。getRedisStatusを抜けます。: " + data.status);
	            return data.status;

	        } catch (error) {
	            console.error("[ERROR] Redisステータス取得中にエラーが発生しました:", error, " - キー:", redisKey);
	            if (attempt === maxRetries - 1) {
	                displayErrorMessage("Redisステータス取得に失敗しました。詳細: " + error.message);
	                return "error: " + error.message;
	            }
	            console.warn("[WARN] リトライ" + (attempt + 1) + "/" + maxRetries + "を試みます...");
	            await new Promise(resolve => setTimeout(resolve, retryInterval));
	        }
	    }

	    console.error("[ERROR] Redisステータスの取得に失敗しました。最大試行回数を超えました。");
	    throw new Error("Redisステータスの取得に失敗しました。最大試行回数を超えました。");
	}
	
	async function waitForOnline() {
	    return new Promise(resolve => {
	        if (navigator.onLine) {
	            resolve(); // すでにオンラインなら即座に解決
	        } else {
	            console.info("[INFO] オンラインになるのを待機中...");
	            window.addEventListener("online", () => {
	                console.info("[INFO] ネットワークが復旧しました！");
	                resolve();
	            }, { once: true });
	        }
	    });
	}
	
	/*
	async function checkEndpoint(url) {
	    console.info("[INFO] checkEndpointが呼ばれました - URL:", url);
	    try {
	        const response = await fetch(url, { method: 'OPTIONS' });
	        console.debug("[DEBUG] エンドポイント確認 - URL:", url, "ステータスコード:", response.status);

	        if (response.ok || response.status === 204) {
	            console.info("[INFO] エンドポイントが正常です - URL:", url);
	        } else {
	            console.error("[ERROR] エンドポイントが無効です - URL:", url, "ステータスコード:", response.status);
	            const contentType = response.headers.get('Content-Type') || "不明なContent-Type";
	            const responseBody = await response.text();

	            console.error("[ERROR] レスポンス詳細:", response);
	            console.error("[ERROR] Content-Type:", contentType);
	            console.error("[ERROR] レスポンス本文:", responseBody);

	            if (response.status === 404) {
	                console.error("[ERROR] 404エラー: 指定されたエンドポイントが見つかりません - URL:", url);
	                console.error("[ERROR] エラーの可能性: エンドポイントが正しく設定されていないか、バックエンド側のルーティングが間違っています。");
	            } else if (response.status === 403) {
	                console.error("[ERROR] 403エラー: アクセスが拒否されました - URL:", url);
	                console.error("[ERROR] エラーの可能性: 認証トークンが不足している、または権限がありません。");
	            } else if (response.status === 500) {
	                console.error("[ERROR] 500エラー: サーバー内部エラー - URL:", url);
	                console.error("[ERROR] エラーの可能性: サーバー側のコードまたは設定に問題があります。");
	            } else {
	                console.error("[ERROR] 予期しないエラー - ステータスコード:", response.status, "ステータステキスト:", response.statusText);
	            }

	            displayErrorMessage("エンドポイントが無効です - URL:" + url + " ステータスコード: " + response.status);
	        }
	    } catch (error) {
	        console.error("[ERROR] エンドポイント確認中にエラーが発生しました - URL:", url, "エラー:", error.message, error);
	        displayErrorMessage("エンドポイントが無効です - URL:" + url + " エラー: " + error.message + error);
	    }
	}

	checkEndpoint(baseUrl + "api/updateRedisStatus");
	checkEndpoint(baseUrl + "api/getRedisStatus");
	checkEndpoint(baseUrl + "api/checkDirectory");
	checkEndpoint(baseUrl + "api/checkFileExists");
	checkEndpoint(baseUrl + "api/generatePDF");
	checkEndpoint(baseUrl + "api/generatePDF/node");
	
	*/

	async function isFileExists(bucket, key, region = "ap-northeast-1") {
	    console.info("[INFO] isFileExistsが呼ばれました。bucket:", bucket, "key:", key, "region:", region);
	    
	    if (!bucket || !key) {
	        const errorMessage = "[ERROR] BucketまたはKeyが無効です。bucket: " + bucket + ", key: " + key;
	        console.error(errorMessage);
	        displayErrorMessage(errorMessage);
	        return false;
	    }

	    try {
	        const url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
	        console.debug("[DEBUG] isFileExistsで作成したurl:", url);

	        const response = await fetch(url, {
	            method: "HEAD",
	        });

	        console.debug("[DEBUG] 取得したresponse:", response);

	        // レスポンスに基づいた処理
	        if (response.ok) {
	            console.info("[INFO] ファイルが存在します:", url);
	            return true;
	        } else if (response.status === 404) {
	            console.error("[ERROR] PDFファイルが見つかりません:", url);
	            displayErrorMessage("PDFが生成されませんでした。再試行してください。");
	            return false;
	        } else {
	            console.error("[ERROR] ファイル確認エラー:", response.statusText, "URL:", url);
	            displayErrorMessage("PDFが生成されませんでした。再試行してください。");
	            return false;
	        }
	    } catch (error) {
	        console.error("[ERROR] ファイル存在確認エラー:", error.message, error.stack, error);
	        displayErrorMessage("ファイル確認中にエラーが発生しました: " + error.message);
	        return false;
	    }
	}

	async function handleError(redisKey, errorMessage) {
	    console.error("[ERROR] エラー発生: " + errorMessage);
	    
	    try {
	        // Redisステータスの更新処理
	        console.debug("[DEBUG] Redisステータスを'error'に更新中。redisKey:", redisKey, "errorMessage:", errorMessage);
	        await updateRedisStatus(redisKey, "error: " + errorMessage);

	        // エラー処理後のログ
	        console.debug("[DEBUG] Redisステータスが'error'に更新されました。");
	        
	    } catch (updateError) {
	        console.error("[ERROR] Redisステータスの更新中にエラーが発生しました:", updateError.message, updateError);
	    }
	    
	    // 最終的にエラーをスロー
	    throw new Error(errorMessage);
	}
	
	function validateInputData(userId, salaryId, paymentMonth) {
	    if (!userId || !salaryId || !paymentMonth) {
	        const errorMessage = "[ERROR] 必要なデータが不足しています: userId=" + userId + ", salaryId=" + salaryId + ", paymentMonth=" + paymentMonth;
	        console.error(errorMessage);
	        displayErrorMessage(errorMessage);
	        throw new Error(errorMessage);
	    }
	}
	
	document.addEventListener("DOMContentLoaded", () => {
	    console.info("[INFO] DOMContentLoaded event fired");

	    try {
	        validateInputData(userId, salaryId, paymentMonth);
	    } catch (error) {
	        console.error("[ERROR] validateInputDataでエラー:", error.message, error);
	        return;
	    }

	    const pdfButton = document.getElementById("pdfButton");
	    const backButton = document.querySelector(".btn-back");
	    const downloadLink = document.getElementById("downloadLink");
	    let isGenerating = false;

	    pdfButton.addEventListener("click", async (event) => {
	        event.preventDefault();

	        if (isGenerating) {
	            console.warn("[WARN] PDF生成はすでに進行中です。新しいリクエストはキャンセルされました。");
	            displayErrorMessage("PDF生成が進行中です。しばらくお待ちください。");
	            return; // 重複リクエストを防止
	        }

	        console.debug("[DEBUG] PDF出力ボタンがクリックされました。PDF生成を開始します...");
	        isGenerating = true;
	        hasDownloaded = false;
	        
	        pdfButton.disabled = true;
	        backButton.disabled = true;
	        pdfButton.style.pointerEvents = "none";
	        backButton.style.pointerEvents = "none";

	        try {
	            console.debug("[DEBUG] 古いRedisキャッシュをクリアします...");
	            await clearRedisCache(userId, salaryId, paymentMonth);

	            // トークンを取得
	            console.debug("[DEBUG] トークンを取得中...");
	            const token = await refreshAuthTokenIfNeeded();
	            if (!token) {
	                console.error("[ERROR] トークンの取得に失敗しました。");
	                throw new Error("トークンの取得に失敗しました。");
	            }
	            console.debug("[DEBUG] 取得したtoken: ", token);

	            // PDF生成リクエストを送信
	            console.debug("[DEBUG] generatePdfを呼び出します - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	            await generatePdf(userId, salaryId, token, paymentMonth, 0);

	            // PDF生成ステータスをポーリングして進行状況を確認
	            console.debug("[DEBUG] pollPdfStatusを呼び出します - userId:", userId, "salaryId:", salaryId, "paymentMonth:", paymentMonth);
	            const status = await pollPdfStatus(userId, salaryId, paymentMonth);

	            console.debug("[DEBUG] pollPdfStatusの結果:", status);

	            if (!status) {
	                console.error("[ERROR] pollPdfStatusがundefinedを返しました。", status);
	                throw new Error("pollPdfStatusがundefinedを返しました。");
	            }

	            if (status === "complete") {
	                console.debug("[DEBUG] PDF生成が完了しました。");
	                displaySuccessMessage("PDF生成が完了しました!");
	                
	                if (!hasDownloaded) {
	                    await downloadPdfWithRetry(userId, salaryId, paymentMonth);
	                } else {
	                    console.info("[INFO] PDFはすでにダウンロード済みです。");
	                }
	            } else if (status.startsWith('error')) {
	                console.error("[ERROR] PDF生成中にエラーが発生しました。", status);
	                throw new Error("PDF生成中にエラーが発生しました。" + status);
	            } else {
	                console.error("[ERROR] PDF生成が正常に完了しませんでした。", status);
	                throw new Error("PDF生成が正常に完了しませんでした: " + status);
	            }
	        } catch (error) {
	            console.error("[ERROR] PDF生成エラー: " + error.toString() + ", スタック: " + error.stack + ", 原因: " + error.cause);
	            displayErrorMessage("PDF生成に失敗しました。詳細: " + error.toString(), error);
	        } finally {
	            console.debug("[DEBUG] PDF生成処理終了。isGeneratingフラグをリセットします。");
	            isGenerating = false;
	            
	            pdfButton.disabled = false;
	            backButton.disabled = false;
	            pdfButton.style.pointerEvents = "auto";
	            backButton.style.pointerEvents = "auto";
	        }
	    });
	});
</script>
</head>
<body>
	<div class="container">
		<div id="error-message" style="display: none; color: red;"></div>
		<div id="success-message" style="display: none; color: green;"></div>
		<div class="print-instruction">
			<a href="${pageContext.request.contextPath}/payroll" class="btn btn-back no-print">🔙</a>
		</div>
		<h1>給与明細</h1>
		<div class="employee-info">
			<h2>従業員情報</h2>
			<table>
				<c:choose>
					<c:when test="${not empty user}">
						<!-- ユーザー情報を表示 -->
						<tr>
							<th>氏名</th>
							<td>${user.username}</td>
						</tr>
						<tr>
							<th>部署</th>
							<td>${user.department.departmentName}</td>
						</tr>
						<tr>
							<th>役職</th>
							<td>${user.role}</td>
						</tr>
					</c:when>
					<c:otherwise>
						<p style="color: red;">ユーザー情報が読み込まれていません。Controllerから渡されているか確認してください。</p>
					</c:otherwise>
				</c:choose>
			</table>
		</div>
		<div class="payroll-details">
			<h2>給与明細 - ${salary.paymentMonth}</h2>
			<table>
				<c:choose>
					<c:when test="${not empty salary}">
						<tr>
							<th>基本給</th>
							<td>${salary.basicSalary.intValue()}円</td>
						</tr>
						<tr>
							<th>手当</th>
							<td>${salary.allowances.intValue()}円</td>
						</tr>
						<tr>
							<th>控除</th>
							<td>${salary.deductions.intValue()}円</td>
						</tr>
						<tr>
							<th>残業時間</th>
							<td>${salary.overtimeHours}時間${salary.overtimeMinutes}分</td>
						</tr>
						<tr>
							<th>残業代</th>
							<td>${salary.overtimePay.intValue()}円</td>
						</tr>
						<tr>
							<th>総支給額</th>
							<td>${salary.totalSalary.intValue()}円</td>
						</tr>
					</c:when>
					<c:otherwise>
						<p style="color: red;">指定された給与情報が見つかりません。給与ID: ${param.salaryId}</p>
					</c:otherwise>
				</c:choose>
			</table>
		</div>
		<form id="generatePdfForm" class="print-instruction">
			<button type="button" id="pdfButton">PDF出力</button>
		</form>
	</div>
</body>
</html>