<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ユーザー登録</title>
    <link rel="stylesheet" href="css/user.css">
    <script src="${pageContext.request.contextPath}/js/role_selection.js"></script>
    <script src="${pageContext.request.contextPath}/js/user_mail.js"></script>
    <script>
	document.addEventListener("DOMContentLoaded", function () {
    	const password = document.getElementById("password");
    	const confirmPassword = document.getElementById("confirmPassword");
    	const passwordError = document.getElementById("passwordError");
    	const postalCodeInput = document.getElementById("postal_code");
    	const form = document.querySelector("form");
    	const phoneInput = document.getElementById("phone");

    	function validatePassword() {
        	if (password.value !== confirmPassword.value) {
            	passwordError.style.display = "inline";
            	return false;
        	} else {
            	passwordError.style.display = "none";
            	return true;
        	}
    	}

    	// 入力時にリアルタイムでチェック
    	confirmPassword.addEventListener("input", validatePassword);

    	// フォーム送信時にチェック
    		form.addEventListener("submit", function (event) {
        	if (!validatePassword()) {
            	event.preventDefault(); // 送信をブロック
            	alert("パスワードが一致しません。修正してください。");
        	}
    	});
    	
    	postalCodeInput.addEventListener("input", function () {
    		let value = postalCodeInput.value.replace(/[^0-9]/g, '');
    		if (value.length >= 7) {
    			postalCodeInput.value = value.slice(0, 3) + "-" + value.slice(3, 7);
    		} else {
    			postalCodeInput.value = value;
    		}
    	});
    	
    	// 入力時はハイフンを追加しない
        phoneInput.addEventListener("input", function () {
            this.value = this.value.replace(/[^0-9]/g, ''); // 数字以外を削除
        });

     	//フォーカスが外れた時にハイフンを追加
        phoneInput.addEventListener("blur", function () {
            let value = this.value.replace(/[^0-9]/g, ''); // 数字以外を削除
            let formatted = "";

            if (value.length === 11) { 
                formatted = value.replace(/^(\d{3})(\d{4})(\d{4})$/, "$1-$2-$3");
            } else if (value.length === 10) { 
                if (value.startsWith("03") || value.startsWith("06")) {
                    formatted = value.replace(/^(\d{2})(\d{4})(\d{4})$/, "$1-$2-$3"); // 03/06-xxxx-xxxx
                } else {
                    formatted = value.replace(/^(\d{3})(\d{3})(\d{4})$/, "$1-$2-$3"); // 045-xxx-xxxx
                }
            } else {
                formatted = value;
            }

            this.value = formatted;
        });
	});
	</script>
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/users" class="btn btn-back">🔙</a>
    	<h1>ユーザー登録</h1>
	</div>
    <form action="${pageContext.request.contextPath}/register" method="post" class="user-form" onsubmit="combineEmail()">
        <div class="form-group">
            <label for="username">氏名:</label>
            <input type="text" id="username" name="username" value="${userForm.username}" required>
        </div>
        <div class="form-group">
            <label for="dob">生年月日:</label>
            <input type="date" id="dob" name="dob" value="${userForm.dob}" required>
        </div>
        <div class="form-group">
            <label for="gender">性別:</label>
            <select id="gender" name="gender" required>
                <option value="">選択してください</option>
                <option value="男性" ${userForm.gender == '男性' ? 'selected' : ''}>男性</option>
                <option value="女性" ${userForm.gender == '女性' ? 'selected' : ''}>女性</option>
                <option value="その他" ${userForm.gender == 'その他' ? 'selected' : ''}>その他</option>
            </select>
        </div>
		<div class="form-group">
    		<label for="email-local">メールアドレス:</label>
    		<div class="email-inputs">
        		<input type="text" id="email-local" value="${fn:substringBefore(userForm.email, '@')}" placeholder="メールのローカル部分" required>
        		<select id="email-domain" required>
            		<option value="@gmail.com" ${fn:contains(userForm.email, '@gmail.com') ? 'selected' : ''}>@gmail.com</option>
            		<option value="@yahoo.co.jp" ${fn:contains(userForm.email, '@yahoo.co.jp') ? 'selected' : ''}>@yahoo.co.jp</option>
            		<option value="@ezweb.ne.jp" ${fn:contains(userForm.email, '@ezweb.ne.jp') ? 'selected' : ''}>@ezweb.ne.jp</option>
            		<option value="@docomo.ne.jp" ${fn:contains(userForm.email, '@docomo.ne.jp') ? 'selected' : ''}>@docomo.ne.jp</option>
            		<option value="@softbank.ne.jp" ${fn:contains(userForm.email, '@softbank.ne.jp') ? 'selected' : ''}>@softbank.ne.jp</option>
        		</select>
    		</div>
		</div>
        <input type="hidden" id="email" name="email" value="${userForm.email}">
        <div class="form-group">
    		<label for="phone">電話番号:</label>
    		<input type="tel" id="phone" name="phone" value="${userForm.phone}" required placeholder="例: 09012345678" maxlength="13">
    		<c:if test="${result != null and result.hasFieldErrors('phone')}">
    			<div class="error-message" style="color:red;">
    				${result.getFieldError('phone').defaultMessage}
    			</div>
    		</c:if>
		</div>
		<div class="form-group">
        	<label for="postal_code">郵便番号:</label>
        	<input type="text" id="postal_code" name="postalCode" value="${userForm.postalCode}" required oninput="getAddressByPostalCode()" placeholder="例: 1234567">
        	<c:if test="${result != null and result.hasFieldErrors('postalCode')}">
        		<div class="error-message" style="color:red;">
            		${result.getFieldError('postalCode').defaultMessage}
        		</div>
    		</c:if>
    	</div>
        <div class="form-group">
            <label for="address">住所:</label>
            <input type="text" id="address" name="address" value="${userForm.address}" required>
        </div>
        <div class="form-group">
            <label for="department">所属部署:</label>
            <select id="department" name="departmentId" required onchange="updateRoleOptions()">
                <option value="">選択してください</option>
                <c:forEach var="department" items="${departments}">
                    <option value="${department.id}" ${userForm.departmentId == department.id ? 'selected' : ''}>${department.departmentName}</option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group">
            <label for="role">役職:</label>
            <select id="role" name="role" required>
            </select>
            <input type="hidden" id="selectedRole" value="${userForm.role}">
        </div>
        <div class="form-group">
            <label for="password">登録するパスワード:</label>
            <input type="password" id="password" name="password" required>
        </div>
        <div class="form-group">
    		<label for="confirmPassword">パスワード確認:</label>
    		<input type="password" id="confirmPassword"　name="confirmPassword" required>
    		<span id="passwordError" style="color: red; display: none;">パスワードが一致しません。</span>
		</div>
        <div class="actions">
        	<c:if test="${not empty errorMessage}">
    			<div class="error-message" style="color: red;">
    				${errorMessage}
    			</div>
    		</c:if>
            <button type="submit" class="btn btn-submit">登録</button>
        </div>
    </form>
</body>
</html>