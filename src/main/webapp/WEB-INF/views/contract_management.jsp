<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>契約管理画面</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/contract_management.css">
    <script src="${pageContext.request.contextPath}/js/delete_confirmation.js"></script>
    <script>
    	var contextPath = "${pageContext.request.contextPath}";
	</script>
</head>
<body>
    <div class="container">
    	<a href="${pageContext.request.contextPath}/users/${user.id}" class="btn btn-back">🔙</a>
        <h1>契約管理</h1>
        <p class="username">${user.username}さん</p>
        <div class="header-actions">
        	<c:if test="${sessionScope.role == '人事管理者' || sessionScope.role == 'システム管理者'}">
        		<a href="${pageContext.request.contextPath}/contracts/new?userId=${user.id}" class="btn btn-insert">追加</a>
    		</c:if>
    	</div>
        <table>
            <thead>
                <tr>
                    <th>契約日</th>
                    <th>有効期限</th>
                    <th>雇用形態</th>
                    <c:if test="${role == '人事管理者'}">
                    	<th>アクション</th>
                	</c:if>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="contract" items="${contracts}">
                    <tr class="${contract.id == activeContractId ? 'highlight' : ''}">
                        <td><fmt:formatDate value="${contract.contractDateAsDate}" pattern="yyyy/MM/dd"/></td>
                        <td><fmt:formatDate value="${contract.expiryDateAsDate}" pattern="yyyy/MM/dd"/></td>
                        <td>${contract.type}</td>
    					<c:if test="${sessionScope.role == '人事管理者'}">
    						<td class="actions">
        						<a href="${pageContext.request.contextPath}/contracts/edit/${contract.id}" class="btn btn-edit">編集</a>
        						<button class="btn btn-delete" onclick="confirmDeleteContract(${contract.id},${user.id})">削除</button>
        					</td>
    					</c:if>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
    <div id="deleteModal" class="modal" style="display:none;">
        <div class="modal-content">
            <span class="close" onclick="closeModal()">&times;</span>
            <h2>契約削除の確認</h2>
            <p>削除するにはパスワードを入力してください。</p>
            <input type="password" id="password" placeholder="パスワード"><br>
            <button id="confirmDeleteButton" class="btn btn-delete">削除</button>
        </div>
    </div>
</body>
</html>