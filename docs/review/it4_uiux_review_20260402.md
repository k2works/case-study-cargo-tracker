# IT4 UI/UX レビュー

## レビュー対象

- IT4 US10 「荷役作業を記録する」
- [handling/new.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/new.html)
- [handling/list.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/list.html)
- [HandlingWebController.java](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/java/com/example/cargotracker/handling/interfaces/web/HandlingWebController.java)
- [handling.spec.ts](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/e2e/src/tests/handling.spec.ts)
- [iteration_plan-4.md](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/docs/development/iteration_plan-4.md)

## 総合評価

Bootstrap ベースで情報構造は素直で、`US10` の受入条件を満たす最短導線はできています。  
一方で、港湾オペレーター向けの「荷役作業記録」と、`RECEIVE` / `MANUAL_UPDATE` のような別業務を同じ汎用フォームに混在させており、業務メンタルモデルと画面の責務がずれ始めています。

## モダンデザイン準拠サマリー

| 評価項目 | 状態 | 備考 |
|---|---|---|
| カラーシステム | 要改善 | Bootstrap 既定色に依存しており、業務上の重要状態に対する意味づけが弱い |
| タイポグラフィ | OK | 見出し、ラベル、テーブルの階層は最低限整理されている |
| Elevation & Surface | OK | card による面の分離は機能している |
| コンポーネント一貫性 | 要改善 | フォームと一覧は一貫しているが、業務別 UI の分離が不足している |
| スペーシング | OK | Bootstrap の間隔で大きな破綻はない |
| レスポンシブ / Adaptive | 要改善 | 一覧テーブルに横スクロール対策がなく、Compact 幅で崩れやすい |
| ダークモード | 未対応 | 現状はライトテーマ前提 |
| 状態デザイン（空 / Loading / Error） | 要改善 | 空状態とエラー状態の次アクション提示が弱い |

## 改善提案（重要度順）

### 高（リリース前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | `RECEIVE` と `MANUAL_UPDATE` を `US10` の汎用登録フォームから分離し、少なくとも用途別の導線または説明を追加する | [new.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/new.html)、[HandlingWebController.java](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/java/com/example/cargotracker/handling/interfaces/web/HandlingWebController.java) | interaction-designer / user-representative | 港湾オペレーターの荷役記録と、引取・手動更新は業務主体も意図も違います。単一セレクトに並べると誤記録の危険が高く、システムメタファーも崩れます。 |
| 2 | 一覧テーブルを `table-responsive` 化し、Compact 幅では主要列のみを優先表示する | [list.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/list.html) | interaction-designer | 現状は `UUID`、場所、日時、メモを横並びで出しており、モバイル幅で可読性が急落します。Adaptive Layout の不足です。 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | `予約 ID` と `場所コード` に入力例だけでなく書式説明を追加し、`UN/LOCODE` の期待値を明示する | [new.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/new.html) | user-representative | 現場利用者は形式知識を前提にしない方が安全です。入力支援が弱く、再入力コストが高いです。 |
| 2 | 空状態メッセージを「未登録」と「検索条件に一致なし」で分け、次アクションを出す | [list.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/list.html) | interaction-designer / user-representative | 今は同じメッセージで、データがないのか絞り込みすぎたのか判別しにくいです。 |
| 3 | 登録完了後の遷移先を一覧固定ではなく、該当 `bookingId` で絞った結果表示にする | [HandlingWebController.java](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/java/com/example/cargotracker/handling/interfaces/web/HandlingWebController.java) | user-representative | 登録直後に自分の入力結果を見失いやすく、確認コストが発生します。 |
| 4 | `datetime-local` の時刻基準を補足し、タイムゾーン前提を UI 上に示す | [new.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/new.html) | interaction-designer | 港湾業務では日時の意味が重要です。ローカル時刻のみだと運用解釈のずれが起きやすいです。 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 一覧の `UUID` は全文表示ではなく短縮表示にし、必要時だけ tooltip や詳細表示にする | [list.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/list.html) | user-representative | 一覧確認の主目的は識別より状況把握です。長い ID は視線ノイズになります。 |
| 2 | 種別 badge の色設計をデザイントークン化し、意味の近い操作に同じルールを適用する | [list.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/list.html) | interaction-designer | Bootstrap の色に直接依存しており、意味体系が UI 全体で再利用しにくいです。 |
| 3 | 成功メッセージに登録した種別や予約 ID を含め、完了フィードバックを具体化する | [new.html](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/resources/templates/handling/new.html)、[HandlingWebController.java](/C:/Users/PC202411-1/IdeaProjects/case-study-cargo-tracker/apps/cargo-tracker/src/main/java/com/example/cargotracker/handling/interfaces/web/HandlingWebController.java) | user-representative | 「何が成功したか」が短文では弱く、確認性が低いです。 |

## 矛盾事項

現時点で、デザイナー視点とユーザー視点の重大な矛盾はありません。  
どちらの視点でも「業務別導線の分離不足」と「一覧の Compact 幅対応不足」が最優先です。

## エージェント別フィードバック詳細

### xp-interaction-designer（高 2 / 中 2 / 低 2）

#### 評価サマリー

画面の骨格はシンプルで、最短経路で記録と検索ができます。  
ただし、`US10` のスコープに対して UI の責務が広く、業務境界の表現が弱いです。

#### 良い点

- 登録フォームと一覧で card と spacing が揃っており、視覚的一貫性があります。
- 必須項目に `*` と `invalid-feedback` があり、基本的なアクセシビリティ配慮があります。
- 一覧に検索フォームを同居させているため、記録後の確認動線は短いです。

#### モダンデザイン準拠状況

- Surface 分離は card で成立しています。
- Typography は最低限整理されていますが、状態表現は Bootstrap 既定に依存しています。
- Adaptive Layout は一覧テーブルで不足しています。

#### 改善提案

- 【重要度: 高】`/handling/new` のイベント種別を用途別に分離し、荷役・引取・手動更新を別入口にしてください。
- 【重要度: 高】一覧テーブルをモバイル対応し、列優先順位を設計してください。
- 【重要度: 中】空状態を複数パターンに分け、次アクションを提示してください。
- 【重要度: 中】時刻入力に運用上の前提を補足してください。

#### 懸念事項

- badge の色が業務意味より Bootstrap の配色都合で決まって見えます。
- `UUID` の全面表示で視覚的ノイズが大きいです。

#### スコープ外の発見

- IT4 計画書では US11 以降を含む成功基準にチェックが入っており、実装進捗との管理粒度に違和感があります。

### xp-user-representative（高 1 / 中 3 / 低 1）

#### 評価サマリー

慣れた管理画面としては使えますが、現場担当者にとっては入力前提知識が少し多いです。  
特に「この画面で何を記録してよいか」が UI だけでは十分に伝わりません。

#### 良い点

- 記録して一覧で確認する流れが素直で、受入条件に沿っています。
- 予約 ID で絞り込めるため、対象貨物の確認がしやすいです。
- 成功メッセージが出るため、登録完了は最低限分かります。

#### モダンデザイン準拠状況

- 他の業務アプリに近いフォーム構成で学習コストは低めです。
- ただし、入力支援と空状態の案内はモダンな業務 UI と比べると弱いです。

#### 改善提案

- 【重要度: 高】荷役記録と引取・手動更新を別業務として見せてください。
- 【重要度: 中】登録後に対象 `bookingId` の一覧へ戻し、確認を 1 画面で完了できるようにしてください。
- 【重要度: 中】`UN/LOCODE` や `UUID` の説明を増やしてください。
- 【重要度: 中】検索結果ゼロと未登録状態を分けてください。
- 【重要度: 低】成功メッセージを具体化してください。

#### 懸念事項

- 現場利用者が `MANUAL_UPDATE` を誤って選ぶと、意図しない状態変更につながります。

#### スコープ外の発見

- REST API の 404 は担保されていますが、Web UI 側で「存在しない予約 ID」の原因説明をもう一段丁寧にすると運用問い合わせが減ります。
