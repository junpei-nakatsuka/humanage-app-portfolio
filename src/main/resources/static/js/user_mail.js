//function combineEmail(){let e=document.getElementById("email-local").value,l=document.getElementById("email-domain").value;document.getElementById("email").value=e+l}document.getElementById("phone").addEventListener("input",function(e){let l=e.target.value.replace(/[^0-9]/g,"");l.length>3&&l.length<=7?l=l.slice(0,3)+"-"+l.slice(3):l.length>7&&(l=l.slice(0,3)+"-"+l.slice(3,7)+"-"+l.slice(7)),e.target.value=l});

window.onload = function() {
    let phoneElement = document.getElementById("phone");
    if (phoneElement) {
        phoneElement.addEventListener("input", function(e) {
            let l = e.target.value.replace(/[^0-9]/g, "");
            l.length > 3 && l.length <= 7 ? l = l.slice(0, 3) + "-" + l.slice(3) : l.length > 7 && (l = l.slice(0, 3) + "-" + l.slice(3, 7) + "-" + l.slice(7));
            e.target.value = l;
        });
    }

    let emailLocalElement = document.getElementById("email-local");
    let emailDomainElement = document.getElementById("email-domain");
    let emailElement = document.getElementById("email");

    if (emailLocalElement && emailDomainElement && emailElement) {
        function combineEmail() {
            let e = emailLocalElement.value,
                l = emailDomainElement.value;
            emailElement.value = e + l;
        }

        // 入力が変更されるたびにcombineEmailを呼び出す
        emailLocalElement.addEventListener('input', combineEmail);
        emailDomainElement.addEventListener('input', combineEmail);
    }
};
