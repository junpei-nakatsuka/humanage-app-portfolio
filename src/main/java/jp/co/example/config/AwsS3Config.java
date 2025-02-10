package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsS3Config {
    
    private static final Logger logger = LoggerFactory.getLogger(AwsS3Config.class);

    @Bean
    public S3Client s3Client() {
        logger.info("[INFO] AwsS3Config.javaのs3Clientが呼ばれました");

        // 環境変数からAWSのクレデンシャルを取得
        try {
            String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
            String region = System.getenv("AWS_REGION");

            if (accessKeyId == null || secretAccessKey == null || region == null) {
            	logger.error("[ERROR] AWSの環境変数が正しく設定されていません。");
                throw new IllegalArgumentException("AWSの環境変数が正しく設定されていません。");
            }

            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .build();
            
        } catch (IllegalArgumentException e) {
            logger.error("[ERROR] AWSの環境変数が不足しています: {}", e.getMessage(), e);
            throw e; // 例外を再スローして設定ミスを上層に伝える
        } catch (Exception e) {
            logger.error("[ERROR] S3Clientの作成中に予期しないエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("S3Clientの作成に失敗しました。", e); // その他のエラーをランタイム例外としてスロー
        }
    }
}
