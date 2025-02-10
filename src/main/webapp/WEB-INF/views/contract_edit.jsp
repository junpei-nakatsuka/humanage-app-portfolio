<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>契約編集</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/contract_management.css">
<script src="${pageContext.request.contextPath}/js/contract_form.js"></script>
</head>
<body>
  <div class="container">
  	<a href="${pageContext.request.contextPath}/users/${contract.user.id}/contracts" class="btn btn-back">🔙</a>
    <h1>契約編集</h1>
    <form action="${pageContext.request.contextPath}/contracts/update" method="post">
        <input type="hidden" name="id" value="${contract.id}">
        <div class="form-group">
            <label for="contractDate">契約日:</label>
            <input type="text" id="contractDate" name="contractDate" readonly value="${contract.contractDate}">
        </div>
        <div class="form-group">
            <label id="expiryDateLabel" for="expiryDate">有効期限:</label>
            <input type="date" id="expiryDate" name="expiryDate" value="${contract.expiryDate != null ? contract.expiryDate.toString() : ''}">
        </div>
        <div class="form-group">
            <label for="type">雇用形態:</label>
            <select id="type" name="type" required>
                <option value="">選択してください</option>
                <option value="正社員" ${contract.type == '正社員' ? 'selected' : ''}>正社員</option>
                <option value="契約社員" ${contract.type == '契約社員' ? 'selected' : ''}>契約社員</option>
                <option value="派遣社員" ${contract.type == '派遣社員' ? 'selected' : ''}>派遣社員</option>
                <option value="アルバイト" ${contract.type == 'アルバイト' ? 'selected' : ''}>アルバイト</option>
                <option value="パート" ${contract.type == 'パート' ? 'selected' : ''}>パート</option>
            </select>
        </div>
        <div class="actions">
        	<c:if test="${not empty errorMessage}">
    			<div class="error-message">${errorMessage}</div>
			</c:if>
            <button type="submit" class="btn btn-submit">更新</button>
        </div>
    </form>
  </div>  
</body>
</html>