/*
 * デモモード中の画面を、裏で進む業務に追随させる。
 *
 * **2 つの間隔で動く。**
 *   - 帯（何が起きたか）は 1 秒ごとに書き換える。ページは読み込み直さない
 *   - 業務の中身は refresh-ms ごとにページごと読み込み直す
 *
 * 帯だけを速く更新するのは、**再読み込みの直前に「何が起きるのか」を
 * 予告できる**ようにするためである。一覧が急に変わる理由が分からないと、
 * 利用者は自分の操作で変わったのかどうか判断できない。
 */
(function () {
  'use strict';

  var script = document.currentScript;
  var statusUrl = script.getAttribute('data-status-url');
  var refreshMs = parseInt(script.getAttribute('data-refresh-ms'), 10) || 5000;

  /** 帯を書き換える間隔。**再読み込みより短くする。** */
  var BANNER_INTERVAL_MS = 1000;

  var summaryEl = document.getElementById('demo-mode-summary');
  var latestEl = document.getElementById('demo-mode-latest');

  /*
   * **入力中は読み込み直さない。**
   *
   * 再読み込みは入力中の内容を捨てる。デモを見せている最中に登録フォームを
   * 触っている人がいると、**打ち込んだ内容が数秒ごとに消える**ことになる。
   * それは業務が進む様子を見せるどころか、画面が壊れているようにしか見えない。
   */
  function isEditing() {
    var el = document.activeElement;
    if (!el) {
      return false;
    }
    var tag = el.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
      return true;
    }
    return el.isContentEditable === true;
  }

  /*
   * **開いている確認ダイアログ（modal）の最中も読み込み直さない。**
   * 押そうとしていたボタンが消える。
   */
  function isModalOpen() {
    return document.querySelector('.modal.show') !== null;
  }

  function describe(event) {
    if (!event) {
      return '';
    }
    var who = event.actor ? event.actor + 'が' : '';
    var what = who + event.what;
    if (event.trackingNumber) {
      return what + '（' + event.trackingNumber + '）';
    }
    if (event.shipperName) {
      return what + '（' + event.shipperName + '）';
    }
    return what;
  }

  function renderBanner(status) {
    if (!status.running) {
      // **止まったら帯を消す。** 出たままだと、まだ動いていると思わせる
      var banner = document.getElementById('demo-mode-banner');
      if (banner) {
        banner.remove();
      }
      stop();
      return;
    }
    summaryEl.textContent =
      '進行中 ' + status.activeCargo + ' 件／完了 ' + status.completedCargo + ' 件' +
      (status.failedCargo > 0 ? '／停止 ' + status.failedCargo + ' 件' : '');
    latestEl.textContent = describe(status.recentEvents[0]);
  }

  var bannerTimer = null;
  var refreshTimer = null;

  function stop() {
    window.clearInterval(bannerTimer);
    window.clearInterval(refreshTimer);
  }

  function pollBanner() {
    fetch(statusUrl, { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('状況を取得できませんでした（' + response.status + '）');
        }
        return response.json();
      })
      .then(renderBanner)
      .catch(function (error) {
        // **黙って止まらない。** 帯が更新されない理由を画面に出す
        latestEl.textContent = error.message;
      });
  }

  function refresh() {
    if (isEditing() || isModalOpen()) {
      return;
    }
    window.location.reload();
  }

  bannerTimer = window.setInterval(pollBanner, BANNER_INTERVAL_MS);
  refreshTimer = window.setInterval(refresh, refreshMs);
  pollBanner();
})();
