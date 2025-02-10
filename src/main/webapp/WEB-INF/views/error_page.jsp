<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>エラー</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
</head>
<body>
    <h1>エラーが発生しました</h1>
    <div class="error-message">
        <p>${errorMessage}</p>
    </div>
    <div class="error-message">
        <p>${successMessage}</p>
    </div>
    <a href="${pageContext.request.contextPath}/top" class="btn btn-back">トップページに戻る</a>
</body>
</html>