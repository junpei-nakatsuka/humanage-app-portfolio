package jp.co.example.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Objects;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jp.co.example.util.HashUtil;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@Entity  //supabaseデータベースのテーブルとして認識
@Table(name = "users")  //supabaseテーブル名を指定
@JsonIgnoreProperties(value = {"attendances", "salaries", "department"}, ignoreUnknown = true)
public class User implements Serializable {
	private static final long serialVersionUID = 1L;
    
    @Id //主キー
    @GeneratedValue(strategy = GenerationType.IDENTITY) //プライマリキーの生成方法を指定
    private Integer id;
    
    //フィールドをデータベースのカラムにマッピング
    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private LocalDate dob;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = true)
    private LocalDate hireDate;

    @Column(nullable = true)
    private LocalDate retirementDate;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "department_id", nullable = false)  //外部キーとして使われるカラムを指定
    private Department department;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "last_modified_by")
    private User lastModifiedBy;
    
    private LocalDateTime lastModifiedAt;
    
    @Column(name = "reset_token", nullable = true, length = 255)
    private String resetToken;
    
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    private List<Salary> salaries;
    
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    private List<Attendance> attendances;
    
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    private List<Contract> contracts;
       
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password != null && !password.isEmpty() && !password.startsWith("$2a$") && !password.startsWith("$2b$")) {
            this.password = HashUtil.hashPassword(password);
        } else {
            this.password = password;
        }
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public LocalDate getRetirementDate() {
        return retirementDate;
    }

    public void setRetirementDate(LocalDate retirementDate) {
        this.retirementDate = retirementDate;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
    
    public int getAge() {
        if(this.dob == null) {
            return 0;
        }
        return Period.between(this.dob, LocalDate.now()).getYears();
    }
    
    public User getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(User lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(LocalDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }
    
    public String getResetToken() {
    	return resetToken;
    }
    
    public void setResetToken(String resetToken) {
    	this.resetToken = resetToken;
    }
    
    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    
    public List<Salary> getSalaries() {
        return salaries;
    }

    public void setSalaries(List<Salary> salaries) {
        this.salaries = salaries;
    }
    
    public List<Attendance> getAttendances() {
    	return attendances;
    }
    
    public void setAttendances(List<Attendance> attendances) {
    	this.attendances = attendances;
    }
    
    public List<Contract> getContracts() {
    	return contracts;
    }
    
    public void setContracts(List<Contract> contracts) {
    	this.contracts = contracts;
    }
    
    @Override
    public String toString() {
        return "User{id=" + id + ", username=" + username + ", department=" + (department != null ? department.getDepartmentName() : "No Department") + "}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
