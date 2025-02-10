package jp.co.example.dto;

public class FormattedAttendance {
    private String date;
    private String startTime;
    private String endTime;
    private long workingTime;
    private String breakStart; // 休憩開始
    private String breakEnd;   // 休憩終了
    private long breakTime;    // 休憩時間合計

    // コンストラクタ
    public FormattedAttendance(String date, String startTime, String endTime, long workingTime, String breakStart, String breakEnd, long breakTime) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.workingTime = workingTime;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.breakTime = breakTime;
    }

    // ゲッターとセッター
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public long getWorkingTime() { return workingTime; }
    public void setWorkingTime(long workingTime) { this.workingTime = workingTime; }

    public String getBreakStart() { return breakStart; }
    public void setBreakStart(String breakStart) { this.breakStart = breakStart; }

    public String getBreakEnd() { return breakEnd; }
    public void setBreakEnd(String breakEnd) { this.breakEnd = breakEnd; }

    public long getBreakTime() { return breakTime; }
    public void setBreakTime(long breakTime) { this.breakTime = breakTime; }
}
