---
title: イテレーション 6 ふりかえり
description: IT6（US14 追跡番号発行・US15 荷役記録・US16 引取記録・US18 追跡照会・Tracking/Handling BC 新設）の KPT ふりかえり。
tags: development, retrospective, iteration-6, kpt, go
---

# イテレーション 6 ふりかえり（KPT）

対象: IT6（2026-07-27 開発完了）。中盤局面（インサイドアウト）の最終イテレーション。**Tracking Context（追跡）と Handling Context（荷役）の 2 BC を新設**し、追跡番号発行（US14）・荷役記録（US15）・引取記録（US16）・追跡照会（US18）を実装。**Phase 2 完了・Release 0.2 到達**。実績 14 SP（計画どおり）。

## Keep（うまくいったこと）

### 技術的成功

- **インサイドアウトの徹底で 2 BC を安全に新設**: 共有 `TransportStatus` → Handling domain → Tracking domain → application → infrastructure → interfaces → 配線の順で内側から積み、各層をコミット単位（16 コミット）で緑に保った。ドメイン層カバレッジは tracking 100%・handling 98.5% と高水準で、複雑な荷役妥当性検証（`IsValidFor` デシジョンテーブル）を隔離検証できた。
- **BC 独立性を合成ルート注入で貫徹**: Handling→Booking の貨物参照（`CargoSnapshotProvider`）、Handling→Tracking の状態同期（`HandlingEventPublisher`）、Booking→Tracking の追跡レコード作成（`TrackingActivityCreator`）、採番（`TrackingNumberIssuer`）をすべて ACL 出力ポート化し、実装（変換アダプタ）を `cmd/server` に閉じ込めた。`make arch` は全 green で、既存 ADR-0007 の先例と構造的に一貫。
- **opening での設計ギャップ先行検出が実装のブレを防いだ**: opening の validating-design で TransportStatus 命名統一（注1）・CUSTOMS 表記（注2）・consignee_confirmation カラム（注3）・US14 発行導線（注4）・public/tracking の US 表記（注5）を注として明記しておいたため、実装と設計是正を同時反映でき、先行乖離が起きなかった。
- **T7（sqlcgen per-BC 分離）を新設 BC で最初から適用**: tracking/handling の sqlcgen を per-BC schema で分離し、go-arch-lint に `tracking-sqlcgen`/`handling-sqlcgen` を宣言。負債を増やさずに新 BC を追加した。

### プロセス的成功

- **クローズの 5 視点レビューが実バグを捕捉**: developing-review（XP 5 視点並列）で、programmer/tester が **CUSTOMS の UNKNOWN が輸送状態を退行させる欠陥**を、user-rep が **MISROUTED/警告が作業員・荷主に届かない業務欠落**を検出。いずれもクローズ前に修正し、テストを追加した。着手前検証（opening）＋締めレビュー（closing）の 2 段が機能した。
- **SonarQube Quality Gate を実際に通してクローズ**: 初回 FAIL（new_coverage 61.9%・violations 1）を、重複リテラルの定数抽出と**リポジトリ統合テスト（testcontainers）整備**で PASS（81.4%・violations 0・重複 0.3%）まで持ち込んだ。テスター指摘の「統合テスト欠如」をゲート通過の実作業として解消できた。
- **ADR で整合性境界の判断を明示的に記録**: architect 指摘の「BC 間状態同期の原子性・イベントロスト」を ADR-0008 として起票し、暫定判断（同期 in-process + 既知制約）と後続 IT の負債を明文化した。

## Problem（うまくいかなかったこと・課題）

- **開発時に妥当性検証のフィードバックが死んでいた**（user-rep 高）: MISROUTED/警告を検出しても、荷役作業員（flash 未表示）にも荷主（追跡照会に未反映）にも一切届かない状態で開発を「完了」と判断していた。受入基準を画面単体で満たしても、検証結果が業務に届くかまで確認していなかった。クローズレビューで発覚し是正。
- **CUSTOMS の状態退行という仕様の穴**（programmer/tester 高）: `ResultingTransportStatus()=UNKNOWN` を無検証で追跡イベントに流し、CurrentStatus が退行する欠陥。荷役種別ごとの状態遷移の副作用を、実装時にテストで塞いでいなかった。
- **統合テスト・フルフロー E2E を開発フェーズで書かなかった**（tester 高）: 新設 2 BC のリポジトリに統合テストが無く（既存 5 BC は全て保有）、デモ E2E も到達性・エラー系のみでフルフロー状態遷移を検証していなかった。クローズで統合テストは整備したが、フルフロー E2E（要 app+DB）は IT7 繰越。
- **BC 間同期の原子性・採番競合が残置**（architect/programmer 高）: 発行フローが非トランザクション、採番が tx 外 count+1。UNIQUE 制約で安全側には倒れるが、原子化・履歴リプレイは未対応（ADR-0008 で IT7 負債化）。

## Try（次イテレーションでの改善アクション）

| Try | 内容 | 担当 | 期限/期待効果 |
|-----|------|------|--------------|
| T1 | **検証結果フィードバックの DoD 化**: 妥当性検証（MISROUTED/警告/例外）を実装したら、その結果が関係ロールの画面に届くことを DoD に含める（画面単体でなくフィードバック到達まで） | AI | IT7〜。検出結果が死ぬ問題の再発防止 |
| T2 | **状態遷移の副作用をテーブル駆動テストで網羅**: 荷役種別×現状態の遷移（特に UNKNOWN/例外の非退行）をテストで塞ぐ | AI | IT7〜 |
| T3 | **BC 間同期の原子化**（ADR-0008）: 追跡番号採番を DB シーケンス/採番テーブルへ、発行〜追跡レコード作成を単一 tx or outbox へ、UNIQUE 衝突リトライ | AI | IT7 |
| T4 | **追跡レコード作成時の荷役履歴リプレイ**（ADR-0008）: 発行前荷役のイベントロスト解消 | AI | IT7 |
| T5 | **フルフロー E2E とリポジトリ統合テストの開発フェーズ内実施**: 状態自動遷移フロー E2E は開発中に追加（クローズに回さない） | AI | IT7〜 |
| T6 | **協議依頼/通知待ちワークリスト（T2/T3・IT5 由来）**: 営業ダッシュボードの業務ループを閉じる | AI | IT7 |
| T7 | **荷役作業員ワークリスト・引取確認の可視化**（user-rep 中）: 場所×状態の作業待ち一覧、引取確認の一覧/追跡表示 | AI | IT7〜 |
| T8 | **公開追跡番号の非連番トークン化**（user-rep 中）: 列挙耐性のセキュリティ強化 | AI | IT7〜 |
| T9 | **architecture_frontend.md のポーリング設計同期**（tech-writer 中）: hx-select 方式に更新 | AI | IT7 |

## 次イテレーション（IT7）への引き継ぎ

- **IT7 スコープ**: 終盤局面（アウトサイドイン）・Phase 3。US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外（release_plan 暫定）。
- **技術的負債（高）**: BC 間同期の原子化・追跡番号採番の競合解消・荷役履歴リプレイ（ADR-0008・T3/T4）、フルフロー E2E（T5）。
- **業務ループ（中）**: 協議依頼/通知待ちワークリスト（T6・IT5 から継続繰越）、荷役ワークリスト・引取確認可視化（T7）。
- **良好な状態の維持**: BC 独立性（go-arch-lint 全 green）・ドメイン層 90%+ カバレッジ・合成ルート ACL 配線・opening/closing の 2 段検証・SonarQube ゲート PASS の規律。

## 実績サマリー

| 項目 | 値 |
|------|-----|
| 計画 SP | 14（US14 3・US15 5・US16 3・US18 3） |
| 実績 SP | 14（100%）+ 2 BC 新設・ADR-0008 起票 |
| ドメイン層カバレッジ | tracking 100%・handling 98.5%・shared 97.4%・booking 97.6% |
| SonarQube Quality Gate | PASS（new_coverage 81.4%・重複 0.3%・violations 0） |
| 品質ゲート | make check green（build/test/lint/govulncheck/arch）・統合テスト（testcontainers）green |
| CI | 未 push（当ブランチは workflow_dispatch 手動起動・push 後にトリガー要） |
| レビュー | developing-review（XP 5 視点並列）・高優先度はクローズ前対応/一部 IT7 繰越（ADR-0008） |
| 設計反映 | ADR-0008（BC 間同期整合性境界）・domain-model/data-model/ui_design 注1〜5 同時反映 |
| マイルストーン | **Phase 2 完了・Release 0.2 到達** |
