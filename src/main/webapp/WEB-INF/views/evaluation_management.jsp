<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>評価管理</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/evaluation.css">
    <script type="text/javascript">
        var contextPath = "${pageContext.request.contextPath}";
    </script>
</head>
<body>
<a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
<h1>評価管理</h1>
<c:if test="${sessionScope.role == '人事管理者'}">
	<div class="header-actions">
    	<a href="${pageContext.request.contextPath}/evaluationManagement/new" class="btn btn-insert">新規評価追加</a>
	</div>
</c:if>
<table>
    <thead>
        <tr>
            <th>氏名</th>
            <th>評価日</th>
            <th>評価スコア</th>
            <th>コメント</th>
            <th>
            	<c:if test="${sessionScope.role == '人事管理者'}">
            		アクション
            	</c:if>
            </th>
        </tr>
    </thead>
    <tbody>
        <c:forEach var="evaluation" items="${evaluations}">
            <c:if test="${sessionScope.role == '人事管理者' || evaluation.user.id == sessionScope.user.id}">
            <tr>
                <td>${evaluation.user.username}</td>
                <td><fmt:formatDate value="${evaluation.evaluationDate}" pattern="yyyy-MM-dd" /></td>
                <td>${evaluation.score.intValue()}</td>
                <td>${evaluation.comments}</td>
                <td>
                	<c:if test="${sessionScope.role == '人事管理者'}">
                    	<a href="${pageContext.request.contextPath}/evaluationManagement/edit/${evaluation.id}" class="btn btn-edit">編集</a>
                    	<button class="btn btn-delete" onclick="confirmDeleteEvaluation(${evaluation.id})">削除</button>
                	</c:if>
                </td>
            </tr>
            </c:if>
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