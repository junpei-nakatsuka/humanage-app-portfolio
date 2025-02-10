<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.Duration" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>勤怠レポート</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/report_generation.css">
	<script>
    	// フォーム送信時に非同期リクエストを使ってデータを送信
    	document.getElementById("pdfForm").addEventListener("submit", function(event) {
        	event.preventDefault(); // デフォルトのフォーム送信をキャンセル

        	// フォームデータを収集
        	const startDate = document.querySelector("input[name='startDate']").value;
        	const endDate = document.querySelector("input[name='endDate']").value;
        	const employeeName = document.querySelector("input[name='employeeName']").value;
        	const reportType = document.querySelector("input[name='reportType']").value;
        	const formData = new FormData(event.target);
            const requestData = Object.fromEntries(formData.entries());
            
        	const token = localStorage.getItem('X-Auth-Token');
        	if (!token) {
        		alert("認証トークンが見つかりません。ログインしなおしてください。");
        		return;
        	}
        	
        	// リクエスト用のデータを作成
        	const requestData = {
            	startDate: startDate,
            	endDate: endDate,
            	employeeName: employeeName,
            	reportType: reportType,
            	token: token
        	};

        	// `fetch` を使ってデータを JSON としてバックエンドに送信
        	fetch("${pageContext.request.contextPath}/api/generatePDF/node", {
            	method: "POST",
            	headers: {
                	"Content-Type": "application/json",  // JSONとして送信
            	},
            	body: JSON.stringify(requestData)  // JSON形式で送信
        	})
        	.then(response => response.blob()) // PDFデータを受け取る
            .then(blob => {
                // PDFファイルをダウンロードする
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = "勤怠レポート.pdf";
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            })
            .catch(error => {
                console.error("エラー:", error);
                alert("PDFの生成に失敗しました。");
            });
    	});
	</script>
</head>
<body>
    <div class="container">
    	<div class="yoko">
        	<h1>勤怠レポート</h1>
        	<h4>${startDate}～${endDate}</h4>
        </div>
	<h2>概要</h2>
<table class="summary-table">
    <thead>
        <tr>
            <th>氏名</th>
            <th>総残業時間</th>
            <th>合計残業代</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>${payrollReport[0].user.username}</td>
            <td>${totalOvertimeHours}時間
            	<c:choose>
            		<c:when test="${totalOvertimeMinutes > 0}">
            			${totalOvertimeMinutes}分
            		</c:when>
            		<c:otherwise>
            			0分
            		</c:otherwise>
            	</c:choose>
            </td>
            <td><fmt:formatNumber value="${totalOvertimePay}" type="number" maxFractionDigits="0" />円</td>
        </tr>
    </tbody>
</table>
<h2>詳細</h2>
<table class="detail-table">
    <thead>
    <tr>
        <th>出勤日</th>
        <th>出勤時間</th>
        <th>退勤時間</th>
        <th>残業時間</th>
    </tr>
</thead>
<tbody>
    <c:forEach var="attendance" items="${attendanceList}">
        <tr>
            <td>${attendance.date}</td>
            <td>${attendance.startTime != null ? attendance.startTime.toLocalTime().format(DateTimeFormatter.ofPattern("H:mm")) : ""}</td>
            <td>${attendance.endTime != null ? attendance.endTime.toLocalTime().format(DateTimeFormatter.ofPattern("H:mm")) : ""}</td>
            <td>
                <c:set var="overtimeHours" value="${attendance.overtimeMinutes / 60}" />
                <c:set var="overtimeMinutes" value="${attendance.overtimeMinutes % 60}" />
                ${overtimeHours.intValue()}時間 ${overtimeMinutes}分
            </td>
        </tr>
    </c:forEach>
</tbody>
</table>
		<c:set var="totalOvertimePay" value="${totalOvertimePay}" />
        <form id="pdfForm">
    		<input type="hidden" name="startDate" value="${startDate}" />
    		<input type="hidden" name="endDate" value="${endDate}" />
    		<input type="hidden" name="employeeName" value="${payrollReport[0].user.username}" />
    		<input type="hidden" name="reportType" value="payroll" />
    		<button class="no-print" type="submit">PDF出力</button>
		</form>
        <a href="${pageContext.request.contextPath}/reportGeneration?employeeName=${payrollReport[0].user.username}&reportType=payroll&startDate=${startDate}&endDate=${endDate}" class="btn btn-back no-print">🔙</a>
    </div>
</body>
</html>