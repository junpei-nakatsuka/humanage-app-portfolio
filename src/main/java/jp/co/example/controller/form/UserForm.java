package jp.co.example.controller.form;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jp.co.example.entity.Department;

public class UserForm {
	
	private static final Logger logger = LoggerFactory.getLogger(UserForm.class);
	
    private String id;
    
    @NotBlank(message = "{user.username.required}")
    @Size(max = 100, message = "{user.username.size}")
    private String username;
    
    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    private String email;
    
    @NotBlank(message = "{user.password.required}")
    @Size(min = 4, message = "{user.password.size}")
    private String password;
    
    @NotNull(message = "{user.dob.required}")
    private String dob;
    
    @NotBlank(message = "{}user.gender.required")
    private String gender;
    
    //@Pattern(regexp = "^\\d{2,4}-\\d{3,4}-\\d{4}$", message="{user.phone.invalid}")
    @Pattern(regexp = "^(0\\d{1,3}-\\d{1,4}-\\d{4})$", message="{user.phone.invalid}")
    private String phone;
    
    private String address;
    
    @NotBlank(message = "{user.role.required}")
    private String role;
    
    private String status;
    private String hireDate;
    private String retirementDate;
    
    @NotNull(message = "所属部署を選択してください。")
    private String departmentId;
    
    private Department department;

    @Pattern(regexp = "^\\d{3}-\\d{4}$", message="{user.postalCode.invalid}")
    private String postalCode;  // 新しく郵便番号フィールドを追加
    
    public String getPostalCode() {
    	return postalCode;
    }
     
    public void setPostalCode(String postalCode) {
    	if (postalCode != null && postalCode.matches("^\\d{7}$")) {
    		this.postalCode = postalCode.substring(0, 3) + postalCode.substring(3);
    	} else {
    		this.postalCode = postalCode;
    	}
    }

    // ゲッターとセッター
    public String getId() {
        return id;
    }

    public void setId(String id) {
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
        this.password = password;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
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

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    public String getRetirementDate() {
        return retirementDate;
    }

    public void setRetirementDate(String retirementDate) {
        this.retirementDate = retirementDate;
    }
    
    public String getDepartmentId() {
    	return departmentId;
    }
    
    public void setDepartmentId(String departmentId) {
    	this.departmentId = departmentId;
    	logger.info("[INFO] 受け取った部門ID: {}", departmentId);
    }
    
    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
        logger.info("[INFO] セットされた部門: {}", department);
    }
    
    public String getDepartmentName() {
        return department != null ? department.getDepartmentName() : null;
    }
}
