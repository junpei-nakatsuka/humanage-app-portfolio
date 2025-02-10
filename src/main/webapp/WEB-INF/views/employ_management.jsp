<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>入社日・退職日管理</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
<script src="${pageContext.request.contextPath}/js/delete_confirmation.js"></script>
<script>
    	var contextPath = "${pageContext.request.contextPath}";
</script> 
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/users/${user.id}" class="btn btn-back">🔙</a>
		<h1>入社日・退職日管理</h1>
		<p class="username">${user.username}さん</p>
	</div>
	<div class="header-actions">
		<c:if test="${role == '人事管理者'}">
			<a href="${pageContext.request.contextPath}/employments/new/${user.id}" class="btn btn-insert">新規登録</a>
		</c:if>
	</div>
	<table>
		<thead>
			<tr>
				<th>入社日</th>
				<th>退職日</th>
				<th>在職状態</th>
				<c:if test="${role == '人事管理者'}">
					<th>アクション</th>
				</c:if>
			</tr>
		</thead>
		<tbody>
			<c:forEach var="employment" items="${employments}">
				<tr data-employment-id="${employment.id}" class="${employment.id == latestEmploymentId ? 'highlight' : ''}">
    				<td>${employment.hireDate}</td>
    				<td>${employment.resignationDate != null ? employment.resignationDate : ''}</td>
    				<td>${employment.status}</td>
    				<c:if test="${role == '人事管理者'}">
    					<td class="actions">
        					<a href="${pageContext.request.contextPath}/employments/edit/${employment.id}" class="btn btn-edit">編集</a>
        					<a href="javascript:void(0);" class="btn btn-delete" onclick="confirmDeleteEmployment(${employment.id}, ${user.id})">削除</a>
    					</td>
    				</c:if>
				</tr>
			</c:forEach>
		</tbody>
	</table>
	<div id="deleteModal" class="modal" style="display:none;">
		<div class="modal-content">
			<span class="close" onclick="closeModal()">&times;</span>
			<h2>入社記録削除の確認</h2>
			<p>削除するにはパスワードを入力してください。</p>
			<input type="password" id="password" placeholder="パスワード"><br>
			<button id="confirmDeleteButton" class="btn btn-delete">削除</button>
		</div>
	</div>
	</body>
</html>