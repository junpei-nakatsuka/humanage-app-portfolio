<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>新規給与情報の追加</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/payroll_management.css">
</head>
<body>
    <div class="container">
    	<a href="${pageContext.request.contextPath}/salaryManagement" class="btn-back">🔙</a>
        <h1>新規給与情報の追加</h1>
        <c:if test="${not empty errorMessage}">
        	<div class="error-message">
        		${errorMessage}
        	</div>
        </c:if>
        <form action="${pageContext.request.contextPath}/addSalary" method="post">
            <div class="form-group">
                <label for="userId">氏名：</label>
                <select name="userId" id="userId" required>
                    <option value="">選択してください</option>
                    <c:forEach var="user" items="${users}">
                        <option value="${user.id}">${user.username}</option>
                    </c:forEach>
                </select>
            </div>
            <div>
            	<label class="form-group">
            	<input type="month" name="paymentMonth" id="paymentMonth" required>
            </div><br>
            <div class="form-group">
                <label for="basicSalary">基本給：</label>
                <input type="number" name="basicSalary" id="basicSalary" required>
            </div>
            <div class="form-group">
                <label for="allowances">手当(残業以外)：</label>
                <input type="number" name="allowances" id="allowances" required>
            </div>
            <div class="form-group">
                <input type="hidden" name="deductions" id="deductions" value="0">
            </div>
            <div class="form-group">
                <label for="overtimeHours">残業時間：</label>
                <div class="overtime-input">
                    <input type="number" name="overtimeHours" id="overtimeHours" value="0" step="1" min="0" class="overtime-field" required> 時間
                    <input type="number" name="overtimeMinutes" id="overtimeMinutes" value="0" step="1" min="0" class="overtime-field" required> 分
                </div>
            </div>
            <div class="form-group">
                <button type="submit">追加</button>
            </div>
        </form>
    </div>
</body>
</html>