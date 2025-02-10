<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>部門登録</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/department_management.css">
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/departments" class="btn btn-back">🔙</a>
    	<h1>部門登録</h1>
	</div>
    <form action="${pageContext.request.contextPath}/departments/save" id="out-line" method="post">
    	<div class="form-group">
        	<label for="departmentName">部門名:</label>
        	<input type="text" id="departmentName" name="departmentName" required>
        	<button type="submit" class="btn btn-submit">登録</button>
   		</div>
	</form>
</body>
</html>