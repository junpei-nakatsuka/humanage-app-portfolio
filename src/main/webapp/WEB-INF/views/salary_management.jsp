<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>給与情報管理</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/payroll_management.css">
</head>
<body>
    <div class="container">
       	<a href="${pageContext.request.contextPath}/payroll" class="btn-back">🔙</a>
        <br>
        <h1>給与情報管理</h1>
        <div class="inner-container">
        <form action="${pageContext.request.contextPath}/salaryManagement" method="get">
            <input type="month" id="selectedMonth" name="selectedMonth" value="${selectedMonth}" required onchange="this.form.submit()">
        </form>
        <div class="search-box">
            <form action="${pageContext.request.contextPath}/salaryManagement" method="get">
                <input type="hidden" name="selectedMonth" value="${selectedMonth}">
                <div class="employee-search">
                    <input type="text" id="employeeSearch" name="employeeSearch" placeholder="氏名で検索" value="${employeeSearch}">
                    <button type="submit" class="btn-search">検索</button>
                </div>
            </form>
        </div>
        <div class="add-salary-out">
        	<button onclick="location.href='${pageContext.request.contextPath}/copySalaryForNextMonth'" class="btn-copy">次月に給与データをコピー</button>
        	<div class="add-salary">
            	<button onclick="location.href='${pageContext.request.contextPath}/addSalary'" id="hannei">給与情報を追加</button>
        	</div>
        </div>
        <c:if test="${not empty message}">
            <div class="alert alert-success">
                ${message}
            </div>
        </c:if>
        <table>
            <thead>
                <tr>
                    <th class="name">氏名</th>
                    <th>基本給</th>
                    <th>手当</th>
                    <th>控除</th>
                    <th>総支給額</th>
                    <th>状況</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="salary" items="${salaries}">
                    <tr>
                        <form action="${pageContext.request.contextPath}/updateSalary" method="post">
                            <input type="hidden" name="salaryId" value="${salary.id}">
                            <input type="hidden" name="paymentMonth" value="${selectedMonth}">
                            <td>${salary.user.username}</td>
                            <td><input type="number" name="basicSalary" value="${salary.basicSalary.intValue()}" step="1" class="wide-input" required></td>
                            <td><input type="number" name="allowances" value="${salary.allowances.intValue()}" step="1" required></td>
                            <td><input type="number" name="deductions" value="${salary.deductions.intValue()}" step="1" readonly></td>
                            <td>${salary.totalSalary.intValue()} 円</td>
                            <td class="actions">
                                <button type="submit">更新</button>
                            </td>
                        </form>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
        </div>
    </div>
</body>
</html>