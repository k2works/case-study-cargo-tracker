---
title: IT5 セルフレビュー
date: 2026-06-22
---

# IT5 セルフレビュー（中間レビュー）

> マルチパースペクティブレビュー 2 段階運用に基づく中間レビュー（Ralph Loop 1 ターン内）。
> staging 完了後に正式な `developing-review`（XP 5 エージェント並列）を実施する。

## サマリ

| 観点 | 重大度 | 件数 |
|------|-------|------|
| プログラマー | 高 | 3 |
| テスター | 高 | 2 |
| アーキテクト | 高 | 2 |
| 中以下（参考観察） | 中 | 3 |

IT4 申し送り（H1-H6）は全件解消済。本セルフレビューは IT5 で新規追加したコード（US14/US15/US18、+ Tracking/Handling Context 新設）を対象とする。

---

## プログラマー観点

### H1. `TrackingActivity` 集約のバージョン非整合（`addEvent` / `appendEvent` 後の戻り値）

- **箇所**: `TrackingActivity.scala:30-37` / `TrackingCommandService.scala:60-68`
- **問題**: `addEvent` は `version` を更新しないため、`appendEvent` で DB 側を `version+1` に進めた後も、`recordEvent` の戻り値 `updated` は古いバージョンのまま。呼び出し側がこの戻り値を再利用すると次回操作で楽観ロック失敗する。
- **修正方針**: (a) `appendEvent` が新バージョンを `updated` として返す API に変更（`Unit` → `TrackingActivity`）、(b) または `addEvent` 時点で `copy(version = version + 1)` する。後者は永続化前に in-memory バージョンを進めるリスクあり、(a) が安全。

### H2. `Cargo.trackingNumber` が `Option[String]` の生型（コンテキスト型分離違反気味）

- **箇所**: `Cargo.scala:19-27` / `BookingCommandService.issueTracking:151-159`
- **問題**: `TrackingNumber` は Tracking Context の opaque type だが、Booking Context 側では `Option[String]` で受領。文字列のまま保存・参照されており、フォーマット検証なし。書込時の `trackingNumber` 引数も `String`。
- **修正方針**: Booking Context 内に `BookingTrackingNumber`（opaque type / 軽量検証）を導入するか、Cargo は文字列のまま保ちつつ `issueTracking(trackingNumber: String)` で「`TN-` プレフィクス + 数字」のバリデーションを行う。IT6 で `TrackingNumber` 型表現を検討。

### H3. `HandlingController.orchestrateRegistration` の 4 段呼出は分散トランザクション境界が曖昧

- **箇所**: `HandlingController.scala:80-112`
- **問題**: `handlingCommandService.register` → `trackingCommandService.recordEvent` → `bookingCommandService.logHandlingNotification` の 3 段を `for` で順次実行するが、それぞれが独立トランザクション（`DB.localTx`）。途中で失敗すると HandlingActivity だけ残り Tracking 履歴が欠落するなどの中途半端な状態になる。
- **修正方針**: (a) 単一トランザクション境界を持つ orchestration サービスを application 層に新設し ACL を経由、(b) または失敗時の補償ロジック（イベント駆動 / アウトボックスパターン）を採用。IT6 以降の課題として記録。

---

## テスター観点

### H4. `addEvent` の時系列検証（`OutOfOrder`）の境界値テストが欠落

- **箇所**: `TrackingActivitySpec.scala`（事象なし）
- **問題**: `addEvent` の不変条件 2「最終イベントより過去の時刻を拒否」を直接検証するユニットテストが欠落。`recordEvent` E2E にも該当ケースなし。
- **修正方針**: `TrackingActivitySpec` に「先に Load(t1) を追加、その後 Receive(t0) を追加 → `Left(OutOfOrder)`」「同時刻イベントは許容」の 2 ケースを追加。

### H5. `appendEvent` の楽観ロック衝突シナリオが未検証

- **箇所**: `ScalikeJdbcTrackingActivityRepository.scala:136-140`
- **問題**: `OptimisticLockException` を throw するパスは IT5 race condition で「偶然」露呈し fix したが、明示的なテストがない。並行 2 リクエストが同じ tracking_number に対して `appendEvent` した場合の挙動仕様が固定されていない。
- **修正方針**: `ScalikeJdbcTrackingActivityRepositorySpec`（integration）に「old_version を持つ activity を save → 期待 OptimisticLockException」テストを追加。並行性は将来 ACL リトライ層で吸収。

---

## アーキテクト観点

### H6. `HandlingCommandService` が ACL を持たず Tracking 側の整合性を保証できない

- **箇所**: `HandlingCommandService.scala` / `HandlingController.scala`
- **問題**: ドメインモデル設計（domain-model.md L855）では `HandlingActivity ..> CargoSnapshot : validates against` とあるが、IT5 実装では `CargoSnapshot` 値オブジェクト不在。Cargo 状態・Itinerary との整合性検証は Controller 経由でも実施されていない（`HandlingCommandService.register` は単純な入力検証のみ）。
- **修正方針**: `CargoSnapshot`（Handling Context 内、Booking から取得した snapshot を保持する ACL VO）を新設し、`HandlingCommandService.register` に注入。Cargo 状態 `TrackingIssued`/`InTransit` のみ受領可とする業務制約を IT6 で追加。

### H7. `transport_status` が DB に冗長保存され Read Model キャッシュとして機能していない

- **箇所**: `tracking_activity.transport_status` / `TrackingActivity.transportStatus`
- **問題**: 集約状態 = イベント履歴から導出（`deriveStatus`）が原則だが、`transport_status` カラムを書込時に同期するだけで、読取時に整合性検証なし。`addEvent` 経由でない手段（直接 UPDATE 等）で transport_status と events が乖離する可能性。
- **修正方針**: (a) Read Model 専用テーブル（`tracking_view`）に分離、(b) または書込時のドメイン不変条件として「events から導出した status が DB の transport_status と一致すること」を assertion 化。IT6 で再検討。

---

## 参考観察（中以下）

- **O1**: `publicDetail.scala.html` / `publicNotFound.scala.html` で Bootstrap CDN リンクを直書き重複（`layout/public.scala.html` のような共通レイアウトに切り出し可能）
- **O2**: `nextTrackingNumber` が `SELECT MAX(id) + 1` 方式で並行採番に弱い（PostgreSQL シーケンス / `DEFAULT nextval()` に移行すべき）。ADR 0010 で「IT5 段階の単純実装」と明記済み
- **O3**: ルート逸脱判定（タスク 2.7）が `false` 固定で未実装。`Itinerary` に leg 詳細を持たせる IT6 拡張後に再評価する旨は `HandlingCommandService.scala` のコメントで明記済

---

## IT5 申し送り（H1-H7 全件 IT6 以降）

| ID | 方針 | 優先度 |
|----|------|--------|
| H1 | `appendEvent` 戻り値を `TrackingActivity` に変更 | 高（IT6 早期） |
| H2 | `BookingTrackingNumber` opaque type 検討 + `issueTracking` バリデーション | 中（IT6） |
| H3 | orchestration サービス + 単一トランザクション境界 | 高（IT6 設計レビュー） |
| H4 | `TrackingActivity.addEvent` の `OutOfOrder` テスト追加 | 高（IT6 早期） |
| H5 | `ScalikeJdbcTrackingActivityRepository` の楽観ロック integration test | 中（IT6） |
| H6 | `CargoSnapshot` ACL + 業務制約検証 | 高（IT6 設計） |
| H7 | `transport_status` キャッシュ整合性の仕組み化 | 中（IT6 / 必要に応じて Read Model 分離） |
| O1 | 公開ページ用 `layout/public.scala.html` 切り出し | 低 |
| O2 | tracking_number 採番を PostgreSQL シーケンス化 | 中（負荷検証時） |
| O3 | ルート逸脱判定の Itinerary leg 詳細実装 | 中（IT6） |

## 次アクション

- すべて IT6 申し送り（`iteration_report-5.md` の「IT6 への申し送り」に統合）
- IT5 staging 完了後に正式な `developing-review`（XP 5 エージェント並列）を実施
