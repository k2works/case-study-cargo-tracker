# IT2 実装マルチパースペクティブレビュー（2026-06-21）

## レビュー対象

- ブランチ: `scala/take-1`
- 期間: IT1 末（コミット `dc47f757`）〜 IT2 末（コミット `c506ad2f`）
- 規模: 32 コミット / 134 ファイル / +5034 / -417 行
- 対象 US: US05（危険物・冷凍貨物予約）、US06（経路設計引き渡し）、US24/US25（航海スケジュール登録・更新）+ Spike US08
- 対象タスク: IT1 申し送り 11 件（0.1〜0.11）+ US05 6 件 + US06 4 件 + US24/25 7 件 + Spike 3 件 + Release 0.1 準備 2 件

## 総合評価

ヘキサゴナル DDD 再構成と Scala 3 イディオムの一貫適用、ArchUnit 5 ルールによる構造的不変条件の仕組み化、Playwright E2E と XP プラクティス（DbCleanupSupport、InMemoryFake）が高水準で揃った IT2 実装。ただし **(1) 経路設計者ダッシュボードから航路管理画面への導線が disabled のまま** と **(2) 航海スケジュール UI が単区間固定で US24 受入条件「複数区間順序付き」を満たさない** の 2 点は受入承認のブロッカー。加えて **(3) ScalikeJdbcVoyageRepository.save が version WHERE 条件を欠き楽観ロックが活性化していない**、**(4) iteration_plan-2.md のサマリチェックリストが未更新で自己矛盾** がリリース判断前に対処すべき項目。

## 改善提案（重要度順）

### 高（マージ前 / IT3 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 「航路管理」カードの `disabled / 準備中` を `/voyages` リンクに差し替え | `app/views/dashboard.scala.html` L48-50 | user-rep | US24/25 実装済みなのに UI から到達不可。受入承認不可 |
| 2 | 航海スケジュール登録画面に複数区間追加 UI を実装（最小限「区間追加」ボタン） | `app/views/voyage/formPage.scala.html` L19-51 | user-rep | US24 受入条件「複数区間を順序通りに登録できる」未達。実運用の航海は多区間が標準 |
| 3 | `ScalikeJdbcVoyageRepository.save` の UPDATE に `WHERE id = ? AND version = ?` を追加し、更新行数 0 で `OptimisticLockException` を投げる | `app/cargotracker/routing/infrastructure/repositories/ScalikeJdbcVoyageRepository.scala` | architect | 楽観ロックが column 追加のみで動作していない。並行更新で last-writer-wins |
| 4 | ArchUnit ルール 3 のコンテキスト一覧に `routing` を追加 | `test/cargotracker/arch/HexagonalArchitectureSpec.scala` | architect | 新設の Routing コンテキストが境界検査対象外 |
| 5 | `iteration_plan-2.md` の成功基準・DoD・進捗率を実装状態に合わせて更新 | `docs/development/iteration_plan-2.md` L27-33, L199-208, L800-808 | tech-writer | 個別タスク `[x]` だがサマリ層 `[ ]`、進捗率 0% のまま。読者混乱 |
| 6 | `VoyageCommandService.register` / `update` の共通骨格を private 抽出（VoyageNumber パース + buildSchedule + save） | `app/cargotracker/routing/application/commandservices/VoyageCommandService.scala` L20-47 | programmer | 90% 重複、将来 schedule バリデーション追加時に二重修正 |
| 7 | `VoyageController.create` / `update` の form.fold 重複を `handleSubmit` で抽出 | `app/cargotracker/routing/interfaces/web/VoyageController.scala` L51-79, 100-129 | programmer | 同 4 箇所の formPage 呼出し、修正漏れの温床 |
| 8 | `BookingCommandService.book` 内の `_ => "荷主が見つかりません"` を sealed エラー網羅 match に | `app/cargotracker/booking/application/commandservices/BookingCommandService.scala` L73 | programmer | 将来 `Cargo.book` が別失敗理由を追加した時メッセージ握りつぶし |
| 9 | Controller 層の Play `FakeRequest` 統合テスト追加（CSRF / AuthFilter リダイレクト / Flash 境界） | `test/cargotracker/.../**EndpointSpec.scala` | tester | new_coverage 68.9% の主因。E2E で埋めるとアイスクリームコーン化 |
| 10 | `BookingCommandService.assignToRouting` / `book` の境界値・エラー経路の網羅（未存在 ID、状態遷移違反、期限切れ、並行衝突） | `test/.../BookingCommandServiceSpec.scala` | tester | 状態遷移はバグの温床、現状 happy path 偏重 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 11 | 予約詳細に温度管理条件の表示を追加（危険物と同形式） | `app/views/booking/detail.scala.html` L23-27 | user-rep | 経路設計者が冷凍要件を確認できない |
| 12 | 危険物・冷凍フィールドを貨物種別による JS トグル / fieldset 開閉で隠す（htmx 移行までの暫定） | `app/views/booking/formPage.scala.html` | user-rep | 一般貨物の予約で誤入力誘発 |
| 13 | 経路設計者ダッシュボードに「受領」「設計開始」等の次アクションを追加 | `app/views/dashboard.scala.html` | user-rep | 引き渡し後の業務追跡が止まる |
| 14 | SonarQube QG セルを「PASS 項目 / ERROR 項目 / 判断根拠 / IT3 対応」の 4 行サブリストに分解、localhost URL は削除またはコメント化 | `docs/development/release-0.1.0-gate-check.md` L12 | tech-writer | リリース判断は監査対象、可読性優先 |
| 15 | `RouteCandidateSearchSpike.search` を `legs.groupBy(_.from)` で隣接リスト化 | `app/cargotracker/routing/application/RouteCandidateSearchSpike.scala` L88-92 | programmer | DFS で毎回 O(\|legs\|) 走査、`maxLegs=5` で爆発 |
| 16 | Twirl テンプレート最小カバレッジ（必須 label/for/id・CSRF token・i18n キー） | `test/...` | tester | アクセシビリティ修正の回帰を E2E 任せにしない |
| 17 | Dashboard の集計ロジックを pure function 切り出し、デシジョンテーブル網羅 | `HomeController` / pure helper | tester | Controller 密結合でテスト不能 |
| 18 | E2E フレーキネス対策（`page.waitForResponse` + seed エンドポイント、`networkidle` 禁止 lint） | `e2e/src/tests/...` | tester | 14 件規模でも CI 不安定化前兆対処 |
| 19 | README に「動かし方」ブロック追加（ロール別ダッシュボード / シードユーザー / ログイン URL） | `apps/cargo-tracker/README.md`（新規 or 拡張） | tech-writer | CHANGELOG だけでは到達できない |
| 20 | CHANGELOG L15 日付 `2026-07-19` を「予定: 2026-07-19」と区別、または実リリース日に差し替え | `apps/cargo-tracker/CHANGELOG.md` | tech-writer | 未来日付（現在 2026-06-21） |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| 21 | `VoyageController` 内の `ZoneId.of("UTC")` を `private val Utc` に切り出し | `VoyageController.scala` L91, 135 | programmer |
| 22 | `RouteCandidateSearchSpike` にユニットテスト追加（直行 / 経由 / 不到達） | `test/.../RouteCandidateSearchSpikeSpec.scala` | programmer（※実は追加済み 7 件あり、再確認） |
| 23 | `BookingCommandService.book` の 45 行 for 式を `validateInputs` / `buildSpec` / `persist` 3 段に分割 | `BookingCommandService.scala` | programmer |
| 24 | Mutation Testing 試走（stryker4s を CommandService 限定）でカバレッジの質を可視化 | CI 拡張 | tester |
| 25 | 荷主コード手入力に検索 / サジェスト導入（IT1 から継続） | `BookingController` フォーム | user-rep |
| 26 | formPage.scala.html の「IT2 では 1 区間入力」注記を本番前に削除 | `voyage/formPage.scala.html` L51 | user-rep |
| 27 | formPage の温度範囲 min を業務値（-60℃ 等）に絞る | `booking/formPage.scala.html` | user-rep |
| 28 | ADR 0005 の関連リンクを相対パス `../development/iteration_plan-2.md` に修正 | `docs/adr/0005-route-search-algorithm.md` L70 | tech-writer |
| 29 | `iteration_plan-2.md` 進捗率テンプレートに `<!-- 完了時に更新 -->` コメント運用ルール化 | テンプレート | tech-writer |
| 30 | CHANGELOG L71-72 比較リンクのリポジトリ名を `case-study-cargo-tracker-scala-take-1` に修正 | `CHANGELOG.md` | tech-writer |
| 31 | `CarrierMovementCommand` を interfaces 層 DTO、application 層は別命名で分離 | パッケージ再編 | programmer |

## 矛盾事項

なし。programmer と tester が tester の「Controller テスト追加」と programmer の「Controller 重複抽出」を独立に指摘しているが、抽出後にテストを書く順序で両立可能。

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 4 / 中: 1 / 低: 3）</summary>

評価サマリー: ヘキサゴナル DDD 再構成と Scala 3 イディオム（Either 駆動スマートコンストラクタ、sealed エラー）が一貫適用された良質な IT2 実装。Application 層の責務境界は概ね妥当だが、create/update の重複と Controller の重複が今後の負債候補。

主要指摘: VoyageCommandService.register/update の構造重複（高）、VoyageController の form.fold 重複（高）、RouteCandidateSearchSpike の隣接リスト化（中）、BookingCommandService のエラー潰し（中）、UTC のハードコード（低）、Spike のテスト所在不明（懸念）、CarrierMovementCommand のパッケージ位置（範囲外）。

</details>

<details>
<summary>xp-tester（高: 3 / 中: 3 / 低: 1）</summary>

評価サマリー: ユニット中心の健全なピラミッドと ArchUnit による構造的不変条件の固定化は高評価。new_coverage 80% を 11pt 下回る主因が Controller / Twirl / Dashboard の未カバーに集中、E2E に逃げず統合層を厚くする必要。

主要指摘: Controller 層テスト追加（高）、CommandService 境界値・エラー経路（高）、CargoBookingSpec と domain-model.md 整合 / ScalaCheck プロパティ化（高）、Twirl 最小カバレッジ（中）、E2E フレーキネス対策（中）、Dashboard 集計切り出し（中）、Mutation Testing 試走（低）。InMemoryRepository のユニーク制約・トランザクション境界が本物 PostgreSQL を再現していない懸念。

</details>

<details>
<summary>xp-architect（高: 2 / 中: 0 / 低: 0）</summary>

評価サマリー（短縮版）: 最重要は「ArchUnit ルール 3 のコンテキスト未追加」と「ScalikeJdbcVoyageRepository.save の楽観ロック WHERE 条件欠落」の 2 点で、いずれも IT3 着手前の対応を推奨。

主要指摘:
- ArchUnit ルール 3 のコンテキスト一覧に `routing` 追加（高）
- ScalikeJdbcVoyageRepository.save の WHERE に version 条件追加 + OptimisticLockException 投出（高）

</details>

<details>
<summary>xp-technical-writer（高: 1 / 中: 3 / 低: 3）</summary>

評価サマリー: 全体として読みやすく、トレーサビリティ（CHANGELOG ↔ ADR ↔ iteration_plan ↔ gate-check）も保たれた高品質ドキュメント群。CHANGELOG と ADR 0005 はそのままリリースノート公開可能。ただし iteration_plan-2.md の進捗マーク不整合と gate-check の SonarQube 判断記述に改善余地。

主要指摘: iteration_plan-2.md サマリチェックリスト未更新（高）、gate-check の SonarQube セル分解（中）、localhost リンク削除（中）、CHANGELOG 未来日付（低）、ADR リンク相対パス化（低）、README 不在（中）、リポジトリ名（範囲外）。ドメインクラス ScalaDoc は別途確認推奨。

</details>

<details>
<summary>xp-user-representative（高: 2 / 中: 3 / 低: 2）</summary>

評価サマリー: US05/06/24/25 コアフローは画面・ロール認可とも実用に耐えるレベル。ただしダッシュボードから航路管理への導線が「準備中」のままで実装済機能に到達できないこと、航海単区間 UI が業務実態と乖離することの 2 点は IT2 完了判定前に手当て必要。

主要指摘: 「航路管理」カード disabled 解除（高）、複数区間追加 UI（高）、温度管理条件の予約詳細表示（中）、危険物・冷凍フィールドのトグル隠し（中）、ダッシュボードの次アクション（中）、形式注記削除（低）、荷主コード検索（低、IT1 継続）。

</details>

## 対応方針（IT2 ふりかえり前に決定）

### 必須対応（IT2 完了承認前）

- #1（航路管理リンク化） + #2（複数区間 UI）→ ユーザー受入の前提
- #3（VoyageRepository 楽観ロック）+ #4（ArchUnit ルール 3）→ アーキテクチャ整合
- #5（iteration_plan サマリ更新）→ ドキュメント整合

### IT3 計画への持ち越し（申し送り）

- #6〜#10（重複抽出 + Controller テスト + 境界値強化）→ IT3 ふりかえり Try 候補
- #11〜#20（中優先 UX / テスト基盤改善）→ IT3 イテレーション計画で順次反映
- #21〜#31（低優先）→ 機会があれば対応

## 関連ドキュメント

- [リリース計画](../development/release_plan.md)
- [イテレーション 2 計画](../development/iteration_plan-2.md)
- [Release 0.1.0 ゲート確認](../development/release-0.1.0-gate-check.md)
- [CHANGELOG](../../apps/cargo-tracker/CHANGELOG.md)
- [ADR 0005 経路探索アルゴリズム](../adr/0005-route-search-algorithm.md)
