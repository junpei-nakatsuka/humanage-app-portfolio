package jp.co.example.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import jp.co.example.entity.Salary;
import jp.co.example.entity.User;

public interface SalaryRepository extends JpaRepository<Salary, Integer>{
    
    // UserのIDを使って給与情報を検索
    Salary findByUserId(Integer userId);

    // Userオブジェクト自体を使って給与情報を検索
    Optional<Salary> findByUser(User user);

    @Query("SELECT s FROM Salary s WHERE s.user.id = :userId AND s.paymentMonth = :paymentMonth")
    Optional<Salary> findByUserIdAndPaymentMonth(@Param("userId") Integer userId, @Param("paymentMonth") String paymentMonth);

    //ユーザー名で部分一致検索をするメソッド
    List<Salary> findByUserUsernameContaining(String username);
    
    @Query(value = "SELECT * FROM salaries WHERE payment_month = :paymentMonth", nativeQuery = true)
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "false"))
    List<Salary> findSalaryByPaymentMonth(@Param("paymentMonth") String paymentMonth);
    
    List<Salary> findByUserUsernameContainingAndPaymentMonth(String username, String paymentMonth);
    
    List<Salary> findAll(Sort sort);
    
    //月で検索し、id順にソートするクエリ
    @Query("SELECT s FROM Salary s WHERE s.paymentMonth = :paymentMonth ORDER BY s.id ASC")
    List<Salary> findByPaymentMonthSorted(@Param("paymentMonth") String paymentMonth);
    
    //名前と月で検索し、id順にソートするクエリ
    @Query("SELECT s FROM Salary s WHERE s.user.username LIKE %:username% AND s.paymentMonth = :paymentMonth ORDER BY s.id ASC")
    List<Salary> findByUsernameAndMonthSorted(@Param("username") String username, @Param("paymentMonth") String paymentMonth);
    
    //Usernameで給与情報をソートして取得するクエリ
    List<Salary> findByPaymentMonthOrderByUserUsernameAsc(String paymentMonth);

    //Usernameで給与情報をソートして取得（検索がある場合）
    List<Salary> findByUserUsernameContainingAndPaymentMonthOrderByUserUsernameAsc(String username, String paymentMonth);
    
    @Query("SELECT s FROM Salary s WHERE s.user = :user ORDER BY s.paymentMonth DESC")
    List<Salary> findLatestSalaryByUser(@Param("user") User user, Pageable pageable);
    
    @Query("SELECT s FROM Salary s WHERE YEAR(s.paymentDate) = :year AND MONTH(s.paymentDate) = :month")
    List<Salary> findSalariesByYearAndMonth(@Param("year") int year, @Param("month") int month);
    
    @Query("SELECT s FROM Salary s WHERE s.paymentDate BETWEEN :start AND :end")
    List<Salary> findSalariesBetweenDates(@Param("start") LocalDate start, @Param("end") LocalDate end);
            
    List<Salary> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
    
    List<Salary> findByPaymentDateBetweenAndUserUsername(LocalDate startDate, LocalDate endDate, String username);
}
