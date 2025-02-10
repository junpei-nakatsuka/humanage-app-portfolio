package jp.co.example.controller.form;

import java.time.LocalDate;

public class EmploymentForm {
	
	private Integer employmentId;
    private Integer userId;
    private String userName;
    private LocalDate hireDate;
    private LocalDate resignationDate;
    private String status;
    
    public Integer getEmploymentId() {
    	return employmentId;
    }
    
    public void setEmploymentId(Integer employmentId) {
    	this.employmentId = employmentId;
    }
    
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public LocalDate getResignationDate() {
        return resignationDate;
    }

    public void setResignationDate(LocalDate resignationDate) {
        this.resignationDate = resignationDate;
    }
    
    public String getStatus() {
    	return status;
    }
    
    public void setStatus(String status) {
    	this.status = status;
    }

}
