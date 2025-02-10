<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>エラーページ</title>
    <link rel="stylesheet" href="css/user.css">
</head>
<body>
	<div class="error-message">
        <p>${errorMessage}</p>
    </div>
    <div class="error-message">
        <p>${successMessage}</p>
    </div>
    <h1>エラーが発生しました</h1>
    <p class="error-center">申し訳ございません。何らかのエラーが発生しました。</p>
    <p class="error-center">もう一度お試しいただくか、管理者にお問い合わせください。</p>
    <a href="${pageContext.request.contextPath}/top" class="btn btn-back">トップページに戻る</a>
</body>
</html>