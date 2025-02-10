<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ログイン</title>
<link rel="stylesheet" href="css/login.css">
<script>
	async function login(event) {
		event.preventDefault();
		const username = document.getElementById('username').value;
		const password = document.getElementById('password').value;
		const requestBody = {
			username : username,
			password : password
		};
		const token = localStorage.getItem('X-Auth-Token'); // localStorageを使用

		console.log('Sending token: ', token);
		try {
			const response = await fetch('/login', {
				method : 'POST',
				headers : {
					'X-Auth-Token' : token || "", // トークンを含める
					'Content-Type' : 'application/json',
					'Accept' : 'application/json'
				},
				body : JSON.stringify(requestBody),
				credentials: 'include'
			});
			if (response.ok) {
			    const newToken = response.headers.get('X-Auth-Token');
			    console.log('Received token:', newToken);
			    if (newToken) {
			        localStorage.setItem('X-Auth-Token', newToken); // トークンを保存
			    } else {
			        console.warn("新しいトークンがありません。ログインに失敗しました。");
			        localStorage.removeItem('X-Auth-Token');  // トークンをクリア
			        window.location.href = '/login';  // ログインページに戻す
			        return;
			    }
			    window.location.href = '/top';  // ログイン成功後、トップページにリダイレクト
			} else {
			    const data = await response.json();
			    alert(data.error || 'ログインに失敗しました。');
			    document.getElementById('loginForm').reset();
			}
		} catch (error) {
			console.error('ログイン出来ませんでした。:', error);
		}
	}
</script>
</head>
<body>
	<div class="login-container">
		<h1>ログイン</h1>
		<c:if test="${param.error == 'true'}">
			<p style="color: red;">ユーザー名またはパスワードが正しくありません。</p>
		</c:if>
		<form id="loginForm" onsubmit="login(event)">
			<div class="form-group">
				<label for="username">氏名</label>
				<input type="text" id="username" name="username" required autocomplete="username">
			</div>
			<div class="form-group">
				<label for="password">パスワード</label>
				<input type="password" id="password" name="password" required autocomplete="current-password">
			</div>
			<div class="form-group" id="login-button">
				<button type="submit" id="login-submit-button">ログイン</button>
			</div>
		</form>
	</div>
</body>
</html>