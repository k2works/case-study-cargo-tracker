# IT5 UI/UX レビュー

**日付**: 2026-04-03  
**対象イテレーション**: IT5  
**レビュー担当**: xp-interaction-designer / xp-user-representative  
**レビュー対象**: 例外事象記録画面（exception/new.html）を中心とした UI/UX 全体

---

## 評価サマリー

IT5 で新規追加された例外事象記録画面は、フォームの構成や荷主自動通知の仕組みなど基本的な業務要件を押さえており、まず使える水準に達しています。一方、POST 後リダイレクトなしによる二重送信リスク、`btn-danger` の意味的誤用、ARIA ロールの誤用、`tracking/show.html` のナビゲーション欠落など、業務信頼性とアクセシビリティに直結する問題が複数あり、これらは早急な対応が必要です。

---

## 良い点

- **フォーム上部の説明文が親切。** 「この画面では何を記録するのか」「記録後に何が起きるのか」が一目で分かる。
- **「紛失」選択時の緊急フラグ自動設定は業務的に正しい設計。** 港湾オペレーターが手動でフラグを立てる手間を省く。
- **`aria-describedby` の適用（`locationCode` フィールド）。** スクリーンリーダー対応の基礎ができている。
- **バリデーション表示の一貫性。** `is-invalid` + `invalid-feedback` が主要フィールドに統一的に実装されている。
- **`data-testid` の付与。** E2E テストとの連携を意識した設計。
- **CSRF 対策。** `_csrf` hidden フィールドが正しく埋め込まれている。
- **`card shadow-sm` パターンの統一。** システム全体で Surface 分離が機能している。
- **荷主への自動通知は業務価値が高い。** 従来は例外記録後に別途メールを送る二度手間が発生しており、自動化は大きな助けになる。

---

## モダンデザイン準拠状況

| 評価項目 | 状態 | 備考 |
|---|---|---|
| カラーシステム | 要改善 | `btn-danger`（赤）を「記録」確定操作に使っており、削除・危険操作と混同される |
| タイポグラフィ | OK | `h4` + `col-form-label` の階層は最低限整理されている |
| Elevation & Surface | OK | `card shadow-sm` が全画面で一貫している |
| スペーシング | 要改善 | `<main>` タグに `py-4` がなく、`tracking/show.html` と spacing ルールが不一致 |
| レスポンシブ | 要改善 | `datetime-local` が `col-sm-6` 固定で、モバイル幅では入力しにくい |
| 状態デザイン | 要改善 | POST 後リダイレクトなしで再送信リスクが残る |
| フィードバック設計 | 要改善 | 「紛失」記録後の緊急フラグ設定を成功メッセージで明示していない |

---

## 改善提案

### 【重要度: 高】POST 後に PRG パターンを適用し二重送信を防ぐ

**対象**: `exception/new.html`、`ExceptionWebController`

現在 POST 成功後は同一ページへのフォワードでサクセスメッセージを表示。ブラウザリロードで同じ POST が再送され例外事象が重複登録される。

**推奨対応**: PRG（Post-Redirect-Get）パターンに変更。

```
POST /exceptions/new
  → 成功 → redirect:/exceptions/new?success=true  ← GET で再表示
  → 失敗 → forward（バリデーションエラー表示）
```

---

### 【重要度: 高】`btn-danger` を `btn-primary` に変更する

**対象**: `exception/new.html`（`<button type="submit" class="btn btn-danger">`）

`btn-danger`（赤）は Bootstrap 慣用として「削除・取り消し・不可逆操作」に用いられる。「例外事象を記録する」は確定・作成操作であり、赤ボタンは心理的抵抗感を与えオペレーターの記録遅延につながる。

```html
<!-- 修正前 -->
<button type="submit" class="btn btn-danger">例外事象を記録</button>

<!-- 修正後 -->
<button type="submit" class="btn btn-primary" data-testid="submit-exception">例外事象を記録</button>
```

---

### 【重要度: 高】`role="note"` を正しい ARIA ロールに修正する

**対象**: `exception/new.html`（`<div class="alert alert-warning small" role="note">`）

`note` は ARIA 仕様に存在しないロール。WCAG 2.1 達成基準 4.1.2 違反。

```html
<!-- 修正後 -->
<div class="alert alert-warning small" role="region" aria-label="業務説明">
```

---

### 【重要度: 高】`tracking/show.html` にグローバルナビゲーションを追加する

**対象**: `tracking/show.html`

`<body>` の直後から `<main>` が始まっており、`<nav th:replace="~{fragments/header :: appHeader}">` が存在しない。追跡画面から他の業務画面への導線が完全に失われており、ユーザーはブラウザの「戻る」ボタンに頼るしかない。

```html
<body class="bg-light">
<nav th:replace="~{fragments/header :: appHeader}"></nav>
<main>
```

---

### 【重要度: 高】追跡画面から例外記録画面への導線を追加する

**対象**: `tracking/show.html`

港湾オペレーターの業務フロー「追跡番号を探す → 追跡画面で状況確認 → 例外を記録する」において、追跡画面に「例外を記録する」ボタンが存在しないため、追跡番号を手で再入力する必要がある。

```html
<!-- 追跡番号カードまたは基本情報カードに追加 -->
<a th:href="@{/exceptions/new(trackingNumber=${trackingInfo.trackingNumber})}"
   class="btn btn-outline-danger btn-sm">例外を記録</a>
```

---

### 【重要度: 高】「発生日時」のデフォルト値を現在日時にする

**対象**: `exception/new.html`（`occurredAt` フィールド）

例外事象は「今まさに起きている問題」を記録する操作。デフォルトを現在日時にすることで、9 割のケースで変更不要になる。コントローラーで `model.addAttribute("defaultOccurredAt", LocalDateTime.now())` を設定し、`th:value="${defaultOccurredAt}"` で反映する。

---

### 【重要度: 中】「戻る」ボタンの遷移先をホームから変更する

**対象**: `exception/new.html`（`<a th:href="@{/}" class="btn btn-outline-secondary">戻る</a>`）

追跡画面から来たユーザーが「戻る」でトップページに遷移するのは不自然。URL パラメータでリファラーを受け取り、戻り先を動的に設定するか、ラベルを「キャンセル」に変更する。

---

### 【重要度: 中】例外種別 badge の色を深刻度別に分ける

**対象**: `tracking/show.html`（例外対応履歴テーブル）

遅延・破損・紛失のすべてが `bg-danger`（赤）で、深刻度の視覚的階層が失われている。

```html
<!-- 遅延: 警告色 -->
<span class="badge bg-warning text-dark">遅延</span>
<!-- 破損: 橙色 -->
<span class="badge bg-danger bg-opacity-75">破損</span>
<!-- 紛失: 最高警戒 -->
<span class="badge bg-danger fw-bold">紛失</span>
```

---

### 【重要度: 中】`occurredAt` に `aria-describedby` を追加する

**対象**: `exception/new.html`（`occurredAt` フィールド）

`trackingNumber` と `locationCode` には `aria-describedby` が設定されているが、`occurredAt` の `invalid-feedback` には設定がない。WCAG 2.1 達成基準 1.3.1 違反。

---

### 【重要度: 中】`datetime-local` にタイムゾーン補足を追加する

**対象**: `exception/new.html`（`occurredAt` フィールド）

国際港湾業務では発生日時のタイムゾーンが重要。IT4 の `handling/new.html` でも同様の指摘あり。

```html
<div class="form-text">ローカル時刻（JST）で入力してください</div>
```

---

### 【重要度: 中】発生場所コード（UN/LOCODE）の入力支援を追加する

**対象**: `exception/new.html`（`locationCode` フィールド）

UN/LOCODE は専門知識が必要で、コードを知らない担当者は何を入力すべきか分からない。よく使う港のリスト（ドロップダウンまたはオートコンプリート）か、空欄でも可という案内を追記する。

---

### 【重要度: 低】`<main>` の上部スペーシングを統一する

**対象**: `exception/new.html`

`tracking/show.html` では `container py-4` で上下パディングがあるが、`exception/new.html` はパディングなし。

```html
<main class="py-4">
```

---

### 【重要度: 低】「紛失」記録後の緊急フラグ設定を成功メッセージで明示する

**対象**: `ExceptionWebController`（POST 成功メッセージの文言）

「緊急フラグが設定されました」という情報を成功メッセージに含めることで、オペレーターが次のアクションを即座に判断できる。

---

## 懸念事項

1. **二重登録リスク（最重要）**: PRG パターンなしの POST 設計により、例外事象が重複登録されると追跡情報が汚染される。港湾業務では記録が保険・法的証拠として使われる可能性があるため、運用上の深刻なリスク。

2. **「例外記録」ナビリンクの文脈不足**: 単独フラットに配置されており、他の業務カテゴリと同列に見える。緊急対応業務であることを示す視覚的区別（アイコン、badge 等）が望ましい。

3. **「対応内容（resolution）」の必須化**: 例外発生直後は対応内容が未確定のケースがある。必須にすることで仮の文言を入力して後で更新する運用が発生しないか懸念。後から更新できる仕組みが必要。

4. **アクセシビリティ連鎖問題**: `role="note"` の誤用と `occurredAt` の `aria-describedby` 欠落が複合すると、WCAG 2.1 AA 達成が困難。

---

## スコープ外の発見

1. **追跡画面に検索フォームがない**: 追跡番号を知っている前提で URL 直打ちかリンク遷移のみが入口になっている。
2. **例外記録の一覧画面がない**: ナビに「荷役一覧」はあるが「例外記録一覧」がない。担当者が今日記録した例外をまとめて確認する業務ニーズは必ず発生する（将来ストーリー候補）。
3. **追跡 URL 共有機能がない**: 物流担当者が荷主に追跡 URL を共有するユースケースに対応できていない（US16 候補）。
4. **IT4 指摘事項（`datetime-local` タイムゾーン補足）が IT5 でも再現**: コーディングスタンダードとして「全フォームの `datetime-local` にはタイムゾーン補足を付ける」ルールの追加を推奨。

---

*高重要度 5 件・中重要度 4 件・低重要度 2 件・懸念事項 4 件・スコープ外 4 件。特に高重要度の「POST 二重送信リスク」「btn-danger 誤用」「ARIA role 誤用」「ナビゲーション欠落」「追跡→例外記録の導線欠落」は業務信頼性・アクセシビリティに直結するため、IT5 クローズ前の対応を推奨します。*
