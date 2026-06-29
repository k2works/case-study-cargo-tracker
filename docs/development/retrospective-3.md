# IT3 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| --- | --- |
| イテレーション | IT3 |
| 期間 | 2026-06-29 (Ralph Loop 51 反復 + クロージング + ポストレビュー対応) |
| 計画 SP | 29 (本体 25 + ストレッチ 4) |
| 実績 SP | 22 完了 / 7 IT4 繰越 (達成率 76%) |
| テスト | 300 examples / 0 failures / 10 pending |
| コミット数 | 48 (IT2 末以降) |
| 新規 ADR | 3 件 (0004 ShipperRef / 0005 BC エラー分離 / 0006 ページネーション) |

実績内訳:

- **完了**: H-01〜H-03/H-07/H-09 (5)、U-01〜U-03/U-05〜U-07/U-09〜U-11/U-13〜U-14 (11)、US07 (3)、US08a (5)、US27 (3)、L-03/L-05/L-07〜L-12/M-02/M-03/M-07/M-08/M-09 + IT3 post-nav H-01〜H-03 (追加)
- **IT4 繰越**: U-04 arch Phase 2 / U-08 Playwright E2E (大部分) / U-12 testcontainers / Phase 3 T-01〜T-03

---

## Keep (継続すべき良かったこと)

### 技術面

- **ADR 駆動のリファクタリングと採用昇格パターン**: ADR-0004 (ShipperRef) → 即時実装、ADR-0005 (BC エラー) → 段階移行、ADR-0006 (ページネーション) → 提案 → ポストレビューで即座に「採用」昇格 + `{-# DEPRECATED #-}` 付与。意思決定→規律強制の往復が機能した
- **`{-# DEPRECATED #-}` プラグマでの規律機械化**: 4 つの `findAll*` Port に同時付与し、新規 callsite の追加を GHC 警告で検知できる状態に。コードレビュー頼みの規律から脱却 ([[feedback_archunit-and-fulltest]] の教訓を実装)
- **ShipperRef VO 導入の小さな手戻り**: U-05 で Booking → Shipper.Domain 直接参照 7 件を ALLOWLIST 0 件に。型システム + Cross-BC ACL Lite (Text 化) の組み合わせは 21 反復目でも安定して機能
- **RouteFinder の性能余裕**: 1000 航海で 12.6ms (目標 500ms の 4%)。純粋関数 + DFS + 早期枝刈りという素直な設計で十分な性能が出た
- **hedgehog プロパティ拡張のテンポ**: U-13 で HsCode / TemperatureRequirement の境界値プロパティを 5 件即追加。Domain 純粋部分は hedgehog がコスト最小

### プロセス面

- **マルチパースペクティブレビューの 2 段運用が定着**: Ralph Loop 中盤の self-review (IT2 マルチパースペクティブ) + IT3 完了直後の post-nav レビューで、それぞれ「次反復に持ち越す指摘」「同反復で即時修正する指摘」を分離できた ([[feedback_review-two-stage]] 通り)
- **ポストレビュー高優先指摘の即時対応**: H-01 情報漏洩 / H-02 識別子露出 / H-03 ADR 自己矛盾の 3 件を同セッション内で commit `f41fcfc3`。レビュー → 対応の遅延ゼロ
- **Ralph Loop end-of-life 運用**: 第 42 反復以降「残作業は IT4 繰越のみ」を no-op 反復で明示し、`/ralph-loop:cancel-ralph` で計画的終了。Stop hook の無限ループを情報ロスなく抜けられた ([[feedback_ralph-loop-end-of-life]] 反復改善)
- **iteration_plan-3.md の状態列を実績反映 → コミット**: チェックボックスが「全て [ ]」のまま腐敗するのを防ぎ、retrospective 着手時に何が繰越かが一目で分かる状態を維持

### ストーリー実装

- **US07/US08a/US27 の Domain → Application → HTTP → UI の縦割り完走**: 3 ストーリーで同じ TDD パターン (Red→Green→Refactor + hspec-wai 受入) を回し、IT2 の規律を IT3 でも再現できた
- **見積一覧画面の追加 (ポスト IT3 対応)**: 当初計画外だが H-04 (DRY) / H-05 (テストピラミッド) の教訓を残せた。次の一覧系拡張のテンプレートにできる

---

## Problem (問題・改善すべき点)

### スコープ管理

- **IT4 繰越 7 SP の偏り**: 繰越の大半が「環境依存 / 大物リファクタ」(U-04 AST バイナリ 8h、U-08 Playwright 6h、Phase 3 T-01〜T-03 9h)。Ralph Loop で AI 単独完結できないタスクが終盤に集中し、第 42 反復以降 no-op が 9 回続いた。最初から「Ralph 適性」でタスク分類しておくべき
- **ADR-0006 を「提案」のまま新規 callsite を増やす自己矛盾**: post-nav レビュー H-03 で指摘されるまで気付かなかった。ADR 起票時のチェックリストに「同 IT 内で旧パターン新規追加を禁止する DEPRECATED プラグマを必ず付与」を組み込むべき
- **見積一覧追加が計画外**: ユーザー要望「他の一覧と合わせて直す」を受けて追加実装したが、計画書には反映していない。差込ストーリーの扱いルール (計画書追記 vs ホットフィックス) を IT4 で定義

### コード品質 (post-nav レビューで顕在化)

- **`estimateListPage` と `bookingListPage` のコピー重複** (H-04): 件数表示 + `>= 100` 警告が二重実装。`listLimit` 定数も Booking 側のみ抽出済で見積側はハードコード退行。SOT (`Shared.Web.ListLimitNotice`) を IT4 で抽出
- **`findAllEst` と `findEst` の SELECT クローン** (M-03): 9 カラム + JOIN shipper + 行→Estimate 構築が二重。`rowToEstimate` private 抽出が必要
- **Booking → Routing/Estimation の URL 文字列連結** (M-02): `"/bookings/" <> bid <> "/routes"` で他 BC の URL 規約を Booking BC が知っている結合度。`Shared.Web.Routes` ヘルパに集約

### テスト品質

- **新規 `GET /estimates` に hspec-wai 受入テストなし** (H-05): E2E でカバーしているがレイヤ 1 が空。アイスクリームコーン化の兆候を即修正できなかった
- **`listLimit` 境界値テスト (99/100/101) 不在**: ハードコード値の回帰検知ができない
- **テスト fake が本番と挙動乖離** (M-04): `findAllEstimates = readIORef ref` は cons 順 (新→古の逆) だが Postgres は `ORDER BY id DESC`。順序検証テストを追加すべきだった
- **E2E voyageNumber が `Date.now().slice(-6)`** (M-05): 並列実行で衝突可能性。UUID/worker id ベースへ
- **E2E HS コード 5 桁不正テストが no-op の可能性** (M-06): `removeAttribute('pattern')` を呼んだが pattern 属性が存在しないため何も無効化していない。POST 直叩きの統合テストに分離

### UX

- **未認証で業務一覧 (荷主/予約/航海) 露出** (H-01): post-nav レビューで指摘されるまで気付かず。「未認証で何を見せるか」のチェックリストが UI 設計プロセスに欠けている
- **ボタン文言に開発識別子 `(US08a)` 露出** (H-02): プロダクション UI への識別子漏洩。`developing-review` の user-representative 観点に「開発識別子の UI 露出禁止」を明文化推奨
- **見積一覧の列構成が業務理解性に欠ける** (M-07): 見積 ID 8 桁切り詰め / 荷主 ID 生表示 / 重量 `0.0` がそのまま。荷主名・登録日・「未入力」表示への変更が必要

### ドキュメント同期

- **`docs/design/ui_design.md` 未更新**: `/estimates` 一覧と新ナビ動線が画面一覧に反映されていない
- **`docs/release/v0.1.0-alpha.md` 未追記**: 見積一覧画面、ホームナビ拡張、ADR-0006 採用が Added セクションに反映されていない
- **`docs/development/iteration_report-3.md` 未作成**: フォーマル報告書がまだ

---

## Try (試したいこと・改善アクション)

### IT4 計画にコミット (高)

| ID | アクション | 期待効果 | 担当 | 期限 |
| :-- | :-- | :-- | :-- | :-- |
| T3-01 | ADR-0006 PG-01〜PG-05 を Shared.Application.Pagination として実装 (`PageReq` / `Page` 型 + `defaultPageLimit`) | `findAll*` callsite を順次 `findCargosPaged` 等へ移行可能に | - | IT4 Week 1 |
| T3-02 | 4 つの `findAll*` を順次廃止 (Booking → Routing → Shipper → Estimation の順) | DEPRECATED 警告 0 化 + Phase 2 完了 | - | IT4 Week 2 |
| T3-03 | `Shared.Web.ListLimitNotice` 抽出 + `Shared.Web.Routes` URL ヘルパ | H-04 / M-02 解消 | - | IT4 Week 1 |
| T3-04 | `GET /estimates` の hspec-wai 受入テスト 3 件 + listLimit 境界値テスト | H-05 / 境界値テスト不在解消 | - | IT4 Week 1 |
| T3-05 | arch-check Phase 2 (haskell-src-exts AST バイナリ + Rule 6) | U-04 繰越解消、構造規約の機械検証強化 | - | IT4 Week 2 |
| T3-06 | Playwright E2E 拡張 (US01/US06/US25 ハッピーパス + US07/US08a/US27 異常系) | U-08 繰越解消 | - | IT4 Week 2 |
| T3-07 | arch-check / HLint 拡張で「`{-# DEPRECATED #-}` 無しの `findAll*` 新規追加禁止」を CI に追加 | M-11 規約の機械強制 | - | IT4 Week 1 |

### プロセス改善 (中)

| ID | アクション | 期待効果 |
| :-- | :-- | :-- |
| T3-08 | ADR 起票時チェックリストに「同 IT 内で旧パターン新規追加禁止 + DEPRECATED 付与」を追加 | ADR 自己矛盾の予防 |
| T3-09 | UI 設計プロセスに「未認証で見せる/見せない情報のロール表」を必須化 | H-01 型情報漏洩の予防 |
| T3-10 | `developing-review` の user-representative ペルソナに「開発識別子の UI 露出」観点を明文化 | H-02 型問題の自動検知 |
| T3-11 | Ralph Loop 計画時にタスクを「AI 完結可」「環境依存」「人手作業」で分類し、`max_iterations` 算定根拠を残す | 終盤の no-op 反復削減 |

### コード品質 (低 / IT4 ストレッチ)

| ID | アクション |
| :-- | :-- |
| T3-12 | `findAllEst` / `findEst` の `rowToEstimate` 抽出 (M-03) |
| T3-13 | `Estimate (..)` open import を qualified 化 ([[feedback_haskell-field-name-collision]] 規約適用) |
| T3-14 | 見積一覧の列構成見直し (荷主名 JOIN / 登録日 / 重量 "未入力" 表示) (M-07) |
| T3-15 | LayoutSpec を「完全一致配列」→「特定 href 存在 + 順序関係」に分解 (L-04) |

---

## メトリクス

| 指標 | IT1 | IT2 | IT3 | 推移 |
| :-- | --: | --: | --: | :-- |
| 計画 SP | 20 | 10 | 29 | +19 |
| 実績 SP | 20 | 18 | 22 | +4 |
| 達成率 | 100% | 180% | 76% | スコープ拡大に対する完遂率は低下 |
| テスト数 | 約 90 | 207 | 300 | +93 |
| コミット数 | 11 | 24 | 48 | 倍々ペース |
| 新規 ADR | 1 | 1 | 3 | +2 |
| HPC カバレッジ (全体) | 約 50% | 62% | 70%+ | ゲート達成 |
| arch-check Rule 4 ALLOWLIST | 6 | 6 | 0 | 完全解消 |

ベロシティ (実績 SP / 期間):

- IT1: 20 SP / 1 日 Ralph Loop
- IT2: 18 SP / 1 日 Ralph Loop
- IT3: 22 SP / 1 日 Ralph Loop (51 反復、繰越 7 SP 込み)

3 イテレーション通算で「Ralph Loop 1 日 = 約 20 SP」が安定値。IT4 の計画は 20 SP を基準にすべき。

---

## 関連ドキュメント

- [IT3 計画](iteration_plan-3.md)
- [IT3 マルチパースペクティブレビュー (post-nav)](../review/it3_post_nav_review_20260629.md)
- [IT2 マルチパースペクティブレビュー](../review/it2_code_review_20260627.md)
- [ADR-0006 一覧 Repository ページネーション戦略](../adr/0006-pagination-strategy.md)
- [リリース計画](release_plan.md)
