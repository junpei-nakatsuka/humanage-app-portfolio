package jp.co.example.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.entity.Contract;
import jp.co.example.exception.ContractNotFoundException;
import jp.co.example.repository.ContractRepository;

@Service
public class ContractService {
	
	private static final Logger logger = LoggerFactory.getLogger(ContractService.class);

    @Autowired
    private ContractRepository contractRepository;

    public List<Contract> getAllContracts() {
        try {
            return contractRepository.findAll();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 全ての契約情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("全ての契約情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAllContractsで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Optional<Contract> getContractById(Integer id) {
        try {
            return contractRepository.findById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの契約情報が見つかりませんでした: {}", e.getMessage(), e);
            throw new ContractNotFoundException("指定されたIDの契約情報が見つかりませんでした: " + id, e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getContractByIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Contract saveContract(Contract contract) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
        logger.info("[INFO] saveContractが呼ばれました。contract: {}, 呼び出し元: {}", contract, callingMethodName);
        
        validateContract(contract);
        
        try {
            logger.debug("[DEBUG] Contract の詳細情報: {}", contract);
            logger.debug("[DEBUG] Contract ID before save: {}", contract.getId());
            return contractRepository.save(contract);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 契約情報の保存に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("契約情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] saveContractで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
	private void validateContract(Contract contract) {
		logger.info("[INFO] validateContractが呼ばれました。contract: {}", contract);
		try {
			if (contract == null) {
				logger.error("[ERROR] 契約情報がnullです");
				throw new IllegalArgumentException("契約情報がnullです");
			}

			logger.debug("[VALIDATION] Contract ID: {}", contract.getId());
			logger.debug("[VALIDATION] User: {}", contract.getUser() != null ? contract.getUser().getId() : "NULL");
			logger.debug("[VALIDATION] Contract Date: {}", contract.getContractDate());
			logger.debug("[VALIDATION] Expiry Date: {}", contract.getExpiryDate());
			logger.debug("[VALIDATION] Type: {}", contract.getType());
			logger.debug("[VALIDATION] Status: {}", contract.getStatus());
			
			contract.setType(contract.getType() != null ? contract.getType().trim() : null);
		    contract.setStatus(contract.getStatus() != null ? contract.getStatus().trim() : null);

			// type のバリデーション (英数字、日本語のみに制限)
			if (contract.getType() == null || !contract.getType().matches("^[a-zA-Z0-9ぁ-んァ-ン一-龥ー]{1,50}$")) {
				logger.error("[ERROR] 契約タイプが不正です: ", contract.getType());
				throw new IllegalArgumentException("契約タイプが不正です: " + contract.getType());
			}

			// status のバリデーション
			if (contract.getStatus() == null || !contract.getStatus().matches("^[a-zA-Z0-9ぁ-んァ-ン一-龥ー]{1,20}$")) {
				logger.error("[ERROR] 契約ステータスが不正です: ", contract.getStatus());
				throw new IllegalArgumentException("契約ステータスが不正です: " + contract.getStatus());
			}

			// contractDate のバリデーション
			if (contract.getContractDate() == null) {
				logger.error("[ERROR] 契約日がnullです");
				throw new IllegalArgumentException("契約日がnullです");
			}

			// expiryDate のチェック (contractDate 以前でないか確認)
			if (contract.getExpiryDate() != null && contract.getExpiryDate().isBefore(contract.getContractDate())) {
				logger.error("[ERROR] 終了日が契約日より前になっています: " + contract.getExpiryDate() + " < " + contract.getContractDate());
				throw new IllegalArgumentException("終了日が契約日より前になっています: " + contract.getExpiryDate() + " < " + contract.getContractDate());
			}
		} catch (Exception e) {
			logger.error("[ERROR] validateContractで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
			throw new RuntimeException("validateContractで予期しないエラーが発生しました: " + e.getMessage(), e);
		}
	}

    @Transactional
    public void deleteContract(Integer id) {
        try {
            contractRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 契約情報の削除に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("契約情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] deleteContractで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public void deleteContractById(Integer id) {
        try {
            logger.info("[INFO] 削除する契約ID: {}", id);
            contractRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 契約情報の削除に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("契約情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] deleteContractByIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<Contract> getContractsByUserId(Integer userId) {
        try {
            return contractRepository.findByUserId(userId);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたユーザーIDの契約情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("指定されたユーザーIDの契約情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getContractsByUserIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Contract getLatestContractByUserId(Integer userId) {
        try {
            return contractRepository.findTopByUserIdOrderByContractDateDesc(userId);
        } catch (Exception e) {
            logger.error("[ERROR] 最新の契約情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return null; // エラーが発生した場合は null を返す
        }
    }

    public List<Contract> getActiveContractsByUserId(Integer userId) {
        try {
            return contractRepository.findActiveContractsByUserId(userId, LocalDate.now());
        } catch (Exception e) {
            logger.error("[ERROR] 有効な契約情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合は空のリストを返す
        }
    }

    public Contract getActiveContractByUserId(Integer userId) {
        try {
            List<Contract> activeContracts = contractRepository.findActiveContractsByUserId(userId, LocalDate.now());
            return activeContracts.isEmpty() ? null : activeContracts.get(0); // 最新の有効な契約を返す
        } catch (Exception e) {
            logger.error("[ERROR] 有効な契約情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return null; // エラーが発生した場合は null を返す
        }
    }
}
