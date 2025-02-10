package jp.co.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jp.co.example.config.UserFromByteArrayConverter;
import jp.co.example.config.UserToByteArrayConverter;
import jp.co.example.entity.Attendance;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;
import jp.co.example.repository.SalaryRepository;

@Service
@Transactional
public class SalaryService {
	
	private static final Logger logger = LoggerFactory.getLogger(SalaryService.class);

    @Autowired
    private SalaryRepository salaryRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AttendanceService attendanceService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
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
        try {
            logger.info("[INFO] SalaryService の初期化を開始します。");
            initializeConverters();

            // 既にGenericConversionService型として注入されているので、instanceofチェックは不要
            genericConversionService.addConverter(userToByteArrayConverter);
            genericConversionService.addConverter(userFromByteArrayConverter);
            logger.info("[INFO] カスタムコンバーターを登録しました。");

            // コンバーターが正しく登録されたかを確認
            checkConverters();

            // 初期化後にdebugConversionServiceを呼び出す
            debugConversionService();
            debugConversionServices();

        } catch (Exception e) {
            logger.error("[ERROR] SalaryService の初期化中にエラーが発生しました: {}", e.getMessage(), e);
        }
    }
    
    @PostConstruct
    public void debugAvailableConversionServices() {
        try {
            String[] beanNames = applicationContext.getBeanNamesForType(GenericConversionService.class);
            logger.info("[INFO] 利用可能な ConversionService Bean: {}", Arrays.toString(beanNames));

            logger.info("[INFO] ConversionServiceの型: {}", genericConversionService.getClass().getName());

            // instanceOfのチェックは不要
            logger.info("[INFO] GenericConversionServiceのインスタンスが注入されています。");

            // 手動で管理している登録済みコンバーターリストを出力
            if (registeredConverters != null && !registeredConverters.isEmpty()) {
                registeredConverters.forEach(converter ->
                    logger.info("[INFO] 登録済みコンバーター: {}", converter.getClass().getSimpleName())
                );
            } else {
                logger.warn("[WARN] 登録済みコンバーターは見つかりませんでした。");
            }
        } catch (Exception e) {
            logger.error("[ERROR] GenericConversionServiceのデバッグ中にエラーが発生しました: {}", e.getMessage(), e);
        }
    }

	private void initializeConverters() {
		try {
			logger.info("[INFO] SalaryService.javaにてコンバーターの初期化を開始します。");
			// GenericConversionService で登録されたコンバーターを手動で管理
			registeredConverters = Arrays.asList(
				userToByteArrayConverter,
				userFromByteArrayConverter
			);
			logger.info("[INFO] コンバーターが正常に初期化されました。");
		} catch (Exception e) {
			logger.error("[ERROR] SalaryService.javaにてコンバーターの初期化中にエラーが発生しました: {}", e.getMessage(), e);
		}
	}

    public void debugConversionServices() {
        try {
            logger.info("[INFO] SalaryService.javaにてGenericConversionService の Bean 名一覧を取得しています...");
            String[] beanNames = applicationContext.getBeanNamesForType(GenericConversionService.class);
            logger.info("[INFO] GenericConversionService の Bean 名一覧: {}", Arrays.toString(beanNames));
        } catch (Exception e) {
            logger.error("[ERROR] SalaryService.javaにてGenericConversionServiceのBean名一覧取得中にエラーが発生しました: {}", e.getMessage(), e);
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
    
    public Optional<Salary> getSalaryById(Integer id) {
    	logger.info("[INFO] getSalaryByidが呼ばれました。ID: {}", id);
        try {
            return salaryRepository.findById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定されたIDの給与情報が見つかりませんでした。ID: {}, ERROR: {}", id, e.getMessage(), e);
            throw new RuntimeException("指定されたIDの給与情報が見つかりませんでした: " + id, e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public Optional<Salary> getSalaryByUser(User user) {
    	logger.info("[INFO] getSalaryByUserが呼ばれました。User: {}", user);
        try {
            return salaryRepository.findByUser(user);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザーの給与情報の取得に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザーの給与情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public Salary saveSalary(Salary salary) {
    	logger.info("[INFO] saveSalaryが呼ばれました。Salary: {}", salary);
        try {
            BigDecimal basicSalary = salary.getBasicSalary() != null ? salary.getBasicSalary() : BigDecimal.ZERO;
            BigDecimal allowances = salary.getAllowances() != null ? salary.getAllowances() : BigDecimal.ZERO;
            
            salary.setBasicSalary(basicSalary.setScale(0, RoundingMode.DOWN));
            salary.setAllowances(allowances.setScale(0, RoundingMode.DOWN));

            double totalOvertimeHours = salary.getOvertimeHours() + (salary.getOvertimeMinutes() / 60.0);

            BigDecimal overtimePay = calculateOvertimePay(salary, totalOvertimeHours);
            salary.setOvertimePay(overtimePay.setScale(0, RoundingMode.DOWN));

            BigDecimal totalDeductions;

            try {
                totalDeductions = calculateDeductions(salary);
                salary.setDeductions(totalDeductions);
                logger.debug("[DEBUG] 計算された控除額: {}", totalDeductions);
            } catch (Exception e) {
                logger.error("[ERROR] 控除額の計算中にエラーが発生しました: {}", e.getMessage(), e);
                throw new RuntimeException("控除額の計算に失敗しました。", e);
            }

            BigDecimal totalSalary = basicSalary.add(allowances).subtract(totalDeductions).add(overtimePay);
            salary.setTotalSalary(totalSalary.setScale(0, RoundingMode.DOWN));

            if (salary.getPaymentDate() == null) {
                salary.setPaymentDate(LocalDate.now());
            }

            return salaryRepository.save(salary);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 給与情報の保存に失敗しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("給与情報の保存に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}, 詳細: {}", ex.getMessage(), ex.toString(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void deleteSalary(Integer id) {
    	logger.info("[INFO] deleteSalaryが呼ばれました。Id: {}", id);
        try {
            salaryRepository.deleteById(id);
        } catch (DataAccessException e) {
            logger.error("[ERROR] 給与情報の削除に失敗しました: {}", e.getMessage(), e);
            throw new RuntimeException("給与情報の削除に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    // 給与の自動計算 (残業時間を含む)
    public Salary calculateSalary(Salary salary, double overtimeHours) {
    	logger.info("[INFO] SalaryService.javaのcalculateSalaryが呼ばれました。salary: {}, overtimeHours: {}", salary, overtimeHours);
        try {
            // 1時間あたりの基本給を計算（月160時間として計算）
            BigDecimal hourlyRate = salary.getBasicSalary().divide(BigDecimal.valueOf(160), RoundingMode.HALF_UP);

            // 残業代を計算（1.25倍）
            BigDecimal overtimePay = hourlyRate.multiply(BigDecimal.valueOf(1.25)).multiply(BigDecimal.valueOf(overtimeHours));
            salary.setOvertimePay(overtimePay); // 残業代を設定

            // 総支給額を計算
            BigDecimal totalSalary = salary.getBasicSalary()
                    .add(salary.getAllowances())
                    .subtract(salary.getDeductions())
                    .add(overtimePay);

            salary.setTotalSalary(totalSalary.setScale(0, RoundingMode.DOWN));

            return salaryRepository.save(salary);
        } catch (ArithmeticException e) {
            logger.error("[ERROR] 計算中にエラーが発生しました: {}", e.getMessage(), e);
            return null; // 計算エラーが発生した場合は null を返す
        } catch (Exception ex) {
            logger.error("[ERROR] その他のエラーが発生しました: {}", ex.getMessage(), ex);
            return null; // その他のエラーが発生した場合は null を返す
        }
    }

    @Transactional
    public Salary calculateSalaryWithOvertime(User user, YearMonth yearMonth) throws ConversionFailedException, ConverterNotFoundException {
    	logger.debug("[DEBUG] SalaryService.javaのcalculateSalaryWithOvertimeが呼ばれました。user: {}, yearMonth: {}", user, yearMonth);
    	logger.debug("[DEBUG] 注入されたGenericConversionServiceのクラス: {}, 名前: {}", genericConversionService.getClass(), genericConversionService.getClass().getName());
    	
    	if (user == null || yearMonth == null) {
    		logger.error("[ERROR] ユーザーまたは月がnullです。");
            throw new IllegalArgumentException("ユーザーまたは月が null です。");
        }

        //再帰深度の監視（スタックトレースを利用）
        if (Thread.currentThread().getStackTrace().length > 1000) {
            logger.error("[ERROR] 無限再帰の可能性が検出されました。処理を中断します。");
            throw new StackOverflowError("無限再帰の可能性があります。処理を中断しました。");
        }
        
    	logger.debug("[DEBUG] UserToByteArrayConverter と byteArrayToUserConverter が登録されているか確認します...");
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
        
    	try {
        	logger.debug("[DEBUG] 給与計算を開始: ユーザー: {}, 月: {}", user, yearMonth);
        	
        	logger.debug("[DEBUG] genericConversionServiceインスタンス: {}", genericConversionService.getClass().getName());
            logger.debug("[DEBUG] Userクラスからbyte[].classへの変換がサポートされているか: {}", genericConversionService.canConvert(User.class, byte[].class));
            logger.debug("[DEBUG] byte[].classからUserクラスへの変換がサポートされているか: {}", genericConversionService.canConvert(byte[].class, User.class));
            
            logger.debug("[DEBUG] 変換対象のユーザー: {}", user);
            logger.debug("[DEBUG] 変換先のクラス: byte[].class");
            
            byte[] serializedUser = null;
            try {
                serializedUser = genericConversionService.convert(user, byte[].class);
                logger.debug("[DEBUG] ユーザーが byte[] に正常に変換されました: {}", Arrays.toString(serializedUser));
            } catch (Exception e) {
                logger.error("[ERROR] ユーザーの変換に失敗しました: {}", e.getMessage(), e);
                throw e;
            }

            // 変換後に状態を出力
            logger.debug("[DEBUG] serializedUser: {}", Arrays.toString(serializedUser));
            logger.debug("[DEBUG] byte[] から User への変換を試みます...");

            User deserializedUser = null;
            try {
                deserializedUser = genericConversionService.convert(serializedUser, User.class);
                logger.debug("[DEBUG] byte[] から User に正常に変換されました: {}", deserializedUser);
            } catch (Exception e) {
                logger.error("[ERROR] byte[] から User への変換に失敗しました: {}", e.getMessage(), e);
                throw e;
            }
        	
        	logger.debug("[DEBUG] ユーザーのシリアライズとデシリアライズに成功しました。serializedUser: {}, deserializedUser: {}", serializedUser, deserializedUser);
        	
        	logger.debug("[DEBUG] 同クラスのgetSalaryByUserAndPaymentMonthを呼びます。userId: {}, yearMonth: {}", user.getId(), yearMonth);
        	Optional<Salary> salaryOpt = getSalaryByUserAndPaymentMonth(user.getId(), yearMonth);

            if (salaryOpt.isPresent()) {
                logger.info("[INFO] 給与はすでに計算されています。再計算をスキップします。既存の給与: {}", salaryOpt.get());
                return salaryOpt.get();  // 既存の給与情報を返す
            } else {
                logger.warn("[WARN] ユーザー: {} の {}-{} の給与情報が見つかりませんでした。新たに計算を行います。", user.getId(), yearMonth.getYear(), yearMonth.getMonthValue());
            }
            
            Salary salary = salaryOpt.orElseGet(Salary::new);
            salary.setUser(user);
            salary.setPaymentMonth(yearMonth);
                        
            //Attendance から残業時間を再計算
            long totalOvertimeMinutes = 0;
            
            try {
                logger.debug("[DEBUG] attendanceServiceのfindAttendanceByUserAndYearMonthから出勤データを取得します。user: {}, year: {}, month: {}", user.getId(), yearMonth.getYear(), yearMonth.getMonthValue());
                List<Attendance> attendanceList = attendanceService.findAttendanceByUserAndYearMonth(user, yearMonth.getYear(), yearMonth.getMonthValue());

                if (attendanceList == null || attendanceList.isEmpty()) {
                    logger.warn("[WARN] 出勤記録が見つかりません。user: {} in {}-{}", user.getId(), yearMonth.getYear(), yearMonth.getMonthValue());
                } else {
                	logger.debug("[DEBUG] attendanceServiceのfindAttendanceByUserAndYearMonthが完了しcalculateSalaryWithOvertimeに戻ってきました。attendanceList: {}", attendanceList);
                }

                for (Attendance attendance : attendanceList) {
                    try {
                        long actualWorkedMinutes = attendanceService.calculateWorkingTime(attendance) - attendanceService.calculateBreakTime(attendance);
                        long dailyOvertimeMinutes = Math.max(0, actualWorkedMinutes - 480); // 8 hours (480 minutes) overtime
                        totalOvertimeMinutes += dailyOvertimeMinutes;

                        logger.debug("[DEBUG] userId={}の{}日の出勤状況を処理しました: 勤務時間 (分) = {}、残業時間 (分) = {}", user.getId(), attendance.getDate(), actualWorkedMinutes, dailyOvertimeMinutes);
                    } catch (Exception e) {
                        logger.error("[ERROR] {}日の勤怠の残業時間の計算中にエラーが発生しました: {}", attendance.getDate(), e.getMessage(), e);
                    }
                }

                logger.debug("[DEBUG] ユーザー {} の {}-{} の合計残業時間: {} 分", user.getId(), yearMonth.getYear(), yearMonth.getMonthValue(), totalOvertimeMinutes);

            } catch (Exception e) {
                logger.error("[ERROR] ユーザー {} の出席データを取得または処理中にエラーが発生しました: {}-{}: {}", user.getId(), yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage(), e);
            }

            salary.setOvertimeHours((int) totalOvertimeMinutes / 60);
            salary.setOvertimeMinutes((int) totalOvertimeMinutes % 60);
            
            logger.debug("[DEBUG] 総残業時間（分換算）: {}時間{}分", totalOvertimeMinutes / 60, totalOvertimeMinutes % 60);

            // 全ての残業時間をトータルで計算し、最後に丸める
            double totalOvertimeHours = totalOvertimeMinutes / 60.0;
            logger.debug("[DEBUG] 総残業時間（時間換算）: {}時間", totalOvertimeHours);
            
            logger.debug("[DEBUG] 同クラスのcalculateOvertimePayを呼びます。salary: {}, totalOvertimeHours: {}", salary, totalOvertimeHours);
            BigDecimal totalOvertimePay = calculateOvertimePay(salary, totalOvertimeHours); // ここで丸めは行わない
            logger.debug("[DEBUG] calculateOvertimePayが完了しcalculateSalaryWithOvertimeに戻ってきました。総残業代: {}", totalOvertimePay);
            salary.setOvertimePay(totalOvertimePay.setScale(0, RoundingMode.DOWN)); // 最後に切り捨てる

            // その他の計算
            logger.debug("[DEBUG] 同クラスのcalculateDeductionsを呼びます。salary: {}", salary);
            BigDecimal totalDeductions = calculateDeductions(salary);
            logger.debug("[DEBUG] calculateDeductionsが完了し、calculateSalaryWithOvertimeに戻ってきました。総控除を確認します: {}", totalDeductions);
            salary.setDeductions(totalDeductions);

            BigDecimal basicSalary = salary.getBasicSalary() != null ? salary.getBasicSalary() : BigDecimal.ZERO;
            logger.debug("[DEBUG] 基本給を確認します: {}", basicSalary);
            
            BigDecimal allowances = salary.getAllowances() != null ? salary.getAllowances() : BigDecimal.ZERO;
            logger.debug("[DEBUG] 手当を確認します: {}", allowances);
            
            BigDecimal totalSalary = basicSalary.add(allowances).subtract(totalDeductions).add(salary.getOvertimePay());
            logger.debug("[DEBUG] 総給与を確認します: {}", totalSalary);
            salary.setTotalSalary(totalSalary.setScale(0, RoundingMode.DOWN)); // 最終的な総支給額だけ切り捨て

            if (salary.getPaymentDate() == null) {
                salary.setPaymentDate(LocalDate.now());
                logger.debug("[DEBUG] 支払日がnullだったので今日: {}を設定しました。", LocalDate.now());
            }

            salary = salaryRepository.save(salary);
            logger.debug("[DEBUG] 給与計算が完了しました: {}", salary);

            return salary;
    	} catch (ConversionFailedException e) {
    	    logger.error("[ERROR] ユーザーの給与計算でConversionFailedExceptionエラーが発生しました。ユーザーID: {}, 年月: {}, エラーメッセージ: {}, 詳細: {}", user.getId(), yearMonth, e.getMessage(), e.toString(), e);
    	    throw new RuntimeException(String.format("ユーザーID: %d の %s 年月の給与計算中にConversionFailedExceptionエラーが発生しました。エラー詳細: %s", user.getId(), yearMonth, e.getMessage()), e);
    	} catch (ConverterNotFoundException ex) {
    	    logger.error("[ERROR] ユーザーの給与計算でConverterNotFoundExceptionエラーが発生しました。ユーザーID: {}, 年月: {}, エラーメッセージ: {}, 詳細: {}", user.getId(), yearMonth, ex.getMessage(), ex.toString(), ex);
    	    throw new RuntimeException(String.format("ユーザーID: %d の %s 年月の給与計算中にConverterNotFoundExceptionエラーが発生しました。エラー詳細: %s", user.getId(), yearMonth, ex.getMessage()), ex);
    	} catch (Exception exx) {
    	    logger.error("[ERROR] ユーザーの給与計算中に予期しないエラーが発生しました。ユーザーID: {}, 年月: {}, エラーメッセージ: {}, 詳細: {}", user.getId(), yearMonth, exx.getMessage(), exx.toString(), exx);
    	    throw new RuntimeException(String.format("ユーザーID: %d の %s 年月の給与計算中に予期しないエラーが発生しました。エラー詳細: %s", user.getId(), yearMonth, exx.getMessage()), exx);
    	}
    }

    //名前で検索するメソッド
    public List<Salary> getSalariesByUsername(String username) {
    	logger.info("[INFO] getSalariesByUsernameが呼ばれました。username: {}", username);
        try {
            return salaryRepository.findByUserUsernameContaining(username);
        } catch (DataAccessException e) {
            logger.error("[ERROR] ユーザー名で給与情報を取得中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("ユーザー名で給与情報を取得中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public void testQuery() {
        String testMonth = "2025-01";
        logger.debug("[TEST] 検索する paymentMonth: {}", testMonth);
        logger.debug("[TEST] 実行するクエリ: SELECT s FROM Salary s WHERE s.paymentMonth = {}", testMonth);
        List<Salary> salaries = salaryRepository.findSalaryByPaymentMonth(testMonth);
        logger.debug("[TEST] 取得した給与データ: {}", salaries);
    }
    
    @Transactional(readOnly = true)
    public List<Salary> getSalariesByMonth(YearMonth month) {
    	testQuery();
        logger.info("[INFO] SalaryService.javaのgetSalariesByMonthが呼ばれました。Month: {}", month);
        try {
        	logger.debug("[DEBUG] 検索に使用するpaymentMonth: {}", month.toString());
        	logger.debug("[DEBUG] データ取得前のクエリパラメータ: {}", month.toString());
        	logger.debug("[DEBUG] 給与データ取得クエリ実行前");
            List<Salary> salaries = salaryRepository.findSalaryByPaymentMonth(month.toString());
            logger.debug("[DEBUG] Repository から取得したデータ: {}", salaries);
            if (salaries == null || salaries.isEmpty()) {
                logger.warn("[WARN] 給与データが見つかりませんでした。Month: {}", month);
            } else {
                logger.debug("[DEBUG] 給与データが正常に取得されました: {}件", salaries.size());
            }
            return salaries;
        } catch (DataAccessException e) {
            logger.error("[ERROR] 指定された月の給与情報の取得に失敗しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("指定された月の給与情報の取得に失敗しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] 予期しないエラーが発生しました: {}, 詳細: {}", ex.getMessage(), ex.toString(), ex);
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public List<Salary> getSalariesByUsernameAndMonth(String username, YearMonth month) {
    	logger.info("[INFO] getSalariesByUsernameAndMonthが呼ばれました。username: {}, month: {}", username, month);
        try {
            return salaryRepository.findByUserUsernameContainingAndPaymentMonth(username, month.toString());
        } catch (Exception e) {
            logger.error("[ERROR] サラリー情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }
    
    @Cacheable(value = "salaryCache", key = "#userId + '_' + (#month != null ? #month.toString() : 'default') + '_key'")
    public Optional<Salary> getSalaryByUserAndPaymentMonth(Integer userId, YearMonth month) {
        logger.info("[INFO] SalaryService.javaのgetSalaryByUserAndPaymentMonthが呼ばれました。userId: {}, YearMonth: {}", userId, month);

        if (userId == null || month == null) {
            logger.error("[ERROR] ユーザーまたは月がnullです。");
            throw new IllegalArgumentException("ユーザーまたは月がnullです。");
        }
        
        logger.debug("[DEBUG] 支払月: {}", month);
        
        if (!genericConversionService.canConvert(User.class, byte[].class)) {
            logger.error("[ERROR] 必要なコンバータが登録されていません");
            throw new IllegalStateException("コンバータが登録されていません");
        }
        
        String monthString = month.toString();
        logger.debug("[DEBUG] 取得したmonthString: {}, String変換前のmonth: {}", monthString, month);

        try {
        	logger.debug("[DEBUG] リポジトリに渡されるuserId: {}", userId);
        	logger.debug("[DEBUG] リポジトリに渡されるpaymentMonth: {}", monthString);
            Optional<Salary> salary = salaryRepository.findByUserIdAndPaymentMonth(userId, monthString);
            logger.debug("[DEBUG] クエリ結果: {}", salary.orElse(null));

            if (salary.isPresent()) {
                logger.info("[INFO] 給与情報が見つかりました: {}", salary.get());
            } else {
                logger.warn("[WARN] 給与情報が見つかりませんでした。新しいSalaryオブジェクトを作成して返します。月: {}", month);
                return Optional.of(new Salary());
            }

            return salary;
        } catch (DataAccessException e) {
            logger.error("[ERROR] getSalaryByUserAndPaymentMonthにてデータベースアクセス中にエラーが発生しました。エラーメッセージ: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw e;
        } catch (IllegalArgumentException ex) {
            logger.error("[ERROR] getSalaryByUserAndPaymentMonthにて不正な引数が渡されました。userId: {}, 月: {}, エラーメッセージ: {}, 詳細: {}", userId, month, ex.getMessage(), ex.toString(), ex);
            throw ex;
        } catch (ConversionFailedException | ConverterNotFoundException exx) {
            logger.error("[ERROR] getSalaryByUserAndPaymentMonthにてコンバータに失敗しました: {}, 詳細: {}", exx.getMessage(), exx.toString(), exx);
            throw exx;
        } catch (Exception exxx) {
            logger.error("[ERROR] getSalaryByUserAndPaymentMonthにて予期しないエラーが発生しました。エラーメッセージ: {}, 詳細: {}", exxx.getMessage(), exxx.toString(), exxx);
            throw exxx;
        }
    }

    public List<Salary> getAllSalariesSortedById() {
        try {
            return salaryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        } catch (Exception e) {
            logger.error("[ERROR] 給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    public List<Salary> getSalariesByMonthSorted(String paymentMonth) {
        try {
            return salaryRepository.findByPaymentMonthSorted(paymentMonth);
        } catch (Exception e) {
            logger.error("[ERROR] 月別給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    public List<Salary> getSalariesByUsernameAndMonthSorted(String username, String paymentMonth) {
        try {
            return salaryRepository.findByUsernameAndMonthSorted(username, paymentMonth);
        } catch (Exception e) {
            logger.error("[ERROR] ユーザーと月別給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    public BigDecimal calculateOvertimePay(Salary salary, double overtimeHours) {
    	logger.info("[INFO] SalaryService.javaのcalculateOvertimePayが呼ばれました。salary: {}, overtimeHours: {}", salary, overtimeHours);
        if (overtimeHours < 0) {
            logger.warn("[WARN] 負の残業時間: {}", overtimeHours);
            return BigDecimal.ZERO; // 負の残業時間は無効
        }

        try {
            BigDecimal basicSalary = salary.getBasicSalary() != null ? salary.getBasicSalary() : BigDecimal.ZERO;
            if (basicSalary.compareTo(BigDecimal.ZERO) == 0) {
            	logger.debug("[DEBUG] 基本給が０円なので残業代も０円に設定します。");
                return BigDecimal.ZERO; // 基本給が0の場合、残業代も0
            }
            logger.debug("[DEBUG] 基本給を確認します: {}", basicSalary);
            
            BigDecimal hourlyRate = basicSalary.divide(BigDecimal.valueOf(160), 2, RoundingMode.DOWN); 
            BigDecimal overtimeRate = hourlyRate.multiply(BigDecimal.valueOf(1.25));
            BigDecimal overtimePay = overtimeRate.multiply(BigDecimal.valueOf(overtimeHours));

            // 丸めて最終的な値を返す
            return overtimePay.setScale(0, RoundingMode.DOWN);
        } catch (ArithmeticException e) {
            logger.error("[ERROR] 残業代の計算中にエラーが発生しました。: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("残業代の計算中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}, 詳細: {}", ex.getMessage(), ex.toString(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    public BigDecimal calculateDeductions(Salary salary) {
        if (salary == null) {
            logger.error("[ERROR] 給与オブジェクトがnullです");
            throw new IllegalArgumentException("給与オブジェクトはnullにできません。");
        }
        
        logger.info("[INFO] SalaryService.javaのcalculateDeductionsが呼ばれました。Salaryオブジェクトを確認します: {}", salary);

        try {
            // 基本給の取得またはデフォルト値の設定
            BigDecimal basicSalary = salary.getBasicSalary() != null ? salary.getBasicSalary() : BigDecimal.ZERO;
            logger.debug("[DEBUG] 基本給: {}", basicSalary);

            // 各種保険の計算
            BigDecimal employmentInsurance = basicSalary.multiply(BigDecimal.valueOf(0.003));
            logger.debug("[DEBUG] 雇用保険計算結果: {}", employmentInsurance);

            BigDecimal healthInsurance = basicSalary.multiply(BigDecimal.valueOf(0.05));
            logger.debug("[DEBUG] 健康保険計算結果: {}", healthInsurance);

            BigDecimal pension = basicSalary.multiply(BigDecimal.valueOf(0.09));
            logger.debug("[DEBUG] 年金計算結果: {}", pension);

            // 四捨五入して切り捨て
            employmentInsurance = employmentInsurance.setScale(0, RoundingMode.DOWN);
            healthInsurance = healthInsurance.setScale(0, RoundingMode.DOWN);
            pension = pension.setScale(0, RoundingMode.DOWN);

            logger.debug("[DEBUG] 切り捨て後の雇用保険: {}", employmentInsurance);
            logger.debug("[DEBUG] 切り捨て後の健康保険: {}", healthInsurance);
            logger.debug("[DEBUG] 切り捨て後の年金: {}", pension);

            // 控除額の合計を計算
            BigDecimal totalDeductions = employmentInsurance.add(healthInsurance).add(pension);
            logger.debug("[DEBUG] 総控除額 (切り捨て前): {}", totalDeductions);

            totalDeductions = totalDeductions.setScale(0, RoundingMode.DOWN);
            logger.debug("[DEBUG] 総控除額 (切り捨て後): {}", totalDeductions);

            // Salaryオブジェクトへの値の設定
            salary.setEmploymentInsurance(employmentInsurance);
            salary.setHealthInsurance(healthInsurance);
            salary.setPension(pension);
            salary.setDeductions(totalDeductions);

            logger.info("[INFO] Salaryオブジェクトに設定完了。totalDeductionsを返します: {}", salary);

            return totalDeductions;
        } catch (ArithmeticException e) {
            logger.error("[ERROR] 控除額の計算中にエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("控除額の計算中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] 予期しないエラーが発生しました: {}, 詳細: {}", ex.getMessage(), ex.toString(), ex);
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    public void copySalaryForNextMonth() {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            YearMonth currentMonth = YearMonth.now();
            YearMonth nextMonth = currentMonth.plusMonths(1);
            String currentMonthStr = currentMonth.format(formatter);
            String nextMonthStr = nextMonth.format(formatter);

            List<User> users = userService.getAllUsers();

            for (User user : users) {
                logger.info("[INFO] ユーザーの給与をコピーします: {}", user.getUsername());

                Optional<Salary> currentSalaryOpt = salaryRepository.findByUserIdAndPaymentMonth(user.getId(), currentMonthStr);
                Optional<Salary> nextMonthSalaryOpt = salaryRepository.findByUserIdAndPaymentMonth(user.getId(), nextMonthStr);

                logger.info("[INFO] 現在の給与データ: {}", currentSalaryOpt.isPresent() ? currentSalaryOpt.get() : "No data");
                logger.info("[INFO] 翌月の給与データ: {}", nextMonthSalaryOpt.isPresent() ? nextMonthSalaryOpt.get() : "No data");

                if (currentSalaryOpt.isPresent()) {
                    Salary currentSalary = currentSalaryOpt.get();
                    Salary salaryToSave = nextMonthSalaryOpt.orElse(new Salary());

                    salaryToSave.setUser(user);
                    salaryToSave.setPaymentMonth(nextMonth);
                    salaryToSave.setBasicSalary(currentSalary.getBasicSalary());
                    salaryToSave.setAllowances(currentSalary.getAllowances());
                    salaryToSave.setOvertimeHours(0);
                    salaryToSave.setOvertimeMinutes(0);

                    BigDecimal totalSalary = currentSalary.getBasicSalary()
                            .add(currentSalary.getAllowances())
                            .subtract(currentSalary.getDeductions())
                            .add(BigDecimal.ZERO);
                    salaryToSave.setTotalSalary(totalSalary);

                    BigDecimal totalDeductions = calculateDeductions(salaryToSave);
                    salaryToSave.setDeductions(totalDeductions);

                    salaryToSave.setPaymentDate(LocalDate.now());

                    salaryRepository.save(salaryToSave);
                }
            }
        } catch (DataAccessException e) {
            logger.error("[ERROR] 次月の給与データのコピー中にエラーが発生しました: {}", e.getMessage(), e);
            throw new RuntimeException("次月の給与データのコピー中にエラーが発生しました: " + e.getMessage(), e);
        } catch (Exception ex) {
            logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
            throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 0 L * ?")
    public void processMonthlyPayroll() {
        try {
            logger.info("[INFO] SalaryService.javaのprocessMonthlyPayrollが呼ばれました。");
            YearMonth currentMonth = YearMonth.now();
            List<User> users = userService.getAllUsers();

            for (User user : users) {
                calculateSalaryWithOvertime(user, currentMonth);
            }
        } catch (Exception e) {
            logger.error("[ERROR] 月次給与処理中にエラーが発生しました: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void reflectOvertime() {
        YearMonth currentMonth = YearMonth.now();
        logger.info("[INFO] SalaryService.javaのreflectOvertime メソッドを開始しました。現在の月: {}", currentMonth);

        try {
            List<User> users = userService.getAllUsers();
            logger.debug("[DEBUG] 取得したユーザー数: {}", users.size());

            for (User currentUser : users) { // 修正: ループ内変数名を変更
                try {
                    logger.debug("[DEBUG] ユーザー {} の残業時間計算を開始します。", currentUser.getId());

                    logger.debug("[DEBUG] 同クラスのcalculateSalaryWithOvertimeを使用します。currentUser: {}, currentMonth: {}", currentUser, currentMonth);
                    calculateSalaryWithOvertime(currentUser, currentMonth);
                    logger.debug("[DEBUG] 同クラスのcalculateSalaryWithOvertimeが完了しreflectOvertimeに戻ってきました。ユーザー {} の残業時間が正常に反映されました。", currentUser.getId());
                } catch (Exception e) {
                    logger.error("[ERROR] reflectOvertime関数にてユーザー {} の残業時間計算中にエラーが発生しました: {}", currentUser.getId(), e.getMessage(), e);
                    // 必要に応じて例外を再スローまたはスキップする
                }
            }

            logger.info("[INFO] 全ユーザーの残業時間が正常に反映されました。");
        } catch (Exception e) {
            logger.error("[ERROR] reflectOvertimeメソッドでエラーが発生しました: {}, 詳細: {}", e.getMessage(), e.toString(), e);
            throw new RuntimeException("reflectOvertimeメソッドで残業時間の反映処理中にエラーが発生しました: " + e.getMessage(), e);
        }
    }
    
    //ユーザー名と支払い月に基づき、給与情報を取得し、ユーザー名でソート
    public List<Salary> getSalariesByUsernameAndMonthSortedByUsername(String username, YearMonth month) {
        try {
            return salaryRepository.findByUserUsernameContainingAndPaymentMonthOrderByUserUsernameAsc(username, month.toString());
        } catch (Exception e) {
            logger.error("[ERROR] ユーザー名と支払い月に基づく給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    // 支払い月に基づき、給与情報を取得し、ユーザー名でソート
    public List<Salary> getSalariesByMonthSortedByUsername(YearMonth month) {
        try {
            return salaryRepository.findByPaymentMonthOrderByUserUsernameAsc(month.toString());
        } catch (Exception e) {
            logger.error("[ERROR] 支払い月に基づく給与情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    // 指定した月の給与レポートを取得するメソッド
    public List<Salary> getSalaryReportByMonth(int year, int month) {
        try {
            return salaryRepository.findSalariesByYearAndMonth(year, month);
        } catch (Exception e) {
            logger.error("[ERROR] 指定した月の給与レポートの取得中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    // 給与レポートを生成するメソッド
    public List<Salary> generatePayrollReport(String startDate, String endDate, String employeeName) {
        try {
            if (employeeName == null || employeeName.isEmpty()) {
                // 社員名が指定されていない場合、全社員のデータを返す
                return salaryRepository.findByPaymentDateBetween(LocalDate.parse(startDate), LocalDate.parse(endDate));
            } else {
                // 社員名が指定されている場合、その社員のデータを返す
                return salaryRepository.findByPaymentDateBetweenAndUserUsername(LocalDate.parse(startDate), LocalDate.parse(endDate), employeeName);
            }
        } catch (Exception e) {
            logger.error("[ERROR] 給与レポートの生成中にエラーが発生しました: {}", e.getMessage(), e);
            return Collections.emptyList(); // エラーが発生した場合、空のリストを返す
        }
    }

    public List<Salary> generatePayrollReportByEmployee(String startDate, String endDate, String employeeName) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);

            return salaryRepository.findByPaymentDateBetweenAndUserUsername(start, end, employeeName);
        } catch (DateTimeParseException e) {
            logger.error("[ERROR] 日付の形式が不正です: {}", e.getMessage(), e);
            throw new IllegalArgumentException("日付の形式が不正です: " + startDate + " または " + endDate, e);
        } catch (Exception ex) {
        	logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        	throw new RuntimeException("予期しないエラーが発生しました: " + ex.getMessage(), ex);
        }
    }
    
    public double calculateOvertimeHours(int totalOvertimeMinutes) {
    	logger.info("[INFO] calculateOvertimeHoursが呼ばれました。totalOvertimeMinutes: {}", totalOvertimeMinutes);
        try {
            // 残業時間を計算し、小数点第2位までで丸める
            return Math.round(totalOvertimeMinutes / 60.0 * 100.0) / 100.0;
        } catch (Exception e) {
            logger.error("[ERROR] 残業時間の計算中にエラーが発生しました: {}", e.getMessage(), e);
            return 0.0; // エラーが発生した場合、0.0 を返す
        }
    }
    
    public Salary calculateTotalSalaryWithOvertime(Salary salary, List<Attendance> attendanceList) {
    	logger.info("[INFO] calculateTotalSalaryWithOvertimeが呼ばれました。Salary: {}, attendanceList: {}", salary, attendanceList);
        try {
            // 基本給を取得、nullの場合は0を設定
            BigDecimal basicSalary = salary.getBasicSalary() != null ? salary.getBasicSalary() : BigDecimal.ZERO;

            // 残業時間の合計を計算
            long totalOvertimeMinutes = 0;
            for (Attendance attendance : attendanceList) {
                // 勤務時間から休憩時間を引いた実働時間を計算
                long actualWorkedMinutes = attendanceService.calculateWorkingTime(attendance) - attendanceService.calculateBreakTime(attendance);
                // 480分（8時間）を超える分が残業時間
                long dailyOvertimeMinutes = Math.max(0, actualWorkedMinutes - 480);
                totalOvertimeMinutes += dailyOvertimeMinutes;
            }

            // 残業時間（時間と分）を給与情報に設定
            salary.setOvertimeHours((int) totalOvertimeMinutes / 60);
            salary.setOvertimeMinutes((int) totalOvertimeMinutes % 60);

            // 残業代を計算
            BigDecimal hourlyRate = basicSalary.divide(BigDecimal.valueOf(160), 2, RoundingMode.DOWN); // 月160時間として計算
            BigDecimal overtimeRate = hourlyRate.multiply(BigDecimal.valueOf(1.25)); // 残業代は1.25倍
            BigDecimal overtimePay = overtimeRate.multiply(BigDecimal.valueOf(totalOvertimeMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.DOWN));

            // 残業代を切り捨てて設定
            salary.setOvertimePay(overtimePay.setScale(0, RoundingMode.DOWN));

            // 控除額を計算
            BigDecimal employmentInsurance = basicSalary.multiply(BigDecimal.valueOf(0.003)).setScale(0, RoundingMode.DOWN);
            BigDecimal healthInsurance = basicSalary.multiply(BigDecimal.valueOf(0.05)).setScale(0, RoundingMode.DOWN);
            BigDecimal pension = basicSalary.multiply(BigDecimal.valueOf(0.09)).setScale(0, RoundingMode.DOWN);
            BigDecimal totalDeductions = employmentInsurance.add(healthInsurance).add(pension);

            salary.setDeductions(totalDeductions);

            // 総支給額を計算（基本給 + 手当 - 控除 + 残業代）
            BigDecimal allowances = salary.getAllowances() != null ? salary.getAllowances() : BigDecimal.ZERO;
            BigDecimal totalSalary = basicSalary.add(allowances).subtract(totalDeductions).add(overtimePay);

            // 総支給額を切り捨てて設定
            salary.setTotalSalary(totalSalary.setScale(0, RoundingMode.DOWN));

            return salary; // 計算された給与情報を返す

        } catch (Exception e) {
            logger.error("[ERROR] 給与計算中にエラーが発生しました: {}", e.getMessage(), e);
            // エラーが発生した場合は null を返すか、またはエラーが発生した旨を示す値を返す
            return null; // 適切なエラー処理を行う
        }
    }
}
