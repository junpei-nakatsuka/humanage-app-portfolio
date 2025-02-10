<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ユーザー編集</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user.css">
    <script src="${pageContext.request.contextPath}/js/role_selection.js"></script>
    <script src="${pageContext.request.contextPath}/js/edit_confirmation.js"></script>
    <script src="${pageContext.request.contextPath}/js/user_mail.js"></script>
</head>
<body>
    <div class="user-form">
        <h1>ユーザー編集</h1>
        <form id="userEditForm" action="${pageContext.request.contextPath}/users/update" method="post" onsubmit="combineEmail()">
            <input type="hidden" id="id" name="id" value="${user.id}">
            <div class="form-group">
                <label for="username">氏名</label>
                <input type="text" id="username" name="username" value="${user.username}">
            </div>
			<div class="form-group">
    			<label for="email-local">メールアドレス:</label>
    			<div class="email-inputs">
        			<input type="text" id="email-local" value="${user.email.split('@')[0]}" placeholder="メールのローカル部分" required> <!-- ローカル部分 -->
        			<select id="email-domain" required>
            			<option value="">選択してください</option>
            			<option value="@gmail.com" ${user.email.endsWith('@gmail.com') ? 'selected' : ''}>@gmail.com</option>
            			<option value="@yahoo.co.jp" ${user.email.endsWith('@yahoo.co.jp') ? 'selected' : ''}>@yahoo.co.jp</option>
            			<option value="@ezweb.ne.jp" ${user.email.endsWith('@ezweb.ne.jp') ? 'selected' : ''}>@ezweb.ne.jp</option>
            			<option value="@docomo.ne.jp" ${user.email.endsWith('@docomo.ne.jp') ? 'selected' : ''}>@docomo.ne.jp</option>
            			<option value="@softbank.ne.jp" ${user.email.endsWith('@softbank.ne.jp') ? 'selected' : ''}>@softbank.ne.jp</option>
        			</select>
    			</div>
			</div>
			<input type="hidden" id="email" name="email" value="${user.email}">
            <div class="form-group">
    			<label for="phone">電話番号:</label>
    			<input type="tel" id="phone" name="phone" value="${user.phone}" required placeholder="例: 090-1234-5678">
				<c:if test="${result != null and result.hasFieldErrors('phone')}">
                    <div class="error-message" style="color:red;">
                        ${result.getFieldError('phone').defaultMessage}
                    </div>
                </c:if>
			</div>
            <div class="form-group">
            	<label for="dob">生年月日</label>
            	<input type="date" id="dob" name="dob" value="${user.dob}" required>
            </div>
            <div class="form-group">
                <label for="gender">性別</label>
                <select id="gender" name="gender" readonly>
                    <option value="男性" ${user.gender == '男性' ? 'selected' : ''}>男性</option>
                    <option value="女性" ${user.gender == '女性' ? 'selected' : ''}>女性</option>
                    <option value="その他" ${user.gender == 'その他' ? 'selected' : ''}>その他</option>
                </select>
            </div>
            <div class="form-group">
                <label for="postal_code">郵便番号:</label>
                <input type="text" id="postal_code" name="postalCode" value="${user.postalCode}" required oninput="getAddressByPostalCode()" placeholder="例: 123-4567">
                <c:if test="${result != null and result.hasFieldErrors('postalCode')}">
                    <div class="error-message" style="color:red;">
                        ${result.getFieldError('postalCode').defaultMessage}
                    </div>
                </c:if>
            </div>
            <div class="form-group">
            	<label for="address">住所</label>
            	<input type="text" id="address" name="address" value="${user.address}">
            </div>
            <div class="form-group">
                <label for="department">所属部署:</label>
                <select id="department" name="department" required onchange="updateRoleOptions()">
                    <option value="">選択してください</option>
                    <c:forEach var="department" items="${departments}">
                        <option value="${department.departmentName}" ${user.department.departmentName == department.departmentName ? 'selected' : ''}>
                            ${department.departmentName}
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group">
                <label for="role">役職:</label>
                <select id="role" name="role" required>
                    <c:if test="${user.department.departmentName == '人事部'}">
                        <option value="人事管理者" ${user.role == '人事管理者' ? 'selected' : ''}>人事管理者</option>
                        <option value="採用担当" ${user.role == '採用担当' ? 'selected' : ''}>採用担当</option>
                    </c:if>
                    <c:if test="${user.department.departmentName != '人事部' && user.department.departmentName != '開発部'}">
                        <option value="部門長" ${user.role == '部門長' ? 'selected' : ''}>部門長</option>
                        <option value="一般" ${user.role == '一般' ? 'selected' : ''}>一般</option>
                    </c:if>
                    <c:if test="${user.department.departmentName == '開発部'}">
                        <option value="部門長" ${user.role == '部門長' ? 'selected' : ''}>部門長</option>
                        <option value="システムエンジニア" ${user.role == 'システムエンジニア' ? 'selected' : ''}>システムエンジニア</option>
                        <option value="プログラマー" ${user.role == 'プログラマー' ? 'selected' : ''}>プログラマー</option>
                    </c:if>
                </select>
            </div>
            <div class="form-group" hidden>
    			<label for="status">ステータス</label>
    			<input type="hidden" id="status" name="status" value="${user.status != null ? user.status : '在職中'}">
			</div>
            <div class="form-group" id="passwordField" style="display: none;">
                <label for="password">あなたのパスワードを入力してください（確認用）:</label>
                <input type="password" id="password" name="password" required>
            </div>
            <div class="actions">
            	<c:if test="${not empty errorMessage}">
    				<div class="error-message" style="color: red;">
    					${errorMessage}
    				</div>
    			</c:if>
                <button type="button" id="submitButton" class="btn-submit" onclick="showPasswordPrompt()">保存</button>
                <button type="submit" id="confirmButton" class="btn-submit" style="display: none;">確認</button>
                <a href="${pageContext.request.contextPath}/users/${user.id}" class="btn-back">🔙</a>
            </div>
        </form>
    </div>
</body>
</html>