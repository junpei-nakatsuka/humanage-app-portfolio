<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>新規契約追加</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/contract_management.css">
<script src="${pageContext.request.contextPath}/js/contract_form.js"></script>
</head>
<body>
  <div class="container">
  	<a href="${pageContext.request.contextPath}/users/${userId}/contracts" class="btn btn-back">🔙</a>
    <h1>新規契約追加</h1>
    <form action="${pageContext.request.contextPath}/contracts/save" method="post">
        <input type="hidden" name="userId" value="${userId}">
        <div class="form-group">
            <label for="contractDate">契約日:</label>
            <input type="date" id="contractDate" name="contractDate" required value="${contractForm.contractDate != null ? contractForm.contractDate.toString() : ''}">
        </div>
        <div class="form-group">
            <label id="expiryDateLabel" for="expiryDate">有効期限:</label>
            <input type="date" id="expiryDate" name="expiryDate" value="${contractForm.expiryDate != null ? contractForm.expiryDate.toString() : ''}">
        </div>
        <div class="form-group">
            <label for="type">雇用形態:</label>
            <select id="type" name="type" required>
                <option value="">選択してください</option>
                <option value="正社員" ${contractForm.type == '正社員' ? 'selected' : ''}>正社員</option>
                <option value="契約社員" ${contractForm.type == '契約社員' ? 'selected' : ''}>契約社員</option>
                <option value="派遣社員" ${contractForm.type == '派遣社員' ? 'selected' : ''}>派遣社員</option>
                <option value="アルバイト" ${contractForm.type == 'アルバイト' ? 'selected' : ''}>アルバイト</option>
                <option value="パート" ${contractForm.type == 'パート' ? 'selected' : ''}>パート</option>
            </select>
        </div>
        <div class="actions">
        	<c:if test="${not empty errorMessage}">
    			<div class="error-message">${errorMessage}</div>
			</c:if>
            <button type="submit" class="btn btn-submit">保存</button>
        </div>
    </form>
  </div>  
</body>
</html>