package jp.co.example.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class ObjectMapperConfig {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMapperConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        logger.info("[INFO] ObjectMapperConfig.javaのobjectMapperメソッドが呼ばれました。");

        try {
            // JsonMapperを使用してObjectMapperを構築
            JsonMapper mapper = JsonMapper.builder()
                    .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS) // 空のBeanをエラーとする
                    .serializationInclusion(JsonInclude.Include.NON_NULL) // Nullフィールドをシリアライズしない
                    .enable(SerializationFeature.INDENT_OUTPUT) // 可読性の高いフォーマット
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // タイムスタンプ形式を無効化
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // 不明なプロパティを無視
                    .configure(SerializationFeature.WRAP_EXCEPTIONS, false) // 例外をラップしない
                    .configure(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL, true) // 自己参照をnullとしてシリアライズ
                    .enable(SerializationFeature.FAIL_ON_SELF_REFERENCES) // 自己参照をエラーにする
                    .addModule(new JavaTimeModule()) // Java8日付モジュールを追加
                    .addModule(new SimpleModule()) // カスタムモジュールを追加
                    .build(); // JsonMapperをビルド

            logger.debug("[DEBUG] ObjectMapperインスタンスを生成しました。");
            return mapper;

        } catch (Exception e) {
            logger.error("[ERROR] ObjectMapperの初期化中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ObjectMapperの初期化に失敗しました: " + e.getMessage(), e);
        }
    }
}