package jp.co.example.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import jp.co.example.entity.Attendance;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;
import jp.co.example.service.AttendanceService;
import jp.co.example.service.SalaryService;
import jp.co.example.service.UserService;

@Controller
public class ReportController {
	
	private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
	
	@Autowired
	private SalaryService salaryService;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private AttendanceService attendanceService;

    // レポート生成画面を表示
	@GetMapping("/reportGeneration")
    public String showReportGenerationPage(@RequestParam(value = "employeeName", required = false) String employeeName,
                                           @RequestParam(value = "reportType", required = false) String reportType,
                                           @RequestParam(value = "startDate", required = false) String startDate,
                                           @RequestParam(value = "endDate", required = false) String endDate,
                                           Model model) {
        try {
            List<User> users = userService.getAllUsers();
            model.addAttribute("users", users);
            model.addAttribute("selectedEmployeeName", employeeName);
            model.addAttribute("selectedReportType", reportType);
            model.addAttribute("selectedStartDate", startDate);
            model.addAttribute("selectedEndDate", endDate);
        } catch (Exception e) {
            logger.error("[ERROR] レポート生成ページの読み込み中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "レポート生成ページの読み込み中にエラーが発生しました。");
            return "error_page";
        }
        return "report_generation";
    }

	@PostMapping("/generateReport")
    public String generateReport(@RequestParam("reportType") String reportType,
                                 @RequestParam("startDate") String startDate,
                                 @RequestParam("endDate") String endDate,
                                 @RequestParam("employeeName") String employeeName,
                                 Model model) {
        try {
            if ("payroll".equals(reportType)) {
                return showPayrollReport(startDate, endDate, employeeName, model);
            }
        } catch (Exception e) {
            logger.error("[ERROR] レポート生成中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "レポート生成中にエラーが発生しました。");
            return "error_page";
        }
        return "report_result";
    }

	@GetMapping("/payrollReport")
	public String showPayrollReport(@RequestParam("startDate") String startDate,
	                                @RequestParam("endDate") String endDate,
	                                @RequestParam("employeeName") String employeeName,
	                                Model model) {
	    try {
	        // 選択された期間の給与データを取得
	        List<Salary> payrollReport = salaryService.generatePayrollReport(startDate, endDate, employeeName);

	        if (payrollReport.isEmpty()) {
	            model.addAttribute("errorMessage", "選択した期間の給与データが見つかりません。");
	            return "error_page"; // 適切なエラーページを表示
	        }

	        // 出勤データを取得し、日付で昇順にソート
	        List<Attendance> attendanceList = attendanceService.getAttendanceByUserAndDateRange(employeeName, startDate, endDate)
	                                                          .stream()
	                                                          .sorted(Comparator.comparing(Attendance::getDate))
	                                                          .collect(Collectors.toList());

	        if (attendanceList.isEmpty()) {
	            model.addAttribute("errorMessage", "選択した期間の出勤データが見つかりません。");
	            return "error_page"; // 適切なエラーページを表示
	        }

	        // 残業代のマップを生成
	        Map<Integer, BigDecimal> overtimePayMap = new HashMap<>();
	        int totalOvertimeMinutes = 0;
	        int totalWorkingMinutes = 0;
	        int totalBreakMinutes = 0;

	        // 各出勤データに対して残業時間と休憩時間を計算
	        for (Attendance attendance : attendanceList) {
	            // 残業時間を再計算
	            long totalMinutesWorked = attendanceService.calculateWorkingTime(attendance);
	            long breakMinutes = attendanceService.calculateBreakTime(attendance);
	            long actualWorkedMinutes = totalMinutesWorked - breakMinutes;

	            // 8時間 (480分) を超えた部分を残業時間とする
	            int overtimeMinutes = (int) Math.max(0, Math.ceil(actualWorkedMinutes - 480));
	            attendance.setOvertimeMinutes(overtimeMinutes);

	            totalOvertimeMinutes += overtimeMinutes; // ここでは累積して保持するだけにする
	            
	            // 個別の残業代を計算してマップに保存
	            BigDecimal individualOvertimePay = salaryService.calculateOvertimePay(payrollReport.get(0), overtimeMinutes / 60.0);
	            individualOvertimePay = individualOvertimePay.setScale(0, RoundingMode.DOWN);
	            overtimePayMap.put(attendance.getId(), individualOvertimePay);
	            
	            totalWorkingMinutes += (actualWorkedMinutes > 0) ? actualWorkedMinutes : 0;
	            totalBreakMinutes += breakMinutes;  // 休憩時間の合計を計算
	        }

	        // ここで全体の残業時間を元に一度だけ残業代を計算
	        BigDecimal finalTotalOvertimePay = salaryService.calculateOvertimePay(payrollReport.get(0), totalOvertimeMinutes / 60.0);
	        finalTotalOvertimePay = finalTotalOvertimePay.setScale(0, RoundingMode.DOWN);

	        // 追加: 残業時間、実働時間、残りの分を計算
	        int totalOvertimeHours = totalOvertimeMinutes / 60;
	        int remainingOvertimeMinutes = totalOvertimeMinutes % 60;

	        int totalWorkingHours = totalWorkingMinutes / 60;
	        int remainingWorkingMinutes = totalWorkingMinutes % 60;

	        // モデルに追加
	        model.addAttribute("payrollReport", payrollReport);
	        model.addAttribute("attendanceList", attendanceList);
	        model.addAttribute("overtimePayMap", overtimePayMap);  // 残業代マップを追加
	        model.addAttribute("startDate", startDate);
	        model.addAttribute("endDate", endDate);
	        model.addAttribute("totalOvertimePay", finalTotalOvertimePay);  // 修正した合計残業代を追加
	        model.addAttribute("totalOvertimeHours", totalOvertimeHours);
	        model.addAttribute("totalOvertimeMinutes", remainingOvertimeMinutes);
	        model.addAttribute("totalWorkingHours", totalWorkingHours);
	        model.addAttribute("remainingWorkingMinutes", remainingWorkingMinutes);
	        model.addAttribute("totalBreakMinutes", totalBreakMinutes); // 合計休憩時間を追加

	        return "payroll_report";

	    } catch (Exception e) {
	        logger.error("[ERROR] 給与レポート生成中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", e.toString(), Arrays.toString(e.getStackTrace()), e.getCause() != null ? e.getCause().toString() : "原因不明", e.getLocalizedMessage());
	        model.addAttribute("errorMessage", "給与レポートの生成中にエラーが発生しました。");
	        return "error_page"; // エラーページを表示
	    }
	}
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, Model model) {
        logger.error("[ERROR] データの整合性に違反する操作が行われました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "データの整合性に違反する操作が行われました。");
        return "error_page";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
        logger.error("[ERROR] アクセス権限がありません: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "アクセス権限がありません。");
        return "error_page";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "システムエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
}
