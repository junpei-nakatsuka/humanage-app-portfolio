package jp.co.example;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication(scanBasePackages = "jp.co.example", exclude = { 
	    org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration.class,
	    org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration.class 
	})
@EnableScheduling
@EnableAsync
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
@EntityScan(basePackages = "jp.co.example.entity")
@ComponentScan(basePackages = "jp.co.example")
public class HumanageAppApplication extends SpringBootServletInitializer {
	
	private static final Logger logger = LoggerFactory.getLogger(HumanageAppApplication.class);

	public static void main(String[] args) {
        try {
            SpringApplication.run(HumanageAppApplication.class, args);
        } catch (Exception e) {
            logger.error("[ERROR] アプリケーションの起動中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            System.exit(1);
        }
    }
}