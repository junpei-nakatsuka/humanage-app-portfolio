package jp.co.example.listeners;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.entity.Employment;
import jp.co.example.entity.User;
import jp.co.example.repository.EmploymentRepository;
import jp.co.example.repository.UserRepository;

@Component
public class EmploymentStatusUpdater {
	
	private static final Logger logger = LoggerFactory.getLogger(EmploymentStatusUpdater.class);

    @Autowired
    private EmploymentRepository employmentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @EventListener
    @Transactional
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("[INFO] EmploymentStatusUpdater.javaのonApplicationEventが呼ばれました。event: {}", event);
        try {
            LocalDate today = LocalDate.now();
            List<Employment> employments = employmentRepository.findByStatusAndResignationDateBefore("退職予定", today);

            for (Employment employment : employments) {
                employment.setStatus("退職済み");
                employmentRepository.save(employment);

                User user = employment.getUser();
                if (user != null) {
                    user.setStatus("退職済み");
                    userRepository.save(user);
                }
            }
            logger.info("[INFO] ステータス更新: 退職予定 -> 退職済み");
        } catch (Exception e) {
            logger.error("[ERROR] EmploymentStatusUpdater.javaのonApplicationEventでエラー発生: {}", e.getMessage(), e);
        }
    }
}
