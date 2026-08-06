// htmx のグローバル設定。CSRF トークンをリクエストヘッダーへ自動付与する。
document.addEventListener('htmx:configRequest', (event) => {
  const tokenMeta = document.querySelector('meta[name="_csrf"]');
  const headerMeta = document.querySelector('meta[name="_csrf_header"]');
  if (tokenMeta && headerMeta) {
    event.detail.headers[headerMeta.content] = tokenMeta.content;
  }
});
