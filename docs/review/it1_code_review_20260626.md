# IT1 コードレビュー結果 (XP 5 エージェント並列)

## レビュー対象

- 範囲: IT1 全体 (haskell/take-1 ブランチ、main 分岐後 76 コミット相当)
- 主要構成: 4 BC × 4 層 + Lucid SSR + htmx + Playwright E2E
- 関連ドキュメント: `iteration_plan-1.md` / `iteration_report-1.md` / `release_plan.md` / `docs/design/`

## 総合評価

IT1 はヘキサゴナル + DDD の境界が一貫して守られ、PRG / htmx 統合・Lucid SSR・Postgres 永続化・arch-check Phase 1 が稼働する**構造的に健全な最小完成形**として成立している。一方で、**(1) PRG/htmx/property のテストカバレッジ欠落、(2) arch-check Phase 1 の BC 横断盲点、(3) ID 手入力 UX の業務適合性、(4) placeholder 群の文書化不足**の 4 領域が IT2 着手前に対処すべき焦点。

## 改善提案 (重要度順)

### 高 (IT2 着手前に対応)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H-01 | `error` 呼び出しを Either ベースに置換 | `PostgresBookingRepository.hs:87` | programmer | 500 化を防ぐ。TOCTOU 隙間で発火する未チェック例外を排除 |
| H-02 | JWT exp 固定 `9999999999` の本番混入防止 | `LoginApi.hs:95` + `Main.hs` 起動時 | programmer | 本番デプロイで永続有効トークンが発行されるリスク。`ENVIRONMENT=production` で fail-fast |
| H-03 | PRG (303) hspec-wai テスト追加 | 全 Form POST | tester | リロード二重 POST デグレを検知できない。`matchStatus = 303` + `Location` ヘッダ検証 |
| H-04 | htmx 部分 HTML エンドポイントのテスト不在 | `ShipperSearchApi` / `VoyageMovementRowApi` | tester | `HX-Request` ヘッダ有無で出力切替がある場合の保証なし |
| H-05 | hedgehog プロパティテストが事実上ゼロ | UnLocode / Voyage / Cargo | tester | hedgehog 依存だけ追加され `forAll` 未使用。境界値 (5/6/7 文字、英数字混在) を最低各 1 件 |
| H-06 | arch-check Phase 1 に Rule 4 追加 (BC Domain 直接 import 禁止) | `scripts/arch-check.sh` | architect | `Booking.Domain.Cargo` が `Shipper.Domain.ShipperId` を直接 import している。ACL 規約と乖離 |
| H-07 | BookingId / ShipperId 手入力廃止 (検索 UI 必須化 + 自動採番) | `BookingFormView` / `ShipperFormView` | user-rep | 業務上 ID 手打ちは誤予約温床。営業フローで耐えられない |
| H-08 | バリデーションエラー: `?error=` クエリを flash + 自己ループに移行 | 全 Form ハンドラ | user-rep | 入力値が消える / エラー位置が分からない。月末予約ラッシュで耐えられない |
| H-09 | `Shipper.name = email` placeholder の Haddock + domain-model.md 明示 | `Shipper.hs` 冒頭 + `domain-model.md` Shipper 表 | writer | DB を覗かないと気付かない構造。IT2 担当が引っかかる罠 |
| H-10 | `iteration_report-1.md` に成功基準 vs 実績表 (HPC %、arch-check ログ、E2E spec 数) を追加 | `iteration_report-1.md` | writer | 完了報告書は「成功基準ごとの達成エビデンス」が本質 |

### 中 (IT2 内で対応)

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| M-01 | Shipper Domain に `name` フィールド追加し DB スキーマと整合 | `Shipper.hs` + migration | programmer / user-rep / writer |
| M-02 | `parseRole` / `showRole` の重複統一 | `JwtIssuer.hs` + `PostgresUserRepository.hs` → `User.hs` | programmer |
| M-03 | `ConcurrentModification` を重複検出と楽観ロック衝突に分離 (`DuplicateEmail` 新設) | `DomainError` | programmer |
| M-04 | 認証必須フローの統合テスト (`/bookings/new` 未ログイン → 401 or /login へ 303) | hspec-wai + E2E | tester |
| M-05 | IORef フェイクの並行性 (`before` フックで fresh 化) | 全 unit spec | tester |
| M-06 | 境界値の同値クラステーブル化 | `ShipperApi` 422 ほか | tester |
| M-07 | `rootApp` パターンマッチ順序の脆さ解消 (Servant 化 or ルーティングテーブル化 ADR) | `Main.hs` | architect |
| M-08 | IT1 placeholder 棚卸し ADR 起票 (cost=4 bcrypt / JWT exp 固定 / stub fallback / Shipper.name) | `docs/adr/` | architect / writer |
| M-09 | stub fallback の本番混入防止 (production profile で fail-fast) | `Main.hs` | architect |
| M-10 | ロール別アクセス制御 (US04 営業のみ / US24 運航管理者のみ) を IT2 必達に固定 | `iteration_plan-2.md` | user-rep |
| M-11 | a11y 属性 (pattern / aria-describedby) 付与 | 各 FormView | user-rep |
| M-12 | API JSON / FormUrlEncoded スキーマを Haddock の `@` ブロックで例示 | `*Api.hs` 冒頭 | writer |
| M-13 | `e2e/README.md` に前提手順 (db migrate / docker compose up) リンク追加 | `e2e/README.md` | writer |

### 低 (改善の余地あり)

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L-01 | `maybe 8080 read` → `readMaybe` | `Main.hs:69` | programmer |
| L-02 | `mkContactEmail` 二重呼出統合 | `RegisterShipperCommand` | programmer |
| L-03 | E2E テストデータの決定論化 (`Date.now()` + random 廃止) | Playwright spec | tester |
| L-04 | Playwright `test.describe.configure({ mode: 'serial' })` 検討 | spec 全体 | tester |
| L-05 | `Shared/Auth` の階層位置検討 (横断 vs 業務 BC) | フォルダ構造 | architect |
| L-06 | 航海区間の削除 / 並べ替えボタン | `VoyageFormView` | user-rep |
| L-07 | 計画書の `[x]` チェック → 報告書に集約 | `iteration_plan-1.md` | writer |
| L-08 | arch-check Phase 1 のカバレッジ一覧をドキュメント化 | `docs/design/` or arch-check.md | writer |

## 矛盾事項

なし。各エージェントの指摘は重複・補完関係にあり、相反する判断はなかった。

## 懸念事項 (横断的リスク)

1. **Domain 層の BC 間共有 (`ShipperId`) が arch-check で検出されない**: ACL 規約と実装乖離。Scala 版 IT8 の「ADR と ArchUnit が乖離して fullTest で初検出」と同型の構造リスク (architect)
2. **stub fallback の本番混入**: `DATABASE_URL` / `JWT_SECRET` 未設定で 500 を返す挙動が本番でも同バイナリで起動する (architect)
3. **DB 統合テストが pending スキップで「常に緑だが実行されていない」状態**: CI で `DATABASE_URL` を設定する運用がなければ機能していない (tester, MEMORY と同型)
4. **`saveCargo` が SELECT shipper.id → INSERT cargo の 2 クエリ非トランザクション**: 荷主削除と同時実行で外部キー違反 (programmer)
5. **ID 手入力 + エラー値消失の UX**: 受入れテストで「使えない」評価のリスク (user-rep)

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer (高: 2 / 中: 4 / 低: 2)</summary>

ヘキサゴナル境界・スマートコンストラクタ・DomainError 単一 sum type・arch-check Phase 1・列挙攻撃対策の `InvalidCredentials` 単一化を高く評価。重大点は `error` 呼出と JWT exp 固定。

</details>

<details>
<summary>xp-tester (高: 3 / 中: 3 / 低: 2)</summary>

レイヤ分離と HTTP セマンティクス網羅を評価。最大の問題は PRG / htmx / property 3 領域のテストカバレッジ欠落で「アイスクリームコーン予備軍」と指摘。`DATABASE_URL` 未設定スキップが「常に緑」化するリスクも警告。

</details>

<details>
<summary>xp-architect (高: 2 / 中: 4 / 低: 1)</summary>

Composition Root の責務集約・ACL 抽象・JSON/SSR 経路分離・ADR 文書化を高評価。重大点は arch-check の BC 横断盲点と AST 解析への移行優先度。

</details>

<details>
<summary>xp-technical-writer (高: 2 / 中: 2 / 低: 2)</summary>

README + Haddock のオンボーディング動線と「学び」セクションを評価。Shipper.name placeholder の文書化不足と完了報告書のエビデンス欠落を指摘。報告書日付と計画書期間の齟齬 (Ralph Loop 前倒し実行) への注記推奨。

</details>

<details>
<summary>xp-user-representative (高: 2 / 中: 3 / 低: 1)</summary>

PRG / htmx 荷主検索 / 区間動的追加を実業務適合性で高評価。ID 手入力 + エラー値消失の組合せが「現場で使えない」評価につながると警告。Shipper.name 未実装は IT2 最優先タスクへ。

</details>

## 次のアクション

1. **IT2 計画作成時に H-01〜H-10 を必達タスクとして組み込む**
2. **M-08 (placeholder 棚卸し ADR) を IT2 着手前に 1 本起票** (`creating-adr`)
3. **H-09 / H-10 のドキュメント反映** (Shipper Haddock + iteration_report-1.md に成功基準表追加) は本レビュー直後に対応可能
4. **本レビュー結果を `docs/review/index.md` に登録**

## メタ情報

- レビュー日: 2026-06-26
- 手法: XP 5 エージェント並列レビュー (programmer / tester / architect / technical-writer / user-representative)
- レビュー対象コミット範囲: main 分岐後の haskell/take-1 全コミット
