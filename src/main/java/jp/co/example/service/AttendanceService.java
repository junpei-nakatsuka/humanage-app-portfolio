package jp.co.example.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.example.config.ThreadLocalContextHolder;
import jp.co.example.config.UserFromByteArrayConverter;
import jp.co.example.config.UserToByteArrayConverter;
import jp.co.example.entity.Attendance;
import jp.co.example.entity.User;
import jp.co.example.exception.AttendanceNotFoundException;
import jp.co.example.repository.AttendanceRepository;
import jp.co.example.repository.UserRepository;
import jp.co.example.util.OvertimeCalculator;

@Service
public class AttendanceService {
	
	private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);
	
    @Autowired
    private AttendanceRepository attendanceRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserToByteArrayConverter userToByteArrayConverter;

    @Autowired
    private UserFromByteArrayConverter userFromByteArrayConverter;
    
    private List<Converter<?, ?>> registeredConverters;
    
    @Autowired
    @Qualifier("genericConversionService")
    private GenericConversionService genericConversionService;

    @PostConstruct
    public void init() {
    	logger.info("[INFO] AttendanceService.javaのinit関数が呼ばれました。");
        try {
            // コンバータの初期化
            logger.debug("[DEBUG] AttendanceService の初期化を開始します。");
            initializeConverters();

            // コンバータの登録
            genericConversionService.addConverter(userToByteArrayConverter);
            genericConversionService.addConverter(userFromByteArrayConverter);
            logger.info("[INFO] User → byte[] コンバーターと byte[] → User コンバーターを登録しました。");

            // 登録確認
            checkConverters();
            
            debugConversionService();

        } catch (Exception e) {
            logger.error("[ERROR] AttendanceService の初期化中にエラーが発生しました: {}", e.getMessage(), e);
        }
    }
    
    private void initializeConverters() {
		try {
			logger.info("[INFO] AttendanceService.javaにてコンバーターの初期化を開始します。");
			// GenericConversionService で登録されたコンバーターを手動で管理
			registeredConverters = Arrays.asList(
				userToByteArrayConverter,
				userFromByteArrayConverter
			);
			logger.info("[INFO] コンバーターが正常に初期化されました。");
		} catch (Exception e) {
			logger.error("[ERROR] AttendanceService.javaにてコンバーターの初期化中にエラーが発生しました: {}", e.getMessage(), e);
		}
	}

    private void checkConverters() {
        logger.info("[INFO] コンバーターが正常に登録されているか確認します...");
        if (genericConversionService.canConvert(User.class, byte[].class)) {
            logger.info("[INFO] User → byte[] コンバーターが正常に登録されています。");
        } else {
            logger.warn("[WARN] User → byte[] コンバーターが登録されていません！");
        }

        if (genericConversionService.canConvert(byte[].class, User.class)) {
            logger.info("[INFO] byte[] → User コンバーターが正常に登録されています。");
        } else {
            logger.warn("[WARN] byte[] → User コンバーターが登録されていません！");
        }
    }
    
    private void debugConversionService() {
        try {
            logger.info("[INFO] 登録済みコンバーターの一覧を表示します...");
            if (registeredConverters != null && !registeredConverters.isEmpty()) {
                for (Converter<?, ?> converter : registeredConverters) {
                    logger.info("  - " + converter.getClass().getSimpleName());
                }
            } else {
                logger.warn("[WARN] 登録済みコンバーターが見つかりません。");
            }
        } catch (Exception e) {
            logger.error("[ERROR] コンバーターのデバッグ中にエラーが発生しました: {}", e.getMessage(), e);
        }
    }
    
    public List<Attendance> getAllAttendances() {
    	logger.info("[INFO] getAllAttendancesが呼ばれました。");
        try {
            return attendanceRepository.findAll();
        } catch (DataAccessException e) {
            logger.error("[ERROR] 全ての勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("全ての勤怠情報の取得に失敗しました。");
        }
    }

    public Optional<Attendance> getAttendanceById(Integer id) {
    	logger.info("[INFO] getAttendanceByIdが呼ばれました。ID: {}", id);
        try {
            return attendanceRepository.findById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの勤怠情報が見つかりませんでした: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new AttendanceNotFoundException("指定されたIDの勤怠情報が見つかりませんでした: " + id, e);
        }
    }
    
    @CacheEvict(value = "attendanceCache", key = "#attendance.user.id + '_' + #attendance.date")
    @Transactional
    public Attendance saveAttendance(Attendance attendance) {
    	logger.info("[INFO] AttendanceService.javaのsaveAttendanceが呼ばれました。attendance: {}", attendance);
        // ユーザーがnullの場合は例外をスロー
        if (attendance.getUser() == null) {
        	logger.error("[ERROR] ユーザーが指定されていません。");
            throw new IllegalArgumentException("ユーザーが指定されていません。");
        }

        attendance.setUpdatedAt(LocalDateTime.now());

        // 退勤時間が出勤時間よりも前である場合は例外をスロー
        if (attendance.getEndTime() != null && attendance.getStartTime() != null && attendance.getEndTime().isBefore(attendance.getStartTime())) {
        	logger.error("[ERROR] 退勤時間は出勤時間より後でなければなりません。");
            throw new IllegalArgumentException("退勤時間は出勤時間より後でなければなりません。");
        }

        try {
            // 残業時間を計算して設定
            int overtimeMinutes = (int) OvertimeCalculator.calculateOvertimeMinutes(attendance);
            attendance.setOvertimeMinutes(overtimeMinutes);
            
            logger.debug("[DEBUG] saveAttendanceにてattendanceRepository.javaのsaveを呼びます: {}", attendance);
            return attendanceRepository.save(attendance);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 勤怠情報のデータベースアクセスに失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("勤怠情報のデータベースアクセスに失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] 勤怠情報の保存に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("勤怠情報の保存に失敗しました。", ex);
        }
    }
    
    //ユーザーの未退勤データがあるかを判定
    public boolean hasUnfinishedWork(User user) {
    	logger.info("[INFO] hasUnfinishedWorkが呼ばれました。User: {}", user);
        try {
            LocalDate today = LocalDate.now();
            boolean result = attendanceRepository.existsByUserAndEndTimeIsNullAndDateBefore(user, today);
            return result;
        } catch (Exception e) {
            logger.error("[ERROR] 未退勤データの判定中にエラーが発生しました。ユーザーネーム: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", user.getUsername(), e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return false;
        }
    }

    // 未退勤データを取得（管理者が手動で修正する場合に使用）
    public List<Attendance> getUnfinishedWork(User user) {
    	logger.info("[INFO] getUnfinishedWorkが呼ばれました。User: {}", user);
        try {
            LocalDate today = LocalDate.now();
            List<Attendance> unfinishedWork = attendanceRepository.findByUserAndEndTimeIsNullAndDateBefore(user, today);
            return unfinishedWork;
        } catch (Exception e) {
            logger.error("[ERROR] 未退勤データの取得中にエラーが発生しました。ユーザーネーム: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", user.getUsername(), e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return Collections.emptyList(); // 空のリストを返す
        }
    }

    // 未退勤データを日付で取得
    public Attendance findUnfinishedAttendanceByDate(LocalDate date) {
    	logger.info("[INFO] findUnfinishedAttendanceByDateが呼ばれました。date: {}", date);
        try {
            Attendance attendance = attendanceRepository.findByDateAndEndTimeIsNull(date);
            return attendance;
        } catch (Exception e) {
            logger.error("[ERROR] 未退勤データの検索中にエラーが発生しました。date: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", date, e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null; // エラー時には null を返す
        }
    }

    // 勤怠情報を更新
    @Transactional
    public void updateAttendance(Attendance attendance) {
    	logger.info("[INFO] updateAttendanceが呼ばれました。attendance: {}", attendance);
        try {
            attendanceRepository.save(attendance);
            logger.info("[INFO] 勤怠情報が正常に更新されました: " + attendance.getId());
        } catch (Exception e) {
            logger.error("[ERROR] 勤怠情報の更新中にエラーが発生しました。ID: {}, エラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", attendance.getId(), e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("勤怠情報の更新中にエラーが発生しました: " + e.getMessage(), e);
        }
    }
    
    public Attendance findUnfinishedAttendanceByUserAndDate(User user, LocalDate date) {
    	logger.info("[INFO] findUnfinishedAttendanceByUserAndDateが呼ばれました。user: {}, date: {}", user, date);
        try {
            return attendanceRepository.findByUserAndDateAndEndTimeIsNull(user, date);
        } catch (Exception e) {
        	logger.error("[ERROR] findUnfinishedAttendanceByUserAndDateでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return null;
        }
    }

    @CacheEvict(value = "attendanceCache", key = "#id")
    @Transactional
    public void deleteAttendance(Integer id) {
    	logger.info("[INFO] AttendanceService.javaのdeleteAttendanceが呼ばれました。ID: {}", id);
        try {
            attendanceRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 勤怠情報のデータベースアクセスに失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("勤怠情報のデータベースアクセスに失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] deleteAttendanceで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("deleteAttendanceで予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Attendance findLatestAttendanceByUser(User user) {
    	logger.info("[INFO] findLatestAttendanceByUserが呼ばれました。User: {}", user);
        try {
        	// Pageオブジェクトを取得
            Page<Attendance> page = attendanceRepository.findLatestAttendanceByUser(user, PageRequest.of(0, 1));
            // Pageからリストを取得
            List<Attendance> attendances = page.getContent();
            // リストが空でなければ最初の要素を返す
            return attendances.isEmpty() ? null : attendances.get(0);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーの最新の勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザーの最新の勤怠情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] ユーザーの最新の勤怠情報の取得中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("ユーザーの最新の勤怠情報の取得中に予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<Attendance> findAttendanceByUser(User user){
    	logger.info("[INFO] findAttendanceByUserが呼ばれました。User: {}", user);
        try {
            return attendanceRepository.findByUser(user);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーの勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザーの勤怠情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] ユーザーの勤怠情報の取得中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("ユーザーの勤怠情報の取得中に予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public Attendance findAttendanceByUserAndDate(User user, LocalDate date) {
    	logger.info("[INFO] AttendanceService.javaのfindAttendanceByUserAndDateが呼ばれました。user: {}, date: {}", user, date);
        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] 取得したuserId: {}", userId);
            return attendanceRepository.findByUserIdAndDate(userId, date).orElse(null);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーと日付での勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザーと日付での勤怠情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] ユーザーと日付での勤怠情報の取得中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("ユーザーと日付での勤怠情報の取得中に予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public List<Attendance> findAttendanceByUserAndMonth(User user, int month){
    	logger.info("[INFO] findAttendanceByUserAndMonthが呼ばれました。User: {}, month: {}", user, month);
        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] 取得したuserId: {}", userId);
            return attendanceRepository.findAttendanceByUserAndMonth(userId, month);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーと月での勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("ユーザーと月での勤怠情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] ユーザーと月での勤怠情報の取得中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("ユーザーと月での勤怠情報の取得中に予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    //利用可能な月を取得するメソッド
    @Cacheable(value = "attendanceCache", key = "#user.id")
    public List<Integer> getAvailableMonths(User user){
    	logger.info("[INFO] AttendanceService.javaのgetAvailableMonthsが呼ばれました。user: {}", user);
        Integer userId = user.getId();
        logger.debug("[DEBUG] ユーザーからIDを取得しました: {}", userId);
        try {
            List<Integer> months = attendanceRepository.findAvailableMonthsByUser(userId);
            if (months == null || months.isEmpty()) {
                logger.warn("[WARN] ユーザーID {} の利用可能な月が見つかりませんでした。デフォルト値を設定します。", userId);
                months = List.of(LocalDate.now().getMonthValue()); // 現在の月をデフォルト値にする
            }
            return months;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 利用可能な月の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("利用可能な月の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 利用可能な月の取得中に予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("利用可能な月の取得中に予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public int sumOvertimeMinutesByUserAndMonth(User user, int month) {
    	logger.info("[INFO] sumOvertimeMinutesByUserAndMonthが呼ばれました。User: {}, month: {}", user, month);
    	Integer userId = user.getId();
    	logger.debug("[DEBUG] userから取得したuserId: {}", userId);
        try {
            List<Attendance> attendances = attendanceRepository.findAttendanceByUserAndMonth(userId, month);
            int totalOvertimeMinutes = attendances.stream().mapToInt(Attendance::getOvertimeMinutes).sum();
            return totalOvertimeMinutes;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 月ごとの残業時間の合計に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("月ごとの残業時間の合計に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 月ごとの残業時間の合計で予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("月ごとの残業時間の合計で予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    private void validateUsername(String username) {
    	logger.info("[INFO] validateUsernameが呼ばれました。username: {}", username);
        String regex = "^[a-zA-Z0-9]+$";
        if (!username.matches(regex)) {
            logger.error("[ERROR] ユーザー名が不正です: {}", username);
            throw new IllegalArgumentException("ユーザー名が不正です: " + username);
        }
    }

    @Transactional(rollbackFor = {RuntimeException.class, Exception.class})
    public void startAttendance(Integer userId) {
    	
        if (ThreadLocalContextHolder.isAlreadyProcessing()) {
            logger.warn("[WARN] startAttendanceの再起呼び出しを防止しました。userId: {}", userId);
            return;
        }
        ThreadLocalContextHolder.setProcessing(true);

        logger.info("[INFO] AttendanceService.javaのstartAttendanceが呼ばれました。userId: {}", userId);

        try {
            // ユーザーの取得
            User user = getUserById(userId);
            logger.debug("[DEBUG] Userを確認しました: {}", user);

            // 日付と現在時刻の取得
            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            // ユーザー名のバリデーション
            validateUsername(user.getUsername());

            // 勤怠データの作成（UserのIDだけを設定したAttendanceオブジェクトを作成）
            Attendance attendance = createAttendance(user, today, now);

            // エンティティの内容をログに出力
            logger.debug("[DEBUG] 保存前のAttendanceの内容: {}", attendance);

            // 勤怠データの保存
            try {
                logger.debug("[DEBUG] 保存直前のAttendance: {}", attendance);
                attendanceRepository.save(attendance);
                logger.debug("[DEBUG] Attendanceの保存が完了しました: {}", attendance);
            } catch (Exception e) {
                logger.error("[ERROR] Attendance保存中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
                throw e;
            }

        } catch (Exception ex) {
            logger.error("[ERROR] 出勤処理中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("出勤処理中にエラーが発生しました: " + ex.getMessage(), ex);
        } finally {
            ThreadLocalContextHolder.clear();
        }
    }

    private User getUserById(Integer userId) {
        logger.info("[INFO] getUserByIdが呼ばれました。userId: {}", userId);

        try {
            if (userId == null) {
                logger.error("[ERROR] userIdがnullです。");
                throw new IllegalArgumentException("ユーザーIDが指定されていません。");
            }

            logger.debug("[DEBUG] ユーザーIDを使用してデータベースからユーザーを検索します。userId: {}", userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        logger.error("[ERROR] 指定されたユーザーが見つかりません。userId: {}", userId);
                        return new IllegalArgumentException("ユーザーが見つかりません: " + userId);
                    });

            logger.info("[INFO] ユーザーが正常に取得されました。user: {}", user);
            return user;
        } catch (IllegalArgumentException e) {
            logger.error("[ERROR] 入力エラー - {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw e; // 再スローして呼び出し元に伝達
        } catch (Exception ex) {
            logger.error("[ERROR] ユーザー取得中に予期しないエラーが発生しました。userId: {}, error: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", userId, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("ユーザー取得中にエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    private Attendance createAttendance(User user, LocalDate date, LocalDateTime startTime) {
        logger.info("[INFO] createAttendanceが呼ばれました。user: {}, date: {}, startTime: {}", user, date, startTime);

        try {
            if (user == null) {
                logger.error("[ERROR] userがnullです。Attendanceを作成できません。");
                throw new IllegalArgumentException("ユーザーが指定されていません。");
            }

            if (date == null) {
                logger.error("[ERROR] dateがnullです。Attendanceを作成できません。");
                throw new IllegalArgumentException("日付が指定されていません。");
            }

            if (startTime == null) {
                logger.error("[ERROR] startTimeがnullです。Attendanceを作成できません。");
                throw new IllegalArgumentException("開始時間が指定されていません。");
            }
            
            Integer userId = user.getId();

            logger.debug("[DEBUG] Attendanceオブジェクトを初期化します。user: {}, userId: {}, date: {}, startTime: {}", user, userId, date, startTime);

            Attendance attendance = new Attendance();

            // UserオブジェクトのIDだけを設定（循環参照防止のため）
            User userCopy = new User();
            userCopy.setId(userId); // UserのIDだけをセット
            attendance.setUser(userCopy); // AttendanceにUserオブジェクトのIDだけをセット

            attendance.setDate(date);
            attendance.setStartTime(startTime);
            attendance.setCreatedAt(startTime);
            attendance.setUpdatedAt(startTime);

            logger.debug("[DEBUG] Attendanceオブジェクトのセットアップが完了しました。attendance: {}", attendance);
            return attendance;
        } catch (IllegalArgumentException e) {
            logger.error("[ERROR] 入力エラー - {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw e; // 再スローして呼び出し元に伝達
        } catch (Exception ex) {
            logger.error("[ERROR] Attendance作成中に予期しないエラーが発生しました。user: {}, date: {}, startTime: {}, error: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", user, date, startTime, ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("Attendance作成中にエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    //出勤状態の確認メソッド
    public boolean isAttendanceStarted(User user) {
    	logger.info("[INFO] isAttendanceStartedメソッドが呼ばれました。user: {}", user);
        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] ユーザーからIDを取得しました: {}", userId);
            Attendance todayAttendance = attendanceRepository.findByUserIdAndDate(userId, LocalDate.now()).orElse(null);

            if (todayAttendance != null) {
                logger.debug("[DEBUG] 本日の出勤情報が見つかりました。Attendance: {}", todayAttendance);
            } else {
                logger.debug("[DEBUG] 本日の出勤情報は見つかりませんでした。");
            }

            boolean isStarted = todayAttendance != null && todayAttendance.getStartTime() != null;

            logger.info("[INFO] 出勤が開始されているか確認しました。結果: {}", isStarted);
            return isStarted;

        } catch (Exception e) {
            logger.error("[ERROR] isAttendanceStartedメソッドでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return false;
        }
    }
    
    public List<Attendance> findAttendanceByUserAndYearMonth(User user, int year, int month) {
    	logger.info("[INFO] AttendanceService.javaのfindAttendanceByUserAndYearMonth関数が呼ばれました。user: {}, year: {}, month: {}", user, year, month);
        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] findAttendanceByUserAndYearMonth 処理開始 - ユーザーID: {}, 年: {}, 月: {}", userId, year, month);
        	
            // ユーザーがnullまたはIDが無効な場合
            if (user == null || user.getId() == null) {
                String errorMessage = "ユーザー情報が無効です。user: " + user;
                logger.error("[ERROR] " + errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            // 年のバリデーション
            if (year < 1900 || year > LocalDate.now().getYear()) {
                String errorMessage = "無効な年です。年: " + year;
                logger.error("[ERROR] " + errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            // 月のバリデーション
            if (month < 1 || month > 12) {
                String errorMessage = "無効な月です。月: " + month;
                logger.error("[ERROR] " + errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            // 正常に勤怠情報を取得する
            logger.debug("[DEBUG] attendanceRepositoryのfindAttendanceByUserAndYearAndMonthを呼びます。userId: {}, year: {}, month: {}", userId, year, month);
            List<Attendance> attendanceList = attendanceRepository.findAttendanceByUserAndYearAndMonth(userId, year, month);
            logger.debug("[DEBUG] AttendanceService.javaに戻ってきました。findAttendanceByUserAndYearAndMonth 結果: {}", attendanceList);

            return attendanceList;
        } catch (IllegalArgumentException e) {
            // バリデーションエラーのログ
            logger.error("[ERROR] バリデーションエラー: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw e;  // そのままスローして、上層で処理させる
        } catch (DataAccessException ex) {
            // データベース関連のエラーのログ
            logger.error("[ERROR] ユーザーと年月での勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            throw new RuntimeException("ユーザーと年月での勤怠情報の取得に失敗しました: " + ex.getMessage(), ex);
        } catch (Exception exx) {
            // その他の予期しないエラーをキャッチ
            logger.error("[ERROR] findAttendanceByUserAndYearMonthで想定外のエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", exx.toString(), Arrays.toString(exx.getStackTrace()), exx.getCause() != null ? exx.getCause().toString() : "原因不明", exx.getLocalizedMessage());
            throw new RuntimeException("予期しないエラーが発生しました: " + exx.getMessage(), exx);
        }
    }
    
    public List<Integer> getAvailableYears(User user) {
    	logger.info("[INFO] AttendanceService.javaのgetAvailableYearsが呼ばれました。user: {}", user);
        Integer userId = user.getId();
        logger.debug("[DEBUG] ユーザーからIDを取得しました: {}", userId);
        try {
            List<Integer> years = attendanceRepository.findAvailableYearsByUser(userId);
            if (years == null || years.isEmpty()) {
                logger.warn("[WARN] ユーザーID {} の利用可能な年が見つかりませんでした。デフォルト値を設定します。", userId);
                years = List.of(LocalDate.now().getYear()); // 現在の年をデフォルト値にする
            }
            return years;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 利用可能な年の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("利用可能な年の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAvailableYearsで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public int sumOvertimeMinutesByUserAndYearMonth(User user, int year, int month) {
    	logger.info("[INFO] sumOvertimeMinutesByuserAndYearMonthが呼ばれました。User: {}, year: {}, month: {}", user, year, month);
        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] ユーザーからIDを取得しました: {}", userId);
            Integer totalOvertimeMinutes = attendanceRepository.sumOvertimeMinutesByUserAndYearMonth(userId, year, month);
            return (totalOvertimeMinutes != null) ? totalOvertimeMinutes : 0;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 年と月ごとの残業時間の合計に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("年と月ごとの残業時間の合計に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] sumOvertimeMinutesByUserAndYearMonthで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    //勤務時間を計算するメソッド
    public long calculateWorkingTime(Attendance attendance) {
    	logger.info("[INFO] calculateWorkingTimeが呼ばれました。attendance: {}", attendance);
        try {
            if (attendance.getStartTime() != null && attendance.getEndTime() != null) {
                Duration duration = Duration.between(attendance.getStartTime(), attendance.getEndTime());
                return duration.toMinutes(); // 分単位で勤務時間を返す
            }
            return 0; // 勤務時間が計算できない場合は 0 分を返す
        } catch (Exception e) {
            logger.error("[ERROR] 勤務時間の計算中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return 0; // エラーが発生した場合でも 0 分を返す
        }
    }

    // 休憩時間を計算するメソッド
    public long calculateBreakTime(Attendance attendance) {
    	logger.info("[INFO] calculateBreakTimeが呼ばれました。attendance: {}", attendance);
        try {
            if (attendance.getBreakStart() != null && attendance.getBreakEnd() != null) {
                Duration breakDuration = Duration.between(attendance.getBreakStart(), attendance.getBreakEnd());
                return breakDuration.toMinutes(); // 分単位で休憩時間を返す
            }
            return 0; // 休憩時間が計算できない場合は 0 分を返す
        } catch (Exception e) {
            logger.error("[ERROR] 休憩時間の計算中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            return 0; // エラーが発生した場合でも 0 分を返す
        }
    }
    
    public List<Attendance> getAttendanceByUserAndDateRange(String username, String startDate, String endDate) {
    	logger.info("[INFO] getAttendanceByUserAndDateRangeが呼ばれました。Username: {}, startDate: {}, endDate: {}", username, startDate, endDate);
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            return attendanceRepository.findByUserUsernameAndDateBetween(username, start, end)
                .stream()
                .sorted(Comparator.comparing(Attendance::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定された日付範囲の勤怠情報の取得に失敗しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            throw new RuntimeException("指定された日付範囲の勤怠情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] getAttendanceByUserAndDateRangeで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
}
