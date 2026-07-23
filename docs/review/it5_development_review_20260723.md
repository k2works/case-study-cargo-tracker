---
title: IT5 開発成果物レビュー（追跡・荷役）
description: US14-17 の追跡番号発行・荷役／引取記録・貨物状態手動更新に対する 5 視点マルチパースペクティブレビュー統合レポート
published: true
date: 2026-07-23T00:00:00.000Z
---

# IT5 開発成果物レビュー（追跡・荷役）

## 概要

| 項目 | 内容 |
|------|------|
| **対象イテレーション** | IT5（US14 追跡番号発行・US15 荷役記録・US16 引取記録・US17 貨物状態手動更新） |
| **レビュー日** | 2026-07-23 |
| **レビュー方式** | XP 5 視点並列（programmer / tester / architect / technical-writer / user-representative） |
| **対象 diff** | `128370a7~1..HEAD`（19 コミット・50 ファイル） |
| **総評** | BC 独立・ヘキサゴナル・ADR 踏襲の設計規律が一貫した高品質な成果。高優先度の指摘は「通知系の未テスト」「ADR-0004 の Booking→Tracking 非対称」「引取確認フィールドの HTML 必須化」「ui_design.md 未反映」の 4 件。 |

## 視点別サマリー

| 視点 | 総評 | 高優先度指摘 |
|------|------|-------------|
| programmer | Rust イディオム・型付きエラー・不変条件の閉じ込めが良質 | なし（中: 通知重複・dead code・ハードコード） |
| tester | domain/app のピラミッド準拠テストは高品質だが通知系に穴 | **通知記録（US15/US17）が全テストレベルで未検証** |
| architect | BC 独立性は完璧・ACL 設計が DIP 徹底 | **ADR-0004 の Booking→Tracking 冪等収束が非対称** |
| technical-writer | rustdoc・domain-model/data-model は同期済み | **ui_design.md に手動更新導線・荷受人確認フィールド未反映** |
| user-representative | 誤操作防止・ロール出し分けが業務水準 | **引取時の荷受人確認が HTML で必須化されていない** |

## 高優先度指摘と対応方針

| # | 視点 | 指摘 | 対応 |
|---|------|------|------|
| H1 | tester | US15 状態変更通知・US17 種類別通知が単体〜E2E のどのレベルでも未検証（`tracking_acl.rs` の通知アダプターがノーテスト・HTTP フローテストが notification テーブル未アサート）。IT4 Try#1「対応表 × 実テスト乖離」の再発 | **クローズ前対応**: HTTP フローテストに notification テーブルの件数・種別アサートを追加 |
| H2 | architect | `issue_tracking` は Booking を先に `TrackingIssued` へ遷移させた後に `TrackingActivity` を保存する。保存失敗時に「予約 TrackingIssued・追跡レコード無し」の中間状態が残り、`TrackingIssued` からの再遷移が不正遷移で弾かれ再操作で収束しない（ADR-0004 の前提外） | **クローズ前対応**: ADR-0006 を起票し回復戦略を明文化（Tracking を先に保存する順序 or 冪等再実行パス） |
| H3 | user-rep | `handling_new.html` の荷受人確認フィールドが `required` でなく、引取（CLAIM）選択時も空送信できる。ドメインは `ReceiptConfirmationRequired` で弾くが UX 上サーバエラーを見せる | **クローズ前対応**: CLAIM 選択時に JS で `required` を付与 |
| H4 | technical-writer | `ui_design.md` 本体に US17 手動更新導線（`POST /tracking/{trackingNumber}/updates`）・荷役登録の荷受人確認フィールド出し分けが未反映（計画が自己申告済みの反映が未完了） | **クローズ前対応**: ui_design.md の追跡詳細・荷役登録セクションに追記 |

## 中・低優先度指摘（次イテレーション or 許容）

| # | 視点 | 指摘 | 方針 |
|---|------|------|------|
| M1 | programmer/architect | 状態変更通知の subject/body/recipient が `SqlxTrackingNotificationPort` と `TrackingReflectionAdapter` で重複・recipient_email ハードコード | IT6 対応（通知の実配信・荷主 contact 解決とあわせて整理） |
| M2 | programmer | `resolve_booking_id` がデッドコード（ACL は `find_by_tracking_number` 経由） | **クローズ前に削除**（YAGNI） |
| M3 | architect | `transport_status` カラムが `current_status()` 導出結果のキャッシュで二重管理の火種 | ADR-0006 で read model 位置づけを明記（IT6 で CQRS 側へ寄せる検討） |
| M4 | architect | RouteCheckPort の「判定不能」と「ルート上」が `Ok(true)` で混在 | IT6 で `enum { OnRoute, OffRoute, Unknown }` に分離検討 |
| L1 | programmer | `TrackingVoyageNumber::new` の doc に `# Errors` があるが `Option` を返す | **クローズ前に修正**（doc 齟齬） |
| L2 | tester | US14 一意採番が形式検証止まり（UUID の一意性は実証していない） | 許容（UUID v4 の一意性は前提・DB UK 制約で担保） |
| L3 | user-rep | 荷役履歴一覧・予約詳細への追跡番号表示・通知の実配信 | IT6 以降のバックログ |

## 品質指標

| 指標 | 実績 |
|------|------|
| 全テスト | 全 green（domain-tracking 9 / domain-handling 9 / app-tracking 4 / app-handling 5 / HTTP フロー 5 / ナビ 2 ほか・ワークスペース exit 0） |
| カバレッジ（IT5 新規クレート・lines） | app-handling 94.8% / app-tracking 93.9% / domain-handling 86-92% / domain-tracking 78-89% |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠 |

## 良い点（統合）

- **BC 独立の徹底**: domain-tracking / domain-handling が他 domain クレートに非依存、ACL を composition 層（`tracking_acl.rs`）に集約。依存グラフは一方向で循環なし。
- **不変条件の閉じ込め**: 引取＝荷受人確認必須をドメインに閉じ込め（UI ガード非依存）。
- **純粋関数による状態導出**: `current_status()` がイベント列末尾から導出し二重管理を回避、IT6 の例外イベント拡張に開いている。
- **テストの網羅性**: mockall で `.never()` を用いた異常系検証、testcontainers でロール別フローと状態遷移を実 DB 検証。
- **リファクタリング反映**: IT4 Try を全返済（`expected_voyages` 集約・round-trip テスト・`BookingStatus` 述語メソッド）。

## 関連ドキュメント

- [イテレーション 5 計画](../development/iteration_plan-5.md)
- [ADR-0004 BC 跨ぎ書き込み一貫性](../adr/0004-cross-context-write-consistency.md)
- [ADR-0005 予約状態機械](../adr/0005-booking-status-state-machine.md)
