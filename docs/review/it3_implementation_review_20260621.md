---
title: IT3 実装レビュー（マルチパースペクティブ）
date: 2026-06-21
---

# IT3 実装レビュー（マルチパースペクティブ）

## レビュー対象

IT3（2026-07-20 〜 2026-08-02 計画 / 1 日 AI ペアプロ実績）の Routing コンテキスト US07/US08 実装、関連テスト、ADR 0006、計画/完了報告、SELECT 句回帰修正。

- Voyage 集約拡張 + V8 マイグレーション + Repository（船名 / 運送会社 / 対応貨物種別）
- `VoyageQueryService.search`、`/voyages/search` 画面（US07）
- `RouteCandidateSearch` 純関数探索 + 隣接リスト最適化 + topN（US08）
- `RouteCandidateQueryService` + `PricingService` 連携 + `PricedRouteCandidate`
- `RouteCandidateController` + `/bookings/:bookingId/routes` 画面 + 期限超過分離
- パフォーマンステスト（1000 件 P95 = 60ms）
- IT3 末期: `ScalikeJdbcEstimateRepository.findById` SELECT 句に `version` を追加、Voyage 側も同型回帰テスト追加

## 総合評価

ヘキサゴナル DDD / CQRS の骨格は維持され、US07・US08 の主要受入条件は機能面で達成済み。`RouteCandidateSearch` の純関数化・隣接リスト最適化・期限超過候補の分離通知など、設計と UX の両面で良質。一方で **(1) 業務導線が「候補表示」止まりで「経路選択確定」へつながっていない**、**(2) `Instant.toString` / `Money` 生表示が業務感覚に合っていない**、**(3) 楽観ロックが例外投擲のままで Either 化計画と乖離**、**(4) Voyage 集約に空文字許容のデフォルト引数が残置**、**(5) ArchUnit ルール 4 を回避する形で Command/DTO を application 直下に移動した設計判断が未明文化** — これらが IT4 以降のリスク。

## 改善提案（重要度順）

### 高（マージ前または IT4 冒頭で対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 経路候補画面に「この経路で確定」アクションを追加（US09 への導線でも可） | views/booking/routes.scala.html L56-66 | user-rep | 経路設計者が画面で意思決定して終わらないと業務が完結しない |
| 2 | 検索画面に bookingId 事前充填の導線（予約詳細→検索）を追加 | search.scala.html / 予約詳細 | user-rep | US07 受入条件 1「予約番号を指定して条件確認」が未充足 |
| 3 | 出港・到着時刻の表示を `yyyy-MM-dd HH:mm Z` 形式に。料金は `Money` フォーマッタ経由（桁区切り + 通貨記号） | search.scala.html L66-67 / routes.scala.html L63,93 | user-rep, programmer, writer | 業務会話は JST / 通貨記号付き、`Instant.toString` 生表示は誤読の温床 |
| 4 | iteration_plan-3.md L21,88,329 の「Either[DomainError.ConcurrentModification, A] を返す」記述を、実装（OptimisticLockException 投擲）に合わせて修正、または ADR で Either 化方針を明文化 | docs/development/iteration_plan-3.md | writer, architect | 計画書と実装の乖離。読者が誤読する |
| 5 | `Voyage.register(2 引数)` / `reconstruct(3 引数)` のオーバーロード（vesselName/carrierCode 空文字デフォルト）を削除し、空文字 vessel の永続化を不可能化 | Voyage.scala L37-38, L49-54 | programmer, architect | US07 で vesselName が検索キーになった以上、空文字許容は不変条件違反 |
| 6 | `RouteCandidateQueryServiceSpec` の `InMemoryVoyageRepository.findByCriteria` が引数を無視している。せめて origin/destination/cargoType を実装 | RouteCandidateQueryServiceSpec L26-32 | tester | 本物 Repository に差し替えた瞬間に振る舞いが変わる偽 stub。テスト価値が下がる |
| 7 | `RouteCandidateEndpointSpec` に「seed なしで 200 + 空表示」のハッピーパスを追加 | RouteCandidateEndpointSpec | tester | 現状は 404 と未認証の 2 ケースのみ、ルーティング層単独の回帰穴 |
| 8 | SELECT 句回帰穴の横展開: 他 Repository（Cargo / Shipper / Booking 系）の Spec で「1 件 seed → list」テストが揃っているか棚卸し | test/cargotracker/*/infrastructure/repositories/ | tester | IT3 末期教訓の横展開。Cargo/Shipper は `SELECT *` で構造的安全だが Spec での担保は別議論 |

### 中（IT4 〜 IT5 で計画的に対応）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 9 | ArchUnit ルール 4 を「queryservices 直下は `*QueryService` または `*Query` / `*Result` の DTO サフィックス許容」に拡張し、Command/DTO を queryservices 配下に戻す | HexagonalArchitectureSpec | architect | 現状は ArchUnit 回避のための物理配置。CQRS Query パッケージのまとまりが崩れる |
| 10 | 楽観ロック失敗を `Either[DomainError.ConcurrentModification, Unit]` に統一する ADR を作成し Repository.save シグネチャを変更 | Routing / Booking / Shipper / Estimate Repository | architect, writer | 例外がレイヤーを貫通し Controller で try-catch 散乱する未来を防ぐ |
| 11 | `RouteCandidateSearch.topN` のソート戦略を `Strategy` として注入できる構造に。料金順並べ替えの IT4 拡張に備える | RouteCandidateSearch.scala L78-82 | architect | 現状 `(legs.size, transitDays, departure)` 固定で `estimatedCost` 未反映 |
| 12 | `RouteCandidateQueryService.price` を `RoutePricer`（料金集計責務）に分離 | RouteCandidateQueryService.scala L43, L66-82 | programmer | SRP。QueryService は探索＋整形に専念 |
| 13 | `routes.scala.html` の期限内/期限超過テーブルをパーシャル（`_routeTable`）化 | booking/routes.scala.html L41-99 | programmer | DRY、列追加が片側だけになる事故防止 |
| 14 | `VoyageController.hasAnyParam` を `SearchVoyageFormData.isEmpty` で判定 | VoyageController.scala L62-67 | programmer | キー集合の二重管理を解消 |
| 15 | 検索画面の `Seq("General","Refrigerated","Hazardous")` を `CargoType.values` から生成 | search.scala.html L32 | programmer, writer | ハードコーディング禁止、列挙追加時の漏れ防止 |
| 16 | `RouteCandidateSearchPerfSpec` を flake 耐性向上: `@Slow` タグで CI 分離、p95 はログ・p99 をハードアサート | RouteCandidateSearchPerfSpec L40-60 | tester | CI ホスト負荷で 3 秒閾値が揺れる |
| 17 | `RouteCandidateSearchSpec` の境界値追加: `maxLegs=0/1`, `origin==destination`, 空 legs, 同時刻接続 | RouteCandidateSearchSpec | tester | BVA の典型対象が抜けている |
| 18 | `VoyageQueryServiceSpec` に `departureDateFrom > departureDateTo` 逆転ケース、Repository Spec に境界完全一致ケースを追加 | 各 Spec | tester | 日時範囲の BVA |
| 19 | 期限超過候補に「何日超過か」列を追加、推奨ソート基準を画面に明示 | routes.scala.html | user-rep | 荷主交渉の材料、説明可能性 |
| 20 | 経由港列を経路候補テーブルに追加（航海番号矢印表記とは別に） | routes.scala.html | user-rep | US08 受入条件「経由港」「区間数」を両方提示 |
| 21 | `Estimation.RouteCandidate`（文字列リスト）と `Routing.RouteCandidate`（構造体）の変換を `RouteCandidateAssembler` ACL として ADR 化 | Estimation/Routing | architect | 現状 Estimation 側に変換余地がない |
| 22 | `ScalikeJdbcEstimateRepository.findAll` の N+1 解消（一括 SELECT + groupBy） | EstimateRepository | architect | スケール時のレイテンシ線形悪化 |
| 23 | `ScalikeJdbcEstimateRepository` の `Money(...).getOrElse(Money.zeroJpy)` を fail-fast に変更 | EstimateRepository L25-28 | programmer | 防御的コードで DB 値を黙って 0 円に書き換えている |
| 24 | RouteCandidateSearch.scala L9, L40 の「サイクル禁止」コメントを「単純経路（simple path）のみ」に正確化 | RouteCandidateSearch.scala | writer | 実装は `visited` で `to` の重複防止 |
| 25 | iteration_plan-3.md L601-604 の ADR 0006 表が二重掲載 / L344 `VARCHAR(200)` vs ADR `VARCHAR(100)` の桁数不一致 | iteration_plan-3.md | writer | コピペ残骸 + スキーマ齟齬 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 26 | `RouteCandidate` に `given Ordering[RouteCandidate]` を置きソートキーのマジック性解消 | RouteCandidate.scala | programmer |
| 27 | `RouteCandidateSearch.topN` Scaladoc 見出しの順序記述（「直行便 → 所要日数 → 区間数」）を実装順に修正 | RouteCandidateSearch.scala L69 | writer |
| 28 | `VoyageEndpointSpec` の Playwright 委譲コメントに逆参照（Playwright 側にも本 Spec 行番号コメント） | VoyageEndpointSpec L75-76 | tester |
| 29 | `RouteCandidateQueryServiceSpec` の StubPricingService を `Right(Money.jpy(1000L).toOption.get)` で意図明示 | Spec L25 | tester |
| 30 | `loadSupportedCargoTypes` の `CargoType.fromName(...).flatten` をサイレント脱落させず `getOrElse(throw IllegalStateException)` に統一 | ScalikeJdbcVoyageRepository | architect |
| 31 | `arrivalDeadline.atTime(23,59,59)` を `LocalTime.MAX` に変更し境界精度向上 | RouteCandidateController L62 | programmer |
| 32 | UnLocode 隣に港名を併記、検索フォーム入力を自動大文字化 | search.scala.html, routes.scala.html | user-rep |
| 33 | `apps/cargo-tracker/.scannerwork/` を .gitignore に追加 | .gitignore | architect |
| 34 | `Controller が OptimisticLockException を import しないこと` を ArchUnit ルールに追加 | HexagonalArchitectureSpec | architect |

## 矛盾事項

5 エージェント間で明確に対立する指摘はなし。programmer と architect が双方「Voyage オーバーロードの空文字許容」を高優先度で指摘しており方向性は一致。

## 対応方針

| カテゴリ | 件数 | 方針 |
|---|---|---|
| 高 #1, #2, #5, #6, #7, #8 | 6 | **IT4 冒頭タスク**として `iteration_plan-4.md` に積む（業務導線・テスト品質） |
| 高 #3 | 1 | **IT4 タスク**として表示フォーマッタ層を追加（`Money` + `Instant` ヘルパ） |
| 高 #4 | 1 | **本レビューと同時に修正**（計画書文言を実装に合わせる） |
| 中 #9, #10, #21, #22 | 4 | **ADR としてまとめ IT4-5 で順次実施** |
| 中 その他 | 13 | **IT4 イテレーション計画タスクに分解** |
| 低 | 9 | **時間が空いた時に Issue 化、優先度低のまま** |

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 2 / 中: 4 / 低: 2）</summary>

設計分離は明確。`RouteCandidateSearch` の純関数化、`VoyageController.handleSubmit` の DRY、N+1 回避が高評価。一方で Voyage の空文字許容デフォルト引数、`SearchVoyageFormData` 判定の二重管理、`ArrayBuffer + foreach` の手続き的構築、`price` 責務の混在、Money のサイレント `getOrElse(zeroJpy)` を主要懸念として指摘。

</details>

<details>
<summary>xp-tester（高: 2 / 中: 5 / 低: 2）</summary>

テストピラミッドは概ね健全、SELECT 句回帰を「1 件 seed → list」で塞いだ判断は正しい。一方で `InMemoryVoyageRepository.findByCriteria` の偽 stub、Endpoint 正常系不足、Perf Spec の flake 耐性、境界値抜け（maxLegs=0/1、同時刻接続、日付逆転）、Pricing 失敗時の仕様テスト欠落を指摘。SELECT 句回帰穴の横展開チェックを推奨。

</details>

<details>
<summary>xp-architect（高: 3 / 中: 5 / 低: 3）</summary>

ヘキサゴナル DDD / CQRS の骨格は維持されているが、ArchUnit ルール 4 を回避するための DTO 移動が「物理配置の意味」を弱めている。楽観ロックの例外貫通、料金ソート戦略未注入、Estimation/Routing の `RouteCandidate` 同名別構造の ACL 不在、`findAll` の N+1、`Voyage.register` 空文字許容残置を主要負債として整理。ArchUnit ルールに「Controller が OptimisticLockException を import しない」を追加することを提案。

</details>

<details>
<summary>xp-technical-writer（高: 1 / 中: 4 / 低: 3）</summary>

Scaladoc・ADR・iteration 報告は整合性が高い。一方で計画書 0.2 タスクの「Either」記述が実装の例外投擲方式と乖離、ADR 0006 と plan/report のスキーマ桁数（VARCHAR 200/100）不一致、`RouteCandidateSearch.topN` の Scaladoc 見出し順序ミス、`RouteCandidate` の「サイクル禁止」表現の不正確さ、`Money` 表示の view ハードコードを指摘。

</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 3 / 低: 1）</summary>

期限内/期限超過の分離と緩和ガイドは実務的に高評価。一方で **(1) 経路選択確定アクションがない**、**(2) 予約番号からの事前充填導線がない**、**(3) `Instant.toString` / `Money` 生表示が業務感覚に合わない** の 3 点が業務導線として未完成。経由港列・推奨ソート根拠・期限超過日数も荷主交渉の説明材料として欲しい。

</details>

## 関連ドキュメント

- [IT3 計画](../development/iteration_plan-3.md)
- [IT3 完了報告書](../development/iteration_report-3.md)
- [ADR 0006 Voyage データモデル拡張](../adr/0006-voyage-data-model-extension.md)
- [IT2 実装レビュー](./it2_implementation_review_20260621.md)
