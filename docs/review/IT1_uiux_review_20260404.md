# IT1 UI/UX レビュー結果

## レビュー対象

- **対象**: IT1 実装 Thymeleaf テンプレート（Bootstrap 5 + Thymeleaf + Spring Boot）
- **レビュー日**: 2026-04-04
- **対象画面**: navbar.html、index.html、login.html、shipper/{index,new,show}.html、booking/{index,new,show}.html（計 9 件）

## 総合評価

Bootstrap 5 のコンポーネントを正しく活用し、OOUX パターン（一覧 → 詳細 → 登録フォーム）が荷主・予約の両ドメインで一貫した実装は、IT1 初回リリースとして堅実な出来です。しかし、**ログイン画面の英語表示**、**予約登録での荷主 UUID 手入力**、**状態・種別の英語表示**（PRELIMINARY, GENERAL 等）、**受入条件に明記された未実装フィールド**（住所・寸法・個数・品名）が残存しており、現状のままでは営業担当者が日常業務でシステムを活用できない水準です。IT2 着手前にこれらの高優先指摘を解消することを強く推奨します。

## モダンデザイン準拠サマリー

| 評価項目 | 状態 | 備考 |
|---|---|---|
| カラーシステム | 要改善 | semantic color は正しく使用しているが、ステータスバッジが全件 `text-bg-secondary`（灰色）一色で状態の意味が伝わらない |
| タイポグラフィ | OK | h3 → 説明文 → カード → テーブルの階層が全画面で統一 |
| Elevation & Surface | OK | `shadow-sm` を全カードに一貫適用、テーブルカードの `p-0` 処理が正確 |
| コンポーネント一貫性 | OK | ボタン・テーブル・フォームのパターンが全画面で統一 |
| スペーシング | OK | `py-4`, `mt-4`, `g-3` 等 Bootstrap utilities を適切に使用 |
| レスポンシブ / Adaptive | 要改善 | `table-responsive` は対応済みだが、ナビバーのハンバーガーメニューが欠落しており 992px 未満でナビが消える |
| ダークモード | 未対応 | Bootstrap デフォルト（ライトモードのみ） |
| 状態デザイン（空/Loading/Error） | 要改善 | 空状態・エラー状態は実装済み。成功フィードバック（フラッシュメッセージ）・ローディング状態は未実装 |

## 改善提案（重要度順）

### 高（IT2 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | ログイン画面を全面日本語化 | login.html | 両エージェント | 全画面が日本語なのにログイン画面だけ英語表記で言語の一貫性が破綻。"Invalid username or password." が日本語ユーザーに伝わらない。システムの「玄関」であり第一印象に直結 |
| H2 | ナビバーにハンバーガーメニューを追加 | navbar.html | xp-interaction-designer | `navbar-expand-lg` を指定しているが `navbar-toggler` ボタンと `collapse` 要素がないため、992px 未満の画面でナビゲーションが完全に消える（操作不能） |
| H3 | フォームに required 属性と aria-describedby を追加 | shipper/new.html, booking/new.html | xp-interaction-designer | 必須項目の `required` 属性が全フィールドに未付与。空送信後のサーバーエラーを見るまで必須項目を認識できない。スクリーンリーダーとエラーの関連付けも未実装（WCAG 2.1 AA 違反） |
| H4 | ステータスバッジを状態別に色分け | booking/index.html | xp-interaction-designer | 全件 `text-bg-secondary`（灰色）でステータスが区別できない。UI 設計ドキュメントで定義された色分け（PRELIMINARY=橙, CONFIRMED=緑, CANCELLED=赤）を実装する |
| H5 | 予約登録の荷主 ID 入力を選択式に変更 | booking/new.html | 両エージェント | 荷主の内部 UUID（36 桁英数字）を手入力させる設計は業務で使用不可能。登録済み荷主のドロップダウン選択（表示形式: `SHP-00000001 - 山田商事`）に変更する |
| H6 | 状態・貨物種別を日本語表示に変換 | booking/index.html, booking/show.html, booking/new.html | xp-user-representative | PRELIMINARY / GENERAL / REFRIGERATED / HAZARDOUS が英語のまま表示されており業務で使用できない。荷主管理側（法人/個人）は既に日本語化されており不整合。マッピング: PRELIMINARY→仮受付, GENERAL→一般貨物, REFRIGERATED→冷凍・冷蔵, HAZARDOUS→危険物 |
| H7 | 割引率の上限を 0.15 から 0.30 に修正 | shipper/new.html | 両エージェント | `max="0.15"` はユーザーストーリー US02 の受入基準「割引率 0〜30%」と乖離。大口顧客への 25% 割引が入力できない致命的な仕様不整合 |
| H8 | 受入条件に明記された未実装フィールドを追加 | shipper/new.html, booking/new.html | xp-user-representative | US02「住所」、US04「貨物種別・重量・寸法・個数・品名」が受入基準に明記されているが未実装。住所は請求書送付先・集荷先特定に必須。寸法・品名は国際貨物の税関申告に法的必要 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | 予約一覧・詳細の荷主欄に荷主名を表示 | booking/index.html, booking/show.html | 両エージェント | 荷主列に UUID を表示しても誰の予約か識別できない。荷主コード + 荷主名（例: SHP-00000001 - 山田商事）の表示が必要 |
| M2 | UN/LOCODE 入力にプレースホルダーと入力例を追加 | booking/new.html | 両エージェント | UN/LOCODE を記憶していないユーザーは入力できない。最低限 `placeholder="例: JPTYO（東京）"` の追加。IT2 以降でオートコンプリートへ発展させる |
| M3 | 詳細画面に編集・削除ボタン枠を追加 | shipper/show.html, booking/show.html | xp-interaction-designer, xp-user-representative | CRUD の U/D がなく修正手段がない。IT1 範囲でなければ `disabled` 状態でボタン枠を配置（「IT2 で対応予定」ツールチップ付き）し、将来の拡張を示す |
| M4 | 登録成功フラッシュメッセージを実装 | shipper/new.html, booking/new.html | xp-interaction-designer | UI 設計ドキュメントの基本 UX 原則「操作成功はフラッシュメッセージで通知」に準拠していない。PRG パターンのリダイレクト後に成功メッセージを表示する仕組みが必要 |
| M5 | 割引率の表示をパーセント形式に変換 | shipper/show.html, shipper/new.html | xp-user-representative | `0.1` 表示では 0.1% なのか 10% なのか判別不能。`10%` 形式での表示に変換する。入力も整数パーセント（0〜30）で受け付け内部変換する方式が自然 |
| M6 | 重量フィールドに単位 (kg) を表示 | booking/new.html, booking/show.html | xp-user-representative | 単位なしの重量入力・表示は混乱を招く。ラベルを「重量 (kg)」に変更、詳細は「10.500 kg」形式で表示 |
| M7 | メールアドレス重複チェック UI を実装 | shipper/new.html | xp-user-representative | US02 受入基準 2「同一メールアドレスの場合に既存荷主を選択できる」が未実装。二重登録による予約管理の混乱を防ぐために必要 |
| M8 | ダッシュボードの `activeMenu='dashboard'` に対応するナビリンクを追加 | navbar.html | xp-interaction-designer | `navbar('dashboard')` でアクティブ状態を渡しているが対応するメニューリンクがなく、ダッシュボード表示時にどのメニューもハイライトされない |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | 詳細画面の dt を `col-md-3` に変更 | shipper/show.html, booking/show.html | xp-interaction-designer | `dt.col-sm-3` は 576px 未満でラベルが折り返される可能性あり。`col-md-3` への変更でより広い範囲で横並びを維持できる |
| L2 | 荷主詳細で個人荷主の法人フィールドを非表示 | shipper/show.html | xp-user-representative | 個人荷主の詳細に「契約番号: -」「割引率: -」が表示され混乱を招く。Thymeleaf `th:if` で法人のみ表示する |
| L3 | 希望着日を日本語フォーマット（yyyy/MM/dd）で表示 | booking/index.html, booking/show.html | xp-user-representative | `2026-04-30`（ISO 形式）は読みにくい。`2026/04/30` または `2026年4月30日` への変換を推奨 |
| L4 | shipper/new.html に Bootstrap JS を追加 | shipper/new.html | xp-user-representative | 他テンプレートでは `bootstrap.bundle.min.js` を読み込んでいるが荷主登録フォームでは未読み込み。ツールチップ等追加時に問題となる |
| L5 | ダッシュボードにサマリー情報を追加（IT2 以降） | index.html | xp-user-representative | 現状はナビバーと同内容のリンクカードのみで業務価値がない。「仮受付中の予約: N 件」等のサマリーを IT2 以降で追加する |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | interaction-designer: UN/LOCODE はオートコンプリートが理想 | user-representative: IT1 では `placeholder` で十分、IT2 以降でオートコンプリート | IT1 の対応範囲 | **user-representative 支持**。IT1 では最低限の `placeholder` を追加し、IT2 でオートコンプリートに発展させる。スコープ管理を優先 |
| 2 | interaction-designer: `container-fluid` を推奨（UI 設計ドキュメント準拠） | user-representative: 現状の `container` でも IT1 テーブル列数では問題なし | コンテナ幅 | **保留**。IT2 でテーブル列が増えた時点で `container-fluid` への移行を判断する |

## エージェント別フィードバック詳細

<details>
<summary>xp-interaction-designer（高: 4 件 / 中: 6 件 / 低: 2 件）</summary>

### 評価サマリー

全体として、Bootstrap 5 のコンポーネントとユーティリティを適切に活用した堅実な実装です。OOUX の「一覧 → 詳細 → アクション」パターンが荷主・予約の両フローで一貫しており、初回リリースとしての完成度は高いと評価します。ただし、UI 設計ドキュメントで定義されたワイヤーフレームとの乖離、アクセシビリティ上の欠落、および言語の一貫性に改善の余地があります。

### 良い点

- **OOUX パターンの一貫適用**: 荷主・予約の両ドメインで「コレクションビュー（一覧テーブル） → シングルビュー（詳細 dl/dd） → 作成フォーム」の 3 画面構成が統一されており、UI 設計ガイドで定義されたパターンに忠実です。
- **空状態の実装**: 一覧画面で `#lists.isEmpty()` による空状態メッセージを表示しており、システム状態をユーザーに明確に伝えています。
- **フォームのバリデーションフィードバック**: `th:errors` と `invalid-feedback d-block` の組み合わせで、フィールド単位のエラー表示が実装されています。
- **視覚的階層の統一**: h3 → 説明テキスト → カード → テーブルという情報の階層が全画面で一貫しています。
- **法人フィールドの条件表示**: JavaScript による種別連動の表示/非表示は、認知負荷の軽減として適切な設計判断です。
- **レスポンシブグリッドの活用**: `col-lg-8` による中央寄せ、`col-md-6` の 2 カラム構成など、ブレークポイントを意識した配置です。

### モダンデザイン準拠状況

| 観点 | 評価 | コメント |
|:---|:---:|:---|
| カラーシステム | 良好 | semantic color を正しく使用。ただしステータスバッジの色が `text-bg-secondary` のみ |
| タイポグラフィ | 良好 | h3 をページタイトル、h5 をカードタイトルに統一 |
| Elevation & Surface | 良好 | 全カードに `shadow-sm` を一貫適用 |
| コンポーネント統一性 | 良好 | ボタン・テーブル・フォームが全画面で統一 |
| スペーシング | 良好 | Bootstrap spacing utilities を適切に使用 |
| レスポンシブ | 概ね良好 | `table-responsive` 適用済み。詳細画面の `dt.col-sm-3` は改善余地あり |
| 状態デザイン | 部分的 | 空状態・エラー状態は対応。成功フィードバック・ローディング状態は未実装 |

### 改善提案

- 【重要度: 高】login.html — ログイン画面の言語混在（詳細は H1 参照）
- 【重要度: 高】navbar.html — ハンバーガーメニュー欠落（詳細は H2 参照）
- 【重要度: 高】フォーム全般 — required 属性・aria-describedby 欠落（詳細は H3 参照）
- 【重要度: 高】booking/index.html — ステータスバッジの色分け未実装（詳細は H4 参照）
- 【重要度: 中】booking/new.html — 荷主 ID の手入力（詳細は H5 参照）
- 【重要度: 中】booking/index.html — 荷主 UUID 表示（詳細は M1 参照）
- 【重要度: 中】booking/new.html — UN/LOCODE 入力補助（詳細は M2 参照）
- 【重要度: 中】show.html 全般 — 詳細画面のアクション不足（詳細は M3 参照）
- 【重要度: 中】フォーム全般 — 成功フィードバック欠如（詳細は M4 参照）
- 【重要度: 中】navbar.html — ダッシュボードのアクティブ状態欠落（詳細は M8 参照）
- 【重要度: 低】shipper/new.html — 割引率上限の仕様乖離（詳細は H7 参照）
- 【重要度: 低】show.html 全般 — dt を col-md-3 に変更（詳細は L1 参照）

### 懸念事項

- ナビバーのハンバーガーメニュー未実装により 992px 未満でナビが消える
- テーブルに `<caption>` がなくスクリーンリーダーでテーブルの目的を把握できない
- login.html に `autocomplete="username"` / `autocomplete="current-password"` が未指定
- バリデーションエラー発生時のフォーカス管理が未実装

### スコープ外の発見

- htmx が index.html でのみ読み込まれており他テンプレートで未使用
- UI 設計ドキュメントとの乖離（`container` vs `container-fluid`、ページネーション未実装、検索/フィルタ未実装）

</details>

<details>
<summary>xp-user-representative（高: 5 件 / 中: 5 件 / 低: 2 件）</summary>

### 評価サマリー

IT1 として「荷主を登録し、予約を登録し、一覧と詳細で確認する」という骨格は構築されていますが、**営業担当者が毎日の業務で実際に使える状態にはまだ達していません**。最大の阻害要因は、予約登録時の荷主 UUID 手入力と業務用語の英語表示の 2 点であり、これらが解消されない限り、現場に展開しても「結局 Excel に戻る」という事態になります。

### 良い点

- 荷主一覧の種別バッジが日本語化されており（法人/個人）、荷主詳細でも同様の処理がされている
- 法人フィールドの動的表示（種別連動）により、個人荷主登録時の余計なフィールドが非表示になっている
- 一覧画面のヘッダー構造（タイトル左揃え・アクションボタン右揃え）が統一されている
- 空状態メッセージが日本語で明確
- フォームのバリデーションが 2 段構え（全体アラート + フィールド個別）になっている
- テーブルに `table-responsive` が適用されている

### 業務適合性評価

- Bootstrap 5 ベースのカード + テーブルレイアウトは業務システムとして十分モダン
- ナビバーに「ダッシュボード」リンクがなくホームへの戻り方が分かりにくい
- フォーム入力の補助（placeholder, ヒントテキスト, ツールチップ）が不足

### 主要改善提案

- 【重要度: 高】login.html — ログイン画面の全面日本語化（詳細は H1 参照）
- 【重要度: 高】booking/new.html — 荷主 UUID 手入力を選択式に変更（詳細は H5 参照）
- 【重要度: 高】booking 全般 — 状態・種別の日本語化（詳細は H6 参照）
- 【重要度: 高】shipper/new.html — 割引率上限修正（詳細は H7 参照）
- 【重要度: 高】shipper/new.html, booking/new.html — 受入条件未実装フィールド（詳細は H8 参照）
- 【重要度: 中】booking/index.html, booking/show.html — 荷主 UUID 表示（詳細は M1 参照）
- 【重要度: 中】shipper/show.html — 割引率をパーセント表示（詳細は M5 参照）
- 【重要度: 中】booking/new.html, booking/show.html — 重量に単位 (kg) 追加（詳細は M6 参照）
- 【重要度: 中】shipper/new.html — メール重複チェック UI（詳細は M7 参照）
- 【重要度: 中】booking/new.html — UN/LOCODE 入力ヒント（詳細は M2 参照）
- 【重要度: 低】show.html 全般 — 編集ボタン枠を追加（詳細は M3 参照）
- 【重要度: 低】index.html — ダッシュボードにサマリー追加（詳細は L5 参照）

### 懸念事項

- 業務フローの断絶（荷主登録完了後に「その荷主で予約登録」へのショートカットがない）
- エラーメッセージが「入力内容を確認してください。」の 1 種類のみで具体性不足
- 希望着日が ISO 形式（2026-04-30）で表示され読みにくい
- 個人荷主詳細に「契約番号: -」「割引率: -」が表示され誤解を招く

### スコープ外の発見

- shipper/new.html に Bootstrap JS が読み込まれていない
- バリデーションのフロント/バック整合性が未確認（割引率 max=0.15 の乖離と同様の問題が他フィールドにある可能性）

</details>

## IT2 対応方針（高優先指摘）

| # | 指摘 | 対応方針 | IT2 ストーリー化 |
|---|------|---------|----------------|
| H1 | ログイン画面日本語化 | 修正する | `messages.properties` を利用して日本語化。i18n 対応の第一歩として実施 |
| H2 | ナビバーハンバーガーメニュー | 修正する | `navbar-toggler` ボタンと `collapse` 要素を追加 |
| H3 | required 属性・aria-describedby | 修正する | 全フォームに required 属性を付与し aria-describedby でエラー関連付け |
| H4 | ステータスバッジ色分け | 修正する | Thymeleaf 条件分岐で Bootstrap semantic color を適用 |
| H5 | 荷主 ID を選択式に変更 | 修正する | `<select>` で登録済み荷主一覧から選択（`SHP-00000001 - 山田商事` 形式） |
| H6 | 状態・種別の日本語化 | 修正する | Thymeleaf マッピング（PRELIMINARY→仮受付等）を全 booking 画面に適用 |
| H7 | 割引率上限 0.15→0.30 | 修正する | `max="0.30"` に修正。バックエンドの `@Max` 値との整合性も確認 |
| H8 | 受入条件未実装フィールド | 保留（範囲確認後） | 住所・寸法・個数・品名の受入基準との整合性を確認し、IT2 スコープとして計画 |
