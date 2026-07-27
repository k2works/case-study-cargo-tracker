---
title: IT6 開発レビュー（マルチパースペクティブ）
description: IT6（US14 追跡番号発行・US15 荷役記録・US16 引取記録・US18 追跡照会・追跡/荷役 BC 新設）の XP 5 視点統合レビューと対応。
tags: review, development, iteration-6, go
---

# IT6 開発レビュー（マルチパースペクティブ）

対象: IT6 実装（`git diff 8344cf8f HEAD`）。追跡（Tracking）・荷役（Handling）の 2 BC 新設で Phase 2 完了（Release 0.2）。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）を並列レビューし統合。

## エグゼクティブサマリー

追跡・荷役の 2 BC を 14 SP で新設。ドメイン層カバレッジ 98-100%、`make arch` 全 green、BC 独立性は ACL ポート（`CargoSnapshotProvider`・`HandlingEventPublisher`・`TrackingActivityCreator`・`TrackingNumberIssuer`）と合成ルート注入で担保。programmer/architect は「BC 独立性・防御的コピー・値オブジェクトの不変条件は高水準」と評価。一方、**BC 間状態同期の整合性境界**と**妥当性検証（MISROUTED/警告）のフィードバック欠落**が主要弱点として複数視点で収束した。高優先度の実装欠陥（CUSTOMS 状態退行・MISROUTED 未反映・警告未表示・公開ページ情報露出・ErrTrackingNotFound 握り潰し）はクローズ前に対応。原子性・履歴リプレイ・統合テスト・フルフロー E2E は ADR-0008 / IT7 の技術的負債として明示繰越した。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高/中優先度指摘 | 対応 |
|------|------|--------------------|------|
| Programmer | 良質（高 2） | CUSTOMS→UNKNOWN 状態退行・採番非原子/競合・DRY（正規表現/表示語彙重複） | 退行を修正（✅）・採番は ADR-0008 で IT7 負債化 |
| Tester | 不完全ピラミッド（高 2） | 統合テスト（testcontainers）全欠如・フルフロー E2E 欠如・IsValidFor 分岐欠落・CUSTOMS 未検証 | 分岐/CUSTOMS/EXCEPTION テスト + handler httptest 追加（✅）・統合/フルフロー E2E は IT7 繰越 |
| Architect | 骨格健全（高 2） | 発行フロー非トランザクション・ErrTrackingNotFound 握り潰しでイベントロスト | ログ追加（✅）・整合性境界を ADR-0008 に記録・原子化/リプレイは IT7 |
| Technical Writer | 命名統一ほぼ完全（中 3） | 公開ページが全履歴公開（仕様は最新のみ）・公開ページ仕様要素欠落・architecture_frontend のポーリング設計未同期 | 公開ページ是正（✅）・architecture_frontend 同期は IT7 |
| User Representative | 基本導線通る（高 2） | MISROUTED/警告が作業員に届かない・MISROUTED が荷主追跡に反映されない・ワークリスト欠落 | 警告 flash 表示 + EXCEPTION 反映（✅）・ワークリストは T2/T3 で IT7 |

## クローズ前に対応した指摘（高優先度）

- **CUSTOMS 状態退行（programmer/tester 高）**: `TransportStatus.CurrentStatus()` が UNKNOWN イベントを読み飛ばし現状態を維持するよう修正。履歴には CUSTOMS イベントを残す。テスト「LOAD→CUSTOMS 後も LOADED」を追加。
- **MISROUTED が荷主追跡に未反映（user-rep 高）**: `RecordHandlingEvent` が Misrouted 時に輸送状態を EXCEPTION として記録し、US18 追跡照会に反映。テスト追加。
- **警告が荷役作業員に届かない（user-rep 高）**: 荷役登録後の PRG で `?warning=` を読み、荷役一覧に警告バッジ（`handling-warning`）を表示。MISROUTED/警告メッセージを URL エンコードで安全に伝播。
- **公開ページの情報露出（tech-writer/user-rep 中）**: 公開追跡を最新イベントのみに絞り、反映タイムラグ注記・連絡先フッターを追加（ui_design 仕様準拠）。
- **ErrTrackingNotFound 握り潰し（architect/programmer 高）**: 追跡番号未発行時の荷役スキップに `slog.WarnContext` を追加。整合性境界と履歴リプレイ方針を ADR-0008 に記録。
- **テスト空洞（tester 高/中）**: tracking/handling の web handler に httptest テストを追加（到達性・PRG・エラー・not-found）。IsValidFor の UNLOAD 一致・第 2 レグ一致の分岐テストを追加。

## 次イテレーション（IT7）への Try（保留・繰越）

| 指摘 | 内容 | 優先度 |
|------|------|--------|
| Architect/Programmer 高 | 追跡番号採番の原子化（DB シーケンス/採番テーブル）・発行フローの単一 tx or outbox 化・UNIQUE 衝突リトライ（ADR-0008） | 高 |
| Architect/Programmer 高 | 追跡レコード作成時の既存荷役履歴リプレイ（発行前荷役のイベントロスト解消） | 高 |
| Tester 高 | tracking/handling リポジトリの統合テスト（testcontainers・採番一意性・時系列復元） | 高 |
| Tester 高 | 発行→RECEIVE→LOAD→照会の状態自動遷移フルフロー E2E | 高 |
| User-rep 中 | 荷役作業員のワークリスト（場所×状態の作業待ち一覧） | 中 |
| User-rep 中 | 引取確認（consigneeConfirmation）の一覧・追跡表示（監査） | 中 |
| User-rep 中 | 公開追跡番号の非連番トークン化（列挙耐性） | 中 |
| User-rep（IT5 繰越） | T2/T3 協議依頼/通知待ちワークリスト | 中 |
| Tech-writer 中 | architecture_frontend.md のポーリング設計を hx-select 方式に同期 | 中 |
| Programmer 低 | 追跡番号正規表現の重複解消（domain の検証を web 側で再利用） | 低 |

## 品質ゲート

| 項目 | 結果 |
|------|------|
| `make check`（build/test/lint/govulncheck/arch） | green |
| ドメイン層カバレッジ | tracking 100%・handling 98.5%・shared 97.4%・booking 97.6% |
| application 層カバレッジ | handling 95.2%・tracking 80%・booking 78% |
| BC 独立性（go-arch-lint） | green（ACL/イベント経由のみ） |
| 統合テスト（testcontainers） | 未整備（IT7 繰越・Docker 依存） |
| デモ E2E | 画面到達性・エラー系を検証（フルフロー状態遷移は IT7） |
