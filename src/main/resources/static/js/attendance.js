function confirmAction(e) {
    let t;
    switch(e) {
        case "start":
            t = "出勤を打刻してよろしいですか？";
            break;
        case "end":
            t = "退勤を打刻してよろしいですか？";
            break;
        case "breakStart":
            t = "休憩開始を打刻してよろしいですか？";
            break;
        case "breakEnd":
            t = "休憩終了を打刻してよろしいですか？";
            break;
        default:
            t = "操作を行ってもよろしいですか？";
    }
    return confirm(t);
}

document.addEventListener("DOMContentLoaded", function () {
    // 勤怠情報の時間を表示する処理
    document.querySelectorAll(".break-time").forEach(function(e) {
        let t = e.getAttribute("data-break-time");
        e.textContent = Math.floor(t / 60) + " 時間 " + t % 60 + " 分";
    });
    document.querySelectorAll(".working-time").forEach(function(e) {
        let t = e.getAttribute("data-working-time");
        e.textContent = Math.floor(t / 60) + " 時間 " + t % 60 + " 分";
    });

    // 年と月の変更イベント
    let e = document.getElementById("yearSelect");
    let t = document.getElementById("monthSelect");

    function n() {
        let n = e.value;
        let a = t.value;
        window.location.href = window.location.pathname + "?year=" + n + "&month=" + a;
    }

    e.addEventListener("change", n);
    t.addEventListener("change", n);

    // モーダル関連
    const modal = document.getElementById("forceEndModal");
    const btn = document.getElementById("showForceEndModal");
    const closeBtn = document.querySelector(".modal .close");

    // ボタンを押したらモーダルを表示
    if (btn) {
        btn.addEventListener("click", function () {
            modal.style.display = "block";
        });
    }

    // ×ボタンを押したらモーダルを閉じる
    if (closeBtn) {
        closeBtn.addEventListener("click", function () {
            modal.style.display = "none";
        });
    }

    // モーダルの外側をクリックしたら閉じる
    window.addEventListener("click", function (event) {
        if (event.target === modal) {
            modal.style.display = "none";
        }
    });
});
