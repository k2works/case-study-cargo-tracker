// 開発環境のログイン画面で、一覧の利用者 ID をクリックしたら入力欄に反映する。
//
// 本スクリプトはログイン画面で app.demo-login が有効なときにのみ読み込まれる。
// インラインの onclick を使わないのは、後で CSP を導入したときに壊れないようにするため。
document.addEventListener('DOMContentLoaded', function () {
  var input = document.getElementById('username');
  if (!input) {
    return;
  }
  document.querySelectorAll('.demo-account').forEach(function (button) {
    button.addEventListener('click', function () {
      input.value = button.dataset.username;
      input.focus();
    });
  });
});
