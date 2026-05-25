# ページネーション機能 マルチパースペクティブレビュー

実施日: 2026-05-25

## レビュー対象

IT2 で追加した「荷主・予約一覧へのページネーション機能」。

**対象コミット (4 件)**

- `14eac9b8` feat(bookingms): 荷主・予約一覧 API にページネーションを追加
- `efc8cd6f` feat(frontend): 荷主・予約一覧にページネーションを追加
- `db2f1236` docs(it2): ページネーション追加を進捗に反映
- `60144bc8` docs(adr): ADR-0008 ページネーション戦略を追加

**範囲**: 25 ファイル / +608 / -61。バックエンド (Mapper / Service / Controller / DTO) + フロントエンド (Pagination + ListPage + API) + ADR + 進捗ドキュメント。

## 総合評価

ADR-0008 のコンプライアンス (PageResponse 形状、サニタイズ規約、共通 Pagination、E2E 3 件) を概ね満たし、IT2 のスコープとしては健全。`Pagination` コンポーネントが presentational に徹し再利用性が高く、ユニット 8 件 + E2E 3 件で境界値・端数・無効化を網羅できている点は良質。

一方で **3 つの構造的負債** が残る:

1. サニタイズロジックが Controller × 2 + Service × 2 = 4 箇所に重複 (DRY 違反)
2. `ShipperController.find` の戻り型 `ResponseEntity<?>` がレスポンス型を消し、OpenAPI 生成と型安全性を犠牲にしている
3. 設計ドキュメント (`architecture_backend.md` / `domain-model.md` の `ListCargoSummariesQuery` / Mapper シグネチャ、`architecture_frontend.md` の React Query 採用宣言) と実装の **設計ドリフト** が ADR-0008 に未反映

これらは IT3 着手前に解消するか、ADR-0008 のフォローアップで明示することを強く推奨。

## 改善提案（重要度順）

### 高（マージ前 / IT3 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :---: | :--- | :--- | :--- | :--- |
| H1 | サニタイズロジックを `PageRequest.sanitize(page, size)` 値オブジェクト + `DEFAULT_PAGE_SIZE=20` / `MAX_PAGE_SIZE=200` 定数に集約し、Controller / Service の二重サニタイズを解消 | `CargoBookingController.java:109-114`, `ShipperController.java:93-94`, `CargoQueryService.java:30-31`, `ShipperQueryService.java:36-37` | programmer / architect | `Math.max(page,0)` / `Math.min(size,200)` が 4 箇所に重複。デフォルト値 (20 / 200) のハードコードも散在。サニタイズの境界 (Controller のみ or Service のみ) を一本化する |
| H2 | `GET /api/v1/shippers?email=` を `GET /api/v1/shippers/search?email=` 等に分離し、`GET /api/v1/shippers` の戻り型を `ResponseEntity<PageResponse<ShipperResponse>>` に固定する | `ShipperController.java:75-100` | programmer / architect / writer | `ResponseEntity<?>` でレスポンス型が `Object` に潰れる。OpenAPI 自動生成や `openapi-typescript` 連携時に型安全性が失われる。SRP に沿い、テストの `@SuppressWarnings("unchecked")` も不要に |
| H3 | ADR-0008 に「設計ドキュメントとの差分」セクションを追加 (Axon QueryHandler は未採用 / Mapper メソッドは `findAllPaged` 命名 / `status?` フィルタ未実装) もしくは `architecture_backend.md` (L760, L818) と `domain-model.md` (L1171) を実装に合わせて更新 | `docs/adr/0008-pagination-strategy.md`, `docs/design/architecture_backend.md`, `docs/design/domain-model.md` | architect / writer | 設計書 SSOT が古いまま放置されると、新規メンバーが Axon QueryHandler を実装してしまう |
| H4 | `architecture_frontend.md` の React Query 採用宣言と実装 (`useState`/`useEffect`) の乖離を ADR か frontend 設計書に明示 (例: 「IT3 以降で React Query 移行」と注記) | `docs/design/architecture_frontend.md:37,229,391`, `docs/adr/0008-pagination-strategy.md` | writer | 設計書本文と実装の乖離が ADR-0008 でも未言及。読者の認識ズレを生む |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :---: | :--- | :--- | :--- | :--- |
| M1 | 後方互換 `fetchBookings` / `fetchShippers` に `@deprecated` JSDoc を付与し、IT3 で削除する Issue/Story を `release_plan.md` に紐付ける | `bookingApi.ts:82-85`, `shipperApi.ts:68-71` | programmer / architect / writer | `size=200` で「全件取得のつもり」を装う実装。totalCount > 200 で **silently truncate** され呼び出し側が気づけない |
| M2 | `PageResponse<T>` 型をフロントエンドの共通モジュール (`src/shared/api/types.ts` 等) に抽出 | `bookingApi.ts`, `shipperApi.ts` | architect | 同一型が 2 ファイルに重複。IT3 で Voyage / Tracking / Handling / Invoice を追加する前に解消すべき |
| M3 | `CargoQueryService` / `ShipperQueryService` のクラスまたは `findAll` / `count` メソッドに `@Transactional(readOnly=true)` を付与 | `CargoQueryService.java`, `ShipperQueryService.java` | architect | ADR-0008 で「同一トランザクション内で発行」と宣言したが現状は別接続。登録レース時に `totalCount` と `items.length` の整合が崩れる可能性 |
| M4 | `cargo_summary` / `shipper` の `(created_at DESC)` インデックス戦略を `data-model.md` に追記し、対応する Flyway マイグレーションを追加 | `docs/design/data-model.md:421` ほか, Flyway | architect / writer | LIMIT/OFFSET + `ORDER BY created_at DESC` は無索引だと数千件規模で劣化 |
| M5 | `PAGE_SIZE = 20` を `apps/frontend/src/components/ui/Pagination.tsx` 近傍の共通定数 (`DEFAULT_PAGE_SIZE`) に抽出 | `BookingListPage.tsx:6`, `ShipperListPage.tsx:8` | programmer | 両 ListPage に同じ定数が散在 |
| M6 | `domain-model.md` (L1171) の `status?` フィルタが未実装。`ListCargoSummariesQuery` のステータス絞り込みを IT3 のタスクとして `iteration_plan-3.md` に記録 | `docs/design/domain-model.md:1171` | architect | 設計と実装のスコープ差分を負債として可視化 |
| M7 | `apps/frontend/README.md` (もしくは `architecture_frontend.md`) に共通 UI コンポーネント節を新設し、`Pagination` の `page`/`size`/`totalCount`/`onPageChange` の 0 始まり規約を明記 | (新規 README or `architecture_frontend.md`) | writer | フロントエンド共通コンポーネントが今後増えるため、利用方法ガイドが必要 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
| :---: | :--- | :--- | :--- | :--- |
| L1 | 引数なし `Mapper.findAll()` と `QueryService.findAll()` を撤去 (実利用箇所は後方互換ラッパのみ) | `CargoSummaryMapper.xml`, `ShipperMapper.xml`, `CargoQueryService.java`, `ShipperQueryService.java` | programmer / architect | デッドコード化したら削除し API 面を縮める |
| L2 | `PageResponse.of(...)` static factory が record の canonical constructor と二重 API。残すなら Javadoc に意図 (型推論補助等) を明記、不要なら削除 | `PageResponse.java` | architect | 二重 API は混乱を生む |
| L3 | `CargoSummaryMapper.xml:61` / `ShipperMapper.xml` の `SELECT *` を明示的なカラム列挙に変更 | Mapper XML 全般 | programmer | 将来のカラム追加で resultMap と乖離するリスク |
| L4 | ArchUnit テストを `bookingms/src/test` に追加し、`interfaces.rest.dto..` → `domain..` への参照禁止を機械的にガード | (新規 ArchUnit テスト) | architect | CQRS 構造の劣化を CI で検出できる |
| L5 | URL クエリパラメータ (`?page=`) で page state を URL に同期し、ブラウザ「戻る」で前のページに戻れるようにする | `BookingListPage.tsx`, `ShipperListPage.tsx` | programmer | 業務作業中断 → 復帰時の UX |
| L6 | `sessionStorage.getItem('token')` を直接読みに行く処理が `bookingApi.ts` / `shipperApi.ts` で重複。共通 `httpClient` への抽出を検討 | `bookingApi.ts`, `shipperApi.ts` | architect | IT3 で API 数が増える前に統一すると効率的 |

## 懸念事項

| # | 懸念 | 影響 | 指摘元 |
| :---: | :--- | :--- | :--- |
| C1 | `count()` が毎ページ呼び出される。テーブル拡大時に COUNT(*) が遅くなる | パフォーマンス | programmer |
| C2 | `ORDER BY created_at DESC` 固定で tiebreaker なし。同一秒に複数 INSERT で **OFFSET ベースのページ間重複/欠落** | データ整合性 | programmer |
| C3 | `useEffect` の page 変更高速連打で古いレスポンスが上書き (race condition) | UX | programmer |
| C4 | `size=200` を許可すると `cargo_summary` (幅広 Projection) で転送量が膨らむ。デフォルト 20 / 最大 100 程度に絞ることを次回検討 | パフォーマンス | architect |
| C5 | Read Model 反映遅延 × Offset の組合せで、新規登録直後の同一ページ再フェッチ時に古いリスト → インクリメント | UX | architect |

## 矛盾事項

各エージェントの指摘は補完関係にあり、明確な相反は検出されず。programmer と architect は「Controller / Service の二重サニタイズ」を共通指摘し、解消策の方向性 (定数集約 + 値オブジェクト化) も一致。

## スコープ外の発見

- `ShipperListPage.tsx` の page state は URL クエリ非同期で、ブラウザ「戻る」で page=0 に戻る (programmer)
- `CargoSummaryMapper.xml` の `SELECT *` (programmer)
- ArchUnit テスト自体が `bookingms/src/test` に存在しない (architect)
- 認証ヘッダ取得が `sessionStorage.getItem('token')` 直接読みで重複 (architect)
- `data-model.md` 全体での Flyway スクリプトと Index 戦略表の整合確認が未実施 (writer)
- `architecture_frontend.md` の状態管理章 (L246) で `useState`/`useReducer` 記載と L37 の React Query 記載が元々混在 (writer)

## エージェント別フィードバック詳細

### xp-programmer (高: 2 / 中: 2 / 低: 2)

**評価サマリー**: TDD サイクルが守られ、PageResponse / Pagination という再利用可能な抽象が導出されている良質な実装。ただし sanitize ロジックの三重複、`ResponseEntity<?>` による型安全性の喪失、`fetchBookings` 後方互換ラッパが items=200 を「全件」と誤認させかねない点に改善余地あり。

**良い点**:
- `PageResponse<T>` を Java の record で宣言し、`items` / `totalCount` / `page` / `size` の単一責任を簡潔に表現
- `Pagination.tsx` は presentational に徹し、`onPageChange` で状態を親に委ねる設計で再利用性が高い (DIP)
- `Pagination.test.tsx` が境界 (0 件・1 ページのみ・端数・最終ページ) を網羅し、TDD の Red を意図的に踏んだ痕跡が読み取れる
- Controller / Service / Mapper の各層に「ページネーション」のテストが順に追加され、インサイドアウト TDD が機能
- ADR-0008 で「offset/limit を採用、cursor は将来検討」と決定背景を残している規律

### xp-architect (高: 3 / 中: 4 / 低: 2)

**評価サマリー**: ADR-0008 のコンプライアンス基準 (サニタイズ、PageResponse 形状、共通 Pagination、E2E) を概ね満たし、IT2 のスコープとしては健全。ただし `domain-model.md` / `architecture_backend.md` で宣言した「`ListCargoSummariesQuery` を介する CQRS 経路」と「Mapper シグネチャ `findAll(offset, limit)`」との設計ドリフトが残り、変更容易性に小さな負債を生んでいる。

**主要指摘**:
- 設計ドリフト: `architecture_backend.md` (L760, L818) と `domain-model.md` (L1171) を実装に合わせて更新するか、ADR-0008 に差分を明記
- `ResponseEntity<?>` でレスポンス型が消失。`/shippers/search?email=` への分離を強く推奨
- `@Transactional(readOnly=true)` 不在で `count()` と `findAllPaged()` の整合性リスク
- フロントの `PageResponse<T>` 重複定義
- ArchUnit テスト未整備

### xp-tester (要約のみ)

xp-tester は関連ファイルリストを返したが、評価本文を出力する前にタスクが完了した。改めてレビュー依頼するか、programmer / architect の指摘で代替可能。

### xp-technical-writer (高: 2 / 中: 4 / 低: 2)

**評価サマリー**: ADR-0008 は背景・代替案・コンプライアンスが体系的に整理され、コード内コメントも要点を押さえた高品質な成果物。一方、ADR で記録された決定が**設計書本体に未反映**であり、SSOT として読む利用者には混乱が残る。

**主要指摘**:
- `architecture_backend.md` Mapper 例の更新 (`findAll` → `findAllPaged` + `countAll`)
- `architecture_frontend.md` の React Query 採用宣言と実装の乖離
- `domain-model.md` の `ListCargoSummariesQuery` 表記
- `data-model.md` の `created_at` Index 戦略追加
- `apps/frontend/README.md` の共通 UI コンポーネント節新設

### xp-user-representative

ユーザー代表のレビュー本文は出力されず、関連ファイルリストのみ返却された。業務視点 (1 ページ 20 件・並び順・URL 同期・モバイル対応等) の評価は未取得。再依頼を推奨。

## 推奨アクション

### 即対応 (H1〜H4)

| # | アクション | 工数目安 |
| :---: | :--- | :--- |
| H1 | `PageRequest.sanitize(page, size)` 値オブジェクト導入、定数 `DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE` 抽出 | 1h |
| H2 | `GET /shippers/search?email=` 分離、`ShipperController.find` の戻り型固定 | 1h |
| H3 | ADR-0008 に「設計ドキュメントとの差分」セクション追記 (もしくは設計書側を更新) | 0.5h |
| H4 | `architecture_frontend.md` に React Query 移行注記追加 | 0.5h |

### IT3 着手前にフォローアップ (M1〜M7)

タスク 6.8 として `iteration_plan-2.md` に追加し、IT3 計画の冒頭で消化する形が現実的。

### 別 ADR / 別 Issue 化を推奨 (L1〜L6, C1〜C5)

低優先度と懸念事項は `release_plan.md` のバックログに登録し、各 IT のスコープと相談しながら順次消化。

## 関連ドキュメント

- [ADR-0008 ページネーション戦略](../adr/0008-pagination-strategy.md)
- [バックエンドアーキテクチャ](../design/architecture_backend.md)
- [フロントエンドアーキテクチャ](../design/architecture_frontend.md)
- [ドメインモデル](../design/domain-model.md)
- [データモデル](../design/data-model.md)
- [IT2 イテレーション計画](../development/iteration_plan-2.md)
