<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>レポート生成画面</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/report_generation.css">
</head>
<body>
    <div class="container">
        <h1>レポート生成</h1>
        <form id="reportForm" action="${pageContext.request.contextPath}/generateReport" method="post">
    <div class="form-group">
        <label for="employeeName">氏名:</label>
        <select id="employeeName" name="employeeName" required>
            <option value="" disabled ${empty selectedEmployeeName ? 'selected' : ''}>選択してください</option>
            <c:forEach var="user" items="${users}">
                <option value="${user.username}" ${user.username == selectedEmployeeName ? 'selected' : ''}>
                    ${user.username}
                </option>
            </c:forEach>
        </select>
    </div>
    <div class="form-group">
        <label for="reportType">レポートタイプ:</label>
        <select id="reportType" name="reportType" required>
            <option value="" disabled ${empty selectedReportType ? 'selected' : ''}>選択してください</option>
            <option value="payroll" ${selectedReportType == 'payroll' ? 'selected' : ''}>勤怠レポート</option>
        </select>
    </div>
    <div class="form-group">
        <label for="startDate">開始日:</label>
        <input type="date" id="startDate" name="startDate" value="${selectedStartDate}" required>
    </div>
    <div class="form-group">
        <label for="endDate">終了日:</label>
        <input type="date" id="endDate" name="endDate" value="${selectedEndDate}" required>
    </div>
    <div class="form-group">
        <button type="submit">表示</button>
    </div>
</form>
        <a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
    </div>
    <script src="${pageContext.request.contextPath}/js/report_generation.js"></script>
</body>
</html>