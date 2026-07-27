---
title: IT7 開発レビュー（マルチパースペクティブ）
description: IT7（US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外）の XP 5 視点統合レビューと対応。
tags: review, development, iteration-7, go
---

# IT7 開発レビュー（マルチパースペクティブ）

対象: IT7 実装（`git diff fedb4b4d HEAD`）。Tracking Context の例外処理・貨物状態手動更新。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）を並列レビューし統合。

## エグゼクティブサマリー

Tracking の例外処理（`TrackingExceptionEvent`・`EscalationPolicy`・例外解決の状態復帰）を 13 SP で実装。ドメイン層カバレッジ 93%+、`make arch` green、SonarQube Quality Gate PASS（80.3%）。programmer/architect は「値オブジェクト・集約・ドメインサービスの分離、CurrentStatus の EXCEPTION 復帰ロジックの凝集」を高評価。複数視点で **通知の非トランザクション副作用**（通知失敗＝ユースケース失敗）と、**検証結果・対応報告が関係ロールに届かない**業務ループの穴が収束した。高優先度はクローズ前に対応し、エスカレーション再評価・イベント配信・管理職ワークリスト・ETA 構造化などは ADR-0009 / IT8 の課題として明示繰越した。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高/中優先度指摘 | 対応 |
|------|------|--------------------|------|
| Programmer | 規律良好（高 1） | 通知失敗＝ユースケース失敗・二重解決・DELAY 登録時固定 | 通知ベストエフォート化（✅）・二重解決拒否（✅）・再評価は IT8（ADR-0009） |
| Tester | 土台堅実・受入対応に穴（高 2） | DELAY 48h サービス層エスカレーション未検証・E2E skip 依存・DAMAGE 経路未検証 | サービス層/DAMAGE/二重解決/複数例外テスト追加（✅）・フルフロー E2E seed は IT8 |
| Architect | 分割健全（高 1） | 通知非トランザクション・手動更新 EXCEPTION 素通し・エスカレーション再評価 | ベストエフォート（✅）・EXCEPTION 拒否（✅）・ADR-0009 起票（✅） |
| Technical Writer | ドキュメント負債中心（高 1） | test_strategy の EscalationPolicy 旧 API・US 番号ズレ・CUSTOMS_HOLD 手動露出 | test_strategy 実 API 是正（✅）・US11-18→US16-23 是正（✅）・CUSTOMS_HOLD 除外（✅） |
| User Representative | 骨格堅実・到達導線に穴（高 2） | 荷主に対応報告届かない・荷役作業員の破損登録導線欠落 | 追跡詳細に状況/対応報告表示（✅）・破損登録を ROLE_HANDLER 開放（✅） |

## クローズ前に対応した指摘（高/中優先度）

- **通知ベストエフォート化（programmer/architect 高）**: 永続化コミット後の荷主・管理職通知失敗はログに留め、ユースケースは成功扱い。二重登録リスクを解消。
- **荷主への対応報告可視化（user-rep 高）**: 追跡詳細（US18 照会）に発生状況・対応報告（ResolutionNotes）を表示。US19 の業務ループを閉じる。
- **荷役作業員の破損登録導線（user-rep 高）**: 例外ルートを ROLE_HANDLER にも開放（US20）。
- **DELAY 48h サービス層エスカレーション検証（tester 高）**: `fixedClock(occurred+48h+1m)` で管理職通知を検証。DAMAGE 経路・二重解決・複数例外部分解決のテストも追加。
- **test_strategy 旧 API（tech-writer 高）**: `EscalationPolicy.RequiresEscalation` の実 API に是正。トレーサビリティ US 番号（US11-18→US16-23）を現行採番に統一。
- **手動更新の EXCEPTION 拒否（architect 中）**: EXCEPTION は例外エンティティ経由のみ設定（二重管理防止）。
- **二重解決拒否（programmer/tester 中）**: `ErrExceptionAlreadyResolved`。
- **CUSTOMS_HOLD 手動露出（tech-writer 中）**: 手動フォームから除外（税関自動登録のみ）。occurredAt 必須化。

## 次イテレーション（IT8）への Try（保留・繰越）

| 指摘 | 内容 | 優先度 |
|------|------|--------|
| Programmer/Architect 中 | DELAY エスカレーションの登録後再評価（定期バッチ）（ADR-0009） | 中 |
| Architect 中 | `TrackingExceptionDetectedEvent` の配信と Booking/Notification 連携（ADR-0009） | 中 |
| User-rep 中 | 管理職向け緊急例外ワークリスト（エスカレーション到達導線） | 中 |
| User-rep 中 | 新到着予定日（ETA）の構造化（resolutionNotes フリーテキストから独立項目へ） | 中 |
| User-rep 中 | 紛失（LOST）解決の終端（CLOSED）扱い（通常状態復帰は業務誤り） | 中 |
| Architect/Tester 中 | 例外の位置 index から安定 ID アドレッシングへ移行 | 中 |
| Tester 高 | フルフロー E2E の seed 整備（skip 依存の撤廃） | 中 |
| User-rep 低 | UC16 拡張 5a（代替ルート→経路再設計 UC04 起動）は今回スコープ外 | 低 |
| ADR-0008 由来 | T3 採番原子化・T4 荷役履歴リプレイ（IT7 未着手） | 高 |
| IT5 由来 | T6 協議依頼/通知待ちワークリスト | 中 |

## 品質ゲート

| 項目 | 結果 |
|------|------|
| `make check`（build/test/lint/govulncheck/arch） | green |
| ドメイン層カバレッジ | tracking 93%+（例外含む） |
| application 層カバレッジ | tracking 80%+ |
| リポジトリ統合テスト（testcontainers） | 例外ライフサイクル（登録→EXCEPTION→解決→復帰）green |
| SonarQube Quality Gate | **PASS**（new_coverage 80.3%・重複 0.3%・new_violations 0） |
| BC 独立性（go-arch-lint） | green（NotificationPort は tracking/application・他 BC 直接依存なし） |
| デモ E2E | 画面到達性・エラー系を検証（フルフロー seed は IT8 繰越） |
