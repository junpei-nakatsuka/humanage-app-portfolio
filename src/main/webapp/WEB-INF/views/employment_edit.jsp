<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>入社日・退職日編集</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
<script src="${pageContext.request.contextPath}/js/employment_form.js"></script>
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/users/${employmentForm.userId}/employment" class="btn btn-back" onclick="console.log('Debug: Back button clicked. userId=' + ${employmentForm.userId});">🔙</a>
    	<h1>入社日・退職日編集</h1>
	</div>
    <form action="${pageContext.request.contextPath}/employments/save" method="post" class="employment-form">
        <input type="hidden" name="employmentId" value="${employmentForm.employmentId}">
        <input type="hidden" name="userId" value="${employmentForm.userId}">
        <div class="form-group">
            <label for="userName">氏名:</label>
            <input type="text" id="userName" name="userName" value="${employmentForm.userName}" readonly>
        </div>
        <div class="form-group">
            <label for="hireDate">入社日:</label>
            <input type="date" id="hireDate" name="hireDate" required value="${employmentForm.hireDate}">
        </div>
        <div class="form-group" id="resignationDateGroup">
    		<label for="resignationDate">退職日:</label>
    		<input type="date" id="resignationDate" name="resignationDate" value="${employmentForm.resignationDate}">
		</div>
        <div class="form-group">
            <label for="status">在職状態:</label>
            <select id="status" name="status" required>
                <option value="在職中" ${employmentForm.status == '在職中' ? 'selected' : ''}>在職中</option>
                <option value="退職済み" ${employmentForm.status == '退職済み' ? 'selected' : ''}>退職済み</option>
                <option value="退職予定" ${employmentForm.status == '退職予定' ? 'selected' : ''}>退職予定</option>
            </select>
        </div>
        <div class="actions">
        	<c:if test="${not empty errorMessage}">
    			<div class="error-message">${errorMessage}</div>
			</c:if>
            <button type="submit" class="btn btn-submit">更新</button>
        </div>
    </form>
</body>
</html>