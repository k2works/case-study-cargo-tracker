# IT3 ナビ・見積一覧拡張 マルチパースペクティブレビュー

実施日: 2026-06-29
対象範囲: `git diff ad2549ae..HEAD` (4 コミット: ADR-0006 / E2E / ナビ / 見積一覧)
レビュワー: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative (並列)

## 総合評価

IT3 のスコープ (US01 見積一覧導線 + US07/US08a/US27 ナビ + E2E) を最小コミットで達成しており、ADR-0006 起票・進捗反映の追跡可能性は良好。一方で **「ADR-0006 を提案 (PG-03 ページネーション化) しながら同 IT 内に旧パターン `findAllEstimates :: m [Estimate]` + `LIMIT 100` ハードコードを新規追加した自己矛盾」** が最大の構造的問題。加えて **未認証で業務一覧 (荷主/予約) が全公開**、**ボタン文言に開発識別子 `(US08a)` を露出**、**`estimateListPage` / `bookingListPage` の件数表示+警告ロジックがコピー重複**、**新規 `GET /estimates` に hspec-wai 受入テスト皆無** といった、業務リスク・規律・テストピラミッド面の指摘が複数視点から重なった。

## 改善提案 (重要度順)

### 高 (マージ前 or 次反復で必ず対応)

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :-- | :-- | :-- | :-- | :-- |
| H-01 | 未認証ナビから `/shippers` `/bookings` `/voyages` 一覧を除外 (`/estimates/new` `/estimates` `/voyages/search` `/login` のみに縮退) | `Layout.hs:78-86` | user-representative / programmer | 荷主名・運賃・予約状況は未認証露出すべきでない (本番運用受け入れ不可) |
| H-02 | ボタン文言 `経路候補を見る (US08a)` から `(US08a)` を除去。識別子は `data-story` 属性または HTML コメントへ | `BookingShowView.hs:78-83` | user-representative / programmer | 業務ユーザーに開発内部識別子を露出するのは UX 上不適切 |
| H-03 | ADR-0006 を「承認」ステータスに昇格し、新規 `findAllEstimates` に `{-# DEPRECATED "ADR-0006 PG-03 — IT4 で findEstimatesPaged に移行" #-}` を即時付与 | `Estimation/Application/Ports.hs:16-20` / `docs/adr/0006-pagination-strategy.md` | architect | ADR を「提案」のまま旧パターンを増殖させると IT4 Phase 2 の移行コスト (4 ポート × 各 View/Handler/Spec ≒ 12〜20 箇所) を線形に押し上げる |
| H-04 | `estimateListPage` と `bookingListPage` で重複する件数表示+`>= 100` 警告ロジックを `Shared.Web.ListLimitNotice` へ抽出 (`listLimit = 100` も Shared に昇格、SQL 側も同一定数を bind) | `EstimateFormView.hs:163-180` / `BookingListView.hs:24-48` / `PostgresEstimateRepository.hs` の `LIMIT 100` | programmer / architect | DRY 違反 + `100` が View・SQL・コメントの 3 箇所で二重管理。ADR-0006 PG-04 (`defaultPageLimit`) の前駆として SOT 化 |
| H-05 | `GET /estimates` に hspec-wai 受入テスト 3 件追加 (0 件 / N 件 / `listLimit` 境界 99-100) | `test/unit/Estimation/Interfaces/EstimatePageApiSpec.hs` | tester | 新規プロダクション機能にレイヤ 1 のテストが欠落。E2E 偏重のアイスクリームコーン化 |

### 中 (対応推奨)

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :-- | :-- | :-- | :-- | :-- |
| M-01 | nav の `MenuKey` データ化 (`data MenuKey = EstimateNew \| EstimateList \| ...` + `visibleFor :: Role -> [MenuKey]`)、項目重複を排除 | `Layout.hs:78-106` | programmer | `/voyages/search` 等が 3 ロールに重複定義。次の追加で N×M 化する兆候 |
| M-02 | Booking → Routing/Estimation の URL 文字列連結 (`"/bookings/" <> bid <> "/routes"`) を `Shared.Web.Routes` の URL ヘルパに集約 | `BookingShowView.hs` / 他 BC View | architect | 文字列で他 BC の URL 規約を知っている結合度を低減 |
| M-03 | `findAllEst` と `findEst` の SELECT + 行→`Estimate` 構築を `rowToEstimate` に private 抽出。状態 parse は `parseEstimateStatus :: Text -> EstimateStatus` 一元化 | `PostgresEstimateRepository.hs:113-176` | programmer | コードクローン。状態変換が二重定義 |
| M-04 | テスト fake `findAllEstimates = readIORef ref` が cons 順序のままで本番の `ORDER BY id DESC` と乖離 → `reverse <$> readIORef ref` または順序検証テスト追加 | `CreateEstimateCommandSpec.hs` / `EstimatePageApiSpec.hs` | tester | fake/本番乖離は順序依存バグの温床 |
| M-05 | E2E voyageNumber `Date.now().toString().slice(-6)` を UUID/worker id ベースに変更 | `it3-stories.spec.ts:39` | tester | 並列実行時の衝突リスク |
| M-06 | HS コード 5 桁不正の E2E は `el.removeAttribute('pattern')` で no-op の可能性 → POST 直叩き + 400 期待の統合テストに分離 | `it3-stories.spec.ts:126-142` | tester | クライアントバリデーション迂回を装ったが実際はサーバ検証を踏んでいない可能性 |
| M-07 | 見積一覧の列構成 (見積 ID 切り詰め / 荷主 ID 生表示 / 重量 `0.0`) を業務意味あるラベルに置換 (荷主名・出発/到着・登録日・「未入力」表示) | `EstimateFormView.hs:202` | user-representative | 月末の見積見直し時に検索照合不能 |
| M-08 | ホーム画面のメニュー 2 列構成を「新規登録する」「探す・確認する」業務動詞見出しに集約 | `HomeView.hs` | user-representative / technical-writer | 初見ユーザーの動線整理 |
| M-09 | 見積詳細 → 一覧へ戻るパンくず or ボタンを追加 | `EstimateFormView.hs:estimateShowPage` | user-representative | 月末の往復作業で頻繁に必要 |
| M-10 | `docs/design/ui_design.md` に `/estimates` と新ナビ動線を反映、`docs/release/v0.1.0-alpha.md` の Added セクションに見積一覧を追記 | docs | technical-writer | Single Source of Truth が割れている |
| M-11 | arch-check / HLint 拡張で「`{-# DEPRECATED #-}` 無しの `findAll[A-Z].* :: m [.*]` 新規追加を禁止」を CI で機械検出 | `apps/cargo-tracker/scripts/arch-check.sh` | architect | [[feedback_archunit-and-fulltest]] — レビュー頼みで規律が失われる |

### 低 (改善の余地あり)

| # | 提案 | 箇所 | 指摘元 |
| :-- | :-- | :-- | :-- |
| L-01 | 見積 ID 切り詰め表示に `title_` 属性で full UUID を載せる | `EstimateFormView.hs` | programmer |
| L-02 | `listLimit` / `LIMIT 100` の haddock コメントから ADR-0006 への明示リンクを追記 | `findAllEst` / `estimateListPage` | programmer / technical-writer |
| L-03 | `Estimate (..)` open import を qualified 化し将来の field 衝突を予防 | `EstimateFormView.hs` | programmer |
| L-04 | LayoutSpec の完全一致配列アサート → 「特定 href 存在」「順序関係」など意図ベースに分解 | `LayoutSpec.hs` | tester |
| L-05 | `docs/development/iteration_report-3.md` を `creating-iteration-report` で生成 | docs | technical-writer |
| L-06 | US08a の `a` サフィックス由来を用語集または iteration_plan-3.md に 1 行注記 | docs | technical-writer |
| L-07 | E2E spec it3-stories.spec.ts の網羅範囲を README / testing.md に 1 行追記 | docs | technical-writer |

## 矛盾事項

なし (5 視点とも問題の方向性が一致。programmer と architect の DRY/SOT 指摘、tester と user-representative の業務要件指摘が相互に補強関係)。

## 主な対応方針

| ID | 判断 | 対応タイミング |
| :-- | :-- | :-- |
| H-01 / H-02 | 修正する | 本反復で即時対応 |
| H-03 | 修正する (ADR ステータス変更 + DEPRECATED プラグマ) | 本反復で即時対応 |
| H-04 / H-05 | 保留 → IT4 Phase 1 (ADR-0006 実装) と一括 | IT4 |
| M-01〜M-11 | iteration_plan-4 (or backlog) に登録 | IT4 |
| L-01〜L-07 | 任意。IT4 で余裕があれば消化 | IT4 ストレッチ |

## エージェント別フィードバック (詳細サマリ)

- **xp-programmer**: DRY 違反 (件数警告 + SELECT クローン)、Lucid View に `listLimit` 定数を置く責務、nav リスト肥大化を指摘。haddock の SOT 化を推奨。
- **xp-tester**: アイスクリームコーン (E2E 偏重)、`/estimates` の hspec-wai 欠落、`listLimit` 境界値テスト不在、fake/本番の順序乖離、E2E HS コードテストの no-op 疑い。
- **xp-architect**: ADR-0006 を提案で起票しながら旧パターンを増殖させた自己矛盾を NG 評価。`{-# DEPRECATED #-}` 即時付与 + ADR 昇格 + arch-check ルール追加を強く推奨。URL 文字列結合の他 BC 知識露出も中。
- **xp-technical-writer**: UI 設計書とリリースノートの未追従、handlerList の haddock 薄さ、ADR への参照リンク不足。「一覧・検索メニュー」見出しの命名提案。
- **xp-user-representative**: 未認証情報漏洩 (NG)、開発識別子 `(US08a)` 露出 (NG)、見積詳細から一覧への戻り導線欠落 (NG)、ナビ 7 項目の折り返しリスク、列構成の業務理解性。

## 関連
- [ADR-0006 一覧 Repository ページネーション戦略](../adr/0006-pagination-strategy.md)
- [IT3 計画](../development/iteration_plan-3.md)
- [v0.1.0-alpha リリースノート](../release/v0.1.0-alpha.md)
