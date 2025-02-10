package jp.co.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import jp.co.example.filter.LoginFilter;

@Configuration
public class FilterConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FilterConfig.class);

    @Bean
    @Lazy
    public LoginFilter loginFilter() {
        logger.info("[INFO] FilterConfig.javaでLoginFilterを作成しています。");
        try {
            return new LoginFilter();
        } catch (Exception e) {
            logger.error("[ERROR] LoginFilterの作成中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("LoginFilterの作成に失敗しました。", e);
        }
    }

    @Bean
    public FilterRegistrationBean<LoginFilter> registerLoginFilter(LoginFilter loginFilter) {
        logger.info("[INFO] FilterConfig.javaのregisterLoginFilterが呼ばれました。loginFilter: {}", loginFilter);
        try {
            FilterRegistrationBean<LoginFilter> registrationBean = new FilterRegistrationBean<>();
            registrationBean.setFilter(loginFilter);
            registrationBean.addUrlPatterns("/*");
            return registrationBean;
        } catch (Exception e) {
            logger.error("[ERROR] FilterRegistrationBeanの登録中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("FilterRegistrationBeanの登録に失敗しました。", e);
        }
    }
}
