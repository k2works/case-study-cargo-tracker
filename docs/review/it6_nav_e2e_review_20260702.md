# IT6 ナビゲーション追加 + E2E マルチパースペクティブレビュー (2026-07-02)

## レビュー対象

- コミット `01659f44` feat(nav): IT6 の送料計算 / 通知一覧をホーム画面とナビメニューに追加
- コミット `c4aeb636` test(e2e): US21 送料計算と US26 通知一覧の Playwright E2E を追加
- 変更規模: 5 ファイル / +80 行

## 総合評価

Domain 責務と概ね整合したロール割当と、Unit + E2E の二重回帰網を持つ低リスクな UI 導線追加。ただし **未認証ホームへの Pricing/Notification 露出が H-01 方針と不整合** であり、H-01 SSoT を IT6 で強制化した直後の変更としては最優先で是正すべき。中期的にはナビの Shared 集中管理の pluggable 化と、送料/運賃の用語統一が課題。

## 改善提案（重要度順）

### 高（マージ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 未認証ホーム (`menuItemsForRole Nothing` 相当の HomeView) から送料計算・通知一覧のカードを非表示にする（またはロール別に切替）| `HomeView.hs:49-50` | xp-user-representative | H-01「業務一覧は未認証で非露出」の設計方針に反する。送料計算は料金体系という営業機密に近く、通知は業務履歴。IT6 で H-01 を warning→error に強制化した直後の逆行になる |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 2 | E2E に載せた計算ロジック検証を Haskell 側 unit (`CalculateShippingCostCommandSpec`) にリバランスし、E2E は導線 1 本に絞る | `pricing-calculation.spec.ts` | xp-tester | テストピラミッド逆転。割引率・距離・重量は純粋関数寄りで unit の方が速く安定 |
| 3 | Sales の見積作成起点で使う用語との一致（「送料計算」→「運賃計算」）| `HomeView.hs`, `Layout.hs` | xp-technical-writer | ユビキタス言語の揺れ。US21 の estimation 文脈との整合 |
| 4 | Handler の「通知一覧」カード文言を「送信済通知の確認」に明示化 | `Layout.hs:113` | xp-user-representative | Handler は発火者。現場が「自分宛通知」と誤解するリスク |
| 5 | Handler / Shipper / Consignee / Accountant / Tracker の `menuItemsForRole` 期待配列テスト追加 | `LayoutSpec.hs` | xp-tester | 回帰穴。Sales/MasterAdmin のみ全 URL 検証で他ロールは非空チェックのみ |
| 6 | `MenuItem` レコードに (path, icon, label, us, roles) を集約し HomeView と Layout で共有 | `HomeView.hs`, `Layout.hs` | xp-programmer | URL/ラベルが 3 ファイルに散在。DRY 違反 |
| 7 | `/notifications` E2E に seed 前提を spec 冒頭コメントで明示、`data-testid=notification-empty` 等を追加 | `notifications.spec.ts` | xp-tester / xp-technical-writer | `h1, h2` 可視のみは実質スモーク。seed 依存が spec から読めない |
| 8 | ADR に「Shared/Web はナビの集約点である」と決定を明示 | `docs/adr/` | xp-architect | 現行の集中管理は 5 BC まで妥当だが SSoT の位置づけを ADR で固定化 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 9 | `/pricing/calculate` `/notifications` のパスリテラルを `Routes` モジュールに集約 | 3 ファイル | xp-programmer | 経路変更時の SSoT |
| 10 | E2E で金額 (割引後合計) 自体のアサートを追加 | `pricing-calculation.spec.ts` | xp-programmer | 回帰検出力向上 |
| 11 | `ui_design.md`, `iteration_report-6.md`, `journal/20260702.md`, `CHANGELOG.md` にトップページ露出変更を反映 | docs | xp-technical-writer | 利用者影響のある UI 変更 |
| 12 | US26 カード説明を「引取確認・誤送通知など送信済通知の履歴」等に拡張 | `HomeView.hs:50` | xp-technical-writer | 例示 1 件だと機能範囲が読めない |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | user-rep: Handler の通知一覧は再考 | programmer: Handler = 発火者は妥当 | Handler ロールに通知一覧を出す意義 | 保持しつつ文言を「送信済通知の確認」に明示化（提案 #4）|

## エージェント別サマリー

- xp-programmer: 高 0 / 中 1 / 低 2 — 「シンプルで低リスク、DRY だけ気になる」
- xp-tester: 高 0 / 中 4 / 低 0 — 「テストピラミッド逆転と境界値の欠落」
- xp-architect: 高 0 / 中 2 / 低 0 — 「Shared 集中管理は 5 BC まで妥当、ADR で SSoT 明示を」
- xp-technical-writer: 高 0 / 中 2 / 低 3 — 「送料/運賃の用語統一とドキュメント同期」
- xp-user-representative: **高 1** / 中 1 / 低 1 — 「未認証露出が H-01 に不整合、Handler 文言の明確化」

## 対応方針

- **提案 #1 (高)**: IT6 内で即時対応推奨。`HomeView.homePage` は現状 `pageLayout` (Nothing) 固定なので、`pageLayoutFor mRole` 化 + `menuItemsForRole` に沿ったカード絞り込みが必要。ただし該当ハンドラの認証統合 (T5-15/T6-09 系) と連動するため、単独では未認証ホームから対象カード 2 枚を暫定的に削除する方が安全。
- **提案 #2-8 (中)**: IT7 T6-02 (developing-review 起因タスク) にまとめて計上。
- **提案 #9-12 (低)**: IT7 のリファクタリングタスクとして残置。

## 関連コミット

- `01659f44`, `c4aeb636`
