# ADR-0008: 一覧 API にページネーション (Offset/Limit + PageResponse) を採用

荷主・予約をはじめとする一覧 API のレスポンスを `PageResponse<T>` 形式に統一し、Offset/Limit 型のページネーションを採用します。

日付: 2026-05-25

## ステータス

承認済み

## コンテキスト

IT2 で `GET /api/v1/shippers` と `GET /api/v1/bookings` を実装した段階では、Mapper の `findAll` が全件を一度に返す設計となっていました。同時に次の課題が顕在化していました。

- フロントエンド一覧画面 (S05 / S08) は全件をクライアントで保持するため、件数が増えると表示・送出量が線形に悪化します。
- 一覧 API のレスポンスが `List<T>` のままだと、件数・カーソル等のメタ情報をフロントに渡せず、UI 側でページネーションを実装する余地がありません。
- 設計ドキュメント (`docs/design/architecture_backend.md` の `findAll(offset, limit)`、`docs/design/domain-model.md` の `ListCargoSummariesQuery(offset, limit, status?)`) は既に Offset/Limit を前提とした記述になっており、実装側の整備が遅れていました。
- IT3 以降で扱う追跡一覧・荷役履歴・請求一覧・経路候補なども同じ一覧構造を持つため、ここで型・規約を固める必要があります。

これらにより、IT2 のうちに一覧 API のページネーション戦略を確定し、Read Model の検索系全体で適用できる共通形式に揃えることが要件になりました。

## 決定

一覧 API は **Offset/Limit 型ページネーション** を採用し、レスポンス形式は次の共通 `PageResponse<T>` に統一します。

```json
{
  "items": [...],
  "totalCount": 47,
  "page": 0,
  "size": 20
}
```

- クエリパラメータは `?page=<int, 0 始まり>&size=<int>` の 2 つに統一します。
- `size` の既定値は **20**、上限は **200** にサーバー側でサニタイズします (`Math.min(size, 200)` / 0 以下は既定値)。
- `page` が負数の場合は 0 にサニタイズします。
- フィルタが必要なエンドポイント (例: `GET /shippers?email=`) は、フィルタ指定時のみ従来の `List<T>` 形式を維持してよいものとします (重複検出など、件数が極めて少ない問い合わせ専用)。
- Mapper では `findAllPaged(offset, limit)` と `countAll()` のペアで実装し、`countAll()` は同一トランザクション内で発行します。
- フロントエンドは共通 `Pagination` コンポーネント (前/次・件数表示) を経由してページ操作を行います。

### 変更箇所

- `apps/backend/bookingms/src/main/java/com/example/bookingms/interfaces/rest/dto/PageResponse.java` (新規)
- `apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/repositories/mybatis/ShipperMapper.java`
- `apps/backend/bookingms/src/main/java/com/example/bookingms/infrastructure/repositories/mybatis/CargoSummaryMapper.java`
- `apps/backend/bookingms/src/main/resources/mapper/ShipperMapper.xml`
- `apps/backend/bookingms/src/main/resources/mapper/CargoSummaryMapper.xml`
- `apps/backend/bookingms/src/main/java/com/example/bookingms/application/ShipperQueryService.java`
- `apps/backend/bookingms/src/main/java/com/example/bookingms/application/CargoQueryService.java`
- `apps/backend/bookingms/src/main/java/com/example/bookingms/interfaces/rest/ShipperController.java`
- `apps/backend/bookingms/src/main/java/com/example/bookingms/interfaces/rest/CargoBookingController.java`
- `apps/frontend/src/components/ui/Pagination.tsx` (新規)
- `apps/frontend/src/features/booking/api/bookingApi.ts` (`fetchBookingsPage` 追加)
- `apps/frontend/src/features/shipper/api/shipperApi.ts` (`fetchShippersPage` 追加)
- `apps/frontend/src/features/booking/pages/BookingListPage.tsx`
- `apps/frontend/src/features/shipper/pages/ShipperListPage.tsx`

### 代替案

- 代替案 1: Cursor (Keyset) ベースのページネーション
  却下理由: Read Model は `created_at DESC` の単一ソート順で十分であり、Offset/Limit でユースケースを満たせます。Cursor は実装コストとフロント側の状態管理コストが大きく、IT2 のスコープと釣り合いません。万件規模のテーブル (例: `tracking_event`) で必要になった時点で個別に再評価します。
- 代替案 2: Spring Data `Page<T>` をそのままレスポンスにする
  却下理由: Spring Data の `Page` は `pageable` 等の付随情報を含み、契約 (JSON 構造) が安定しません。フロントの型定義も複雑になるため、最小限の `{ items, totalCount, page, size }` に絞った専用 DTO を用意します。
- 代替案 3: GraphQL `connection` 風 (edges/pageInfo) を採用
  却下理由: REST + OpenAPI 路線 (`docs/design/architecture_backend.md`、`tech_stack.md`) と整合しません。フロントの型生成ツール (`openapi-typescript`) との親和性も低下します。
- 代替案 4: クライアント側の `Array.slice` だけで分割表示
  却下理由: 全件取得の問題 (転送量・メモリ) が解決されないため、本質的な解決になりません。
- 代替案 5: `?limit=&offset=` を直接公開する
  却下理由: 内部実装が Offset/Limit である事実は隠蔽し、`page` 単位の素朴な API にした方がフロントの URL/状態管理がシンプルになります。サーバー側で `offset = page * size` を計算します。

## 影響

### ポジティブ

- 一覧 API の転送量・メモリ使用量が件数に依存しなくなります (`size` で上限制御)。
- 件数表示・前後ボタンを共通 `Pagination` で実装できるため、新しい一覧画面の追加コストが下がります。
- レスポンス契約が `PageResponse<T>` に統一されるため、フロントエンドの型定義と OpenAPI 生成が機械的に行えます。
- 入力サニタイズ (`page<0` → 0、`size<=0` → 20、`size>200` → 200) を 1 箇所に集約でき、防御層が薄くなります。

### ネガティブ

- `GET /api/v1/bookings` / `GET /api/v1/shippers` のレスポンス形式が `List<T>` から `PageResponse<T>` に **破壊的変更** されました。フロントエンドは `fetchBookingsPage` / `fetchShippersPage` に移行する必要があり、既存の `fetchBookings` / `fetchShippers` は当面 `items` 抽出版として残しています (将来削除予定)。
- 結果整合性 (Read Model 反映遅延) と Offset の相互作用で、新規挿入直後に同一ページを再フェッチした際に並びがズレる可能性があります。`ORDER BY created_at DESC` は決定的ですが、複数並行登録時は要注意です。
- `LIMIT/OFFSET` は大きな `page` 値で性能が劣化します。将来 100 万行を超える Read Model (例: `tracking_event`) では Cursor 型への切り替えを別 ADR で検討します。
- `GET /api/v1/shippers/search?email=` を独立エンドポイントに分離しました (レビュー H2 への対応、2026-05-25 更新)。`GET /api/v1/shippers` は `PageResponse<ShipperResponse>` 固定型を返し、重複検出は `/search` で `List<ShipperResponse>` を返します。

## 設計ドキュメントとの差分

`docs/design/` の既存記述と本 ADR の実装には以下の差分があります。IT3 で設計書側を更新するまでの間は、本 ADR が **Single Source of Truth** として優先されます。

| 設計書 | 既存記述 | 実装 | 扱い |
| :--- | :--- | :--- | :--- |
| `architecture_backend.md` (L754-760, L818) | `Mapper.findAll(offset, limit)` 単独 + `@QueryHandler handle(ListCargoSummariesQuery)` を想定 | `findAllPaged(offset, limit)` + `countAll()` のペア。`@QueryHandler` は未採用で Controller → QueryService → Mapper の直行 | 本 ADR を優先。IT3 で設計書を更新 |
| `architecture_backend.md` Mapper 例 | レスポンスが `CargoSummary` の `List<T>` | `PageResponse<T>` に変更 | 同上 |
| `architecture_frontend.md` (L37, L229, L391) | React Query (TanStack Query) を採用宣言 | IT2 では `useState` + `useEffect` で実装 (素の React) | IT3 以降で React Query 移行を別タスクとして計画。Pagination コンポーネントは React Query 化しても再利用可能な設計 |
| `domain-model.md` (L1171) | `ListCargoSummariesQuery(offset, limit, status?)` の `status?` フィルタを記述 | `status?` フィルタは未実装 | IT3 以降の絞り込み機能で実装。`PageRequest` を拡張して対応予定 |
| `data-model.md` | `cargo_summary` / `shipper` に `(created_at DESC)` インデックスが未記載 | LIMIT/OFFSET + `ORDER BY created_at DESC` をクエリで使用 | IT3 で Flyway マイグレーションを追加し、`data-model.md` を更新 |

これらは Negative Impact (技術的負債) として認識しており、フォローアップタスクは `docs/development/iteration_plan-3.md` (作成時) で消化します。

## コンプライアンス

次を満たすことで、決定の実装完了を確認します。

- `GET /api/v1/shippers?page=0&size=20` と `GET /api/v1/bookings?page=0&size=20` が `{ items, totalCount, page, size }` 形式の JSON を返すこと。
- `?page=-1&size=0` 等の無効値で 200 OK を返し、`page=0, size=20` にサニタイズされたレスポンスとなること (ControllerTest で検証)。
- サニタイズロジックは `PageRequest` (record) に集約されており、Controller / Service / Mapper のいずれにも重複していないこと (レビュー H1 対応)。
- `GET /api/v1/shippers/search?email=` がメール検索専用エンドポイントとして `List<ShipperResponse>` を返し、`GET /api/v1/shippers` の戻り型が `PageResponse<ShipperResponse>` に固定されていること (レビュー H2 対応)。
- `CargoQueryService` / `ShipperQueryService` に `@Transactional(readOnly = true)` が付与され、`findAll` と `count` が同一トランザクションで実行されること。
- `Pagination` コンポーネントが「前へ」「次へ」と「X-Y / Z 件」表示を提供し、最初/最後で前後ボタンが無効化されること (ユニットテスト 8 件で検証)。
- ShipperListPage / BookingListPage で「次へ」をクリックすると、対応する API へ `page=1` が渡ること (ユニットテスト各 1 件)。
- E2E `booking.spec.ts` の「ページネーション E2E」3 件が PASS すること。

## 備考

- 著者: Claude
- 関連コミット: `14eac9b8`, `efc8cd6f`, `db2f1236`
- 関連 ADR: ADR-0002 (MyBatis 採用)
- 関連ドキュメント: `docs/design/architecture_backend.md` (Projection 設計)、`docs/design/domain-model.md` (`ListCargoSummariesQuery` の `offset/limit`)
