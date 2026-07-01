# 0011 荷役オフライン対応方式 (Service Worker + IndexedDB キュー + BackgroundSync)

港湾荷役作業員がネットワーク切断状態でも荷役登録を継続できるようにする、ブラウザ側キュー方式の選定

日付: 2026-07-01

## ステータス

提案 (2026-07-01、IT5 task 8.1/8.2/8.3 で実装予定、ストレッチ扱い)

Sprint 0 H-14 で「港湾は電波状況が不安定で荷役登録の中断が業務阻害要因」と特定され、IT5 でオフライン対応の MVP を提案するに至った。実装が想定以上に複雑な場合は Release 1.1 に分割する退避方針も同時に決定 (iteration_plan-5.md task 8 拡張 SP)。

## コンテキスト

US15 (荷役作業を記録する) の受入基準:

> 荷役作業員は港湾内で HandlingType を選択して現場登録できる。電波状況が不安定な場合でも記録が失われず、ネットワーク復帰後に自動送信される。

港湾内 (特に大型コンテナヤードや倉庫内部) は Wi-Fi / LTE の受信が不安定で、荷役作業員のスマートフォン / タブレット端末で数十秒〜数分のネットワーク切断が発生する。切断中に POST `/handling/new` を送信すると即時失敗し、荷役作業員が手動で再入力する運用は業務効率の観点で許容できない。

Web 標準ベースの対応方式として以下を検討した:

- (A) **Service Worker + IndexedDB + BackgroundSync API**: PWA 標準。切断時に IndexedDB キューへ保存、`sync` イベントで自動再送
- (B) **localStorage + polling 再送**: 実装単純、ただし容量制限 (5-10MB)・sync 保証弱
- (C) **専用ネイティブアプリ (Android/iOS)**: 対応 OS 数が増え、業務範囲外
- (D) **オフライン対応なし**: 現状放置、業務阻害

BackgroundSync API は Chrome/Edge/Opera でサポート、Safari は未対応 (iOS ではフォアグラウンド即時再送で代替)。ブラウザ差異は運用者の端末選定で吸収する。

## 決定

**(A) Service Worker + IndexedDB キュー + BackgroundSync API を採用する**。

- **Service Worker**: `static/js/sw.js` を `/handling/new` スコープで登録
- **IndexedDB キュー**: `handling-queue` object store に `{ id: uuid, payload: FormData, createdAt: number, attemptCount: number }` を保存
- **fetch intercept**: `/handling/new` への POST を Service Worker が横取り、オンラインなら即時 forward、オフラインなら IndexedDB queue に保存し UI に「オフライン検知、復帰時に自動送信」flash を返す
- **BackgroundSync**: `self.registration.sync.register('handling-queue')` で再送タスクを登録。ネットワーク復帰時にブラウザが自動的に `sync` イベントを発火 → IndexedDB queue を順次 POST 送信
- **retry**: 送信失敗時は `attemptCount` を +1、5 回で lock (SEC-04 準拠のパターン)、失敗ログを UI に表示
- **順序保証**: `createdAt` 昇順で送信 (楽観ロック衝突を回避)
- **Safari フォールバック**: BackgroundSync 未対応環境では `visibilitychange` + `online` イベントで polling 再送

## 影響

- **新規ファイル**:
  - `static/js/sw.js` (Service Worker 本体)
  - `static/js/offline-queue.js` (IndexedDB CRUD ラッパー)
  - `static/js/handling-form.js` (Service Worker 登録 + UI 連携)
- **既存への影響**:
  - `Views.Handling.New.hs` に `<script src="/static/js/handling-form.js" defer>` を追加
  - `Layout.hs` に `<meta name="theme-color">` 等 PWA hint を追加 (任意)
- **セキュリティ**:
  - Service Worker は同一オリジンでのみ動作、CSRF トークン (既存 Double Submit Cookie) は IndexedDB queue と一緒に保存し復帰時に再送
  - 個人情報 (荷役作業員 ID、荷主情報) が IndexedDB に一時保存されるため、`Cache-Control: no-store` および期限 (24h) 経過で自動 purge
- **テスト**:
  - Playwright で `context.setOffline(true/false)` を使った E2E シナリオを追加 (IT5 task 7.4 / IT6)
  - hspec-wai は Service Worker 未実行なので、サーバ側は通常 POST として検証

## 将来の水平スケール時 / 分割方針

- **Release 1.1 分割トリガ**: IT5 task 8.1/8.2 の実装時点で Service Worker + IndexedDB の学習コストが 2 SP を超える見込みの場合、ADR-0011 の Status を「提案 (Release 1.1 に分割)」に変更し IT5 では ADR 起票のみで完結する
- **将来検討**: PWA install (`beforeinstallprompt`) は現段階では見送り、オフライン対応が浸透したら別 ADR で追加

## 関連

- Sprint 0 H-14 (荷役オフライン対応の要求特定)
- `docs/design/ui_design.md` §荷役登録画面 (L483 wireframe + L544 Service Worker 記述)
- `docs/development/iteration_plan-5.md` task 8.1 / 8.2 / 8.3
- ADR-0010 (セッション認証、CSRF 共存規約)
