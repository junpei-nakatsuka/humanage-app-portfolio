package jp.co.example.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jp.co.example.dto.FormattedAttendance;
import jp.co.example.entity.Attendance;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;
import jp.co.example.service.AttendanceService;
import jp.co.example.service.SalaryService;
import jp.co.example.service.UserService;

@Controller
public class AttendanceController {
	
	private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);

    @Autowired
    private AttendanceService attendanceService;
    
    @Autowired
    private SalaryService salaryService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private HttpSession session;
    
    @Autowired
    @Qualifier("genericConversionService")
    private GenericConversionService genericConversionService;
    
    @GetMapping("/attendanceManagement")
    public String showAttendanceManagement(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month, Model model) {
    	logger.info("[INFO] AttendanceController.javaのshowAttendanceManagementが呼ばれました。year: {}, month: {}, model: {}", year, month, model);
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                logger.error("[ERROR] ユーザーのセッションがnullのためログイン画面にリダイレクトします。");
                model.addAttribute("errorMessage", "セッションが無効です。ログインしてください。");
                return "redirect:/login";
            }
            logger.debug("[DEBUG] セッションから取得したユーザー情報: {}", user);
            
            //`userList` を取得して `model` に追加する
            List<User> userList = userService.getAllUsers();
            model.addAttribute("userList", userList);
            
            logger.debug("[DEBUG] AttendanceService.javaのfindAttendanceByUserAndDateを呼びます。user: {}, date: {}", user, LocalDate.now());
            Attendance todayAttendance = attendanceService.findAttendanceByUserAndDate(user, LocalDate.now());
            logger.debug("[DEBUG] AttendanceService.javaのfindAttendanceByUserAndDateが完了しAttendanceController.javaのshowAttendanceManagementに戻ってきました。獲得したtodayAttendance: {}", todayAttendance);
            
            logger.debug("[DEBUG] === 出勤データ取得 ===");
            logger.debug("[DEBUG] ユーザーID: {}", user.getId());
            if (todayAttendance == null) {
            	logger.info("[INFO] 本日の出勤データなし");
            } else {
            	logger.info("[INFO] 出勤開始: {}", todayAttendance.getStartTime());
            	logger.info("[INFO] 休憩開始: {}", todayAttendance.getBreakStart());
            	logger.info("[INFO] 休憩終了: {}", todayAttendance.getBreakEnd());
            	logger.info("[INFO] 退勤時間: {}", todayAttendance.getEndTime());
            }
            
            boolean isWorking = todayAttendance != null && todayAttendance.getStartTime() != null && todayAttendance.getEndTime() == null;
            boolean isOnBreak = todayAttendance != null && todayAttendance.getBreakStart() != null && todayAttendance.getBreakEnd() == null;
            boolean isBreakFinished = todayAttendance != null && todayAttendance.getBreakStart() != null && todayAttendance.getBreakEnd() != null;
            
            logger.debug("[DEBUG] isWorking: {}", isWorking);
            logger.debug("[DEBUG] sessionScope.isOnBreak before update: {}", session.getAttribute("isOnBreak"));
            logger.debug("[DEBUG] isOnBreak: {}", isOnBreak);
            logger.debug("[DEBUG] isBreakFinished: {}", isBreakFinished);
            
            session.setAttribute("isWorking", isWorking);
            session.setAttribute("isOnBreak", isOnBreak);
            session.setAttribute("isBreakFinished", isBreakFinished);
            
            logger.debug("[DEBUG] sessionScope.isOnBreak after update: {}", session.getAttribute("isOnBreak"));
            
            boolean hasUnfinishedWork = attendanceService.hasUnfinishedWork(user);
            session.setAttribute("hasUnfinishedWork", hasUnfinishedWork);
            model.addAttribute("hasUnfinishedWork", hasUnfinishedWork);

            logger.debug("[DEBUG] isWorkingを確認します: {}", isWorking);
            model.addAttribute("isWorking", isWorking); 
            
            logger.debug("[DEBUG] isOnBreakを確認します: {}", isOnBreak);
            model.addAttribute("isOnBreak", isOnBreak);
            
            logger.debug("[DEBUG] isBreakFinishedを確認します: {}", isBreakFinished);
            model.addAttribute("isBreakFinished", isBreakFinished);
            
            LocalDate currentDate = LocalDate.now();
            logger.debug("[DEBUG] カレントデートを確認します。currentDate: {}", currentDate);
            
            int selectedYear = (year == null) ? currentDate.getYear() : year;
            logger.debug("[DEBUG] セレクトされた年を確認します。selectedYear: {}", selectedYear);
            
            int selectedMonth = (month == null) ? currentDate.getMonthValue() : month;
            logger.debug("[DEBUG] セレクトされた月を確認します。selectedMonth: {}", selectedMonth);
            
            logger.debug("[DEBUG] AttendanceService.javaのfindAttendanceByUserAndYearMonthを呼びます。user: {}, selectedYear: {}, selectedMonth: {}", user, selectedYear, selectedMonth);
            List<Attendance> attendanceList = attendanceService.findAttendanceByUserAndYearMonth(user, selectedYear, selectedMonth);
            logger.debug("[DEBUG] AttendanceService.javaのfindAttendanceByUserAndYearMonthが完了し、AttendanceController.javaのshowAttendanceManagementに戻ってきました。取得したattendanceList: {}", attendanceList);
            
            attendanceList = attendanceList.stream().sorted((a1, a2) -> a2.getDate().compareTo(a1.getDate())).collect(Collectors.toList());
            
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            List<FormattedAttendance> formattedAttendanceList = attendanceList.stream()
                .map(attendance -> new FormattedAttendance(
                    attendance.getDate().format(dateFormatter),
                    attendance.getStartTime() != null ? attendance.getStartTime().format(timeFormatter) : "未設定",
                    attendance.getEndTime() != null ? attendance.getEndTime().format(timeFormatter) : "未設定",
                    calculateWorkingTime(attendance),
                    attendance.getBreakStart() != null ? attendance.getBreakStart().format(timeFormatter) : "未設定",
                    attendance.getBreakEnd() != null ? attendance.getBreakEnd().format(timeFormatter) : "未設定",
                    calculateBreakTime(attendance)
                ))
                .collect(Collectors.toList());
            
            logger.debug("[DEBUG] formattedAttendanceListを確認します: {}", formattedAttendanceList);
            model.addAttribute("formattedAttendanceList", formattedAttendanceList);
            
            logger.debug("[DEBUG] selectedYearを確認します: {}", selectedYear);
            model.addAttribute("selectedYear", selectedYear);
            
            logger.debug("[DEBUG] selectedMonthを確認します: {}", selectedMonth);
            model.addAttribute("selectedMonth", selectedMonth);

            List<Integer> availableYears = attendanceService.getAvailableYears(user);
            List<Integer> availableMonths = attendanceService.getAvailableMonths(user);
            
            logger.debug("[DEBUG] availableYearsを確認します: {}", availableYears);
            model.addAttribute("availableYears", availableYears);
            
            logger.debug("[DEBUG] availableMonthsを確認します: {}", availableMonths);
            model.addAttribute("availableMonths", availableMonths);
            
            return "attendance_management";
        } catch (Exception e) {
            logger.error("[ERROR] showAttendanceManagementでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            model.addAttribute("errorMessage", "勤怠管理情報の取得中にエラーが発生しました。");
            return "error_page";
        }
    }
    
    @PostMapping("/attendance/forceEnd")
    public String forceEndAttendance(
        @RequestParam("userId") Integer userId,
        @RequestParam("forceEndDate") String forceEndDateStr,
        @RequestParam("forceEndTime") String forceEndTimeStr,
        Model model
    ) {
        try {
        	logger.info("[INFO] forceEndAttendanceが呼ばれました。userId: {}, forceEndDateStr: {}, forceEndTimeStr: {}", userId, forceEndDateStr, forceEndTimeStr);
            User adminUser = (User) session.getAttribute("user");
            logger.debug("[DEBUG] 取得したadminUser: {}", adminUser);
            
            if (adminUser == null || !"人事管理者".equals(adminUser.getRole())) {
            	logger.error("[ERROR] 権限がありません。");
                model.addAttribute("errorMessage", "権限がありません。");
                return "redirect:/attendanceManagement";
            }

            User targetUser = userService.getUserById(userId);
            logger.debug("[DEBUG] 取得したtargetUser: {}", targetUser);
            if (targetUser == null) {
            	logger.error("[ERROR] 指定されたユーザーが存在しません。");
                model.addAttribute("errorMessage", "指定されたユーザーが存在しません。");
                return "redirect:/attendanceManagement";
            }

            LocalDate forceEndDate = LocalDate.parse(forceEndDateStr);
            LocalTime forceEndTime = LocalTime.parse(forceEndTimeStr);
            logger.debug("[DEBUG] 取得したforceEndDate: {}, forceEndTime: {}", forceEndDate, forceEndTime);
            
            logger.debug("[DEBUG] attendanceServiceのfindUnfinishedAttendanceByUserAndDateを呼びます。targetUser: {}, forceEndDate: {}", targetUser, forceEndDate);
            Attendance attendance = attendanceService.findUnfinishedAttendanceByUserAndDate(targetUser, forceEndDate);
            if (attendance == null) {
            	logger.error("[ERROR] 未退勤のデータが見つかりません。");
                model.addAttribute("errorMessage", "未退勤のデータが見つかりません。");
                return "redirect:/attendanceManagement";
            }
            
            logger.debug("[DEBUG] 取得したattendance: {}", attendance);

            attendance.setEndTime(LocalDateTime.of(forceEndDate, forceEndTime));
            
            //休憩の未終了チェック
            if (attendance.getBreakStart() != null && attendance.getBreakEnd() == null) {
                LocalDateTime breakEndTime = attendance.getBreakStart().plusHours(1);
                attendance.setBreakEnd(breakEndTime);
                logger.debug("[DEBUG] 休憩終了時間がセットされました: {}", breakEndTime);
            }
            
            //労働時間・休憩時間・残業時間を計算
            long totalMinutesWorked = calculateWorkingTime(attendance); 
            long breakMinutes = calculateBreakTime(attendance);         
            long actualWorkedMinutes = totalMinutesWorked - breakMinutes;    
            long overtimeMinutes = Math.max(0, actualWorkedMinutes - (8 * 60));
            attendance.setOvertimeMinutes((int) overtimeMinutes);
            
            logger.debug("[DEBUG] attendanceServiceのupdateAttendanceを呼びます。attendance: {}", attendance);
            attendanceService.updateAttendance(attendance);
            
            //給与計算（オプション）
            YearMonth yearMonth = YearMonth.from(forceEndDate);
            salaryService.calculateSalaryWithOvertime(targetUser, yearMonth);

            model.addAttribute("successMessage", "強制退勤が完了し、残業時間が反映されました。");
            return "redirect:/attendanceManagement";
        } catch (Exception e) {
        	logger.error("[ERROR] 強制退勤処理中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            model.addAttribute("errorMessage", "強制退勤処理中にエラーが発生しました。");
            return "redirect:/attendanceManagement";
        }
    }

    @PostMapping("/attendance/start")
    public String startAttendance(RedirectAttributes redirectAttributes) {
    	logger.info("[INFO] AttendanceService.javaのstartAttendanceが呼ばれました。redirectAttributes: {}", redirectAttributes);
        User user = (User) session.getAttribute("user");
        if (user == null) {
            logger.error("[ERROR] セッションからユーザー情報を取得できませんでした。");
            redirectAttributes.addFlashAttribute("error", "セッションの有効期限が切れています。再度ログインしてください。");
            return "redirect:/login";
        }

        try {
        	Integer userId = user.getId();
        	logger.debug("[DEBUG] 取得したuserId: {}", userId);
        	logger.debug("[DEBUG] SalaryService.javaのgetSalaryByUserAndPaymentMonthを呼びます。user: {}, month: {}", userId, YearMonth.now());
        	Optional<Salary> salaryOpt = salaryService.getSalaryByUserAndPaymentMonth(userId, YearMonth.now());
        	if (salaryOpt.isEmpty() || salaryOpt.get().getBasicSalary() == null) {
        		logger.warn("[WARN] 給与情報が見つかりませんでした。user: {], month: {}", userId, YearMonth.now());
        		redirectAttributes.addFlashAttribute("error", "基本給情報が設定されていないため、出勤できません。");
        		return "redirect:/attendanceManagement";
        	}
        	logger.debug("[DEBUG] SalaryService.javaのgetSalaryByUserAndPaymentMonthが完了し、AttendanceController.javaのstartAttendanceに戻ってきました。取得したOptional<Salary>salaryOpt: {}", salaryOpt);
        	
        	BigDecimal basicSalary = salaryOpt.get().getBasicSalary();
            if (basicSalary == null) {
            	logger.warn("[WARN] 基本給情報が設定されていないため、出勤できません。");
                redirectAttributes.addFlashAttribute("error", "基本給情報が設定されていないため、出勤できません。");
                return "redirect:/attendanceManagement";
            }
            logger.debug("[DEBUG] userId={}の基本給を取得しました。値: {}", userId, basicSalary);
        	
            boolean attendanceAlreadyStarted = attendanceService.isAttendanceStarted(user);
            if (attendanceAlreadyStarted) {
                logger.warn("[WARN] 既に出勤しています。");
                redirectAttributes.addFlashAttribute("error", "既に出勤しています。");
                return "redirect:/attendanceManagement";
            }
            
            logger.debug("[DEBUG] AttendanceService.javaのstartAttendanceを呼びます。user: {}, userId: {}", user, userId);
            attendanceService.startAttendance(userId);
        } catch (IllegalStateException e) {
        	logger.error("[ERROR] startAttendanceでIllegalStateExceptionが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/attendanceManagement";
        } catch (Exception ex) {
            logger.error("[ERROR] startAttendanceで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", "出勤処理中にエラーが発生しました。");
            return "redirect:/attendanceManagement";
        }

        return "redirect:/attendanceManagement";
    }

    @PostMapping("/attendance/end")
    public String endAttendance(RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        
        logger.info("[INFO] AttendanceService.javaのendAttendaceが呼ばれました。redirectAttributes: {}, user: {}", redirectAttributes, user);
        
        try {
            Attendance todayAttendance = attendanceService.findAttendanceByUserAndDate(user, LocalDate.now());
            
            if (todayAttendance == null || todayAttendance.getStartTime() == null) {
            	logger.error("[ERROR] まだ出勤していません");
                redirectAttributes.addFlashAttribute("error", "まだ出勤していません");
                return "redirect:/attendanceManagement";
            }
            
            if (todayAttendance.getEndTime() != null) {
            	logger.error("[ERROR] 既に退勤しています");
                redirectAttributes.addFlashAttribute("error", "既に退勤しています");
                return "redirect:/attendanceManagement";
            }
            
            todayAttendance.setEndTime(LocalDateTime.now());
            
            long totalMinutesWorked = calculateWorkingTime(todayAttendance); 
            long breakMinutes = calculateBreakTime(todayAttendance);         
            long actualWorkedMinutes = totalMinutesWorked - breakMinutes;    
            long overtimeMinutes = Math.max(0, actualWorkedMinutes - (8 * 60));
            todayAttendance.setOvertimeMinutes((int) overtimeMinutes);

            attendanceService.saveAttendance(todayAttendance);

            YearMonth yearMonth = YearMonth.now(); 
            
            logger.debug("[DEBUG] calculateSalaryWithOvertimeを呼びます。user: {}, yearMonth: {}", user, yearMonth);
            salaryService.calculateSalaryWithOvertime(user, yearMonth);
            
            session.setAttribute("isWorking", false);
            session.setAttribute("isOnBreak", false);

            redirectAttributes.addFlashAttribute("success", "退勤が完了し、残業時間が反映されました");
        } catch (DataIntegrityViolationException e) {
            logger.error("[ERROR] 出席終了時のデータ整合性違反: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", "データ整合性エラーが発生しました。");
            return "redirect:/attendanceManagement";
        } catch (Exception ex) {
            logger.error("[ERROR] endAttendanceで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", ex.toString(), Arrays.toString(ex.getStackTrace()), ex.getCause() != null ? ex.getCause().toString() : "原因不明", ex.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", "退勤処理中にエラーが発生しました。");
            return "redirect:/attendanceManagement";
        }

        return "redirect:/attendanceManagement";
    }
    
    @PostMapping("/attendance/breakStart")
    public String startBreak(RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        
        logger.info("[INFO] startBreakが呼ばれました。redirectAttributes: {}, user: {}", redirectAttributes, user);

        try {
            Attendance attendance = attendanceService.findAttendanceByUserAndDate(user, LocalDate.now());
            
            if (attendance == null || attendance.getStartTime() == null) {
            	logger.error("[ERROR] 休憩を開始できません");
                redirectAttributes.addFlashAttribute("error", "休憩を開始できません");
                return "redirect:/attendanceManagement";
            }
            
            if (attendance.getBreakStart() != null && attendance.getBreakEnd() == null) {
            	logger.error("[ERROR] 既に休憩を開始しています");
                redirectAttributes.addFlashAttribute("error", "既に休憩を開始しています");
                return "redirect:/attendanceManagement";
            }
            
            attendance.setBreakStart(LocalDateTime.now());
            attendanceService.saveAttendance(attendance);
            logger.debug("[DEBUG] 休憩開始時間が保存されました: {}", attendance.getBreakStart());
            
            //session.setAttribute("isOnBreak", true);
            Attendance updatedAttendance = attendanceService.findAttendanceByUserAndDate(user, LocalDate.now());
            logger.debug("[DEBUG] 取得したupdatedAttendance: {}", updatedAttendance);
            session.setAttribute("isOnBreak", updatedAttendance.getBreakStart() != null && updatedAttendance.getBreakEnd() == null);
            logger.debug("[DEBUG] セッションセットしたupdatedAttendance: {}", updatedAttendance);

            logger.debug("セッションのisOnBreak値を更新: {}", session.getAttribute("isOnBreak"));
            
            logger.debug("[DEBUG] === 休憩開始処理 ===");
            logger.debug("[DEBUG] ユーザーID: {}", user.getId());
            logger.debug("[DEBUG] 休憩開始時間: {}", attendance.getBreakStart());
        } catch (Exception e) {
            logger.error("[ERROR] startBreakで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", "休憩開始中にエラーが発生しました。");
            return "redirect:/attendanceManagement";
        }

        return "redirect:/attendanceManagement";
    }

    @PostMapping("/attendance/breakEnd")
    public String endBreak(RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        
        logger.info("[INFO] endBreakが呼ばれました。redirectAttributes: {}, user: {}", redirectAttributes, user);

        try {
            Attendance attendance = attendanceService.findAttendanceByUserAndDate(user, LocalDate.now());
            
            if (attendance == null || attendance.getStartTime() == null) {
            	logger.error("[ERROR] 休憩を終了できません");
                redirectAttributes.addFlashAttribute("error", "休憩を終了できません");
                return "redirect:/attendanceManagement";
            }
            
            if (attendance.getBreakStart() == null) {
            	logger.error("[ERROR] まだ休憩を開始していません");
                redirectAttributes.addFlashAttribute("error", "まだ休憩を開始していません");
                return "redirect:/attendanceManagement";
            }
            
            if (attendance.getBreakEnd() != null) {
            	logger.error("[ERROR] 既に休憩を終了しています");
                redirectAttributes.addFlashAttribute("error", "既に休憩を終了しています");
                return "redirect:/attendanceManagement";
            }
            
            attendance.setBreakEnd(LocalDateTime.now());
            attendanceService.saveAttendance(attendance);
            logger.debug("[DEBUG] 休憩終了時間が保存されました: {}", attendance.getBreakEnd());
            
            session.setAttribute("isOnBreak", false);
            session.setAttribute("isBreakFinished", true);
            
            logger.debug("[DEBUG] === 休憩終了処理 ===");
            logger.debug("[DEBUG] ユーザーID: {}", user.getId());
            logger.debug("[DEBUG] 休憩開始時間: {}", attendance.getBreakStart());
            logger.debug("[DEBUG] 休憩終了時間: {}", attendance.getBreakEnd());
            logger.debug("[DEBUG] isOnBreak (session): {}", session.getAttribute("isOnBreak"));
            logger.debug("[DEBUG] isBreakFinished (session): {}", session.getAttribute("isBreakFinished"));
        } catch (Exception e) {
            logger.error("[ERROR] endBreakで予期しないエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            redirectAttributes.addFlashAttribute("error", "休憩終了中にエラーが発生しました。");
            return "redirect:/attendanceManagement";
        }

        return "redirect:/attendanceManagement";
    }
    
    private long calculateBreakTime(Attendance attendance) {
        try {
            if (attendance.getBreakStart() != null && attendance.getBreakEnd() != null) {
                Duration breakDuration = Duration.between(attendance.getBreakStart(), attendance.getBreakEnd());
                return breakDuration.toMinutes();
            }
        } catch (Exception e) {
            logger.error("[ERROR] calculateBreakTime メソッドでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            // 必要に応じて 0 ではなく特定のエラーハンドリングを行う
        }
        return 0;
    }

    private long calculateWorkingTime(Attendance attendance) {
        try {
            if (attendance.getStartTime() != null && attendance.getEndTime() != null) {
                Duration duration = Duration.between(attendance.getStartTime(), attendance.getEndTime());
                return duration.toMinutes(); // 分単位で返す
            }
        } catch (Exception e) {
            logger.error("[ERROR] calculateWorkingTime メソッドでエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
            // 必要に応じて 0 ではなく特定のエラーハンドリングを行う
        }
        return 0;
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("[ERROR] 予期しないエラー: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "予期しないエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
}
