<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>トップページ</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/dashboard.css">
</head>
<body>
<header>
    <c:choose>
        <c:when test="${sessionScope.user.department.departmentName == '人事部'}">
            <h1>人事・労務管理システム</h1>
        </c:when>
        <c:otherwise>
            <h1>社員ポータルシステム</h1>
        </c:otherwise>
    </c:choose>
    <p>ようこそ、<c:out value="${sessionScope.user.username}"/> さん</p>
</header>
<nav>
    <a href="${pageContext.request.contextPath}/users">ユーザー管理</a>
    <a href="${pageContext.request.contextPath}/departments">部門管理</a>
    <c:if test="${sessionScope.user.role == '人事管理者'}">
    	<a href="${pageContext.request.contextPath}/reportGeneration">レポート生成</a>
    </c:if>
</nav>
<a href="${pageContext.request.contextPath}/logout" class="logout-link">ログアウト</a>
<section class="dashboard">
    <div class="widget">
        <h2>給与情報</h2>
        <a href="${pageContext.request.contextPath}/payroll">最新の給与情報を確認</a>
    </div>
    <div class="widget">
        <h2>勤怠状況</h2>
        <a href="${pageContext.request.contextPath}/attendanceManagement">現在の勤怠ステータス</a>
    </div>
    <div class="widget">
        <h2>評価管理</h2>
        <a href="${pageContext.request.contextPath}/evaluationManagement">従業員の評価履歴を確認</a>
    </div>
</section>
</body>
</html>