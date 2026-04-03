---
title: イテレーション 5 ふりかえり
description: IT5 の Keep / Problem / Try を整理し、IT6 へ反映するためのふりかえり記録。
published: true
date: 2026-04-03T00:00:00.000Z
tags: retrospective, it5
---

# イテレーション 5 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT5 |
| 計画期間 | 2026-05-26 〜 2026-06-08 |
| 実績期間 | 2026-04-03 |
| 対象ストーリー | US14 / US15 / US16 |
| 計画 SP | 11 |
| 実績 SP | 11 |
| テスト件数 | 449 件（全 Green） |
| カバレッジ | 90.7%（instruction） |
| SonarQube | Quality Gate PASS（new_violations: 0） |
| レビュー指摘対応 | UI/UX 高優先度 4 件 + Code Smell 修正完了 ✅ |

## Keep

### 技術面

- **Phase 1 完結と Phase 2 開始を 1 イテレーションで達成**: US14・US15（例外処理）で Phase 1 コア輸送管理フロー（US01〜US15, 51SP）を完結させ、US16（輸送料金算出）で Phase 2（billing BC）を開始するという複合目標を 11SP で完遂しました。
- **新規 billing BC の ACL パターン（FreightBookingQueryPortAdapter）**: billing BC が booking BC のドメインモデルを直接参照せず、ACL アダプター経由で `FreightBookingSummary` に変換することで BC 境界を維持しました。`CONFIRMED 予約 + RECEIVE イベント存在` を条件とする業務ルールをアダプター層に集約し、テスト可能な設計を実現しました。
- **SonarQube Quality Gate PASS を維持**: `unnamed pattern _`（Java 21）適用・`HttpStatus.UNPROCESSABLE_ENTITY` → `UNPROCESSABLE_CONTENT` 修正・inner import 除去・assertThat チェーン化など 16 ファイルの Code Smell を修正し、new_violations: 0 を達成しました。
- **UI/UX レビュー指摘への体系的対応**: インタラクションデザイナー・ユーザー代表の 2 エージェント並列レビューで発見されたグローバルナビ欠落・h1 見出し・テーブルレスポンシブ・aria-label を同一イテレーション内に対応し、アクセシビリティと業務継続性の問題を解消しました。
- **テスト件数 449 件（IT4 比 +88 件）**: exception BC（CargoException・RecordCargoExceptionCommandService）と billing BC（FreightCharge・CalculateFreightCommandService・FreightBookingQueryPortAdapter）のテストを積み上げ、カバレッジ 90.7% を維持しました。

### プロセス面

- IT4 の Try 事項「エージェントへのプロンプトで『スコープ外の機能追加は禁止』を明示する」を適用し、billing BC の実装範囲を制御できました。
- SonarQube の `new_violations` と `sinceLeakPeriod` の違いを把握し、Quality Gate の判定基準（変更行上のみカウント）に合わせた修正戦略を確立しました。
- コミットを「機能実装 → Code Smell 修正 → UI/UX 改善 → ドキュメント更新」の意味単位に分割し、変更理由の追跡を容易にしました。

## Problem

### 設計・実装

- **E2E テストが RECEIVE 荷役イベントの前提条件を考慮していなかった**: `FreightBookingQueryPortAdapter` が `CONFIRMED 予約 + RECEIVE イベント存在` を条件とする仕様なのに、`US16E2ETest.createConfirmedBooking()` に RECEIVE イベント登録ステップが含まれず、404 が返り続けました。単体テストは通るが E2E テストで初めて前提条件の欠落が発覚するパターンで、API の事前条件をテストデータ準備に反映する確認が不足していました。
- **`receiveConfirmationCode` フィールドの存在を E2E テスト作成時に見落とし**: RECEIVE イベント登録 API が `receiveConfirmationCode` を必要とすることを見落とし、最初のリクエストが 400 で失敗しました。API 仕様（DTO）を確認してからテストデータを作成するプロセスが徹底されていませんでした。
- **staged 変更と E2E テストの整合性確認が不十分**: `FreightBookingQueryPortAdapter.java` の staged 変更（RECEIVE チェック復活）をコミット前に E2E テストと照合する工程が欠けていました。コミット直前に `./gradlew test` で全件確認を必須にするルールを徹底すべきでした。

### 品質管理

- **分岐カバレッジの目標未設定**: IT4 で課題として挙げた分岐カバレッジ（71%）の改善目標を IT5 計画に取り込みましたが、最終的な分岐カバレッジを計測・記録しませんでした。instruction カバレッジの把握に偏りがあります。
- **UI/UX レビュー指摘のうち中・低優先度（UUID 直接入力・確認ダイアログ・通貨記号）が次 IT 積み残し**: 実用性に影響する UUID 入力の改善と確認ダイアログは次 IT の backlog として管理する必要があります。

## Try

| Try | 担当 | 期限 | 期待効果 |
|-----|------|------|----------|
| E2E テスト作成前に対象 API の DTO 仕様（リクエスト必須フィールド）を必ず確認する: テストデータ構築前に `RecordXxxRequest` などの DTO を grep し、必須フィールドを列挙してからリクエストボディを作成するステップを Developer チェックリストに追加する | Copilot | IT6 開始時 | API 仕様とテストデータの不整合による 400 エラーを事前防止できる |
| staged 変更をコミットする前に `./gradlew test` で全件 GREEN を確認する: `git add` + `git commit` の間に必ずテスト実行を挟み、E2E テストを含む全スイートが GREEN であることを確認してからコミットするルールを iteration_plan に明記する | Copilot | IT6 開始時 | staged 変更と E2E テストの整合性チェック漏れによるコミット後の修正を防止できる |
| 分岐カバレッジも毎イテレーションの完了基準に含める: `iteration_plan` の成功基準に「分岐カバレッジ 75% 以上」を追加し、SonarQube の branch coverage メトリクスを確認・記録することを完了定義（DoD）に含める | Copilot | IT6 計画時 | カバレッジの指標が instruction 一辺倒になる問題を解消し、テストの網羅性を多面的に評価できる |
| UI/UX 中・低優先度指摘（UUID 入力改善・確認ダイアログ・通貨記号）を IT6 backlog に積む: 今 IT で対応できなかった指摘事項を GitHub Issue として登録し、IT6 スコープ選定時に考慮する | Copilot | IT6 計画時 | 技術的負債をトラッキングし、次 IT で選択的に取り込む判断材料とする |

## 次イテレーションへの引き継ぎ

- **IT6 ゴール**: US17（法人割引を適用する・3SP）・US18（精算を処理する・5SP）で billing BC を完成させ、v1.0.0 をリリースする。
- **品質ベースライン**: backend 449 テスト Green、SonarQube Quality Gate PASS（new_violations: 0）、カバレッジ 90% 以上を維持条件とする。
- **IT5 確立パターンの継続適用**:
  - billing BC の ACL アダプターパターン（`FreightBookingQueryPortAdapter` 設計）を US17・US18 でも踏襲する
  - E2E テスト作成前に API DTO の必須フィールドを確認するルールを適用する
  - コミット前の `./gradlew test` 全件実行を徹底する
- **実績ベロシティ**: IT1: 10 / IT2: 10 / IT3: 12 / IT4: 13 / IT5: 11 → 平均 **11.2 SP**。IT6 の 8 SP は平均を下回るため達成可能性は高い。
- **IT5 積み残し（UI/UX 中・低優先度）**: UUID 直接入力の改善（荷主選択 UI）・確定ボタン確認ダイアログ・通貨記号表示は IT6 backlog に積む。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-03 | IT5 ふりかえりを作成 | Copilot |
