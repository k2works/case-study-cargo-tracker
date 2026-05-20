---
title: イテレーション 7 ふりかえり
description: IT7（shared モジュール昇格・Event 駆動 ACL 本実装・US19/US20 例外処理・IT4/IT6 負債回収）の KPT ふりかえり。11 SP 完了、E2E 全通過、SonarQube PASS（new_coverage 84.1%、violations 0）、XP マルチパースペクティブレビューで指摘 38 件を集約。
---

# イテレーション 7 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 7 |
| **期間** | Week 13-14（2026-08-06 〜 2026-08-19 計画 / 実績 2026-05-19〜20 集中実装） |
| **ゴール** | 例外処理（US19/US20）を実装すると同時に、IT5/IT6 で持ち越した shared モジュール昇格 + Event 駆動 ACL を完成させ、IT4 由来の技術的負債を回収する |
| **計画 SP** | 11 |
| **実績 SP** | 11 |
| **達成率** | 100% |

---

## 結果サマリー

### 完了ストーリー / タスク

| ID | ユーザーストーリー / タスク | SP | 結果 |
|----|------------------|----|----|
| TI07 | IT7 第 0 スプリント（shared 昇格 + Event 駆動 ACL + IT4 由来負債） | 3 | ✅ 完了 |
| TI08 | IT6 レビュー高優先度残課題 + ADR-0013/0014 + 設計書同期 | 2 | ✅ 完了 |
| US19 | 遅延例外を処理する | 3 | ✅ 完了 |
| US20 | 破損・紛失例外を処理する | 3 | ✅ 完了 |
| **合計** | | **11** | **100%** |

### 品質指標

| 指標 | 結果 | 目標 |
|------|------|------|
| SonarQube new_coverage | 84.1% | ≥ 80% ✅ |
| SonarQube new_violations | 0 件 | 0 件 ✅ |
| Playwright E2E | 全通過（13 シナリオ以上） | 全通過 ✅ |
| バックエンドユニット・統合テスト | 全通過 | 全通過 ✅ |
| XP レビュー指摘（高） | 10 件 | - |
| XP レビュー指摘（中） | 18 件 | - |
| XP レビュー指摘（低） | 10 件 | - |

---

## KPT ふりかえり

### Keep（継続すること）

**技術面**

- **第 0 スプリント方式の有効性**: TI07 を「第 0 スプリント」として最優先に完了させてから US19/US20 に移行する段取りが機能した。shared モジュール昇格・Event 駆動 ACL 本実装が完了したことで、後続の US19/US20 が既存の暫定コードと衝突せずに実装できた。
- **SonarQube Quality Gate を KPI として維持**: new_coverage 84.1%、violations 0 を達成。Code Smell 7 件を「実装後すぐ修正」のサイクルで対応できたのは、品質ゲートを「チェックポイントではなく継続的フィードバック」として扱う姿勢が根付いた成果。
- **TDD サイクルの遵守**: Aggregate ユニットテスト（`AggregateTestFixture`）+ Controller 統合テスト（`@SpringBootTest`）の両層でテストを書いてから実装したパターンが継続できた。
- **XP マルチパースペクティブレビューの実施**: 5 つのエージェント（programmer / tester / architect / technical-writer / user-representative）を並列起動し、38 件の指摘を多角的に収集できた。単一視点のレビューでは見落とされやすい「業務担当者視点の UX 問題」「API ドキュメント陳腐化」「テストのアサーション不足」が特定できた。
- **ADR の継続的な起票**: ADR-0014（shared モジュール責務拡張）を起票・承認し、設計判断のトレーサビリティが保たれている。

**プロセス面**

- **コミット規律の維持**: Conventional Commits（feat / fix / refactor / test / docs）に一貫して準拠できた。
- **ドキュメント同期**: `data-model.md` / `domain-model.md` / `docs/index.md` / `mkdocs.yml` を実装後に即座に同期した。

---

### Problem（問題点）

**技術面**

- **`TrackingController` の責務肥大化**: 330 行・10 エンドポイントに拡大。SRP 違反が蓄積し、テストも 1 ファイルに集約されて認知負荷が高い。M-15（TrackingController 分離）はフィーチャバッファとして計画されたが実施されなかった。
- **インフラ層 Record の REST 直露出**: `TrackingExceptionRecord`（MyBatis Record）を REST レスポンス型として直接使用。DB スキーマ変更が API 契約に直撃するリスクが存在する。IT6 の同様指摘（`TrackingListItemResponse` 導入）と一貫性が取れていない。
- **`exceptionType` の String 流通**: DELAY/DAMAGE/LOSS を Enum 化せず `String` で Aggregate〜Projection〜DB まで流通させた。不正値の混入リスクとビジネスロジック（`"LOSS".equals(...)` の escalated 判定）のハードコードが残る。
- **テストのアサーション不足**: `registerException_CommandGatewayへ送信()` / `registerException_operatorIdNullでsystemが使われる()` が `ArgumentCaptor` なしで CommandGateway 送信の中身を検証していない。仕様化テストとして機能不全。
- **LOSS 緊急通知の未実装**: フロントエンドに「緊急通知が送信されます」と表示しているが、実際の通知チャネル（メール / ダッシュボード通知）は未実装。ユーザーへの虚偽表示リスク。
- **Aggregate 内 `LocalDateTime.now()` の使用**: `TrackingActivity.resolveException` 内で `LocalDateTime.now()` を呼ぶため、Event Sourcing の再生再現性が担保されない。
- **SonarQube Quality Gate チェックボックスの更新漏れ**: iteration_plan-7.md の成功基準「SonarQube Quality Gate PASS」チェックボックスが未チェックのまま残った（実際は PASS 済み）。

**プロセス面**

- **受入条件のチェックボックス管理が煩雑**: iteration_plan-7.md の受入条件とタスクチェックボックスが二箇所に分散し、実装完了後の更新が追いつかない場面があった。
- **フィーチャバッファタスクの未実施**: M-2 / M-5 / M-6 / M-15 の 4 件がバッファとして計画されたが、QG 修正対応に時間を使ったため着手できなかった。

---

### Try（次に試すこと）

**技術面**

| # | 改善アクション | 対象イテレーション | 期待効果 |
|---|-------------|-----------------|---------|
| T1 | `TrackingExceptionController` を分離（`/api/v1/tracking/{tn}/exceptions`） | IT8 | TrackingController の SRP 改善・テスト分割 |
| T2 | `TrackingExceptionResponse` DTO を新設し `TrackingExceptionRecord` の REST 直露出を解消 | IT8 | DB スキーマ変更の API への波及防止 |
| T3 | `ExceptionType enum`（`isEscalated()` メソッド付き）を導入し String 流通を排除 | IT8 | 不正値防止・Projection のハードコード解消 |
| T4 | `registerException` テストに `ArgumentCaptor` を追加してコマンド内容を検証 | IT8 | 仕様化テストとして機能させる |
| T5 | LOSS 緊急通知の最小実装（管理者ダッシュボードの未読バッジ or ログ出力の明示化） | IT8 | フロントとバックエンドの整合 |
| T6 | `ResolveTrackingExceptionCommand` に `resolvedAt: LocalDateTime` を追加し Aggregate 内 `now()` を除去 | IT8 | Event Sourcing 再生の再現性確保 |
| T7 | `Aggregate ユニットテスト（AggregateTestFixture）` で LOSS→escalated・resolveException 不変条件を検証 | IT8 | 統合テスト過多の是正・ユニット層での仕様化 |

**プロセス面**

| # | 改善アクション | 対象イテレーション | 期待効果 |
|---|-------------|-----------------|---------|
| P1 | SonarQube QG を実装の最終段階ではなく「PR マージ前チェック」として位置付ける（Code Smell の早期発見） | IT8 | 修正の後回し防止 |
| P2 | iteration_plan のチェックボックスを「タスク完了時に即更新」するルールを設ける | IT8 | 更新漏れ防止 |
| P3 | フィーチャバッファタスクを「バックログ候補」として GitHub Issue に登録し次イテレーション計画に自動連携 | IT8 | バッファ未実施タスクの追跡改善 |

---

## ベロシティ実績

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|--------------|---------|---------|--------|
| IT1 | 14 | 14 | 100% |
| IT2 | 14 | 14 | 100% |
| IT3 | 16 | 16 | 100% |
| IT4 | 25 | 25 | 100% |
| IT5 | 11 | 11 | 100% |
| IT6 | 8 | 8 | 100% |
| **IT7** | **11** | **11** | **100%** |
| **IT1-IT7 平均** | **14.1** | **14.1** | **100%** |

> **IT8 予測**: IT1-IT7 平均ベロシティ 14.1 SP。IT8 計画 13 SP（平均の 92%）は持続可能ペースの範囲内。

---

## 次のイテレーション（IT8）への引き継ぎ

### 継続中の技術的負債（優先度順）

1. **TrackingExceptionController 分離**（T1）— TrackingController が 330 行に達しており SRP 違反が顕在
2. **TrackingExceptionResponse DTO 新設**（T2）— インフラ層 Record の REST 直露出を解消
3. **ExceptionType enum 導入**（T3）— String 流通のリスクを排除
4. **テスト仕様化の強化**（T4 / T7）— ArgumentCaptor 追加・Aggregate ユニットテスト追加
5. **LOSS 通知の最小実装**（T5）— UX 整合

### IT8 スコープ候補

| ID | ストーリー / タスク | SP | 備考 |
|----|------------------|----|------|
| US21 | 輸送料金を算出する | 3 | Phase 2 機能 |
| US22 | 法人割引を適用する | 3 | Phase 2 機能 |
| US23 | 精算を処理する | 3 | Phase 2 機能 |
| TI09 | IT7 技術的負債回収（例外 DTO / Enum 化 / テスト強化） | 4 | レビュー指摘高・中 対応 |
| **合計** | | **13** | |

---

## 関連ドキュメント

- [iteration_plan-7.md](./iteration_plan-7.md): IT7 計画書
- [IT7_xp_multiperspective_review_20260520.md](../review/IT7_xp_multiperspective_review_20260520.md): XP マルチパースペクティブレビュー（高 10 / 中 18 / 低 10）
- [retrospective-6.md](./retrospective-6.md): IT6 ふりかえり
- [iteration_report-7.md](./iteration_report-7.md): IT7 完了報告書（作成予定）
