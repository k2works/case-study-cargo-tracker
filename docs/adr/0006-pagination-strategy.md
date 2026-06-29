# 0006 一覧 Repository API のページネーション戦略

`findAllCargos` / `findAllVoyages` / `findAllShippers` / `findAllEstimates` 等の一覧取得 API を、ページネーション必須化 + 検索フィルタ拡張に対応した API に段階移行する規約

日付: 2026-06-29

## ステータス

2026-06-29 提案 (IT3 L-11 で起票、IT4 Phase 1 で実装着手)

## コンテキスト

IT2 マルチパースペクティブレビュー L-11 / L-12 で以下が指摘された。

* 一覧 Repository (`findAllCargos` 等) が `m [Cargo]` を返し、SQL 側で `LIMIT 100` のハードコード
* Application 層 / Views は件数上限を意識せず、50 件超で運用に支障が出る
* 検索・ソート・フィルタの拡張余地が API に組み込まれていない

IT3 では `BookingListView` に件数表示と上限警告を最小実装した (L-12 commit ad2549ae) が、根本対応は API 側の改修が必要。本 ADR で段階移行戦略を確定する。

## 検討した選択肢

| 案 | 内容 | 評価 |
| :--- | :--- | :--- |
| A. 現状維持 (`[a]` 返却 + LIMIT 100) | 簡単。50 件以下のデモ環境では問題なし | 実運用で破綻 (情報欠落)、運用者が気付けない |
| B. `m [a]` のまま LIMIT を引数化 | `findAllCargos :: Int -> m [a]` | 残ページ判定ができない (合計件数 / has-next が見えない) |
| C. ページネーション結果型を導入 (本 ADR の採用案) | `findCargosPaged :: PageReq -> m (Page Cargo)` | 業界標準 (cursor / offset / page など) を統合できる |
| D. Stream / Conduit ベース | `findAllCargos :: Stream Cargo` | UI 用途には過剰、SQL での LIMIT 制御が難しい |

案 C を採用。

## 決定

### 規約 PG-01: 一覧 Repository は `Page a` を返す

```haskell
-- 共通: Cargotracker.Shared.Application.Pagination
data PageReq = PageReq
  { pageOffset :: !Int  -- 0..
  , pageLimit  :: !Int  -- 1..200 (上限は規約 PG-04)
  } deriving stock (Eq, Show)

data Page a = Page
  { pageItems  :: ![a]
  , pageTotal  :: !Int  -- 全件数 (フィルタ後)
  , pageOffset :: !Int  -- echo back
  , pageLimit  :: !Int  -- echo back
  } deriving stock (Eq, Show)

hasNextPage :: Page a -> Bool
hasNextPage p = pageOffset p + length (pageItems p) < pageTotal p
```

### 規約 PG-02: 検索フィルタは別途 `Criteria` 型で受ける

```haskell
-- 例: Booking
data BookingSearchCriteria = BookingSearchCriteria
  { criteriaShipperRef :: !(Maybe ShipperRef)
  , criteriaStatus     :: !(Maybe BookingStatus)
  , criteriaOrigin     :: !(Maybe UnLocode)
  , criteriaDestination:: !(Maybe UnLocode)
  } deriving stock (Eq, Show)

emptyBookingCriteria :: BookingSearchCriteria

findCargosPaged :: BookingSearchCriteria -> PageReq -> m (Page Cargo)
```

`emptyBookingCriteria` を渡せば現状の `findAllCargos` と同等の挙動。

### 規約 PG-03: 既存 `findAll*` は段階削除

* **Phase 1** (IT4): 新 API `findCargosPaged` / `findVoyagesPaged` 等を追加。既存 `findAllCargos` は `findCargosPaged emptyCriteria (PageReq 0 100)` の薄いラッパとして残し、`{-# DEPRECATED #-}` を付与
* **Phase 2** (IT5): View / Handler を新 API に移行。`findAllCargos` を呼ぶ箇所が 0 になったら Port から削除
* **Phase 3** (IT6+): 必要に応じて cursor ベースの API (`findCargosCursor :: BookingSearchCriteria -> CursorReq -> m (Cursor Cargo)`) を追加検討

### 規約 PG-04: ページサイズ上限と既定値

| パラメータ | 既定値 | 上限 |
| :--- | ---: | ---: |
| `pageLimit` (未指定時) | 25 | 200 |
| `pageOffset` | 0 | (なし、ただし要件次第で cursor 化を検討) |

上限を超えた `pageLimit` は Application 層で `clamp 200` する。DOS 防止のため URL クエリで `?limit=10000` を指定されても 200 で打ち切る。

### 規約 PG-05: View 側の表示規約

* 「表示中 N 件 / 全 M 件 (offset: X)」を常時表示
* `hasNextPage` 真のときに「次へ」リンクを表示
* `pageOffset > 0` のときに「前へ」リンクを表示
* L-12 (IT3 最小実装) の「上限件数到達 warning」は段階廃止 (Phase 2 で View 移行と同時に削除)

## 段階移行計画

| 段階 | タイミング | 内容 |
| :--- | :--- | :--- |
| Phase 0 (本 IT3) | 2026-06-29 | 本 ADR で規約確定、`Shared.Application.Pagination` 型のドラフト |
| Phase 1 | IT4 | 各 BC の Repository に `findCargosPaged` / `findVoyagesPaged` を追加、既存 `findAll*` は DEPRECATED 化 |
| Phase 2 | IT5 | View / Handler を新 API に移行、`findAll*` を Port から削除 |
| Phase 3 | IT6+ (任意) | 大規模データ向け cursor API、検索フィルタの GraphQL 化検討 |

## 影響

### 影響を受けるモジュール (Phase 1)

| 層 | モジュール | 変更 |
| :--- | :--- | :--- |
| Shared | `Cargotracker.Shared.Application.Pagination` (新規) | `PageReq` / `Page` 型 + ヘルパ |
| Booking Application | `Booking.Application.Ports` (BookingRepository) | `findCargosPaged` 追加 / 既存 `findAllCargos` DEPRECATED |
| Booking Application | `Booking.Application.Query.SearchBookings` (新規 Use Case) | `BookingSearchCriteria` を組み立てて Port 呼び出し |
| Booking Infrastructure | `PostgresBookingRepository` | LIMIT を `pageLimit`、`OFFSET pageOffset`、`SELECT COUNT(*) OVER ()` で `pageTotal` も同時取得 |
| Booking Views | `BookingListView` | `Page Cargo` を受け、件数表示と「次へ / 前へ」リンクを描画 |
| (同様に) Routing / Shipper / Estimation | 各 Port / Repo / View | 同パターンで段階移行 |

### CI / arch-check への影響

* `findAll*` を Port から削除した時点で、依存している Handler / View / テストが網羅的に検出される (孤立した呼び出し残しがあればコンパイルエラー)
* arch-check / Rule 4 への影響なし

### ロールバック

* Phase 1 のロールバックは新 API を削除して既存 `findAll*` の DEPRECATED を外せば完了 (View / Handler は無変更)
* Phase 2 以降は段階的な PR でロールバック可

## 関連 ADR

* [ADR-0002](0002-arch-check-implementation.md) - 依存方向規約と相互運用
* [ADR-0004](0004-cross-bc-shipper-ref.md) - `BookingSearchCriteria.criteriaShipperRef` で `ShipperRef` を使う

## 参照

* [IT2 マルチパースペクティブレビュー](../review/it2_code_review_20260627.md) L-11 / L-12
* [イテレーション 3 計画](../development/iteration_plan-3.md) L-12 (IT3 最小実装) / IT4 への繰越
* [v0.1.0-alpha リリースノート](../release/v0.1.0-alpha.md) §既知の制約
