window.onload = function() {

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
