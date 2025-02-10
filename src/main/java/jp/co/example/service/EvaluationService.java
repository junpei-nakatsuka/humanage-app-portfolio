package jp.co.example.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import jp.co.example.entity.Evaluation;
import jp.co.example.repository.EvaluationRepository;

@Service
public class EvaluationService {
	
	private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

    @Autowired
    private EvaluationRepository evaluationRepository;

    public List<Evaluation> getAllEvaluations() {
        try {
            return evaluationRepository.findAll();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 全ての評価情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("全ての評価情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Optional<Evaluation> getEvaluationById(Integer id) {
        try {
            return evaluationRepository.findById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの評価情報の取得に失敗しました。ID: {}, ERROR: {}", id, e.getMessage(), e);
            throw new RuntimeException("指定されたIDの評価情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Evaluation saveEvaluation(Evaluation evaluation) {
        try {
            return evaluationRepository.save(evaluation);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 評価情報の保存に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("評価情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public void deleteEvaluation(int evaluationId) {
        try {
            evaluationRepository.deleteById(evaluationId);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの評価情報の削除に失敗しました。ID: {}, ERROR: {}", evaluationId, e.getMessage(), e);
            throw new RuntimeException("指定されたIDの評価情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
}
