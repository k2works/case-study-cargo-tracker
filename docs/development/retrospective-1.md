# IT1 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| --- | --- |
| イテレーション | IT1 |
| 期間 | 2026-06-26 (Ralph Loop 28 イテレーション + 後続改善 10 コミット) |
| 計画 SP | 13 |
| 実績 SP | 20 (達成率 154%) |
| テスト | 117 examples / 0 failures / 10 pending |
| コミット数 | 76 (main 分岐後) |

---

## Keep (継続すべき良かったこと)

### 技術面

- **ヘキサゴナル + DDD 4 BC × 4 層構造の一貫性**: arch-check Phase 1 で構造規約を機械検証し、ADR 0001/0002 で意思決定経緯を残せた
- **レコード of 関数によるポート抽象**: 型クラスより軽量で Composition Root が読みやすく、IORef フェイクで単体テスト容易
- **スマートコンストラクタ + DomainError sum type**: 無効値の集約侵入を型で防げた (`mkVoyage` 区間連続性検証、`mkCorporateNumber` 13 桁検証など)
- **PRG + htmx の段階的実装**: Phase A (PRG/詳細画面) → Phase B (htmx 統合) を分離してコミットしたことでレビュー単位が小さく保てた
- **JSON API と SSR の経路分離 (`/api/*` vs `/*`)**: Main.hs の pathInfo 分岐が宣言的で IT2 移行点が明示的
- **ホットリロード (ghcid + `dev:watch`)**: 開発体験を底上げした XP 観点の良い投資
- **ログイン画面のシードユーザー一覧 + デフォルト入力**: 受入れデモが格段に楽になった

### プロセス面

- **Ralph Loop による圧縮実装**: 計画 14 日 → 1 日完結。学習コスト係数 1.20 を上回るベロシティを実証
- **マルチパースペクティブレビュー (XP 5 エージェント並列)**: 計画・実装・テスト・ドキュメント・UX を網羅的に検証し高 10/中 13/低 8 件の指摘を得た
- **Conventional Commits + pre-commit hook の遵守**: 履歴の追跡性が高い
- **memory の活用**: `feedback_review-two-stage.md`, `feedback_archunit-and-fulltest.md` 等の過去知見が IT1 の判断を加速した

---

## Problem (問題・改善すべき点)

### テスト品質

- **PRG (303) のテストが皆無**: `grep '303'` が unit/E2E ともに 0 ヒット。リロード二重 POST デグレを検知不能
- **htmx 部分 HTML エンドポイントのテスト不在**: `ShipperSearchApi` / `VoyageMovementRowApi` の保証なし
- **hedgehog プロパティテストが事実上ゼロ**: 依存だけ追加され `forAll` 未使用。境界値 (UnLocode 5/6/7 文字) など典型不変条件が未検証
- **Postgres 統合テストが pending スキップ**: CI で `DATABASE_URL` を設定する運用がなく「常に緑だが実行されていない」リスク (memory `feedback_archunit-and-fulltest.md` と同型)
- **HPC カバレッジ実測なし**: 計画では「Domain ≥95%、全体 ≥70%」だが未測定

### コード品質

- **`PostgresBookingRepository.hs:87` の `error` 呼び出し**: shipper 未解決時に 500 化。TOCTOU 隙間で発火するリスク
- **JWT exp 固定 `9999999999`**: 本番デプロイで永続有効トークン発行リスク。`Main.hs` 起動時の fail-fast なし
- **`parseRole` / `showRole` の重複**: `JwtIssuer.hs` と `PostgresUserRepository.hs` で同じ 8 ロール変換が二重定義
- **`saveCargo` 非トランザクション**: SELECT shipper.id → INSERT cargo の 2 クエリ間で外部キー違反の隙間

### 構造的負債

- **`Booking.Domain.Cargo` が `Shipper.Domain.ShipperId` を直接 import**: ACL 規約と乖離。arch-check Phase 1 が BC 横断を検出しない盲点 (Scala 版 IT8 と同型の構造リスク)
- **stub fallback の本番混入リスク**: `DATABASE_URL`/`JWT_SECRET` 未設定で 500 スタブを返す挙動が本番でも同バイナリで起動
- **IT1 placeholder 群が ADR 化されていない**: bcrypt cost=4 / JWT exp / Shipper.name=email / stub fallback の「意図的な負債」一覧と利息明示なし

### UX

- **BookingId / ShipperId 手入力**: 業務上 ID 手打ちは誤予約温床。検索 UI 必須化 + 自動採番への移行が必要
- **`?error=` クエリでのエラー表示**: 入力値が消える、エラー位置が分からない。flash + 自己ループ (入力値保持) への移行が必要
- **Shipper.name フィールド未実装**: DB スキーマには `name NOT NULL` がある placeholder 状態。業務上「機能未完」

### ドキュメント

- **`Shipper.name = email` placeholder が Haddock/domain-model.md に未記載**: DB を覗かないと気付かない罠
- **`iteration_report-1.md` に成功基準 vs 実績表がない**: HPC %、arch-check ログ、E2E spec 数のエビデンス不足
- **API JSON/FormUrlEncoded スキーマが Haddock に未記載**: ソースを開かないと入出力形が分からない

### 計画と実態の齟齬

- **計画期間 (2026-07-06〜07-19) と実装日 (2026-06-26) のズレ**: Ralph Loop 前倒し実行による。報告書に注記推奨
- **画面遷移・インタラクション設計が後追い**: 初期実装が結果ページ返却で、計画書 (PRG + 詳細画面 + htmx) との乖離を Phase A+B で事後追従

---

## Try (次のアクション)

### IT2 必達 (高優先度レビュー指摘の解消)

| # | アクション | 担当 | 期限 | 期待効果 |
| --- | --- | --- | --- | --- |
| T-01 | `PostgresBookingRepository` の `error` を Either ベースに置換 | IT2 担当 | IT2 Week 1 | 500 化防止 |
| T-02 | JWT exp を実時刻ベース化 + production fail-fast | IT2 担当 | IT2 Week 1 | 本番混入リスク排除 |
| T-03 | PRG (303) hspec-wai テスト追加 (`matchStatus = 303` + Location 検証) | IT2 担当 | IT2 Week 1 | リロード二重 POST デグレ検知 |
| T-04 | htmx 部分 HTML エンドポイントのテスト追加 | IT2 担当 | IT2 Week 1 | 部分 HTML 出力保証 |
| T-05 | hedgehog プロパティテスト最低 3 件 (UnLocode / Voyage / Cargo) | IT2 担当 | IT2 Week 1 | 境界値・不変条件の機械検証 |
| T-06 | arch-check Phase 1 に Rule 4 (BC Domain 直接 import 禁止) 追加 | IT2 担当 | IT2 Week 1 | BC 横断盲点の解消 |
| T-07 | BookingId / ShipperId の手入力廃止 (検索 UI 必須 + 自動採番) | IT2 担当 | IT2 Week 2 | 業務適合性向上 |
| T-08 | バリデーションエラーを flash + 自己ループ (入力値保持) に移行 | IT2 担当 | IT2 Week 2 | 入力値消失の解消 |
| T-09 | `Shipper.name` フィールド追加 + Haddock/domain-model.md 整合 | IT2 担当 | IT2 Week 1 | placeholder 解消 |
| T-10 | `iteration_report-1.md` に成功基準 vs 実績表追加 (HPC %、arch-check ログ、E2E spec 数) | 即時対応 | IT2 着手前 | 完了エビデンス確立 |

### IT2 推奨 (中優先度)

| # | アクション | 期限 |
| --- | --- | --- |
| T-11 | IT1 placeholder 棚卸し ADR 起票 (`creating-adr`) | IT2 着手前 |
| T-12 | ロール別アクセス制御 (US04 営業 / US24 運航管理者) 実装 | IT2 内 |
| T-13 | 認証必須フロー統合テスト (`/bookings/new` 未ログイン → 303 / login) | IT2 内 |
| T-14 | API JSON/FormUrlEncoded スキーマを Haddock の `@` ブロックで例示 | IT2 内 |
| T-15 | a11y 属性 (pattern / aria-describedby) 付与 | IT2 内 |
| T-16 | `parseRole`/`showRole` を `User.hs` に統合 (DRY) | IT2 内 |
| T-17 | stub fallback を production profile で fail-fast | IT2 内 |
| T-18 | `saveCargo` を `withTransaction` でトランザクション化 | IT2 内 |

### プロセス改善

- **`DATABASE_URL` を CI で設定する運用を確立**: pending スキップを実行に変える (memory `feedback_archunit-and-fulltest.md` の規律を CI に組み込む)
- **イテレーション開始時に validating-iteration-plan を実行**: 計画書と設計ドキュメントの整合性を機械的に確認
- **画面遷移・インタラクション設計を実装前に再確認**: 計画書の PlantUML を開発タスクに展開する手順を IT2 で確立
- **Ralph Loop の終了判定基準を明確化**: AI 単独完結タスク消化後の Stop hook 継続を最小応答で抜ける (memory `feedback_ralph-loop-end-of-life.md` の運用)

---

## ベロシティ実績

| イテレーション | 計画 SP | 実績 SP | 達成率 | 備考 |
| --- | --- | --- | --- | --- |
| IT1 | 13 | 20 | 154% | Ralph Loop 圧縮実行、後続改善含む |

**学習コスト係数 1.20 の妥当性検証**: IT1 では 1.20 を上回るベロシティを実証したが、Ralph Loop による圧縮実行と Scala 版資産流用の影響が大きい。IT2 で通常運用ベロシティを再測定し、3 イテレーション (IT1-IT3) 平均で係数を再評価する。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| --- | --- | --- |
| 2026-06-26 | 初版作成 | Claude |
