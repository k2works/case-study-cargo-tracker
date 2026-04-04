# IT7 UI/UX レビュー（routing/search.html・routing/design-condition.html）

**レビュー日**: 2026-04-04  
**対象イテレーション**: IT7（Phase 3 前半、US21 経路候補算出）  
**対象画面**: `routing/search.html`（経路候補検索結果）、`routing/design-condition.html`（経路設計条件確認）

---

## 総合評価

フィルタ適用説明文・候補なし時の再検索フォーム・「✅ 間に合います」バッジなど、業務フローを強く意識した実装が評価できる。一方、**「この予約に割り当てる」ボタンに確認ステップがない**（非可逆操作の誤操作リスク）と**出発予定日が非表示**（意思決定情報の欠落）が最優先で解消すべき課題として両エージェントから共通して指摘された。

---

## モダンデザイン準拠サマリー

| 評価項目 | 状態 | 備考 |
|---------|------|------|
| カラーシステム | 要改善 | `bg-secondary` がセマンティックでない用途に使われている（件数バッジ・貨物種別バッジ） |
| タイポグラフィ | OK | Bootstrap 5 デフォルトで概ね適切 |
| Elevation & Surface | OK | カード・アラートの階層は明確 |
| コンポーネント一貫性 | 要改善 | 「✅」絵文字がアイコンシステムと不整合（Bootstrap Icons 未使用） |
| スペーシング | 要改善 | `<main class="py-4">` の余白が不足気味 |
| レスポンシブ / Adaptive | OK | Bootstrap グリッドを使用 |
| ダークモード | 未対応 | 全体的に未対応（スコープ外） |
| 状態デザイン（空 / Loading / Error） | 要改善 | 候補なし状態は設計済み。Loading・Error 状態は未設計 |

---

## 改善提案（重要度順）

### 高（リリース前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | 「この予約に割り当てる」に確認モーダルを追加 | `search.html` ルートカード | interaction-designer、user-representative | 非可逆操作。B2B 業務アプリでは誤操作のリカバリーコストが高い。Bootstrap 5 の `<div class="modal">` で対応可能 |
| H2 | `✅` 絵文字を Bootstrap Icons の `<i class="bi bi-check-circle-fill">` に置換 | `search.html` 推定着日欄 | interaction-designer | アクセシビリティ（スクリーンリーダー）対応必須。絵文字はレンダリング環境依存でフォールバックが保証されない |
| H3 | 出発予定日（`estimatedDeparture`）をルートカードに追加 | `search.html` ルートカード | user-representative | 出発日がないと所要日数だけでは実際のスケジュールを判断できない。「14 日」でも「3 週間後出発」と「明日出発」では着日が大きく異なる |
| H4 | UN/LOCODE を港名と併記（例: `JPTYO（東京）→ SGSIN（シンガポール）`） | `search.html` 経由港表示 | user-representative | 業務担当者は「KRPUS」では判読できない。特に新人や経由が多い航路で問題 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | 候補件数バッジを `bg-secondary` → `bg-primary` に変更 | `search.html` 件数バッジ | interaction-designer | `bg-secondary` は「補助的情報」を表すセマンティクス。件数は主要情報 |
| M2 | 貨物種別バッジを `bg-secondary` → `bg-info` に変更 | `search.html` 対応貨物種別バッジ | interaction-designer | 情報性バッジには `bg-info` が意味論的に適切 |
| M3 | `<main class="py-4">` → `<main class="py-5">` でスペーシング改善 | `search.html`、`design-condition.html` | interaction-designer | 現状の余白が狭く、コンテンツが詰まった印象 |
| M4 | 「補完依頼を行う」ボタン名を「予約詳細に戻る（営業担当者への連絡が必要）」に変更 | `design-condition.html` 不完全時のボタン | user-representative | 現状のラベルでは「通知が自動送信された」と誤解される。実際は画面遷移のみ |
| M5 | ルートカードに「出発地 → 経由港 → 目的地」の全経路フローを 1 行で表示 | `search.html` ルートカード | user-representative | 現状は「経由港」項目のみで出発地・目的地が含まれず、カード単体で全体像がつかめない |
| M6 | 対応貨物種別バッジの表示を絞り込む（検索済みの種別は自明のため追加対応種別のみ表示） | `search.html` 対応貨物種別バッジ | user-representative | 一般貨物で検索した場合に 3 バッジ全て表示されるのは情報密度過多 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | 再検索フォームのボタン幅を `w-100`（全幅）に調整 | `search.html` 候補なし時 | interaction-designer | 小幅ボタンはモバイルでのタップ領域が狭い |
| L2 | ソート切り替えオプション（所要日数順 / 料金安い順）を追加 | `search.html` | user-representative | コスト優先の案件では料金昇順ソートが有用 |
| L3 | 条件不完全な予約からのルート検索時に `design-condition.html` を必須経由に | 予約詳細 → search 遷移 | user-representative | 不完全条件で検索すると「候補なしの原因がわからない」状態になる可能性 |

---

## 矛盾事項

| # | 視点 A（interaction-designer） | 視点 B（user-representative） | 論点 | 推奨判断 |
|---|-------------------------------|-------------------------------|------|----------|
| 1 | 貨物種別バッジを情報バッジとして全表示（航海の能力を可視化する意義あり） | フィルタ済みなら自明なので追加対応種別のみ表示が適切 | 対応貨物種別の表示範囲 | 初回リリースは現状維持。ユーザーフィードバックを得てから絞り込みを検討 |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-interaction-designer（高: 2 / 中: 3 / 低: 1）</summary>

**評価サマリー**: 最も優先度が高いのは割り当てアクションの確認ステップ。B2B 業務アプリでは誤操作のリカバリーコストが高く、Bootstrap 5 のモーダルコンポーネントで対応できる。

**改善提案（優先度順）**:

| 優先度 | 対象 | 対応 |
|--------|------|------|
| 🔴 高 | 割り当てボタンの確認モーダル | 非可逆アクションにつき必須 |
| 🔴 高 | 絵文字「✅」→ Bootstrap Icons | アクセシビリティ必須対応 |
| 🟡 中 | 候補件数バッジ `bg-secondary` → `bg-primary` | 色の意味論的修正 |
| 🟡 中 | 貨物種別バッジ `bg-secondary` → `bg-info` | 視覚的区別の強化 |
| 🟡 中 | `<main class="py-4">` → `<main class="py-5">` | 視覚的余白の改善 |
| 🟢 低 | 再検索ボタンの幅調整 | 任意 |
</details>

<details>
<summary>xp-user-representative（高: 4 / 中: 4 / 低: 1）</summary>

**評価サマリー**: UI の丁寧な作り込みや候補なし時の再検索フォームなど、実際の業務フローを意識した実装が随所に見られる。しかし経路設計者が「最初に見る情報」である出発予定日が画面に存在しないため、現時点では実務での意思決定に使えない。

**受入条件充足状況（US21 の観点）**:
- 受入条件 2（ソート）: ✅ 充足
- 受入条件 3（フィルタ説明文）: ✅ 充足
- 受入条件 5（再検索フォーム）: ✅ 充足
- 割り当て操作の確認ステップ: ❌ 未対応（安全性リスク）

**懸念事項**:
- 割り当て後のルート変更・取り消しフローが見当たらない（再割り当てシナリオ）
- 「希望着日」と「希望引渡日」の用語混在が混乱を招く可能性

**スコープ外の発見**:
- 「補完依頼を行う」ボタンが画面遷移のみで通知を送信しないにもかかわらず、ラベルが「依頼を行う」となっており誤解を招く
</details>

---

## 改善アクション提案

### H1: 割り当てボタンへの確認モーダル追加

```html
<!-- search.html: 確認モーダルを追加 -->
<!-- トリガーボタン（既存の form submit を置き換え） -->
<button type="button" class="btn btn-primary btn-sm"
        data-bs-toggle="modal"
        data-bs-target="#assignModal"
        th:attr="data-voyage-number=${candidate.voyageNumber}">
  この予約に割り当てる
</button>

<!-- モーダル（body タグ直前に追加） -->
<div class="modal fade" id="assignModal" tabindex="-1" aria-labelledby="assignModalLabel" aria-hidden="true">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="assignModalLabel">経路の割り当て確認</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
      </div>
      <div class="modal-body">
        航海番号 <strong id="assignVoyageNumber"></strong> をこの予約に割り当てます。よろしいですか？
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
        <form id="assignForm" method="post" th:action="@{/routings/assign}">
          <input type="hidden" name="bookingId" th:value="${bookingId}" />
          <input type="hidden" id="assignVoyageInput" name="voyageNumber" />
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
          <button type="submit" class="btn btn-primary">割り当てる</button>
        </form>
      </div>
    </div>
  </div>
</div>
```

### H2: Bootstrap Icons への置換

```html
<!-- 変更前 -->
<span class="badge bg-success">✅ 間に合います</span>

<!-- 変更後 -->
<span class="badge bg-success">
  <i class="bi bi-check-circle-fill" aria-label="間に合います"></i> 間に合います
</span>

<!-- ※ Bootstrap Icons の CDN を <head> に追加 -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
```
