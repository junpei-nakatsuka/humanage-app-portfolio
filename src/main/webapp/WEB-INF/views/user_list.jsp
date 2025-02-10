<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ユーザー一覧</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
    <script>
    	var contextPath = "${pageContext.request.contextPath}";
	</script>
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
    	<h1>ユーザー一覧</h1>
	</div>
	<div class="oya">
	<div class="search-box">
		<form action="${pageContext.request.contextPath}/users" method="get">
			<input type="text" name="query" placeholder="名前、TEL、メールで検索" value="${param.query}" class="search-box-in">
			<button type="submit" class="search-box-btn">検索</button>
		</form>
	</div>
	<c:if test="${sessionScope.role == '人事管理者' || sessionScope.role == 'システム管理者'}">
        <div class="header-actions">
            <a href="${pageContext.request.contextPath}/register" class="btn btn-insert">新規登録</a>
        </div>
    </c:if>
    </div>
    <table>
        <thead>
            <tr>
                <th>氏名</th>
                <th>メールアドレス</th>
                <th>電話番号</th>
                <th>役職</th>
                <th>所属部署</th>
                <th>アクション</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="user" items="${users}">
                <tr>
                    <td><c:out value="${user.username}"/></td>
                    <td><c:out value="${user.email}"/></td>
                    <td><c:out value="${user.phone}"/></td>
                    <td><c:out value="${user.role}"/></td>
                    <td><c:out value="${user.department.departmentName}"/></td>
                    <td class="actions">
    					<a href="${pageContext.request.contextPath}/users/${user.id}" class="btn btn-info">詳細</a>
    					<c:if test="${sessionScope.role == '人事管理者' || sessionScope.role == 'システム管理者'}">
        					<button class="btn btn-delete" onclick="confirmDeleteUser(${user.id})">削除</button>
        				</c:if>
					</td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
    <div id="deleteModal" class="modal" style="display:none;">
        <div class="modal-content">
            <span class="close" onclick="closeModal()">&times;</span>
            <h2>削除の確認</h2>
            <p>削除するにはパスワードを入力してください。</p>
            <input type="password" id="password" placeholder="パスワード">
            <button id="confirmDeleteButton" class="btn btn-delete">削除</button>
        </div>
    </div>
    <script src="${pageContext.request.contextPath}/js/delete_confirmation.js"></script>
</body>
</html>