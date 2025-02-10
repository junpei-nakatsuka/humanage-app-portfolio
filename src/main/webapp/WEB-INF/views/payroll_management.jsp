<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>給与情報</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/payroll_management.css">
</head>
<body>
    <div class="container">
        <div class="navigation">
            <a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
            <c:if test="${sessionScope.user.role == '人事管理者'}">
                <div class="navigation-in">
                    <button onclick="location.href='${pageContext.request.contextPath}/salaryManagement'">管理</button>
                </div>
            </c:if>
        </div>
        <h1>給与情報</h1>
        <form action="${pageContext.request.contextPath}/payroll" method="get">
            <input type="month" id="selectedMonth" name="selectedMonth" value="${selectedMonth}" required onchange="this.form.submit()">
        </form>
        <div class="employee-search">
            <form action="${pageContext.request.contextPath}/payroll" method="get">
                <input type="hidden" name="selectedMonth" value="${selectedMonth}">
                <c:if test="${sessionScope.user.role == '人事管理者'}">
                    <input type="text" id="employeeSearch" name="employeeSearch" placeholder="氏名で検索" value="${employeeSearch}">
                </c:if>
                <button type="submit" class="btn-search">検索</button>
            </form>
        </div>
        <table>
            <thead>
                <tr>
                    <th>氏名</th>
                    <th>役職</th>
                    <th>基本給</th>
                    <th>手当</th>
                    <th>控除</th>
                    <th>残業時間</th>
                    <th>残業代</th>
                    <th>総支給額</th>
                    <c:if test="${sessionScope.user.role == '人事管理者'}">
                        <th>操作</th>
                    </c:if>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="user" items="${users}">
                    <tr>
                        <td>${user.username}</td>
                        <td>${user.role}</td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
    							<c:if test="${salary.user.id == user.id}">
        							${salary.basicSalary.intValue()}円
    							</c:if>
							</c:forEach>
                        </td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
                                <c:if test="${salary.user.id == user.id}">
                                    ${salary.allowances.intValue()}円
                                </c:if>
                            </c:forEach>
                        </td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
                                <c:if test="${salary.user.id == user.id}">
                                    ${salary.deductions.intValue()}円
                                </c:if>
                            </c:forEach>
                        </td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
                                <c:if test="${salary.user.id == user.id}">
                                    <c:set var="totalOvertimeMinutes" value="${salary.overtimeHours * 60 + salary.overtimeMinutes}" />
                                    <c:set var="totalHours" value="${totalOvertimeMinutes / 60}" />
                                    <c:set var="totalMinutes" value="${totalOvertimeMinutes % 60}" />
                                    ${totalHours.intValue()}時間${totalMinutes}分
                                </c:if>
                            </c:forEach>
                        </td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
                                <c:if test="${salary.user.id == user.id}">
                                    <fmt:formatNumber value="${salary.overtimePay}" type="number" maxFractionDigits="0" />円
                                </c:if>
                            </c:forEach>
                        </td>
                        <td>
                            <c:forEach var="salary" items="${salaries}">
                                <c:if test="${salary.user.id == user.id}">
                                    ${salary.totalSalary.intValue()}円
                                </c:if>
                            </c:forEach>
                        </td>
                        <c:if test="${sessionScope.user.role == '人事管理者'}">
                            <td class="actions">
                                <c:if test="${user.id != null && selectedMonth != null}">
                                    <a href="${pageContext.request.contextPath}/salaryDetails?userId=${user.id}&paymentMonth=${selectedMonth}" class="btn btn-info">給与明細</a>
                                </c:if>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
</body>
</html>