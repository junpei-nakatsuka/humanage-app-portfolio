package jp.co.example.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@Entity
@Table(name = "salaries")
@JsonIgnoreProperties(ignoreUnknown = true, value = {"user"})
public class Salary implements Serializable {
	private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basicSalary;

    @Column(precision = 10, scale = 2)
    private BigDecimal allowances;

    @Column(precision = 10, scale = 2)
    private BigDecimal deductions;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSalary;

    @Column(nullable = false)
    private LocalDate paymentDate;
    
    @Column(nullable = false)
    private int overtimeHours; // 残業時間（時間単位）

    @Column(nullable = false)
    private int overtimeMinutes; // 残業時間（分単位）
    
    @Column(name = "payment_month", nullable = false)
    private String paymentMonth;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal overtimePay;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal healthInsurance; // 健康保険

    @Column(precision = 10, scale = 2)
    private BigDecimal employmentInsurance; // 雇用保険

    @Column(precision = 10, scale = 2)
    private BigDecimal pension; // 厚生年金
    
    public Salary() {
        this.basicSalary = BigDecimal.ZERO;
        this.allowances = BigDecimal.ZERO;
        this.deductions = BigDecimal.ZERO;
        this.totalSalary = BigDecimal.ZERO;
        this.overtimePay = BigDecimal.ZERO;
        this.healthInsurance = BigDecimal.ZERO;
        this.employmentInsurance = BigDecimal.ZERO;
        this.pension = BigDecimal.ZERO;
    }
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public void setBasicSalary(BigDecimal basicSalary) {
        this.basicSalary = basicSalary;
    }

    public BigDecimal getAllowances() {
        return allowances;
    }

    public void setAllowances(BigDecimal allowances) {
        this.allowances = allowances;
    }

    public BigDecimal getDeductions() {
        return deductions;
    }

    public void setDeductions(BigDecimal deductions) {
        this.deductions = deductions;
    }

    public BigDecimal getTotalSalary() {
        return totalSalary;
    }

    public void setTotalSalary(BigDecimal totalSalary) {
        this.totalSalary = totalSalary;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }
    
    public int getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(int overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public int getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(int overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }
    
    public YearMonth getPaymentMonth() {
        if (this.paymentMonth == null) {
            return YearMonth.now(); // 現在の年月をデフォルト値として返す
        }
        return YearMonth.parse(this.paymentMonth);
    }

    public void setPaymentMonth(YearMonth paymentMonth) {
    	this.paymentMonth = paymentMonth.toString();
    }
    
    public BigDecimal getOvertimePay() {
    	return overtimePay;
    }
    
    public void setOvertimePay(BigDecimal overtimePay) {
    	this.overtimePay = overtimePay;
    }
    
    public BigDecimal getHealthInsurance() {
        return healthInsurance;
    }

    public void setHealthInsurance(BigDecimal healthInsurance) {
        this.healthInsurance = healthInsurance;
    }

    public BigDecimal getEmploymentInsurance() {
        return employmentInsurance;
    }

    public void setEmploymentInsurance(BigDecimal employmentInsurance) {
        this.employmentInsurance = employmentInsurance;
    }

    public BigDecimal getPension() {
        return pension;
    }

    public void setPension(BigDecimal pension) {
        this.pension = pension;
    }
    
    @JsonIgnore
    public BigDecimal getTotalDeductions() {
        return deductions.add(healthInsurance).add(employmentInsurance).add(pension);
    }
    
    @Override
    public String toString() {
        return "Salary{" +
                "username='" + (user != null ? user.getUsername() : "null") + '\'' +
                ", paymentMonth=" + (paymentMonth != null ? paymentMonth.toString() : "null") +
                ", basicSalary=" + basicSalary +
                ", overtimePay=" + overtimePay +
                ", totalSalary=" + totalSalary +
                '}';
    }
}
