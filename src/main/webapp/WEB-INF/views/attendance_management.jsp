<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate" />
	<meta http-equiv="Pragma" content="no-cache" />
	<meta http-equiv="Expires" content="0" />
    <title>勤怠管理</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
    <script src="${pageContext.request.contextPath}/js/attendance.js"></script>
</head>
<body>
    <a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
    <c:if test="${not empty error}">
        <div class="error-message">
            ${error}
        </div>
        <script>
            setTimeout(function () {
                window.location.href = window.location.href.split('?')[0];
            }, 2000);
        </script>
    </c:if>
    <h1>勤怠管理</h1>
    <div class="actions" id="btn">
        <c:if test="${not sessionScope.isWorking}">
            <form action="${pageContext.request.contextPath}/attendance/start" method="post" onsubmit="return confirmAction('start')">
                <button type="submit" id="btn1">出勤</button>
            </form>
        </c:if>
        <c:if test="${(sessionScope.isWorking or sessionScope.hasUnfinishedWork) and not sessionScope.isOnBreak}">
            <form action="${pageContext.request.contextPath}/attendance/end" method="post">
    			<button type="submit" id="btn1" onclick="return confirmAction('end');">退勤</button>
			</form>
        </c:if>
		<c:if test="${sessionScope.user.role == '人事管理者'}">
    		<!-- 強制退勤ボタン -->
    		<button type="button" id="showForceEndModal" class="btn btn-force-end">強制退勤</button>
    		<!-- モーダル（初期状態は非表示） -->
    		<div id="forceEndModal" class="modal">
        		<div class="modal-content">
            		<span class="close">&times;</span>
            		<h2>強制退勤</h2>
            		<form action="${pageContext.request.contextPath}/attendance/forceEnd" method="post">
                		<label for="userId">ユーザーを選択:</label>
                		<select name="userId" required>
                    		<c:forEach var="user" items="${userList}">
                        		<option value="${user.id}">${user.username}</option>
                    		</c:forEach>
                		</select>
                		<label for="forceEndDate">未退勤の日を選択:</label>
                		<input type="date" name="forceEndDate" required>
                		<label for="forceEndTime">退勤時間を設定:</label>
                		<input type="time" name="forceEndTime" required>
                		<button type="submit" onclick="return confirm('本当に強制退勤しますか？')">強制退勤</button>
            		</form>
        		</div>
    		</div>
		</c:if>
    	<c:if test="${sessionScope.isWorking and not sessionScope.isOnBreak and not sessionScope.isBreakFinished}">
        	<form action="${pageContext.request.contextPath}/attendance/breakStart" method="post" onsubmit="return confirmAction('breakStart')">
            <button type="submit" id="btn1">休憩開始</button>
        	</form>
    	</c:if>
        <c:if test="${sessionScope.isOnBreak}">
            <form action="${pageContext.request.contextPath}/attendance/breakEnd" method="post" onsubmit="return confirmAction('breakEnd')">
                <button type="submit" id="btn1">休憩終了</button>
            </form>
        </c:if>
    </div>    
    <h2>勤怠情報一覧</h2>
    <form action="${pageContext.request.contextPath}/attendanceManagement" method="get">
        <label for="yearSelect">年を選択：</label>
        <select name="year" id="yearSelect" onchange="this.form.submit()">
            <c:forEach var="year" items="${availableYears}">
                <option value="${year}" ${year == selectedYear ? 'selected' : ''}>
                    ${year}年
                </option>
            </c:forEach>
        </select>
        <label for="monthSelect">月を選択：</label>
        <select name="month" id="monthSelect" onchange="this.form.submit()">
            <c:forEach var="month" items="${availableMonths}">
                <option value="${month}" ${month == selectedMonth ? 'selected' : ''}>
                    ${month}月
                </option>
            </c:forEach>
        </select>
    </form>
    <table border="1">
        <thead>
            <tr>
                <th>日付</th>
                <th>出勤時間</th>
                <th>退勤時間</th>
                <th>休憩時間</th>
                <th>実働時間</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="attendance" items="${formattedAttendanceList}" varStatus="status">
    			<tr class="${status.index == 0 ? 'latest-attendance' : ''}">
        			<td>${attendance.date}</td>
        			<td>${attendance.startTime}</td>
        			<td>${attendance.endTime}</td>
        			<td class="break-time" data-break-time="${attendance.breakTime}">
            			<c:set var="breakHours" value="${attendance.breakTime / 60}" />
            			<c:set var="breakMinutes" value="${attendance.breakTime % 60}" />
            			${breakHours} 時間 ${breakMinutes} 分
        			</td>
        			<td class="working-time" data-working-time="${attendance.workingTime - attendance.breakTime}">
            			<c:set var="workingHours" value="${(attendance.workingTime - attendance.breakTime) / 60}" />
            			<c:set var="workingMinutes" value="${(attendance.workingTime - attendance.breakTime) % 60}" />
            			${workingHours} 時間 ${workingMinutes} 分
        			</td>
    			</tr>
			</c:forEach>
        </tbody>
    </table>
</body>
</html>