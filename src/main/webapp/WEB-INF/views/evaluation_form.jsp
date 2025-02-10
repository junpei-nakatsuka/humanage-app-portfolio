<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>評価フォーム</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/evaluation.css">
</head>
<body>
	<a href="${pageContext.request.contextPath}/evaluationManagement" class="btn btn-back">🔙</a>
    <h1>${evaluation.id == null ? '新規評価追加' : '評価編集'}</h1>
    <form action="${pageContext.request.contextPath}/evaluationManagement/save" method="post">
        <input type="hidden" name="id" value="${evaluation.id}">
        <label for="user">氏名:</label>
        <select name="user.id" required>
            <c:forEach var="user" items="${users}">
                <option value="${user.id}" ${user.id == evaluation.user.id ? 'selected' : ''}>${user.username}</option>
            </c:forEach>
        </select>
        <label for="evaluationDate">評価日:</label>
		<input type="date" name="evaluationDate" value="<fmt:formatDate value='${evaluation.evaluationDate}' pattern='yyyy-MM-dd'/>" required>
        <label for="score">評価スコア:</label>
        <input type="number" name="score" min="1" max="10" step="1" value="${evaluation.score.intValue()}" required>
        <label for="comments">コメント:</label>
        <textarea name="comments" rows="4" required>${evaluation.comments}</textarea>
        <button type="submit">${evaluation.id == null ? '登録' : '更新'}</button>
    </form>
</body>
</html>