import { getRedisClient } from './src/main/resources/static/js/generate_pdf.js';

async function cleanupExpiredKeys(redisClient) {
    console.info("[INFO] clear_jobs.mjsのcleanupExpiredKeys関数が呼ばれました:");
    try {
        const keys = await redisClient.keys("token_expiration:*");
        const currentTime = Date.now();
        for (const key of keys) {
            const expirationTime = parseInt(await redisClient.get(key));
            if (expirationTime < currentTime) {
                const tokenKey = key.replace("token_expiration:", "token:");
                await redisClient.del(key);
                await redisClient.del(tokenKey);
                console.log(`Expired token cleaned: ${key}`);
            }
        }
    } catch (error) {
        console.error("[ERROR] cleanupExpiredKeys中にエラーが発生しました:", error.message, error);
    }
}

async function clearJobs() {
    console.log("clear_jobs.mjsのclearJobs関数が呼ばれました");
    const redisClient = await getRedisClient();
    
    if (typeof redisClient.on !== 'function') {
        console.error("[DEBUG] redisClientは正しいRedisインスタンスではありません:", redisClient);
        throw new Error("Redisクライアントが正しく初期化されていません");
    }

    if (!redisClient || redisClient.status !== 'ready') {
        console.warn("Redisクライアントが接続されていません。現在の状態:", redisClient?.status || "未初期化");
        throw new Error("Redisクライアントが初期化されていません");
    }

    const maxRetries = 5;
    const retryDelay = 2000;

    async function cleanupKeysWithRetries(attempt = 1) {
        console.log("clear_jobs.mjsのcleanupKeysWithRetries関数が呼ばれました");
        try {
            console.log(`Redisジョブキー削除 試行中 (試行回数: ${attempt}/${maxRetries})...`);
            const keys = await redisClient.keys("pdf_status:*");
            console.log(`削除対象キー数: ${keys.length}`);
            if (keys.length > 0) {
                console.log(`削除するキー数: ${keys.length}`);
                await redisClient.del(keys);
                console.log("ジョブ関連キーを正常に削除しました。", keys);
            } else {
                console.log("削除対象のジョブキーが見つかりません。");
            }
        } catch (error) {
            console.error(`Redisジョブキー削除エラー (試行回数: ${attempt}/${maxRetries}) ${error.message}:`, error.message, error);
            if (attempt >= maxRetries || !error.message.includes("ECONNREFUSED")) {
                console.error("再試行終了。エラーが続いています:", error.message, error);
                throw error;
            }
            console.warn(`再試行まで ${retryDelay}ms 待機中...`);
            await new Promise((resolve) => setTimeout(resolve, retryDelay));
            await cleanupKeysWithRetries(attempt + 1);
        }
    }

    try {
        console.log("期限切れトークンを削除しています...");
        await cleanupExpiredKeys(redisClient);

        console.log("ジョブ関連のキーを削除しています...");
        await cleanupKeysWithRetries();

        console.log("ジョブキーのクリーンアップが完了しました。");
    } catch (error) {
        console.error("ジョブクリーンアップ中にエラーが発生しました:", error.message, error);
        throw error;
    }
}

clearJobs()
    .then(() => {
        console.log("ジョブキーのクリーンアップが成功しました。");
        process.exit(0);
    })
    .catch((err) => {
        console.error("ジョブクリーンアップ中にエラーが発生しました:", {
            message: err.message,
            stack: err.stack,
            additionalInfo: err,
        });
        process.exit(1);
    });