<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>部門管理</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/department_management.css">
    <script>
    	var contextPath = "${pageContext.request.contextPath}";
	</script>    
</head>
<body>
	<div class="container">
		<a href="${pageContext.request.contextPath}/top" class="btn btn-back">🔙</a>
    	<h1>部門管理</h1>
	</div>
    <div class="header-actions">
    	<c:if test="${sessionScope.user.role == '人事管理者'}">
        	<a href="${pageContext.request.contextPath}/departments/new" class="btn btn-insert">部門追加</a>
    	</c:if>
    </div>
    <table>
        <thead>
            <tr>
                <th>部門名</th>
                <th>部門長</th>
                <th>所属人数</th>
                <th>アクション</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach var="department" items="${departments}">
                <c:if test="${department.departmentName != 'システム管理'}">
                    <tr>
                        <td>${department.departmentName}</td>
                        <td>
                            <c:choose>
                                <c:when test="${department.departmentName == '人事部'}">
                                    ${sessionScope.user.role == '人事管理者' ? sessionScope.user.username : '未設定'}
                                </c:when>
                                <c:otherwise>
                                    ${department.manager != null ? department.manager.username : '未設定'}
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>${department.users.size()}</td>
                        <td class="actions">
                            <a href="javascript:void(0);" class="btn btn-info" id="btn-info" onclick="toggleMembers('${department.id}')">メンバー</a>
                            <c:if test="${department.manager != null && department.manager.id == sessionScope.user.id}">
                                <a href="${pageContext.request.contextPath}/departments/edit/${department.id}" class="btn btn-edit">部門名編集</a>
                            </c:if>
                            <c:if test="${sessionScope.user.role == '人事管理者'}">
    							<button class="btn btn-delete" onclick="confirmDeleteDepartment(${department.id})">削除</button>
							</c:if>
                        </td>
                    </tr>
                    <tr id="members-${department.id}" style="display:none;">
                        <td colspan="5">
                            <table class="inner-table">
                                <thead>
                                    <tr>
                                        <th>氏名</th>
                                        <th>役職</th>
                                        <th>アクション</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="member" items="${department.users}">
                                        <tr>
                                            <td>${member.username}</td>
                                            <td>${member.role}</td>
                                            <td class="actions">
                                                <a href="javascript:void(0);" class="btn btn-info" onclick="showUserDetails(${member.id})">詳細</a>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </td>
                    </tr>
                </c:if>
            </c:forEach>
        </tbody>
    </table>
	<div id="userDetailModal" class="modal" style="display:none;">
    <div class="modal-content">
        <span class="close" onclick="closeUserDetailModal()">&times;</span>
        <h2>ユーザー詳細</h2>
        <p>氏名:<span id="userName"></span></p>
        <p>メールアドレス:<span id="userEmail"></span></p>
        <p>電話番号:<span id="userPhone"></span></p>
        <p>役職:<span id="userRole"></span></p>
    </div>
</div>
	<div id="deleteModal" class="modal" style="display:none;">
        <div class="modal-content">
            <span class="close" onclick="closeModal()">&times;</span>
            <h2>部門削除の確認</h2>
            <p>削除するにはパスワードを入力してください。</p>
            <input type="password" id="password" placeholder="パスワード"><br>
            <button id="confirmDeleteButton" class="btn btn-delete">削除</button>
        </div>
    </div>
	<script src="${pageContext.request.contextPath}/js/department_management.js" defer></script>
	<script src="${pageContext.request.contextPath}/js/delete_confirmation.js" defer></script>
</body>
</html>