---
title: IT5 分析成果物レビュー
description: IT5（US14 遅延例外・US15 破損紛失例外・US16 輸送料金算出）のユーザーストーリー・ドメインモデル・データモデル・UI 設計に対するマルチパースペクティブレビュー結果
published: true
date: 2026-04-03T00:00:00.000Z
tags: review, it5, analysis
---

# IT5 分析成果物レビュー

## レビュー対象

| ドキュメント | 対象 |
|---|---|
| `docs/development/iteration_plan-5.md` | IT5 イテレーション計画（US14/US15/US16）|
| `docs/requirements/user_story.md` | US14/US15/US16 受入基準 |
| `docs/design/domain-model.md` | exception BC・billing BC ドメインモデル |
| `docs/design/data-model.md` | cargo_exceptions・freight_charges テーブル設計 |
| `docs/design/ui_design.md` | 例外記録フォーム・輸送料金算出画面 |

## 総合評価

IT5 の US14 基盤実装（ドメイン〜アプリケーション〜インフラ〜インターフェース層）の TDD サイクルは適切に機能しており、例外 BC の独立性・依存逆転の適用・集約設計という基礎構造は評価できる。一方で、**「通知メッセージが実態と乖離している」「受入基準と実装の乖離（US14 受入基準 4 の ETA フィールド欠如）」「tracking BC から exception BC への BC 境界違反」「ExceptionType への UI 関心事の混入」** という 4 件の高重要度問題が存在し、v0.1.0 リリース前に解消が必要。US15・US16 は未実装（8SP 残存）であり、イテレーション完了基準を現時点では満たしていない。

---

## 改善提案（重要度順）

### 高（リリース前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | 通知メッセージを実態に合わせ「手動通知が必要」と変更する | ExceptionWebController・tracking/show.html の `shipperNotificationStatus` | ユーザー代表・テスター・アーキテクト | Phase 1 では通知ログ記録のみにも関わらず「通知済み」と表示されるため、現場で通知漏れが発生する |
| H2 | DELAY 時の「新しい到着予定日」を独立した日付フィールドとして追加する | exception/new.html・CargoExceptionForm・CargoIncident | ユーザー代表・インタラクションデザイナー・PM | US14 受入基準 4 に明記されているが未実装。荷主が最も必要とする情報がテキスト自由記述に埋没している |
| H3 | `user_story.md` の通知系受入基準に Phase 1 制約注記を追加する | docs/requirements/user_story.md | PM | US14/US15 の通知受入基準と iteration_plan の「Phase 1 ではログ記録」方針が乖離しており、将来の混乱を招く |
| H4 | US15 受入基準と US14 の非対称性（対応内容・例外履歴）を解消する | docs/development/iteration_plan-5.md | PM | US14 には「例外対応履歴が記録される」「対応内容の入力」があるが US15 にはない |
| H5 | `TrackingQueryService` が `CargoExceptionRepository` を直接 import している BC 境界違反を修正する | tracking BC / exception BC | アーキテクト | exception BC を将来別サービスに分割する際に tracking BC の変更が必要になる。`ExceptionInfoQueryPort` で抽象化すべき |
| H6 | `ExceptionType.getBadgeClass()` をドメイン層から除去し interfaces 層に移動する | ExceptionType.java | アーキテクト | Bootstrap CSS クラス名（UI 関心事）がドメインモデルに混入している。フレームワーク変更時にドメイン層修正が必要になる |
| H7 | E2E シナリオ番号の命名衝突（E15/E16）を修正する | exception-flow.spec.ts・tracking-flow.spec.ts | テスター | CI ログでシナリオ番号から受入基準を追跡できなくなる |
| H8 | US15 の E2E テスト（E16 紛失緊急フラグ・DAMAGE シナリオ）を追加する | exception-flow.spec.ts | テスター | US15 の受入基準 3「紛失→緊急フラグ」と DAMAGE の `isUrgent()=false` が E2E レベルで未検証 |
| H9 | US14 受入基準 4 の仕様乖離を解消する（Option A: 受入基準を実装に合わせる / Option B: PATCH エンドポイントで 2 段階化） | docs/development/iteration_plan-5.md・RecordCargoExceptionRequest | テスター | `resolution` が例外記録時に必須入力になっており、「記録後に対応内容を更新する」受入基準の意図と乖離している |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | US16 受入基準 6（例外時の料金調整）を IT5 スコープから外し独立ストーリーとして切り出す | iteration_plan-5.md | PM | billing BC から exception BC への cross-BC クエリが必要で、US14/US15 完了が前提となる INVEST 違反 |
| M2 | US14/US15 の優先度を iteration_plan で「高」に修正する | iteration_plan-5.md | PM | user_story.md では「高」だが iteration_plan では「中」と不整合 |
| M3 | `MoneyAmount` 値オブジェクトを billing BC に導入する | billing BC 設計 | アーキテクト | architecture_backend.md に明記されているが設計・実装に反映されていない |
| M4 | `FreightCharge.bookingId` を `UUID` から `BookingId` 値オブジェクトに変更する | FreightCharge 設計 | アーキテクト | 他の UUID（cargoId・voyageId 等）と混用するバグを型システムで防げない |
| M5 | `resolved_at` カラムをスキーマに追加するか設計書から削除するかを明示的に決定する | V010 SQL・data-model.md | アーキテクト | 設計書にあるが実装（V010 SQL・CargoIncident）にない。SLA 分析・遅延対応時間レポートに必要 |
| M6 | 例外種別によるフィールド動的表示（DELAY→ETA フィールド表示など）を JavaScript で実装する | exception/new.html | インタラクションデザイナー | 種別固有フィールドが全件表示されるため、DAMAGE/LOSS 記録時に不要項目の入力混乱が生じる |
| M7 | 成功後のリダイレクト先を追跡画面に変更する | ExceptionWebController | インタラクションデザイナー | 記録成功後に空フォームに戻るのではなく、追跡番号に紐づく追跡画面に戻る方が操作フローとして自然 |
| M8 | キャンセルボタンのリンク先を `returnTo` パラメータで動的制御する | exception/new.html | インタラクションデザイナー | 現在は `/exceptions/new` 自身を指しており、追跡画面からのキャンセルで元の画面に戻れない |
| M9 | `TrackingRestControllerTest` に例外あり追跡番号の `exceptionHistory` JSON 検証を追加する | TrackingRestControllerTest | テスター | exceptionHistory が常に空の場合のみテストされており、例外種別・badge クラス等の JSON シリアライズ検証が欠如 |
| M10 | `CargoExceptionRestControllerTest` に `urgent` フィールドの検証を追加する | CargoExceptionRestControllerTest | テスター | DELAY の `urgent=false`・LOSS の `urgent=true` がレスポンス JSON レベルで未検証 |
| M11 | 料金算出画面を 2 ステップ設計（入力→確認・確定）に分離した salt 図を追加する | iteration_plan-5.md | インタラクションデザイナー | 現 salt 図は予約 ID 入力箇所がなく、「確定する」の意味が曖昧 |
| M12 | DAMAGE の対応内容プレースホルダーを「補償方針・保険申請番号」等に変更する | exception/new.html | ユーザー代表 | 遅延前提の文言が全種別に共通適用されており、DAMAGE/LOSS 記録時に業務上必要な情報が不明瞭 |
| M13 | `ADR-007`「CargoException を CargoIncident に改名する」を作成する | docs/adr/ | アーキテクト | SonarQube S2166 対応の改名が ADR として記録されておらず、設計書と実装の乖離が未文書化 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | グローバルナビゲーションに「追跡」リンクを追加する | fragments/header.html | インタラクションデザイナー | /tracking へのナビリンクがなくオペレーターが URL 直打ちしか方法がない |
| L2 | tracking/show.html の `<main>` にも `py-4` クラスを追加し exception/new.html と統一する | tracking/show.html | インタラクションデザイナー | スペーシングの一貫性 |
| L3 | US16 「距離係数」の取得元（静的テーブル・外部 API）を設計書に明記する | iteration_plan-5.md | PM | Day 6 の実装開始前に設計上の決定として必要 |
| L4 | `urgent` カラムを DB から削除し、`exceptionType` から導出するよう変更する | V010 SQL・CargoIncident | アーキテクト | `exceptionType` が保存済みのため `urgent` は冗長。仕様変更時にデータ整合性リスクがある |
| L5 | `toExceptionSummary()` の `exceptionHistory` ソート順を明示的に定義する | TrackingQueryService | テスター | 複数例外がある場合に `resolveCurrentState` の動作が非決定的になるリスク |
| L6 | 発生場所コードの入力支援（よく使う港湾ドロップダウン）を将来対応として記録する | exception/new.html | ユーザー代表 | UNLOCODE を暗記しているオペレーターは稀。入力支援がないと誤入力が多発する可能性 |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | インタラクションデザイナー：成功後に追跡画面にリダイレクトすべき | PM：例外フォームは独立した業務画面として再利用される（複数件連続登録シナリオ） | リダイレクト先は追跡画面か空フォームか | `trackingNumber` パラメータがある場合→追跡画面、ない場合→空フォームの条件分岐で双方対応 |
| 2 | アーキテクト：`urgent` を DB から除去し導出値に統一すべき | テスター：`urgent` が DB に保存されていると「記録時点の判断」として监査証跡になり得る | `urgent` の永続化は冗長か記録か | 業務上の監査証跡が必要なら保持、不要なら除去。Phase 1 スコープではリスクが低いため現状維持でも可 |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-product-manager（高: 3 / 中: 2 / 低: 3）</summary>

### 評価サマリー
IT5 の計画は全体として実現可能で、Phase 1 完結という目標に向けた論理的な構成をとっている。ただし、`user_story.md` と `iteration_plan-5.md` の間に受入条件の不整合が複数存在しており、このまま実装に入ると後でトラブルになるリスクがある。

### 主な指摘
- **H3** US15 の受入条件が iteration_plan と user_story.md で乖離（「対応内容入力」の欠如）
- **H4** 通知系受入基準に Phase 1 制約が未記載
- **M1** US16 受入基準 6 が INVEST 違反（US14/US15 依存）
- v0.1.0 リリースタグの付与タイミング（US15 完了時か US16 完了時か）が未定義
- US15 の「荷役作業員」ロールが受入条件でテストされていない

</details>

<details>
<summary>xp-architect（高: 3 / 中: 4 / 低: 3）</summary>

### 評価サマリー
exception BC の実装は依存逆転の適用・集約設計・イベント発行という基本構造は適切に実現できている。ただし「tracking BC が exception BC の repository を直接参照している BC 境界違反」および「ドメインモデルへの UI 関心事の混入」の 2 点は変更を難しくする根本的な課題として解消が必要。

### 主な指摘
- **H5** `TrackingQueryService` → `CargoExceptionRepository` の直接参照（BC 境界違反）
- **H6** `ExceptionType.getBadgeClass()` のドメイン層への UI 関心事混入
- **H9(関連)** `CargoException` → `CargoIncident` 改名の ADR 未記録
- **M3** billing BC に `MoneyAmount` VO が設計されていない
- **M4** `FreightCharge.bookingId` が Primitive Obsession
- `CargoExceptionRepository`・`CargoExceptionRecordedEvent` が旧命名のまま

</details>

<details>
<summary>xp-interaction-designer（高: 3 / 中: 3 / 低: 2）</summary>

### 評価サマリー
例外記録フォームは Bootstrap 5・ARIA 対応を含め基礎的な品質は確保されているものの、設計（salt 図）と実装の間に構造的な乖離があり、特に「遅延時の新しい到着予定日」が非構造化テキストに埋没する問題は、荷主への情報通知精度とデータ活用性を損なう。IT5 の UI 完成度は 50% 程度。

### 主な指摘
- **H2** DELAY 時 ETA フィールドの欠如
- **M7** 成功後のリダイレクト先が不自然（空フォームに戻る）
- **M8** キャンセルボタンのリンク先が `/exceptions/new` 自身を指している
- **M6** 例外種別によるフィールド動的表示が未実装
- `role="region"` と Bootstrap `alert` クラスの意味論的矛盾

</details>

<details>
<summary>xp-tester（高: 4 / 中: 2 / 低: 1）</summary>

### 評価サマリー
US14 のドメイン〜インフラ〜コントローラー各層のテストは丁寧に実装されており、TDD の基礎は確立されている。一方で、E2E シナリオ番号の命名衝突・US15/US16 の E2E 未実装・通知ログの固定値偽装という 3 つの重大な品質リスクが残存。

### 主な指摘
- **H7** E2E シナリオ番号の命名衝突（E15/E16）
- **H8** US15 E2E テスト（紛失緊急フラグ・DAMAGE）未実装
- **H1(関連)** `shipperNotificationStatus` が常に "通知済み" のハードコード
- **H9** US14 受入基準 4 と実装の仕様乖離（`resolution` が記録時必須）
- **M9/M10** RestControllerTest の検証不足

</details>

<details>
<summary>xp-user-representative（高: 2 / 中: 2 / 低: 1）</summary>

### 評価サマリー
US14 の基盤実装は業務の流れに沿っており評価できるが、遅延時に荷主が最も必要とする「新しい到着予定日」が構造化フィールドとして存在しない点と、「通知しました」というメッセージが実態（ログ記録のみ）と乖離している点は、実運用で深刻なトラブルを招くためこのままリリースできない。

### 主な指摘
- **H1** 通知メッセージが実態と乖離（手動通知漏れリスク）
- **H2** US14 受入基準 4「新しい到着予定日」フィールドの欠如
- **M12** DAMAGE の `resolution` プレースホルダー文言が不適切
- US14 の完了マーク（`[x]`）が受入基準 4 未実装のまま

</details>

---

## 参照

- [IT5 UI/UX レビュー（2026-04-03）](it5_uiux_review_20260403.md)
- [IT5 イテレーション計画](../development/iteration_plan-5.md)
- [ユーザーストーリー](../requirements/user_story.md)
