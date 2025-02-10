package jp.co.example.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import jp.co.example.entity.Department;
import jp.co.example.entity.Employment;
import jp.co.example.entity.User;
import jp.co.example.repository.EmploymentRepository;
import jp.co.example.repository.UserRepository;
import jp.co.example.util.HashUtil;

@Service
public class UserService {
	
	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EmploymentRepository employmentRepository;

    @Autowired
    private ModelMapper modelMapper;
    
    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private HttpSession session;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public List<User> getAllUsers() {
        logger.info("[INFO] UserService.javaのgetAllUsers() メソッドが呼び出されました。Supabaseからユーザーデータを取得中...");
        try {
            logger.debug("[DEBUG] ユーザー情報の取得を開始します...");
            
            validateSupabaseConnection();
            
            List<User> users = userRepository.findAll();
            
            if (users == null || users.isEmpty()) {
                logger.warn("[WARN] 取得したユーザー情報は空です。Supabaseからユーザーデータを取得できませんでした。");
            } else {
                logger.debug("[DEBUG] 正常に{}人のユーザーを取得しました。", users.size());
            }
            
            logger.info("[INFO] 取得したユーザー数: {}, ユーザーリスト: {}", users.size(), users);
            return users;
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー情報の取得中にデータベースエラーが発生しました。エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] getAllUsersで予期しないエラーが発生しました。エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    private void validateSupabaseConnection() {
        logger.info("[INFO] validateSupabaseConnectionが呼ばれました。Supabase接続を検証中...");
        try {
            String supabaseUrl = System.getenv("SUPABASE_URL");
            String databaseUrl = System.getenv("DATABASE_URL");
            
            if (supabaseUrl == null || databaseUrl == null) {
            	logger.error("[ERROR] supabase接続に必要な環境変数が設定されていません。");
            	throw new IllegalArgumentException("supabase接続に必要な環境変数が設定されていません");
            }
            
            List<User> users = userRepository.findAll();
            if (users.isEmpty()) {
                logger.warn("[WARN] Supabaseにはユーザーデータが存在しません。接続に問題があるかもしれません。");
            } else {
                logger.info("[INFO] Supabaseからユーザーデータを正常に取得しました。");
            }
        } catch (Exception e) {
            logger.error("[ERROR] Supabase接続に失敗しました。エラーメッセージ: {}", e.getMessage(), e);
            throw new RuntimeException("Supabase接続失敗: " + e.getMessage(), e);
        }
    }
    
    public User getUserById(Integer id) {
    	logger.info("[INFO] getUserByIdが呼ばれました: {}", id);
        try {
            return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("不当なユーザーID:" + id));
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDのユーザー情報が見つかりませんでした。ID: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", id, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("指定されたIDのユーザー情報が見つかりませんでした: " + id, e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getUserByIdで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public User saveUser(User user) {
    	logger.info("[INFO] saveUserが呼ばれました: {}", user);
    	Integer userId = user.getId();
        logger.debug("[DEBUG] Userから取得したuserId: {}", userId);
        try {
            if (userId != null) {
                User existingUser = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません：" + userId));

                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    user.setPassword(existingUser.getPassword());
                } else if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
                    user.setPassword(HashUtil.hashPassword(user.getPassword()));
                }
            } else {
                if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                    user.setPassword(HashUtil.hashPassword(user.getPassword()));
                }
            }

            user.setResetToken(null);
            
            Department department = user.getDepartment();
            logger.debug("[DEBUG] 取得したdepartment: {}", department);
            
            if (department != null) {
            	if (department.getDepartmentName() == null) {
                    logger.error("[ERROR] 部門名が設定されていません: {}", department);
                    throw new IllegalArgumentException("部門名が設定されていません。");
                }
            	logger.debug("[DEBUG] user.getDepartment()がdetached状態なのでmergeします。");
            	Department managedDepartment = entityManager.merge(department);
            	user.setDepartment(managedDepartment);
            }

            User savedUser = userRepository.save(user);
            
            try {
                String userJson = objectMapper.writeValueAsString(savedUser);
                redisTemplate.opsForValue().set("user:" + savedUser.getUsername(), userJson);
            } catch (JsonProcessingException e) {
                logger.error("[ERROR] ユーザー情報のJSON変換中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                throw new RuntimeException("ユーザー情報のJSON変換に失敗しました: " + e.getMessage(), e);
            } catch (Exception ex) {
            	logger.error("[ERROR] saveUserでjson変換中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
            }
            
            logger.info("[INFO] saveUserが成功しました。savedUserId: {}", savedUser.getId());
            return savedUser;
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー情報の保存に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザー情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] saveUserで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void deleteUser(Integer id) {
        logger.info("[INFO] deleteUserが呼ばれました。ユーザーID: {}", id);
        try {
        	User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + id));
        	
        	logger.debug("[DEBUG] 取得したuser: {}", user);
            userRepository.deleteById(id);
            
            redisTemplate.delete("user:" + user.getUsername());
            logger.debug("[DEBUG] Idが{}のユーザーの削除が完了しました。", id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー情報の削除に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザー情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (IllegalArgumentException ex) {
            logger.error("[ERROR] ユーザー削除エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw ex;
        } catch (Exception exx) {
        	logger.error("[ERROR] deleteUserで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", exx.toString(), Arrays.toString(exx.getStackTrace()), exx.getCause() != null ? exx.getCause().toString() : "原因不明", exx.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + exx.getMessage(), exx);
        }
    }
    
    public List<User> getActiveEmployees() {
        try {
            return userRepository.findByRetirementDateIsNull();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 在職中のユーザー情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("在職中のユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getActiveEmployeesで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public List<User> getRetiredEmployees() {
        try {
            return userRepository.findByRetirementDateIsNotNull();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 退職済みユーザー情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("退職済みユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getRetiredEmployeesで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public boolean isPasswordCorrect(User user, String rawPassword) {
    	logger.info("[INFO] isPasswordCorrectが呼ばれました。ユーザー: {}, rawPassword: {}", user, rawPassword);
        try {
            if (user.getPassword() == null || rawPassword == null) {
            	logger.error("[ERROR] パスワードまたはユーザーがnullです。");
                return false;
            }
            
            String hashedPassword = user.getPassword();
            
            boolean result = HashUtil.checkPassword(rawPassword, hashedPassword);
            logger.debug("[DEBUG] パスワード一致チェック: rawPassword=[****], hashedPassword=[****], isMatch={}", result);
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] パスワード検証中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return false; // エラーが発生した場合、パスワードが一致しないとみなす
        }
    }
    
    public User authenticate(String username, String password) {
        logger.info("[INFO] authenticateが呼ばれました。Redisを使用してユーザーを認証中。ユーザーネーム: {}, パスワード: [****]", username);

        try {
            // Redisからユーザー情報を取得
            String redisKey = "user:" + username;
            String userJson = (String) redisTemplate.opsForValue().get(redisKey);
            logger.debug("[DEBUG] Redisキー: {}, 取得したユーザーデータ: {}", redisKey, userJson);
            
            if (userJson == null) {
                logger.warn("[WARN] Redisに該当するユーザーが存在しません: {}", username);
                return null;
            }

            // JSON文字列をUserオブジェクトに変換
            User user = objectMapper.readValue(userJson, User.class);
            
            boolean result = isPasswordCorrect(user, password);
            
            // パスワードを検証
            if (result) {
                if ("退職済み".equals(user.getStatus())) {
                    logger.warn("[WARN] 退職済みのユーザーがログインを試みました: {}", username);
                    return null;
                }
                logger.info("[INFO] Redisを使用して認証成功: {}", username);
                return user;
            } else {
            	logger.warn("[WARN] パスワードが一致しません: {}", username);
                return null;
            }
        } catch (Exception e) {
            logger.error("[ERROR] Redisを使用した認証中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("Redisを使用した認証中にエラーが発生しました: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void updateUserStatusAndEmploymentStatus(Integer userId, String newStatus) {
        logger.info("[INFO] updateUserStatusAndEmploymentStatusが呼ばれました。User ID: {}, New Status: {}", userId, newStatus);
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("不当なuserId:" + userId));

            user.setStatus(newStatus);
            
            user = entityManager.merge(user);
            userRepository.save(user);
            logger.debug("[DEBUG] IDが{}のユーザーのステータスを更新しました", userId);

            List<Employment> employments = employmentRepository.findByUserId(userId);
            if (!employments.isEmpty()) {
                Employment latestEmployment = employments.get(employments.size() - 1);
                latestEmployment.setStatus(newStatus);
                
                latestEmployment = entityManager.merge(latestEmployment);
                employmentRepository.save(latestEmployment);
                logger.debug("[DEBUG] IDが{}のユーザーのemploymentが更新されました。", userId);
            }
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーおよび雇用状況の更新中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザーおよび雇用状況の更新中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] updateUserStatusAndEmploymentStatusで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Optional<User> findByUsername(String username){
        try {
            return userRepository.findByUsername(username);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー名によるユーザー情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザー名によるユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] findByUsernameで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public void updateUser(User updatedUser, User currentUser) {
        logger.info("[INFO] updateUserが呼ばれました。updatedUser: {}, currentUser: {}", updatedUser, currentUser);
        Integer updatedUserId = updatedUser.getId();
        logger.debug("[DEBUG] 取得したupdatedUserId: {}", updatedUserId);
        try {
            // 既存ユーザーを取得
            User existingUser = userRepository.findById(updatedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません：" + updatedUserId));

            String existingPassword = existingUser.getPassword();

            // updatedUserの情報を既存エンティティにマッピング
            modelMapper.map(updatedUser, existingUser);

            // パスワードのハッシュ化処理
            String updatedPassword = updatedUser.getPassword();
            if (updatedPassword != null && !updatedPassword.isEmpty()) {
                if (!updatedPassword.startsWith("$2a$") && !updatedPassword.startsWith("$2b$")) {
                    updatedPassword = HashUtil.hashPassword(updatedPassword);
                }
                existingUser.setPassword(updatedPassword);
            } else {
                existingUser.setPassword(existingPassword); // パスワードがnullなら元のまま
            }

            // 更新者情報の設定
            existingUser.setLastModifiedBy(currentUser);
            existingUser.setLastModifiedAt(LocalDateTime.now());

            Integer updatedUserDepartmentId = updatedUser.getDepartment() != null ? updatedUser.getDepartment().getId() : null;

            // 部門情報の取得（Lazy Loadingを防ぐため明示的に取得）
            Department department = departmentService.getDepartmentById(updatedUserDepartmentId);
            existingUser.setDepartment(department);

            String updatedUserRole = updatedUser.getRole();
            logger.debug("[DEBUG] 取得したupdatedUserRole: {}", updatedUserRole);

            Integer departmentManagerId = (department.getManager() != null) ? department.getManager().getId() : null;
            Integer existingUserId = existingUser.getId();

            logger.debug("[DEBUG] 取得したdepartmentManagerId: {}, existingUserId: {}", departmentManagerId, existingUserId);

            // 部門長の処理
            if ("部門長".equals(updatedUserRole)) {
                department.setManager(existingUser);
                departmentService.saveDepartment(department);
            } else {
                if (department.getManager() != null && departmentManagerId.equals(existingUserId)) {
                    department.setManager(null);
                    departmentService.saveDepartment(department);
                }
            }

        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー情報の更新中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザー情報の更新中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] updateUserで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<User> searchUsers(String query) {
        try {
            return userRepository.findByUsernameContainingOrEmailContainingOrPhoneContaining(query, query, query);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー検索中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザー検索中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] searchUsersで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    @Transactional
    public List<User> getAllUsersSortedByUsername() {
        try {
            return userRepository.findAllByOrderByUsernameAsc();
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー情報のソート中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザー情報のソート中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAllUsersSortedByUsernameで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<User> searchUsersByUsername(String username){
        try {
            return userRepository.findByUsernameContainingIgnoreCase(username);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー名での検索中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザー名での検索中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] searchUsersByUsernameで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Optional<User> findByEmail(String email) {
        try {
            return userRepository.findByEmail(email);
        } catch (DataAccessException e) {
            logger.error("[ERROR] メールアドレスによるユーザー情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("メールアドレスによるユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] findByEmailで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void updateResetToken(String token, String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if(userOpt.isPresent()) {
                User user = userOpt.get();
                user.setResetToken(token);
                userRepository.save(user);
            } else {
            	logger.error("[ERROR] Eメールが存在しません: {}", email);
                throw new IllegalArgumentException("Eメールが存在しません:" + email);
            }
        } catch (DataAccessException e) {
            logger.error("[ERROR] リセットトークンの更新中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("リセットトークンの更新中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] updateResetTokenで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Optional<User> findByResetToken(String token) {
        try {
            return userRepository.findByResetToken(token);
        } catch (DataAccessException e) {
            logger.error("[ERROR] リセットトークンによるユーザー情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("リセットトークンによるユーザー情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] findByResetTokenで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void updatePassword(User user, String newPassword) {
        try {
            String hashedPassword = HashUtil.hashPassword(newPassword);
            user.setPassword(hashedPassword);
            user.setResetToken(null);
            userRepository.save(user);
        } catch (DataAccessException e) {
            logger.error("[ERROR] パスワードの更新中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("パスワードの更新中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] updatePasswordで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public boolean emailExists(String email) {
        try {
            return userRepository.existsByEmail(email);
        } catch (DataAccessException e) {
            logger.error("[ERROR] メールアドレスの存在チェック中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("メールアドレスの存在チェック中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] emailExistsで予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public void updateUserStatusIfNewEmployment(Integer userId, String newStatus) {
        try {
            if ("在職中".equals(newStatus)) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    user.setStatus("在職中");
                    userRepository.save(user);
                }
            }
        } catch (Exception e) {
            logger.error("[ERROR] ユーザーのステータス更新中にエラーが発生しました (userId: {}, newStatus: {}): {}", userId, newStatus, e.getMessage(), e);
        }
    }

    public void updateUserStatus(Integer userId, String status) {
        try {
            User user = getUserById(userId);
            if (user != null) {
                user.setStatus(status);
                saveUser(user);
            }
        } catch (Exception e) {
            logger.error("[ERROR] ユーザーのステータスを更新中にエラーが発生しました (userId: {}, status: {}): {}", userId, status, e.getMessage(), e);
        }
    }

    public User getCurrentUser() {
        try {
            return (User) session.getAttribute("user");
        } catch (Exception e) {
            logger.error("[ERROR] 現在のユーザー情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return null; // エラーが発生した場合は null を返す
        }
    }
}
