package jp.co.example.service;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.entity.Employment;
import jp.co.example.repository.EmploymentRepository;

@Service
public class EmploymentService {
	
	private static final Logger logger = LoggerFactory.getLogger(EmploymentService.class);

    @Autowired
    private EmploymentRepository employmentRepository;

    public List<Employment> getAllEmployments() {
        try {
            return employmentRepository.findAll();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 全ての雇用情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("全ての雇用情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAllEmploymentsで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public List<Employment> getEmploymentByUserId(Integer userId) {
        try {
            return employmentRepository.findByUserId(userId);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたユーザーの雇用情報の取得に失敗しました。ID: {}, ERROR: {}", userId, e.getMessage(), e);
            throw new RuntimeException("指定されたユーザーの雇用情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getEmploymentByUserIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public void saveEmployment(Employment employment) {
    	
        if (employment.getUser() == null || employment.getStatus() == null) {
        	logger.error("[ERROR] ユーザーまたはステータスがnullです。");
            throw new IllegalArgumentException("ユーザーまたはステータスがnullです。");
        }
        
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        
        logger.info("[INFO] saveEmploymentが呼ばれました。employment: {}, 呼び出し元: {}", employment, callingMethodName);
        
        try {
            employmentRepository.save(employment);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 雇用情報の保存に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("雇用情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] saveEmploymentで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public void deleteEmploymentByUserId(Integer userId) {
        try {
            employmentRepository.deleteByUserId(userId);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたユーザーの雇用情報の削除に失敗しました。ID: {}, ERROR: {}", userId, e.getMessage(), e);
            throw new RuntimeException("指定されたユーザーの雇用情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] deleteEmploymentByUserIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<Employment> getEmploymentsByUserId(Integer userId) {
        try {
            return employmentRepository.findByUserId(userId);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたユーザーの雇用情報の取得に失敗しました。ID: {}, ERROR: {}", userId, e.getMessage(), e);
            throw new RuntimeException("指定されたユーザーの雇用情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getEmploymentsByUserIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public void deleteEmploymentById(Integer id) {
        try {
            employmentRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの雇用情報の削除に失敗しました。ID: {}, ERROR: {}", id, e.getMessage(), e);
            throw new RuntimeException("指定されたIDの雇用情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] deleteEmploymentByIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Employment getEmploymentById(Integer id) {
        try {
            return employmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("指定されたIDの雇用情報が見つかりません: " + id));
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの雇用情報の取得に失敗しました。ID: {}, ERROR: {}", id, e.getMessage(), e);
            throw new RuntimeException("指定されたIDの雇用情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getEmploymentByIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
}
