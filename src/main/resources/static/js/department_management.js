function toggleMembers(e){var t=document.getElementById("members-"+e);"none"===t.style.display?t.style.display="table-row":t.style.display="none"}function showUserDetails(e){fetch(contextPath+"/users/"+e+"/details").then(e=>e.json()).then(e=>{document.getElementById("userName").textContent=e.username,document.getElementById("userEmail").textContent=e.email,document.getElementById("userRole").textContent=e.role,document.getElementById("userPhone").textContent=e.phone,document.getElementById("userDetailModal").style.display="block"}).catch(e=>console.error("ユーザー情報の取得に失敗しました:",e))}function closeUserDetailModal(){console.log("closeModal function is called");var e=document.getElementById("userDetailModal");e?(console.log("Modal found, hiding it."),e.style.display="none"):console.error("Modal element not found")}document.addEventListener("DOMContentLoaded",function(){window.onclick=function(e){var t=document.getElementById("userDetailModal"),n=document.getElementById("deleteModal");e.target==t?closeModal():e.target==n&&closeModal()}});