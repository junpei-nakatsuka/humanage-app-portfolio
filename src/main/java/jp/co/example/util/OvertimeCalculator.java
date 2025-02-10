package jp.co.example.util;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.example.entity.Attendance;

public class OvertimeCalculator {
	
	private static final Logger logger = LoggerFactory.getLogger(OvertimeCalculator.class);

    // 勤務時間を計算するメソッド
	public static long calculateWorkingTime(Attendance attendance) {
		logger.info("[INFO] OvertimeCalculator.javaのcalculateWorkignTime関数が呼ばれました。Attendance: {}", attendance);
		try {
			logger.debug("[DEBUG] 勤務時間計算中...");
			if (attendance.getStartTime() != null && attendance.getEndTime() != null) {
				Duration duration = Duration.between(attendance.getStartTime(), attendance.getEndTime());
				long workingMinutes = duration.toMinutes(); // 分単位で勤務時間を返す
				logger.info("[INFO] 勤務時間計算結果:" + workingMinutes + "minutes");
				return workingMinutes;
			}
			logger.warn("[WARN] 勤務開始時間か終了時間がnullです。0分を返します。");
			return 0; // 勤務時間が計算できない場合は 0 分を返す
		} catch (Exception e) {
			logger.error("[ERROR] 勤務時間の計算中にエラーが発生しました: {}", e.getMessage(), e);
			throw new RuntimeException("勤務時間の計算中にエラーが発生しました: " + e.getMessage(), e);
		}
	}

    // 休憩時間を計算するメソッド
	public static long calculateBreakTime(Attendance attendance) {
		logger.info("[INFO] OvertimeCalculator.javaのcalculateBreakTime関数が呼ばれました。Attendance: {}", attendance);
		try {
			logger.debug("[DEBUG] 休憩時間計算中...");
			if (attendance.getBreakStart() != null && attendance.getBreakEnd() != null) {
				Duration breakDuration = Duration.between(attendance.getBreakStart(), attendance.getBreakEnd());
				long breakMinutes = breakDuration.toMinutes(); // 分単位で休憩時間を返す
				logger.info("[INFO] 休憩時間計算結果:" + breakMinutes + "minutes");
				return breakMinutes;
			}
			logger.warn("[WARN] 休憩開始時間か終了時間がnullです。0分を返します。");
			return 0; // 休憩時間が計算できない場合は 0 分を返す
		} catch (Exception e) {
			logger.error("[ERROR] 休憩時間の計算中にエラーが発生しました: {}", e.getMessage(), e);
			throw new RuntimeException("休憩時間の計算中にエラーが発生しました: " + e.getMessage(), e);
		}
	}

    // 残業時間を計算するメソッド
	public static long calculateOvertimeMinutes(Attendance attendance) {
		logger.info("[INFO] OvertimeCalculator.javaのcalculateOvertimeMinutes関数が呼ばれました。Attendance: {}", attendance);
		try {
			logger.debug("[DEBUG] 残業時間計算中...");
			long totalMinutesWorked = calculateWorkingTime(attendance);
			long breakMinutes = calculateBreakTime(attendance);
			long actualWorkedMinutes = totalMinutesWorked - breakMinutes;
			long overtimeMinutes = Math.max(0, actualWorkedMinutes - 480); // 480分 (8時間) を超えた部分が残業時間
			logger.info("[INFO] 残業時間計算結果:" + overtimeMinutes + "minutes");
			return overtimeMinutes;
		} catch (Exception e) {
			logger.error("[ERROR] 残業時間の計算中にエラーが発生しました: {}", e.getMessage(), e);
			throw new RuntimeException("残業時間の計算中にエラーが発生しました: " + e.getMessage(), e);
		}
	}
}
