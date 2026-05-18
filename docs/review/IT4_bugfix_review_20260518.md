---
title: IT4 バグ修正コードレビュー
description: IT4 E2E テスト全通過のための 4 件のバグ修正（@TargetEntityId・sendAndWait・gateway ルーティング・VARCHAR 拡張）に対する XP 5 エージェント並列レビュー。
---

# IT4 バグ修正コードレビュー

## レビュー対象

| 項目 | 内容 |
|------|------|
| **対象コミット** | `c6522909..3ce96c55`（IT4 E2E バグ修正 4 件） |
| **レビュー日** | 2026-05-18 |
| **レビュー方式** | XP 5 エージェント並列レビュー |

### 変更ファイル一覧

| ファイル | 変更内容 |
|---------|---------|
| `AssignRouteToCargoCommand.java` | `@TargetEntityId` 追加 |
| `ConfirmBookingCommand.java` | `@TargetEntityId` 追加 |
| `IssueTrackingNumberCommand.java` | `@TargetEntityId` 追加 |
| `NotifyRouteCommand.java` | `@TargetEntityId` 追加 |
| `BookingController.java` | `send()` → `sendAndWait()` 3 箇所 |
| `V005__extend_tracking_number.sql` | `tracking_number VARCHAR(25)` |
| `BookingControllerIntegrationTest.java` | モック `send` → `sendAndWait` |
| `gatewayms/application.yml` | `/api/v1/routing/**` 追加 |
| E2E テスト 5 ファイル | ロケーター修正 |

---

## 総合評価

4 件のバグはいずれも「存在すれば確実に障害となる」クリティカルなものであり、根本原因の特定と修正は的確。特に Axon `@TargetEntityId` 欠落とゲートウェイルーティング漏れは、テストなしでは発見困難なインフラ層の問題であり、E2E テストがそれらを検出したことはアーキテクチャ投資として価値が高い。一方、`sendAndWait()` 同期化の副作用（タイムアウト未指定・スレッドブロッキング）と E2E ロケーターの脆弱性は IT5 で対処すべき技術的負債として残る。

---

## 改善提案（重要度順）

### 高（IT5 着手前に対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | ArchUnit で `@TargetEntityId` 未付与コマンドを CI で検出 | bookingms テスト | xp-architect | 手動レビュー依存では再発必至 |
| H2 | `sendAndWait()` のデフォルトタイムアウトを明示指定 | `BookingController.java` | xp-programmer / xp-tester | 障害時に無限待機となりスレッドを枯渇させる恐れ |
| H3 | confirm・issue-tracking の統合テストも `sendAndWait` に更新 | `BookingControllerIntegrationTest.java` | xp-tester | assign-route のみ更新済みで不整合。production と verify の乖離 |
| H4 | Axon Saga / ProcessManager への移行検討 ADR 起票 | bookingms ↔ routingms | xp-architect | `sendAndWait` 連鎖は分散デッドロックの温床。IT5 の tracknigms 設計前に方針確定が必要 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `data-testid` 属性を UI 要素に付与 | E2E テスト全般 | xp-programmer / xp-tester | `.first()` はナビ構造変更でサイレントに誤要素を掴む |
| M2 | gatewayms predicates を YAML リスト形式に変更 | `application.yml` | xp-programmer | 文字列連結より 1 行 1 パス記法の方が差分が読みやすい |
| M3 | Tracking Number フォーマット仕様を ADR に記録 | `TrackingNumber.java` | xp-architect / xp-user-representative | `VARCHAR(25)` の根拠と桁設計を明文化。将来の拡張時に参照できる |
| M4 | `sendAndWait` 変更理由を Javadoc に追記 | `BookingController.java` | xp-technical-writer | git blame なしに「なぜ同期化したか」を理解できるようにする |
| M5 | `NotifyRouteCommand` に IT5+ メール送信予定を記載 | コマンド Javadoc | xp-technical-writer | コントローラだけでなくコマンド側にも制約を明記 |
| M6 | `sendAndWait` でレスポンス遅延が 1-2 秒超の場合は処理中インジケータ追加 | フロントエンド | xp-user-representative | 二重クリックによる二重予約を防ぐ |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | ナビバーに「見積」リンクを追加 | フロントエンド AppLayout | xp-programmer / xp-tester | `page.goto('/quotations')` 直接遷移では UI ナビゲーション欠落を検出できない |
| L2 | 経路候補 0 件時のメッセージを業務理由まで表示 | 経路設計ワークベンチ UI | xp-user-representative | 「候補がありません」だけでは利用者が対処できない |
| L3 | ダッシュボード・ナビバー双方の「荷主管理」リンク重複を整理 | UI コンポーネント | xp-user-representative | 片方の更新漏れリスク |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | xp-programmer: `sendAndWait()` 同期化は意図が明確で妥当 | xp-architect: 連続 `sendAndWait` はスレッドブロッキング・分散デッドロックのリスク | IT4 現状で `sendAndWait` 採用は正当。ただし IT5 以降の連鎖 Aggregate 操作では Saga 化を検討すべき | IT5 で ADR 起票。IT4 はそのまま |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 2 / 中: 2 / 低: 1）</summary>

### 評価サマリー
バグ修正としては的確で、Axon の規約遵守と同期処理化により整合性問題を解消しています。ただし `sendAndWait()` の連続呼び出しと E2E の brittle なロケータに改善余地があります。

### 良い点
- `@TargetEntityId` 追加は Axon の規約に従い、ルーティング不整合の根本原因を解消
- `notifyRoute` のみ `send()` のまま残した判断は通知の fire-and-forget 設計と一貫
- Flyway による DDL 変更がスキーマ進化として適切に管理されている

### 改善提案
- 【高】BookingController: 3 連続 `sendAndWait()` は同期ブロッキングが直列化し、レイテンシ悪化とトランザクション境界が不明瞭。Saga / ProcessManager 化、または `CompletableFuture` 合成を検討
- 【高】`sendAndWait()` のデフォルトタイムアウト未指定。障害時に無限待機する可能性
- 【中】E2E テスト: `.first()` はナビ構造変更でサイレントに誤要素を掴むリスク。`data-testid` 属性を導入すべき
- 【中】gatewayms YAML: Path 述語の文字列連結は読みにくい。YAML リスト記法で 1 行 1 パスに
- 【低】ナビバーに「見積」リンク不在は UX 不整合。バックログ化推奨
</details>

<details>
<summary>xp-tester（高: 2 / 中: 2 / 低: 0）</summary>

### 評価サマリー
sendAndWait への切り替えと E2E ロケータの厳密化は妥当な修正ですが、テスト一貫性・回帰防止・境界値検証の観点で穴が残っています。

### 良い点
- E2E で `exact: true` + `toHaveURL` を併用し、strict mode 違反と false-positive 両方を防いでいる点
- Flyway V005 がテスト DB にも自動適用される構成で、スキーマ変更がテスト環境と乖離しない点

### 改善提案
- 【高】confirm/issue-tracking の統合テストで `send` → `sendAndWait` の更新が未反映。全 controller integration test を一括更新すべき
- 【高】V005 migration: tracking_number=25 文字の境界値テストと既存データの回帰テストを追加
- 【中】E2E `page.goto('/quotations')` 直接遷移はナビゲーション欠落を検出できない
- 【中】`.first()` 多用はリンク重複の温床。`toHaveCount(1)` で重複自体を検証

### 懸念事項
- `sendAndWait` 化で異常系（タイムアウト・CommandExecutionException）のテストが見当たらない
- E2E がアイスクリームコーン化の兆候。ナビゲーション検証はコンポーネントテストへ降ろすべき
</details>

<details>
<summary>xp-architect（高: 2 / 中: 2 / 低: 1）</summary>

### 評価サマリー
バグの直接原因には正しく対処していますが、「フレームワーク契約の規約化と境界の明示」が弱く、再発リスクが残ります。

### 良い点
- `@TargetEntityId` 欠落と Gateway predicate 漏れという「契約違反」を正しく特定
- `notifyRoute` のみ fire-and-forget を残し、整合性要件で send/sendAndWait を使い分け

### 改善提案
- 【高】ArchUnit で `@TargetEntityId` を強制するアーキテクチャテスト追加
- 【高】`sendAndWait` 連鎖は Saga か外部 ProcessManager へ寄せる ADR を起票
- 【中】gatewayms: predicate 管理方針を ADR で固定
- 【中】Flyway V005: VARCHAR(25) の根拠（採番仕様）をマイグレーション header に記載
- 【低】E2E に「コマンド→投影反映」の契約テストを追加

### 懸念事項
- 同期化により bookingms→routingms の時間結合が増加。routingms 障害が予約フローを直撃
- Event Sourcing 側と CRUD/MyBatis 側の整合方式が暗黙。境界づけられたコンテキスト間の統合パターンを ADR 化すべき
</details>

<details>
<summary>xp-technical-writer（高: 1 / 中: 1 / 低: 1）</summary>

### 評価サマリー
IT4 修正は技術的には妥当ですが、ユーザー視点での「設計改善でドキュメント不要化」の余地と、ユーザー向け説明の欠落が目立ちます。

### 良い点
- `BookingController.java` のコメントが「IT4 はログのみ、メール送信は IT5+」と将来の挙動を明示
- `V005` のコメントが「4+1+8+1+8=22」と算出根拠を示しており、なぜ 25 かが自明

### 改善提案
- 【高】`BookingController.java`: `send()` → `sendAndWait()` 変更の理由を Javadoc に 1 行追記
- 【中】`NotifyRouteCommand.java` 等: 「IT4 はログのみ」の制約をコマンドの Javadoc にも記載
- 【低】TRK フォーマット定義は TrackingNumber VO に集約すると DRY

### スコープ外の発見
- `gatewayms/application.yml` のルーティング追加は外部公開エンドポイント変更の可能性。OpenAPI ドキュメントの更新要否を確認
</details>

<details>
<summary>xp-user-representative（高: 0 / 中: 1 / 低: 2）</summary>

### 評価サマリー
経路設計から予約確定、追跡番号発行までの一連の業務フローがようやく実用レベルで動くようになりました。今回の修正はユーザー価値の観点で必須の対応です。

### 良い点
- 経路候補が表示されるようになり、オペレーターが比較・選択する本来の業務が成立する
- 同期化により「予約できたのか分からない」という不安が解消され、追跡番号の即時提示も実現
- ナビバーの「予約」表記は現場の口頭呼称と一致

### 改善提案
- 【中】`sendAndWait()` 同期化によりレスポンスが遅延する場合は処理中インジケータが必須（二重予約防止）
- 【低】追跡番号桁設計の根拠を ADR に記録
- 【低】ダッシュボード・ナビバー双方の「荷主管理」リンク重複を整理

### 懸念事項
- 経路候補 0 件時のメッセージが業務理由（出発地と到着地が同一等）まで示せているか要確認
</details>

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-18 | 初版作成（IT4 バグ修正 5 エージェント並列レビュー） | AI Agent |
