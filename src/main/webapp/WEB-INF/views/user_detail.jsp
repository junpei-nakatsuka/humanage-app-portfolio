<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ユーザー詳細</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
    <script src="${pageContext.request.contextPath}/js/delete_confirmation.js"></script>
    <script type="text/javascript">
    	var contextPath = "${pageContext.request.contextPath}";
	</script>
</head>
<body>
    <h1>ユーザー詳細</h1>
    <div class="user-detail">
        <div class="user-info">
            <label for="user-name">氏名:</label>
            <span id="user-name">${user.username}</span>
        </div>
        <div class="user-info">
            <label for="user-dob">生年月日:</label>
            <span id="user-dob">${formattedDob}</span>
        </div>
        <div class="user-info">
            <label for="user-age">年齢:</label>
            <span id="user-age"><c:out value="${user.age}"/></span>
        </div>
        <div class="user-info">
            <label for="user-gender">性別:</label>
            <span id="user-gender">${user.gender}</span>
        </div>
        <div class="user-info">
            <label for="user-email">メールアドレス:</label>
            <span id="user-email">${user.email}</span>
        </div>
        <div class="user-info">
            <label for="user-phone">電話番号:</label>
            <span id="user-phone">${user.phone}</span>
        </div>
        <div class="user-info">
        	<label for="user-postalCode">郵便番号:</label>
        	<span id="user-postalCode">${user.postalCode}</span>
        </div>
        <div class="user-info">
            <label for="user-address">住所:</label>
            <span id="user-address">${user.address}</span>
        </div>
        <div class="user-info">
            <label for="user-role">役職:</label>
            <span id="user-role">${user.role}</span>
        </div>
        <div class="user-info">
            <label for="user-department">所属部署:</label>
            <span id="user-department">${user.department.departmentName}</span>
        </div>
        <div class="user-info">
    		<label for="employmentType">雇用形態:</label>
    		<span id="employmentType">${latestContract != null ? latestContract.type : '未設定'}</span>
		</div>
        <div class="user-info">
    		<label for="lastModifiedBy">最終更新者:</label>
    		<span id="lastModifiedBy">${user.lastModifiedBy.username}</span>
		</div>
		<div class="user-info">
    		<label for="lastModifiedAt">最終更新日時:</label>
    		<span id="lastModifiedAt">${formattedLastModifiedAt}</span>
		</div>
    </div>
    <div class="actions">
        <c:if test="${sessionScope.role == '人事管理者' || sessionScope.role == 'システム管理者'}">
    <a href="${pageContext.request.contextPath}/users/edit/${user.id}" class="btn btn-edit">編集</a>
    <a href="javascript:void(0);" class="btn btn-delete" onclick="confirmDeleteUser(${user.id})">削除</a>
</c:if>
        <a href="${pageContext.request.contextPath}/users/${user.id}/contracts" class="btn btn-info">契約管理</a>
        <a href="${pageContext.request.contextPath}/users/${user.id}/employment" class="btn btn-info">入社・退職管理</a>
        <a href="${pageContext.request.contextPath}/users" class="btn btn-back">🔙</a>
    </div>
    <div id="deleteModal" class="modal" style="display:none;">
    	<div class="modal-content">
    		<span class="close" onclick="closeModal()">&times;</span>
    		<h2>削除の確認</h2>
    		<p>削除するにはパスワードを入力してください。</p>
    		<input type="password" id="password" placeholder="パスワード">
    		<button id="confirmDeleteButton" class="btn btn-delete">削除</button>
    	</div>
    </div>
</body>
</html>