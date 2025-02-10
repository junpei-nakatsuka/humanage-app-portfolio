package jp.co.example.service;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.example.entity.Department;
import jp.co.example.entity.User;
import jp.co.example.exception.DepartmentNotFoundException;
import jp.co.example.repository.DepartmentRepository;
import jp.co.example.repository.UserRepository;

@Service
public class DepartmentService {
	
	private static final Logger logger = LoggerFactory.getLogger(DepartmentService.class);

    @Autowired
    private DepartmentRepository departmentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Department> getAllDepartments() {
    	logger.info("[INFO] getAllDepartmentsが呼ばれました。");
        try {
            return departmentRepository.findAll();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 全ての部門情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("全ての部門情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAllDepartmentsで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Department getDepartmentById(Integer id) {
    	logger.info("[INFO] getDepartmentByIdが呼ばれました。ID: {}", id);
        try {
        	Department dept = departmentRepository.findById(id).orElse(null);
            logger.info("[INFO] 取得した部門: {}", dept != null ? dept.getDepartmentName() : "null");
            return dept;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの部門情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("指定されたIDの部門情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getDepartmentByIdで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public Department saveDepartment(Department department) {
        try {
        	//部門が新規の場合はそのまま保存
        	if (department.getId() == null) {
        		logger.debug("[DEBUG] 部門が新規なのでセーブします。");
        		return departmentRepository.save(department);
        	} else {
        		//既存の部門をマージ
        		logger.debug("[DEBUG] 部門が既にあるのでマージします。");
        		return mergeDepartment(department);
        	}
        } catch (DataAccessException e) {
            logger.error("[ERROR] 部門情報の保存に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("部門情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] saveDepartmentで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public Department mergeDepartment(Department department) {
        try {
            // まず、DepartmentがManagedかDetachedかを確認する
            if (department.getId() != null) {
                // IDが存在する場合は、既存の部門を取得
                Department existingDepartment = departmentRepository.findById(department.getId())
                        .orElseThrow(() -> new RuntimeException("指定された部門が存在しません。"));

                // すでに管理されているエンティティがある場合、マージしないようにする
                if (!entityManager.contains(existingDepartment)) {
                    // EntityManagerに存在しない場合はマージして管理状態に戻す
                    existingDepartment = entityManager.merge(department);
                } else {
                    // EntityManagerに存在する場合はそのまま更新
                    existingDepartment.setDepartmentName(department.getDepartmentName());
                    existingDepartment.setManager(department.getManager());
                    existingDepartment.setUsers(department.getUsers());
                }

                // 更新された部門情報を保存
                return existingDepartment;
            } else {
                // IDが存在しない場合は新規保存
                return departmentRepository.save(department);
            }
        } catch (Exception e) {
            logger.error("[ERROR] 部門情報のマージに失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("部門情報のマージに失敗しました: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteDepartment(Integer id) {
        try {
            departmentRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 部門情報の削除に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("部門情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public void updateDepartment(Department department) {
    	logger.info("[INFO] updateDepertmentが呼ばれました。department: {}", department);
        try {
        	Integer departmentId = department.getId();
        	logger.debug("[DEBUG] 取得したdepartmentId: {}", departmentId);
            Department existingDepartment = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new DepartmentNotFoundException("不当なdepartmentID: " + departmentId));
            
            String departmentName = department.getDepartmentName();
            logger.debug("[DEBUG] 取得したdepartmentName: {}", departmentName);
            existingDepartment.setDepartmentName(departmentName);
            
            Department managedDepartment = entityManager.merge(existingDepartment);
            departmentRepository.save(managedDepartment);
            
            Integer managedDepartmentId = managedDepartment.getId();
            logger.debug("[DEBUG] 取得したmanagedDepartmentId: {}", managedDepartmentId);
            
            List<User> users = userRepository.findByDepartmentId(managedDepartmentId);
            for (User user : users) {
                user.setDepartment(managedDepartment); 
                userRepository.save(user);
            }
        } catch (DataAccessException e) {
            logger.error("[ERROR] 部門情報の更新に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("部門情報の更新に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] updateDepartmentで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Department findByDepartmentName(String departmentName) {
        try {
            return departmentRepository.findByDepartmentName(departmentName)
                    .orElseThrow(() -> new DepartmentNotFoundException("Departmentが見つかりませんでした: " + departmentName));
        } catch (DataAccessException e) {
            logger.error("[ERROR] 部門名による検索に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("部門名による検索に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
}
