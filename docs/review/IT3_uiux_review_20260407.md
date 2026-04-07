---
title: IT3 UI/UX レビュー（Estimation コンテキスト）
date: 2026-04-07
target: estimation/index.html, estimation/new.html, estimation/show.html, booking/new.html（pre-fill 対応）
reviewers: xp-interaction-designer, xp-user-representative
---

# IT3 UI/UX レビュー結果

## レビュー対象

- `apps/cargo-tracker/src/main/resources/templates/estimation/index.html`（見積一覧）
- `apps/cargo-tracker/src/main/resources/templates/estimation/new.html`（見積作成フォーム）
- `apps/cargo-tracker/src/main/resources/templates/estimation/show.html`（見積詳細）
- `apps/cargo-tracker/src/main/resources/templates/booking/new.html`（予約登録フォーム - 見積 pre-fill 対応）
- `apps/cargo-tracker/src/main/resources/templates/fragments/navbar.html`（ナビゲーション）

## 総合評価

見積コンテキストの UI は既存の予約管理・荷主管理画面と高い構造的一貫性を保ち、OOUX の「一覧 → 詳細 → アクション」パターンに忠実です。見積から予約フォームへの導線（pre-fill）はデータの二重入力を排除する実用的な機能です。一方で、UN/LOCODE の生コード表示・テーブルアクセシビリティ・貨物種別の日本語表示不足・見積状態バッジの固定色など、営業担当者の日常業務における実用性に影響する問題が複数確認されています。

---

## モダンデザイン準拠サマリー

| 評価項目 | 状態 | 備考 |
|---|---|---|
| カラーシステム | 要改善 | 見積状態バッジが `text-bg-secondary` 固定。booking 側の `statusBadgeColor` 動的制御と不一致 |
| タイポグラフィ | 基本的 | `h3` + `text-secondary` の 2 階層のみ。情報の優先度付けが不十分 |
| Elevation & Surface | OK | Bootstrap `shadow-sm` カードで適切に表現 |
| コンポーネント一貫性 | OK | ボタン・バッジ・カード・テーブルの使い方は既存画面と一致 |
| スペーシング | Bootstrap 依存 | 8px グリッドへの明示的な整合は未検証 |
| レスポンシブ / Adaptive | 部分的 | `table-responsive` 適用済み。モバイルでの 7 カラムテーブルの可読性に課題 |
| ダークモード | 未対応 | `prefers-color-scheme` / Bootstrap ダークモード切替なし |
| 状態デザイン（空 / Error） | 部分的 | 空状態あり。`errorMessage` 用 alert が見積詳細に未設定 |

---

## 改善提案（重要度順）

### 高（リリース前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | `CargoType` enum に `displayName` を追加し、一覧・詳細で日本語表示（「一般」「危険物」「冷凍・冷蔵」）にする | `estimation/index.html`, `estimation/show.html` | xp-user-representative | 生コード「GENERAL」は業務ユーザーの心理モデルと乖離。既存の `EstimateStatus.displayName` と同パターンで解決可能 |
| 2 | UN/LOCODE を都市名との併記形式（例: 東京 (JPTYO)）に変更 | `estimation/index.html`, `estimation/show.html` | xp-interaction-designer | 生コード表示では複数見積を比較する際に瞬時判別が困難 |
| 3 | テーブルの `<th>` 全てに `scope="col"` を追加（WCAG 1.3.1） | `estimation/index.html`, `estimation/show.html` | xp-interaction-designer | スクリーンリーダーによるセルとヘッダの関連付けが不可能な状態 |
| 4 | 「この見積で予約する」ボタンに `estimateId` をパラメータとして含め、予約完了時に見積を `BOOKED` 状態へ遷移させる設計を追加 | `estimation/show.html`, バックエンド | xp-user-representative | 同一見積から複数の予約が作成できてしまう。二重予約防止と US04 受入基準の充足が必要 |
| 5 | 見積一覧に最低限のステータスフィルタ（全件/作成済/予約済）を追加 | `estimation/index.html` | xp-interaction-designer | UI 設計ドキュメントに「一覧テーブル・検索」と記載されているが未実装 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 6 | 見積状態バッジカラーを状態に応じて動的切り替え（作成済=secondary、見積完了=info、予約済=success） | `estimation/index.html`, `estimation/show.html` | xp-interaction-designer | booking 側の `statusBadgeColor` パターンと不一致。視覚スキャン効率が低下 |
| 7 | ボタンラベルを「この見積で予約する」→「この内容で予約フォームへ進む」に変更 | `estimation/show.html` | xp-interaction-designer | 遷移先がフォームであることを明示し、ユーザーの期待値を正確に伝える |
| 8 | ルート候補テーブルに `aria-label="ルート候補一覧"` を追加 | `estimation/show.html` | xp-interaction-designer | 一覧画面のテーブルには付与済みで不一致 |
| 9 | pre-fill 時に「見積 EST-XXXX の内容が入力されています」インフォメーションバナー（`alert-info`）を表示 | `booking/new.html` | xp-interaction-designer | データの出所が不明でユーザーが混乱する可能性がある |
| 10 | `booking/new.html` の全必須フィールドに `<span class="text-danger">*</span>` を統一付与 | `booking/new.html` | xp-interaction-designer | 「重量」のみ `*` 付与で「荷主」「貨物種別」等に欠落。`estimation/new.html` と不一致 |
| 11 | ルート候補ゼロ件時のメッセージを「ルート候補がありません。」→「現在この区間のルート候補がありません。希望期限を延長するか、別の区間で再度見積もってください。」に変更 | `estimation/show.html` | xp-user-representative | 次アクションが不明でユーザーが行き詰まる |
| 12 | 見積詳細に `errorMessage` 用 alert を追加（`booking/show.html` との一貫性） | `estimation/show.html` | xp-interaction-designer | エラー発生時の表示パスが未設計 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 13 | ナビゲーション順序を業務フロー順（見積管理→予約管理→荷主管理）に変更 | `fragments/navbar.html` | xp-interaction-designer | 実際の業務フロー（見積→予約）と逆順になっている |
| 14 | `estimation/new.html` の手動 CSRF トークンを削除（`th:action` で自動挿入） | `estimation/new.html` | xp-interaction-designer | `booking/new.html` と実装パターンが不一致（動作には影響なし） |
| 15 | 見積番号の表示形式を UUID 切り詰めから人間可読な形式（例: EST-A1B2C3D4）に変更 | `estimation/index.html` | xp-interaction-designer | モバイルではツールチップが表示されず、フル ID を確認する手段がない |

---

## 矛盾事項

| # | 視点 A（インタラクションデザイナー） | 視点 B（ユーザー代表） | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | 「この見積で予約する」にモーダル確認ダイアログが必要（booking の慣例との整合） | フォームへの遷移なので確認は不要、ボタンラベルで期待値を伝えれば十分 | 状態変更操作 vs フォーム遷移操作の区別 | **ユーザー代表の判断を採用**: フォームへの遷移には確認ダイアログ不要。ボタンラベルの改善（提案 #7）で対応する |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-interaction-designer（高: 4 / 中: 5 / 低: 3）</summary>

### 評価サマリー
見積コンテキストの UI は、既存の予約管理・荷主管理画面と高い構造的一貫性を持ち、OOUX の「コレクションビュー → シングルビュー → アクション」という基本パターンに正しく準拠しています。一方で、情報設計（UN/LOCODE の生コード表示）、見積から予約への導線設計、アクセシビリティの細部、およびモダンデザインシステムへの準拠には改善の余地があります。

### 良い点
- OOUX パターンとの整合: 一覧(index) → 詳細(show) → アクション(予約へ遷移)という画面遷移が、UI 設計ガイドのコレクションビュー/シングルビューパターンに忠実
- 既存画面との構造的一貫性: `booking/index.html` と `estimation/index.html` のレイアウト構造（ヘッダー + 説明 + テーブルカード）が統一されており、ユーザーの学習コストが低い
- PRG パターンの遵守: フォーム送信後のリダイレクト + フラッシュメッセージが設計方針通りに実装
- 空状態の表示: 一覧・候補テーブルの両方で空状態メッセージが提供されており、ユーザーが「何もない」ことを認識できる
- フォームの補助テキスト: `aria-describedby` による入力補助、プレースホルダー、ヘルプテキストが適切に配置
- レスポンシブ対応: `table-responsive` と Bootstrap 5 のグリッドシステムが適切に使われている

### モダンデザイン準拠状況
- **デザイントークン**: 未導入。Bootstrap のデフォルトカラー/スペーシングをそのまま使用
- **セマンティックカラー**: 部分的。badge の `text-bg-secondary` は状態を反映しているが、状態ごとの色分けが未実装
- **Typography 階層**: 基本的。`h3` + `text-secondary` の 2 階層のみ
- **Motion / トランジション**: 未対応。画面遷移時のフィードバックアニメーションが皆無
- **ダークモード**: 未対応
- **コンポーネント一貫性**: 高い。ボタン・バッジ・カード・テーブルの使い方は既存画面と一致

### 懸念事項
- モバイルでの 7 カラムテーブルの可読性: 外出先でスマートフォンから見積を確認するシナリオでは重要情報優先のカード型レイアウトが必要
- 見積詳細の概算料金フォーマット: `#numbers.formatCurrency` でロケールに依存した通貨記号が表示されるリスク
- 見積と予約の貨物種別の値の整合: 見積フォームはハードコード option、予約フォームはサーバーサイド生成で不一致

### スコープ外の発見
- `booking/new.html` の `onchange="toggleSpecialFields()"` がインライン JS で CSP 導入時に問題
- `booking/index.html` のテーブルに `aria-label` が未付与
- `htmx 2.x` が技術スタックに記載されているが未使用
- ダッシュボードから見積一覧への導線が未実装
</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 3 / 低: 0）</summary>

### 評価サマリー
見積作成から予約作成への pre-fill 機能は営業担当者の二重入力を排除する実用的な改善です。ただし、生コード表示（GENERAL、JPTYO）と見積から予約への遷移時の状態管理欠如が、実際の業務運用で問題になると判断します。

### 良い点
- 見積から予約へのデータ引き継ぎ（pre-fill）により、荷主に提示した見積内容を正確に予約フォームに反映できる
- 見積作成フォームがシンプルで、必要最低限の入力項目に絞られており学習コストが低い
- ルート候補の一覧表示により、コスト・所要日数の比較が可能

### 最重要改善事項
1. **`CargoType.displayName` の追加**: 生コード「GENERAL」は業務ユーザーの心理モデルと乖離。`EstimateStatus` と同じパターンで「一般」「危険物」「冷凍・冷蔵」を表示
2. **見積状態管理の追加**: 「この見積で予約する」に `estimateId` を含め、予約完了時に見積を `BOOKED` 状態に遷移させる。同一見積から複数予約が作成される二重予約リスクがある
3. **ルート候補ゼロ時のガイダンス**: 「ルート候補がありません」のみでは次のアクションが不明。「希望期限を延長する」「別の区間を検討する」などの具体的な誘導メッセージに変更
</details>

---

## 次のステップ

### 優先対応（IT4 に向けて）

1. **#1 CargoType.displayName 追加**（バックエンド + テンプレート変更、工数小）
2. **#3 テーブル `scope="col"` 追加**（テンプレート変更のみ、工数極小）
3. **#11 ルート候補ゼロ件メッセージ改善**（テンプレート変更のみ、工数極小）
4. **#12 見積詳細に `errorMessage` alert 追加**（テンプレート変更のみ、工数極小）
5. **#4 見積→予約の状態遷移設計**（バックエンド設計変更が必要、工数大 → IT4 設計に含める）

### 許容（現状維持）

- **#2 UN/LOCODE 都市名併記**: ロケーションマスターデータが必要で工数大。バックログとして記録
- **#5 見積一覧の検索機能**: イテレーション計画外。見積件数が増えてから対応
- **#13 ナビゲーション順序変更**: 既存ユーザーへの影響を考慮し次リリース以降

---

*Generated by xp-interaction-designer + xp-user-representative 並列レビュー*
