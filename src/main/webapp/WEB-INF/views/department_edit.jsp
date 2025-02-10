<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>部門編集</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
</head>
<body>
    <h1>部門編集</h1>
    <form action="${pageContext.request.contextPath}/departments/update" method="post">
    	<input type="hidden" name="id" value="${department.id}">
    	<div class="form-group">
        	<label for="departmentName">部門名</label>
        	<input type="text" id="departmentName" name="departmentName" value="${department.departmentName}" required>
    	</div>
    	<div class="form-actions">
        	<button type="submit" class="btn btn-edit">保存</button>
        	<a href="${pageContext.request.contextPath}/departments" class="btn btn-back">🔙</a>
    	</div>
	</form>
</body>
</html>