package jp.co.example.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.service.SalaryService;

@Component
public class OvertimeReflector {
	
	private static final Logger logger = LoggerFactory.getLogger(OvertimeReflector.class);

    @Autowired
    private SalaryService salaryService;
    
    @EventListener
    @Transactional
    public void onApplicationEvent(ContextRefreshedEvent event) {
    	logger.info("[INFO] OvertimeReflector.javaのonApplicationEventが呼ばれました: {}", event);
        try {
        	logger.debug("[DEBUG] SalaryService.reflectOvertimeを呼び出します");
            salaryService.reflectOvertime();  // 残業時間の反映処理
            logger.info("[INFO] SalaryService.reflectOvertimeが完了しOvertimeReflector.javaに戻ってきました。残業時間が反映されました");
        } catch (Exception e) {
            logger.error("[ERROR] OvertimeReflector.javaにて残業時間の反映中にエラーが発生しました - エラー: {}", e.getMessage(), e);
        }
    }
}
