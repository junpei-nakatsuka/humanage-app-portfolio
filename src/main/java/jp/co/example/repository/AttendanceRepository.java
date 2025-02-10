package jp.co.example.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.entity.Attendance;
import jp.co.example.entity.User;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {
	
	List<Attendance> findByUser(User user);
	
	@Transactional
	void deleteByUserId(Integer userId);

    @Query("SELECT a FROM Attendance a WHERE a.user = :user ORDER BY a.startTime DESC")
    Page<Attendance> findLatestAttendanceByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT a FROM Attendance a WHERE a.user.id = :userId AND a.date = :date")
    Optional<Attendance> findByUserIdAndDate(@Param("userId") Integer userId, @Param("date") LocalDate date);

    // 月の勤怠情報を取得 (ネイティブSQLに修正)
    @Query(value = "SELECT * FROM attendance WHERE user_id = :userId AND EXTRACT(MONTH FROM date) = :month", nativeQuery = true)
    List<Attendance> findAttendanceByUserAndMonth(@Param("userId") Integer userId, @Param("month") int month);

    // 利用可能な月のリストを取得 (ネイティブSQLに修正)
    @Query(value = "SELECT DISTINCT EXTRACT(MONTH FROM date) FROM attendance WHERE user_id = :userId ORDER BY EXTRACT(MONTH FROM date)", nativeQuery = true)
    List<Integer> findAvailableMonthsByUser(@Param("userId") Integer userId);

    // 月ごとの残業時間合計 (ネイティブSQLに修正)
    @Query(value = "SELECT SUM(overtime_minutes) FROM attendance WHERE user_id = :userId AND EXTRACT(MONTH FROM date) = :month", nativeQuery = true)
    Integer sumOvertimeMinutesByUserAndMonth(@Param("userId") Integer userId, @Param("month") int month);

    // 年・月ごとの残業時間合計 (ネイティブSQLに修正)
    @Query(value = "SELECT SUM(overtime_minutes) FROM attendance WHERE user_id = :userId AND EXTRACT(YEAR FROM date) = :year AND EXTRACT(MONTH FROM date) = :month", nativeQuery = true)
    Integer sumOvertimeMinutesByUserAndYearMonth(@Param("userId") Integer userId, @Param("year") int year, @Param("month") int month);

    // 年・月の勤怠情報を取得 (ネイティブSQLに修正)
    @Query(value = "SELECT * FROM attendance WHERE user_id = :userId AND EXTRACT(YEAR FROM date) = :year AND EXTRACT(MONTH FROM date) = :month", nativeQuery = true)
    List<Attendance> findAttendanceByUserAndYearAndMonth(@Param("userId") Integer userId, @Param("year") int year, @Param("month") int month);

    // 利用可能な年のリストを取得 (ネイティブSQLに修正)
    @Query(value = "SELECT DISTINCT EXTRACT(YEAR FROM date) FROM attendance WHERE user_id = :userId ORDER BY EXTRACT(YEAR FROM date)", nativeQuery = true)
    List<Integer> findAvailableYearsByUser(@Param("userId") Integer userId);

    // ユーザー名と日付範囲で検索 (Userエンティティに `username` があることを前提)
    List<Attendance> findByUserUsernameAndDateBetween(String username, LocalDate startDate, LocalDate endDate);
    
    //ユーザーの過去の未退勤データが存在するか確認
    boolean existsByUserAndEndTimeIsNullAndDateBefore(User user, LocalDate date);

    // ユーザーの未退勤の出勤データを取得
    List<Attendance> findByUserAndEndTimeIsNullAndDateBefore(User user, LocalDate date);
    
    @Query("SELECT a FROM Attendance a WHERE a.date = :date AND a.endTime IS NULL")
    Attendance findByDateAndEndTimeIsNull(@Param("date") LocalDate date);
    
    @Query("SELECT a FROM Attendance a WHERE a.user = :user AND a.date = :date AND a.endTime IS NULL")
    Attendance findByUserAndDateAndEndTimeIsNull(@Param("user") User user, @Param("date") LocalDate date);
}