function addAllowance(){let e=document.getElementById("allowances-container"),n=document.createElement("div");n.classList.add("allowance-item"),n.innerHTML=`
        <input type="text" name="allowanceName[]" placeholder="手当の名称">
        <input type="number" name="allowanceAmount[]" placeholder="金額を入力">
        <button type="button" onclick="removeAllowance(this)">削除</button>
    `,e.appendChild(n)}function removeAllowance(e){e.parentElement.remove()}