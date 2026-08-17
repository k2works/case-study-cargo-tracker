/*
 * 自動実行デモの進み具合を描く。
 *
 * **繰り返し読みに来る。** 実行はサーバー側の別スレッドで進むため、
 * 画面は定期的に問い合わせて差分を描き直す。
 *
 * **終わったら止める。** 完了・失敗のあとも問い合わせ続けると、
 * 開いたままのタブが延々と要求を出し続ける。
 */
(function () {
  'use strict';

  var script = document.currentScript;
  var progressUrl = script.getAttribute('data-progress-url');
  var trackingUrl = script.getAttribute('data-tracking-url');

  var stepsEl = document.getElementById('demo-steps');
  var barEl = document.getElementById('demo-progress-bar');
  var barWrapEl = document.getElementById('demo-progress-bar-wrap');
  var resultEl = document.getElementById('demo-result');
  var trackLinkEl = document.getElementById('demo-track-link');

  /** 手順の状態ごとの見た目。 */
  var BADGES = {
    RUNNING: { className: 'text-bg-primary', label: '実行中' },
    DONE: { className: 'text-bg-success', label: '完了' },
    FAILED: { className: 'text-bg-danger', label: '失敗' }
  };

  function render(progress) {
    stepsEl.replaceChildren();
    progress.steps.forEach(function (step) {
      stepsEl.appendChild(stepItem(step));
    });

    var done = progress.steps.filter(function (s) { return s.state === 'DONE'; }).length;
    var percent = Math.round((done / progress.totalSteps) * 100);
    barEl.style.width = percent + '%';
    barWrapEl.setAttribute('aria-valuenow', String(percent));

    if (!progress.finished) {
      return;
    }
    barEl.classList.remove('progress-bar-animated', 'progress-bar-striped');
    showResult(progress);
  }

  function stepItem(step) {
    var li = document.createElement('li');
    li.className = 'list-group-item d-flex justify-content-between align-items-start gap-2';

    var body = document.createElement('div');
    body.className = 'ms-2 me-auto';

    var title = document.createElement('div');
    title.className = 'fw-semibold';
    title.textContent = step.title;
    body.appendChild(title);

    var detail = document.createElement('div');
    detail.className = 'small text-muted';
    // **担当を必ず出す。** どのロールの仕事かが分からないと、
    // あとで自分の画面から同じことを辿れない
    detail.textContent = step.detail ? step.actor + '／' + step.detail : step.actor;
    body.appendChild(detail);

    li.appendChild(body);

    var badge = BADGES[step.state] || BADGES.RUNNING;
    var span = document.createElement('span');
    span.className = 'badge rounded-pill ' + badge.className;
    span.textContent = badge.label;
    li.appendChild(span);

    return li;
  }

  function showResult(progress) {
    resultEl.classList.remove('d-none', 'alert-info', 'alert-success', 'alert-danger');
    if (progress.state === 'COMPLETED') {
      resultEl.classList.add('alert-success');
      resultEl.textContent = '予約から請求まで一本つながりました。追跡番号は '
        + progress.trackingNumber + ' です。';
      if (trackingUrl && progress.trackingNumber) {
        trackLinkEl.href = trackingUrl + '?trackingNumber='
          + encodeURIComponent(progress.trackingNumber);
        trackLinkEl.classList.remove('d-none');
      }
      return;
    }
    resultEl.classList.add('alert-danger');
    resultEl.textContent = '途中で止まりました: ' + (progress.failureReason || '理由は不明です');
  }

  function poll() {
    fetch(progressUrl, { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('進み具合を取得できませんでした（' + response.status + '）');
        }
        return response.json();
      })
      .then(function (progress) {
        render(progress);
        if (!progress.finished) {
          window.setTimeout(poll, 700);
        }
      })
      .catch(function (error) {
        // **黙って止まらない。** 通信が切れたことを画面に出さないと、
        // 実行が止まったのか画面が止まったのか判断できない
        resultEl.classList.remove('d-none', 'alert-info');
        resultEl.classList.add('alert-danger');
        resultEl.textContent = error.message;
      });
  }

  poll();
})();
