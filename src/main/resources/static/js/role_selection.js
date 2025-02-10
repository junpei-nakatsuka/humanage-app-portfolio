//function updateRoleOptions(){let e=document.getElementById("department"),t=document.getElementById("role"),l=e.options[e.selectedIndex].text,d=document.getElementById("selectedRole").value;t.innerHTML="";let n=[];(n="人事部"===l?["人事管理者","一般"]:"開発部"===l?["部門長","システムエンジニア","プログラマー"]:["部門長","一般"]).forEach(function(e){let l=document.createElement("option");l.value=e,l.text=e,e===d&&(l.selected=!0),t.appendChild(l)})}function getAddressByPostalCode(){var e=document.getElementById("postal_code").value.replace("-","");7===e.length&&fetch("https://zipcloud.ibsnet.co.jp/api/search?zipcode="+e).then(e=>e.json()).then(e=>{if(e.results&&e.results.length>0){var t=document.getElementById("address"),l=e.results[0];t.value=l.address1+l.address2+l.address3}else alert("該当する住所が見つかりませんでした。")}).catch(e=>{console.error("Error fetching address:",e)})}window.onload=function(){updateRoleOptions()};

window.onload = function() {
    let selectedRoleElement = document.getElementById("selectedRole");
    if (selectedRoleElement) {
        updateRoleOptions();
    } else {
        console.error("selectedRole element not found");
    }
};

function updateRoleOptions() {
    let e = document.getElementById("department"),
        t = document.getElementById("role"),
        l = e.options[e.selectedIndex].text,
        d = document.getElementById("selectedRole").value;

    if (!e || !t) return; // 必要な要素が存在しない場合は処理を中止

    t.innerHTML = "";
    let n = [];

    if ("人事部" === l) {
        n = ["人事管理者", "一般"];
    } else if ("開発部" === l) {
        n = ["部門長", "システムエンジニア", "プログラマー"];
    } else {
        n = ["部門長", "一般"];
    }

    n.forEach(function(role) {
        let option = document.createElement("option");
        option.value = role;
        option.text = role;
        if (role === d) {
            option.selected = true;
        }
        t.appendChild(option);
    });
}

function getAddressByPostalCode() {
    var e = document.getElementById("postal_code").value.replace("-", "");
    if (e.length === 7) {
        fetch("https://zipcloud.ibsnet.co.jp/api/search?zipcode=" + e)
            .then(response => response.json())
            .then(data => {
                if (data.results && data.results.length > 0) {
                    var t = document.getElementById("address"),
                        address = data.results[0];
                    t.value = address.address1 + address.address2 + address.address3;
                } else {
                    alert("該当する住所が見つかりませんでした。");
                }
            })
            .catch(error => {
                console.error("Error fetching address:", error);
            });
    }
}
