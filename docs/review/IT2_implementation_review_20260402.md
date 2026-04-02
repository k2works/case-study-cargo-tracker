# IT2 実装レビュー（輸送見積・ルート検索）

## コードレビュー結果

### レビュー対象

- **イテレーション**: IT2（輸送見積・ルート検索）
- **コミット範囲**: `b5be1ef`（IT1 完了）→ `1c3b51f`（IT2 完了）
- **変更規模**: 94 ファイル変更、6,727 行追加、59 行削除
- **レビュー日**: 2026-04-02
- **主要対象コンテキスト**: quote、routing、booking（ACL）

---

### 総合評価

ヘキサゴナルアーキテクチャの責務分離・DDD パターンの適用・テストピラミッドのバランスは全体的に高い水準を維持できており、IT2 の基本的な実装品質は合格ラインを超えている。一方で、**`LocalDate.now()` 直呼び出しによるテスト不安定性**と **E2E テストの固定日付による時限爆弾**は IT3 進行中に実害が出る前に対処が必要である。ユーザー視点では**再検索後に予約への導線が断ち切られる UI バグ**が業務フローを阻害する深刻な問題として浮上した。ドキュメント面では CHANGELOG の未更新と `routing` パッケージの説明欠如が改善急務。

---

### 改善提案（重要度順）

#### 高（マージ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | `RouteOption.isOnTime(LocalDate)` の `LocalDate.now()` を `Clock` 注入に変更 | `RouteOption.java:65` | programmer / tester / user-rep | テスト実行日で結果が変わる。TDD サイクルで安定した Red-Green が保証できない。また見積作成後に適合判定が変化するという業務上の問題も招く |
| H2 | `RoutingWebController.search()` の `routeSearchService.isEmpty()` 判定をメソッド先頭に移動 | `RoutingWebController.java` | architect | 現在はサービス未登録でも `bookingQueryPort.findById()` が実行されてから redirect する。無駄な I/O とデバッグ困難を招く |
| H3 | 再検索フォームに `bookingId` hidden フィールドを追加 | `routing/search.html:83` | user-rep | 再検索後に「予約詳細に戻る」ボタンが消え、経路設計者の業務フローが断ち切られる UI バグ |
| H4 | E2E テストの固定日付（`2026-06-30`・`2026-05-15`）を相対日付に変更 | `routing.spec.ts:74`、`quote.spec.ts` | tester | 2026-06-30 経過後にバッジ判定が反転しテストが失敗する時限爆弾。現在も `quote.spec.ts` の日付はすでに過去日リスク圏内 |
| H5 | `RouteOptionTest` に BVA 境界+1 ケース（ちょうど 1 日遅れる→false）を追加 | `RouteOptionTest.java:140` | tester | 現在テストは「同日 true」と「4 日超過 false」のみ。最重要な境界ケースが抜けている |
| H6 | ArchUnit に routing→booking・quote→routing/booking の依存禁止ルールを追加 | `ArchitectureTest.java` | architect | 現在 A01〜A05b は booking↔shipper のみ保護。新規開発者が routing ドメインから booking を直接参照しても検知できない |
| H7 | `CHANGELOG.md` に IT2（見積登録・ルート検索）の変更内容を追記 | `CHANGELOG.md` | technical-writer | v0.1.0（IT1）止まりのまま。運用担当者・外部レビュアーが IT2 の変更内容を追跡できない |
| H8 | `routing/package-info.java` に routing コンテキストの責務説明を追記 | `routing/package-info.java` | technical-writer | `package` 宣言 1 行のみで Javadoc なし。quote は最低限の説明があり不均衡。新規参加者が routing の責務を把握できない |
| H9 | `QuoteCondition` の `equals` で `BigDecimal.compareTo()` を使用し hashCode と統一 | `QuoteCondition.java` | programmer | `1.0` と `1.00` が `equals` では不等、`hashCode` では同値になる契約不整合リスク。重複登録や検索ミスを招く可能性がある |

#### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `RegisterQuoteCommandServiceTest` で `quoteRouteProviderPort` 呼び出しを `argThat()` で検証 | `RegisterQuoteCommandServiceTest.java:47` | tester | `any()` で受け流しており、コマンドフィールドが正しく渡されているか未検証。`RouteSearchServiceTest` の `argThat()` 検証と非対称 |
| M2 | `QuoteConditionTest` に過去日・同一地点のケースを追加（仕様の明示） | `QuoteConditionTest.java` | tester | 「過去日を許容するか」「出発地=目的地を許容するか」がドメインルールとして未定義。テストで意図を表明すること |
| M3 | `RoutingWebController` の 2 ステップ呼び出しの設計意図をコメントで明記 | `RoutingWebController.java` | architect | Web 側はフォーム表示用スナップショットが必要で二重 DB アクセスではない。コードを読むだけでは誤解を招く |
| M4 | `RouteSearchServiceUnavailableException` を package-private または独立クラスに格上げ | `RoutingRestController.java` | architect | `private static nested` のため `catch` するテストが書けず、503 レスポンスパスが未テストになる |
| M5 | `routing/search.html` の貨物種別表示を `displayName` に統一 | `routing/search.html:60` | user-rep | `DANGEROUS_GOODS` 等の enum 名がそのまま表示される。他フォームは `displayName`（危険物・冷凍冷蔵）を使っており不統一 |
| M6 | `Quote.java` の `issue()` に `@param` と `@throws` を追加 | `Quote.java` | technical-writer | 4 条件で `IllegalArgumentException` をスローするが Javadoc に記述なし。IT2_US01_review でも未対応 |
| M7 | `QuoteRestController` クラスレベル Javadoc を充実させる | `QuoteRestController.java` | technical-writer | 1 行のみで薄い。`RoutingRestController` が Optional 注入の理由まで説明しているのと著しく不均衡 |
| M8 | `createShipper()` テストヘルパーを共通クラスに移動 | `BookingQueryPortAdapterTest.java`、`RouteSearchServiceIntegrationTest.java` | tester | 同一の private ヘルパーが 2 ファイルに重複。テストフィクスチャの DRY 違反 |
| M9 | `Optional<RouteSearchService>` 注入を Null Object パターンまたは `@ConditionalOnMissingBean` で置き換え | `RoutingWebController.java`、`RoutingRestController.java` | programmer | 2 つの Controller が同じ `isEmpty()` チェックを持ち DRY 違反。チェック漏れによるバグリスクもある |
| M10 | `QuoteRepositoryImpl` の `ObjectMapper` をフィールド DI に変更 | `QuoteRepositoryImpl.java` | programmer | 生成コストの高いオブジェクトをメソッド内でインスタンス化している疑い。また viaLocodes の JSON 保存はカンマ区切りで代替可能か検討を |
| M11 | 見積一覧に「ルート候補数」と「希望着日に間に合うルート有無」列を追加 | `quote/list.html` | user-rep | 20 件超の見積確認時に有効なルートの存在を一覧で把握できない。優先順位をつけた業務確認が困難 |

#### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | `QuoteWebController` の UUID パースを `@PathVariable UUID id` で Spring MVC に委譲 | `QuoteWebController.java` | programmer | try-catch による手動パースは不要。`@ControllerAdvice` で統一ハンドリング可能 |
| L2 | `routeSearchService.isEmpty()` 時にログ出力を追加 | `RoutingWebController.java` | programmer | 設定ミスや Bean 未登録をサイレントリダイレクトで握りつぶしており本番障害の診断が困難 |
| L3 | `StubQuoteRouteProviderAdapterTest` に E2E 依存の固定データ（航海番号等）の検証を追加 | `StubQuoteRouteProviderAdapterTest.java` | tester | E2E は `SG001`/`SG002`/`JP001` 等の具体的な値に依存するが Unit テストが件数のみ確認しており内容変更を検知できない |
| L4 | `BookingQueryPortAdapter.convertCargoType()` の `switch` 各 case にインラインコメント追加 | `BookingQueryPortAdapter.java` | technical-writer | `DANGEROUS_GOODS → HAZARDOUS` 等のマッピング意図がメソッドを開いて初めてわかる |
| L5 | `quote/package-info.java` の説明を充実させる | `quote/package-info.java` | technical-writer | 「Quote（見積）コンテキスト。」のみ。ルート照会との関係が記述されていない |
| L6 | `RouteOption` に通貨コード（`JPY`/`USD`）フィールドを追加 | `RouteOption.java` | user-rep | 現在 stub が `150,000 円` 固定。将来の外部システム接続時に通貨混在バグのリスク |
| L7 | `CargoType` 3 コンテキスト分散の設計判断を ADR に記録 | ADR | architect | booking/quote（GENERAL_CARGO/DANGEROUS_GOODS）と routing（GENERAL/HAZARDOUS）の命名乖離の経緯がコードベースに残っていない |

---

### 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | **programmer**: `RoutingWebController` が `BookingQueryPort` を直接呼び出すのは Controller の責務過多。`RouteSearchService` に委譲すべき | **architect**: Web 側はフォーム表示用スナップショットが別途必要であり、二重 DB アクセスにはなっておらず設計意図は正しい | WebController における BookingQueryPort 直接呼び出しの是非 | **暫定対応**: 設計意図をコメントで明記（M3）。将来的に `RouteSearchService.prepareFromBooking()` メソッドを追加してフォーム初期化ロジックをサービスに委譲するリファクタリングを検討（IT4 以降） |

---

### エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 3 / 中: 3 / 低: 2）</summary>

#### 評価サマリー
ヘキサゴナルアーキテクチャの責務分離・DDD パターンの適用・N+1 解消など、全体的に高い設計品質が見られる。ただし、テスト容易性を損なう隠れた時刻依存、`BigDecimal` の equals/hashCode 契約違反リスク、Controller の責務過多という 3 点が実装の安全性に影響しており、IT3 以降の変更容易性に直結する。

#### 良い点
- `Quote.issue()` / `reconstitute()` の明確な分離。防衛的コピーで不変性担保
- `QuoteCondition` の `hashCode` における `stripTrailingZeros()` 正規化
- `BookingQueryPortAdapter` の網羅的な `switch` 式（default なし・全ケース列挙）
- `QuoteRepositoryImpl` の IN 句バッチ取得による N+1 解消
- `applyFilters()` の Stream 合成による変更局所化

#### 改善提案
- **【高】`RouteOption.isOnTime(LocalDate)` — `LocalDate.now()` 直呼び出しによるテスト不能化** → Clock 注入設計に変更
- **【高】`QuoteCondition` — `equals` と `hashCode` の BigDecimal 契約不整合** → equals で `compareTo()` を使用
- **【高】`RoutingWebController` — Controller が `BookingQueryPort` を直接呼び出している** → サービスに委譲
- **【中】`Optional<RouteSearchService>` 注入** → Null Object パターンまたは `@ConditionalOnMissingBean`
- **【中】`QuoteRepositoryImpl` の `ObjectMapper` ライフサイクル管理** → DI 注入またはカンマ区切り代替
- **【低】`QuoteWebController` 手動 UUID パース** → `@PathVariable UUID` に委譲
- **【低】`routeSearchService.isEmpty()` 時のサイレントリダイレクト** → ログ出力追加

#### 懸念事項
- `Quote.domainEvents` のクリア処理が `save()` 後に保証されているか要確認
- `RouteSearchService` に `@Service` アノテーションがない点の設計意図ドキュメント化
- `RegisterQuoteCommand` と `QuoteCondition` の構造的重複による将来の shotgun surgery リスク
</details>

<details>
<summary>xp-tester（高: 3 / 中: 3 / 低: 1）</summary>

#### 評価サマリー
全体的に AAA パターン・`@DisplayName` による可読性・テストピラミッドのバランスは良好で、テスト設計の基礎水準は高い。ただし `LocalDate.now()` 依存メソッドの未テスト、境界値の抜け、E2E に潜む時限爆弾など、将来の信頼性を損なうリスクが複数存在する。

#### 良い点
- `RouteSearchServiceTest` の過不足ないフィルタテスト設計（4 パターン明確分離）
- `BookingQueryPortAdapterTest` の貨物種別マッピング全件検証
- `QuoteConditionTest` のバリデーション網羅と日本語メッセージ検証
- `QuoteTest` の `reconstitute` でドメインイベントが発行されないことを検証
- `RouteSearchServiceTest` の `argThat()` による Query 組み立て 5 フィールド検証

#### 改善提案
- **【高】`RouteOptionTest` — `isOnTime` 単引数版が未テスト** → Clock DI 設計変更と合わせてテスト追加
- **【高】E2E テストの固定日付（`2026-06-30`・`2026-05-15`）** → 相対日付に変更
- **【高】`RouteOptionTest` BVA 境界+1 ケース欠落** → 「ちょうど 1 日遅れる→false」ケース追加
- **【中】`RegisterQuoteCommandServiceTest` — コマンドパラメータ pass-through 未検証** → `argThat()` に統一
- **【中】`QuoteConditionTest` — 過去日・同一地点ケース未テスト** → 仕様を明示するテスト追加
- **【中】`createShipper()` ヘルパーの重複** → 共通テストファクトリに移動
- **【低】`StubQuoteRouteProviderAdapterTest` — E2E 依存の固定データ未検証** → 航海番号等の具体値を検証

#### 懸念事項（フレイキーリスク）
- `QuoteTest.quoteNumberHasExpectedFormat` が `LocalDate.now()` に暗黙依存（深夜ロールオーバー）
- `reconstitute()` への null 引数時の振る舞い未テスト
- `RouteSearchService.searchByBookingId()` でルート 0 件時の振る舞い未テスト
</details>

<details>
<summary>xp-architect（高: 2 / 中: 3 / 低: 1）</summary>

#### 評価サマリー
ヘキサゴナルアーキテクチャの基本構造は正しく実装されており、Port & Adapters パターン・ACL・CQRS の設計意図が一貫して守られている。一方、ArchUnit ルールのカバレッジ不足、WebController と RestController の非対称な呼び出しパターン、`CargoType` の重複定義など、IT3 以降の拡張で技術的負債に転化するリスクが複数点在している。

#### 良い点
- `BookingQueryPort` の依存方向（消費者がポートを所有）が正確
- `RouteSearchService` が POJO として DI コンテナ非依存でテスト可能
- `BookingSnapshot` の軽量DTO化による将来のサービス分割対応
- ArchUnit A01〜A05b による構造テスト安全網
- `RouteCandidate` の防衛的コンストラクタ + 不変性保証
- `DomainEvent` の BC 分離（cross-context 結合を回避）

#### 改善提案
- **【高】`RoutingWebController.search()` — `routeSearchService.isEmpty()` 判定をメソッド先頭に移動**
- **【高】ArchUnit に routing/quote コンテキスト間の依存制約ルール（A06/A07）を追加**
- **【中】`quote.CargoType` と `booking.CargoType` の重複 — 意図的独立か単純コピーかを ADR で明確化**
- **【中】`RoutingWebController` と `RoutingRestController` の呼び出しパターン非対称 — コメントで設計意図明記**
- **【中】`RouteSearchServiceUnavailableException` を package-private 以上に格上げ**
- **【低】`RoutingConfig` のコメントに Stub プロファイル前提を追記**

#### 懸念事項
- `CargoType` 3 コンテキスト分散によるショットガン・サージェリーリスク（新規追加時に 3 箇所 + 変換ロジック修正）
- `viaLocodes` JSON 保存による将来的なクエリ制約（特定港経由で絞り込みが SQL レベル不可）
- ドメインイベントが `RegisterQuoteCommandService` で発行されていない（IT3 以降の billing/tracking 連携に影響）
- `RestTemplate` → `RestClient` 移行（Spring Boot 4 での deprecated 対応）
</details>

<details>
<summary>xp-technical-writer（高: 2 / 中: 3 / 低: 2）</summary>

#### 評価サマリー
Javadoc の品質は IT1 比で顕著に向上しており、とくにサービス層・ACL アダプターの設計意図を言語化できている点は高評価。一方で、CHANGELOG が IT2 の機能追加を一切反映していないこと、`routing/package-info.java` に説明が皆無であること、`Quote.java` の `@param`/`@throws` 欠落といった残存課題がある。

#### 良い点
- `RouteOption.isOnTime()` オーバーロード Javadoc が模範的（`@param baseDate "通常は見積作成日 = 今日"` の補足）
- `RouteSearchService` クラス Javadoc がフィルタロジックを条件式つきで明示
- `BookingQueryPortAdapter` が ACL の役割と `requestedDeliveryDate → requestedArrivalDate` 変換を明示
- `RoutingRestController` が `Optional<RouteSearchService>` 注入の理由と 503 パターンを記述
- `RoutingWebController.search()` の 3 分岐フローを箇条書きで文書化

#### 改善提案
- **【高】`CHANGELOG.md` — IT2 機能追加エントリが存在しない** → `[Unreleased]` または `[0.2.0]` セクション追加
- **【高】`routing/package-info.java` — Javadoc が皆無** → 最低限の責務説明を追記
- **【中】`Quote.java` の `issue()` — `@param`/`@throws` 欠落**（IT2_US01_review 未対応）
- **【中】`QuoteRestController` クラスレベル Javadoc が薄い** → エンドポイント概要とエラーコードを追記
- **【中】`BookingQueryPortAdapter.convertCargoType()` — private メソッドにコメントなし** → switch 各 case にマッピング意図を追記
- **【低】`quote/package-info.java` — 内容が「Quote（見積）コンテキスト。」のみ**

#### 懸念事項
- `CargoType` 命名不一致が OpenAPI/Swagger UI で混在露見するリスク
- `iteration_report-2.md` の E2E 未確認状態が README/CHANGELOG に未言及
- 422（ルートなし）と 503（外部サービス障害）の区別が API ドキュメントで不明瞭
</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 2 / 低: 1）</summary>

#### 評価サマリー
受入条件の形式的な達成度は高く、基本的なフローは動作する。ただし、実際の業務で毎日使う営業担当者・経路設計者の立場から見ると、**「UN/LOCODE の入力障壁」「再検索時のコンテキスト喪失」「見積の適合判定が時間経過で変わる」** の 3 点に業務上の深刻なリスクがある。

#### 良い点
- 見積番号の形式（Q-YYYYMMDD-XXXX）が電話口で伝えやすく、日付絞り込みと一致
- 直行便を「（直行）」と明示して「データ欠落か直行か」の混乱を回避
- 予約詳細から「この条件で見積を作成」で条件が引き継がれる（入力二度手間なし）
- ルート候補 0 件時に再検索フォームをその場に表示（別ページ往復なし）

#### 改善提案
- **【高】UN/LOCODE 入力 — 港名から候補を選べない** → 部分一致サジェストまたは「よく使う港コード一覧」リンクを追加
- **【高】再検索後に「予約詳細に戻る」リンクが消える** → `routing/search.html` の再検索フォームに `<input type="hidden" name="bookingId">` を追加
- **【高】`isOnTime()` 判定が「今日」基準で変わる** → 見積作成日時を `Quote` に持たせ、保存時の判定結果を永続化
- **【中】貨物種別が enum 名（`DANGEROUS_GOODS`）で表示される** → `displayName` に統一
- **【中】見積一覧にルート候補件数・適合状況列がない** → 有効ルート有無を一覧で確認できるよう改善
- **【低】概算料金の通貨が「円」固定** → 通貨コード（JPY/USD）を `RouteOption` に持たせる

#### 懸念事項
- Stub の経由港が入力と無関係な固定値（デモ時に東京発→東京経由という混乱が起きやすい）
- ルート候補の出発日が表示されていない（顧客の倉庫手配・通関スケジュール照合が不可）
- `RouteSearchService` 未設定時に説明なしリダイレクト（システム障害と誤認される）

#### スコープ外の発見（IT3 以降）
- 見積詳細から予約への動線がない（IT3 のルート割り当て画面設計と合わせて早期合意が必要）
- 見積の有効期限の概念がない（予約作成フロー前にビジネスルール確認が必要）
- 見積一覧のソート・検索機能がない（IT3 前に作成日降順ソートを推奨）
</details>

---

### 次のアクション（推奨優先順位）

#### IT2 残対応（現在の origin/java/take-1 に積む前に）

1. **H3 修正（即時）**: `routing/search.html` 再検索フォームに `bookingId` hidden field 追加 — 業務フロー断絶バグ
2. **H4 修正（即時）**: E2E テスト固定日付を相対日付に変更 — 時限爆弾の除去
3. **H7/H8 修正（即時）**: `CHANGELOG.md` 追記 + `routing/package-info.java` Javadoc 追加 — ドキュメント

#### IT3 開始前に実施（設計変更が必要なもの）

4. **H1/H5（H6 と連動）**: `RouteOption` の `Clock` DI 化 → `isOnTime` 単引数版テスト追加 → BVA 境界テスト追加
5. **H2**: `RoutingWebController.search()` の empty チェック順序修正
6. **H6**: ArchUnit A06/A07 追加（routing→booking・quote→routing 依存禁止）
7. **H9**: `QuoteCondition.equals()` を `compareTo()` に修正
8. **M1〜M4**: テスト補強・例外クラス格上げ

#### IT3 計画に組み込む（新規開発時に対処）

9. **M5/M11/UN/LOCODE サジェスト**: UI 改善は IT3 ストーリーとして追加検討
10. **L7**: ADR 作成（CargoType 3 コンテキスト分散の設計判断記録）
11. **Clock 標準化**: `@Bean Clock.systemDefaultZone()` を共通設定として整備

---

*レビュー実施: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative（並列実施）*
*統合: GitHub Copilot — developing-review スキル*
