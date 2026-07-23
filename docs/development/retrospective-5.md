---
title: イテレーション 5 ふりかえり
description: IT5（追跡・荷役・US14/US15/US16/US17）の Keep・Problem・Try
published: true
date: 2026-07-23T00:00:00.000Z
---

# イテレーション 5 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 5（追跡・荷役） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 14 / 14（達成率 100%） |
| **対象ストーリー** | US14・US15・US16・US17 |
| **テスト** | domain-tracking 9 + domain-handling 9 + app-tracking 4 + app-handling 5 + HTTP フロー 5（通知アサート含む）+ ナビ検証 2 + E2E 4（it5-demo）＝全 green |
| **カバレッジ（IT5 新規クレート・lines）** | app-handling 94.8% / app-tracking 93.9% / domain-handling 86-92% / domain-tracking 78-89% |
| **実装コミット** | feat/refactor/test 多数（ADR-0006 起票・IT4 Try#1-6 全返済・4 ACL アダプター新設） |
| **成果** | Tracking / Handling Context をスケルトンから本格実装。追跡番号発行→荷役反映→引取→手動更新の一貫フローが実 PostgreSQL 上で成立。BC 独立を 4 ACL ポートで維持。**Release 1.0 MVP 完成** |

## Keep（継続すること）

### 技術的成功事項

- **BC 独立の徹底と ACL 集約**: `domain-tracking`／`domain-handling` を他 domain クレート非依存に保ち、BC 跨ぎ連携（Booking→Tracking／Handling→Tracking）を 4 ACL ポート（`ConfirmedBookingIssuer`／`TrackingReflectionPort`／`RouteCheckPort`／`TrackingNotificationPort`）で受け、実装を composition 層 `tracking_acl.rs` 一箇所に集約した。architect が「BC 独立性は完璧・依存グラフは一方向で循環なし」と評価。
- **追跡状態の純粋関数導出**: `TrackingActivity::current_status()` を保持イベント列末尾から導出し状態を二重管理しない設計。IT6 の例外イベント拡張に開いている（ADR-0006 で明文化）。
- **不変条件のドメイン閉じ込め**: 引取（Claim）＝荷受人確認必須を `HandlingActivity::register` に閉じ込め、UI ガードに依存しない。「引取以外で渡された確認は保持しない」まで集約で担保しテストで実証。IT4 Problem（UI ガード依存）の再発を防いだ。
- **インサイドアウトの一貫実施**: domain→app→infra→interface の順で TDD。domain/app 層を mockall で堅牢に固めてから統合へ積み上げ、貧血ドメインを回避。
- **IT4 Try の完全返済**: #1 対応表想定テスト名・#2 確認ダイアログ・#3 条件協議依頼の実導線化・#4 期限超過候補の選択不可化・#5 `expected_voyages` 集約＋round-trip テスト・#6 `BookingStatus` 述語メソッドを全て実装。

### プロセス的成功事項

- **設計反映の同時実施**: 実装と同一 IT で domain-model.md（`ReceiptConfirmation`・`HandlingType`・荷役↔状態対応表）・data-model.md（`receipt_confirmation` 列）を反映し先行乖離を残さなかった。
- **計画の設計図を PlantUML 5 図で整備**: ユーザー要望を受け、設計節の全トピック（ドメインモデル・状態遷移・ER・画面遷移・シーケンス）を図示した。
- **レビュー→改善ループ**: 5 視点並列レビューで高優先度 4 件（通知未テスト・ADR-0004 非対称・引取確認必須化・ui_design 未反映）をクローズ前に対応した。

## Problem（問題点）

### テスト・受入基準の課題

- **通知系受入基準の全レベル未検証が発覚（IT4 Try#1 の再発）**: US15 状態変更通知・US17 種類別通知が単体〜E2E のどのレベルでも検証されておらず、通知アダプター（`tracking_acl.rs`）自体がノーテストだった。tester が重大指摘。クローズ前に HTTP フローテストで notification テーブルをアサートして補完したが、「送信＝記録」系は実装があってもテストが追随しにくい構造的弱点が 2 IT 連続で露見した。

### 設計・実装の課題

- **ADR-0004 の Booking→Tracking 非対称**: `issue_tracking` は Booking を先に `TrackingIssued` へ遷移させるため、後続の `TrackingActivity` 保存失敗時に「予約 TrackingIssued・追跡レコード無し」の中間状態が残り、`TrackingIssued` からの再遷移が不正遷移で弾かれ再操作で収束しない。ADR-0004 が前提とした route_confirm 型の冪等収束が成立しないケース（architect 高）。ADR-0006 で回復戦略（冪等再操作パス・監視検出）を明文化したが、実装は未着手で規約止まり。
- **transport_status カラムの二重管理の火種**: `current_status()` 導出結果をカラムにキャッシュしており、書き込み経路が save のみに統制される限り整合するが「二重管理しない」設計意図と表面的に矛盾。ADR-0006 で Read Model キャッシュと位置づけた。
- **通知ロジックの重複・宛先ハードコード**: 状態変更通知の subject/body が 2 箇所で重複、recipient_email が固定値。実配信（IT6）とあわせて整理予定（programmer 中）。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | 「送信＝記録」系（通知）の受入基準は、実装時に必ず永続化テーブルをアサートする統合テストをセットで書く運用を DoD 化する（対応表に「通知アサートテスト名」列を設ける） | 開発 | IT6 | 通知系未テスト（IT4-5 で 2 回再発）の根本対策 |
| 2 | ADR-0006 の Booking→Tracking 冪等再操作パスを実装する（`TrackingIssued` かつ追跡レコード無しの再実行を許容） | 開発 | IT6 | 中間状態からの自動回復・architect 高指摘の実装返済 |
| 3 | 通知の実配信（`NotificationPort` 実装差し替え）と荷主 contact 解決を導入し、宛先ハードコード・subject/body 重複を解消する | 開発 | IT6 | 通知の可視化（user-rep 高）・DRY 違反解消 |
| 4 | `transport_status` を CQRS Read Model として整理するか、キャッシュである旨をコード・スキーマにコメント明記する | 開発 | IT6 | 二重管理の火種の明確化（architect 中） |
| 5 | RouteCheckPort の戻り値を `enum { OnRoute, OffRoute, Unknown }` にして「判定不能」と「ルート上」を分離する | 開発 | IT6 | 警告抑止ロジックの意味明確化（architect 中） |
| 6 | dashboard の最新荷役一覧・予約詳細への追跡番号表示を追加する | 開発 | IT6 | 業務導線の充実（user-rep・ui_design 想定の実装） |

## 次イテレーション（IT6）への引き継ぎ

- **IT6 スコープ**: US01（輸送見積）・US18（追跡情報照会）・US19（遅延例外処理）で終盤（アウトサイドイン）に入る。Tracking Context の例外イベント（`TrackingExceptionEvent`）・通関（Handling の `CustomsDeclaration`）の本格実装が始まる。
- **例外イベント導入**: `current_status()` の末尾判定に例外状態（EXCEPTION）を織り込む拡張。ADR-0006 の導出方式を踏襲。
- **通知の実配信・可視化**: 本 IT では「送信＝記録」に限定。US18 追跡照会画面で通知履歴・追跡番号を荷主に見せる導線とあわせて IT6 で対応。
- **ADR-0006 回復パスの実装**: Booking→Tracking の冪等再操作パス（Try#2）。
- **通関前提チェック（US16 の Claim）**: `CustomsStatus == Cleared` の前提チェックは IT6 の通関スコープ。本 IT では荷受人確認のみを不変条件とした。

## 数値指標

| 指標 | 実績 |
|------|------|
| テストカバレッジ（IT5 新規クレート） | app-handling 94.8% / app-tracking 93.9% / domain-handling 86-92% / domain-tracking 78-89% lines |
| 全テスト | 全 green（ワークスペース exit 0・domain/app 単体 + infra/interface 統合 + E2E 構文検証） |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠 |
| ベロシティ | 14 SP（IT1=16 → IT2=11 → IT3=11 → IT4=14 → IT5=14、計画ラインと一致し安定） |
| 累計進捗 | 66/97 SP（68%）・Phase 2 完了・Release 1.0 MVP 完成 |

## 関連ドキュメント

- [イテレーション 5 計画](./iteration_plan-5.md)
- [IT5 開発成果物レビュー](../review/it5_development_review_20260723.md)
- [ADR-0006 追跡状態の純粋関数導出と Booking→Tracking 回復戦略](../adr/0006-tracking-status-derivation-and-cross-context-recovery.md)
- [イテレーション 4 ふりかえり](./retrospective-4.md)
