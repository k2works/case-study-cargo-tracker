---
type: Design
title: "フロントエンドアーキテクチャ - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）"
description: "CQRS / Event Sourcing 版 Cargo Tracker のフロントエンドアーキテクチャ。React SPA で Command / Query を分けた API クライアントを持ち、投影の遅延を 202 Accepted の「反映中」として扱う UX とロール別・状態別の到達性を定める。"
tags: [design,architecture,frontend,react,cqrs]
status: draft
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T03:59:57Z }
---

# フロントエンドアーキテクチャ - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）

## 概要

国際貨物輸送管理システム（Cargo Tracker）のフロントエンドを、React による SPA として設計します。バックエンドは [Axon Framework 5 による CQRS / Event Sourcing のマイクロサービス](architecture_backend.md) で、API は `gatewayms` 経由の REST です。

フロントエンドの設計で CQRS / Event Sourcing に固有なのは 1 点だけです。**書き込みの結果が読み取りに反映されるまでに遅れがある**ことです。コマンドは集約の結果を待って応答しますが、一覧や詳細は投影テーブルを読むため、登録直後の画面には「まだ反映されていない」状態があります。本設計はこれを隠さず、API の `202 Accepted` を「反映中」として画面に出します。それ以外は参照元（`take-4`・`java-3`）の React 構成をそのまま採ります。

| 参照元 | 採るもの | 変えるもの |
| :--- | :--- | :--- |
| `tmp/take-4/docs/design/architecture_frontend.md` | React SPA、フィーチャー内の Command / Query 分離、結果整合性への UX 対応の選択肢 | 「202 + 後追い確認」を既定にし、SSE は採らない |
| `docs/article/source/java-3/docs/design/architecture_frontend.md` | プロジェクト構造、Container / Presentational、Custom Hooks、導線設計の原則（ロール別の作業入口）、無操作タイムアウト、認証ストア | — |

## アーキテクチャ決定

### レンダリング戦略

| 選択肢 | 判断 | 理由 |
| :--- | :--- | :--- |
| **SPA（React + Vite）** | 採用 | 業務画面は認証後の操作が中心で SEO 不要。参照元 2 つと同じ |
| SSR / SSG | 不採用 | 公開ページは認証不要の追跡照会 1 画面だけ。SPA 内の公開ルートで足りる |
| Thymeleaf + htmx | 不採用 | マイクロサービス構成では画面を出すサービスが要る。Gateway の後ろに SPA を置くほうが単純 |

### 状態管理

| 状態 | 手段 | 理由 |
| :--- | :--- | :--- |
| サーバー状態（一覧・詳細） | TanStack Query | キャッシュ・再取得・ポーリングを宣言的に書ける。結果整合の吸収に必要 |
| 認証状態（トークン・ロール） | Zustand（`sessionStorage` 永続化） | タブを閉じたら消える。無操作 15 分で警告、20 分で破棄 |
| フォーム状態 | React Hook Form + Zod | 入力検証をスキーマで持つ |
| 画面ローカル状態 | `useState` | グローバルにしない |

### スタイリング

Tailwind CSS。コンポーネントライブラリは shadcn/ui（Radix ベース）をコピーして使い、依存に持ちません。

## プロジェクト構造

```text
apps/frontend/
├── src/
│   ├── app/                        # ルーティング・レイアウト・認可ガード
│   │   ├── router.tsx
│   │   ├── layouts/{AppLayout, PublicLayout}.tsx
│   │   └── guards/RequireRole.tsx
│   ├── features/                   # BC ごとのフィーチャー
│   │   ├── auth/
│   │   ├── booking/
│   │   │   ├── api/
│   │   │   │   ├── commands.ts     # POST / PUT。CommandGateway に対応
│   │   │   │   ├── queries.ts      # GET。QueryGateway に対応
│   │   │   │   └── index.ts
│   │   │   ├── hooks/              # useBookCargo, useCargoSummaries, useCargoDetail ...
│   │   │   ├── components/         # Presentational
│   │   │   ├── pages/              # Container（ルートに対応）
│   │   │   └── types.ts            # API の応答型（OpenAPI から生成）
│   │   ├── routing/
│   │   ├── tracking/
│   │   ├── handling/
│   │   └── billing/
│   ├── shared/
│   │   ├── api/client.ts           # queryClient / commandClient
│   │   ├── api/pending.ts          # 202 の扱い・後追い確認
│   │   ├── components/             # Button, Table, StatusBadge, PendingBanner ...
│   │   ├── hooks/                  # useBusinessDate, useInactivityTimeout
│   │   └── lib/                    # 日付（業務タイムゾーン）、UN/LOCODE 表示名
│   └── main.tsx
├── e2e/                            # Playwright
└── vite.config.ts
```

フィーチャーは BC に対応します。フィーチャー間の依存は `shared` 経由だけに限り、`booking` が `tracking` のコンポーネントを直接 import しません。ESLint の `import/no-restricted-paths` で固定します。

## API 連携

### Command / Query の分離

バックエンドの CommandGateway / QueryGateway に対応して、クライアントも書き込みと読み取りを分けます。

```ts
// shared/api/client.ts
export const queryClient = {
  get: <T>(path: string) => request<T>(path),
};

export const commandClient = {
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
};
```

| 種別 | HTTP | 応答 | クライアント側の扱い |
| :--- | :--- | :--- | :--- |
| コマンド | `POST` / `PUT` | `201 Created` + 識別子、または `200 OK` | 成功したら関連する Query を `invalidateQueries`。**集約は更新済みだが投影はまだ**、と理解する |
| クエリ（一覧） | `GET` | `200 OK` | キャッシュ。`staleTime` は画面ごと |
| クエリ（詳細） | `GET` | `200 OK`、または **`202 Accepted`（投影がまだ無い）** | `202` なら「反映中」を表示し、短い間隔で再取得する |
| 認可エラー | `403` | — | 画面に「権限がありません」。入力検証より先に返る |
| 認証切れ | `401` | — | 認証ストアを破棄してログインへ |

### 結果整合性への UX 対応

| 場面 | 戦略 | 実装 |
| :--- | :--- | :--- |
| フォーム送信後に一覧へ戻る | invalidate + refetch | `useMutation.onSuccess` で `invalidateQueries`。一覧に**まだ出ていない行**があり得るため、送信直後は「登録しました。一覧への反映には数秒かかります」を出す |
| 登録直後に詳細へ遷移する | 202 + 後追い確認 | `POST` の応答の識別子で `GET` し、`202` の間は `PendingBanner` を出して 1 秒間隔で再取得。上限 30 秒で「反映に時間がかかっています」に切り替え、再読込ボタンを出す |
| 状態を進めるボタン（確定・承認） | 楽観的更新 | `onMutate` でボタンを無効化し状態バッジを次の値にする。`onError` で戻す。**遷移してよいかの判定は集約が持つ**ので、失敗は普通に起きる前提で戻し方を作る |
| 追跡照会・作業一覧 | ポーリング | `refetchInterval` 10 秒。画面が非表示なら止める |
| SSE / WebSocket | 採らない | 現時点で要件に無い。要るなら `trackingms` からの配信を ADR で起こす |

**表示は投影・判定は集約**の分業を画面でも守ります。ボタンの出し分けは投影の `status` を読みますが、押した結果は集約の判定に従います。投影が古くてボタンが出ていても、集約が拒否したら `409 Conflict` を「状態が変わっています。再読込してください」として扱います。

### 認証

| 項目 | 方針 |
| :--- | :--- |
| トークン | `authms` が発行する JWT を `Authorization: Bearer` で送る。保存先は `sessionStorage` |
| ロール | JWT のクレームから `Role[]` を取り出し、`RequireRole` でルートを守る |
| 無操作タイムアウト | 15 分で警告モーダル、20 分で認証ストアを破棄してログインへ |
| ログイン失敗 | 失敗理由を問わず同一メッセージ。ロック中も同じ（US31） |

## 画面構成

### 主要画面一覧

| 画面 | ルート | ロール | 主なクエリ / コマンド | 結果整合の扱い |
| :--- | :--- | :--- | :--- | :--- |
| ログイン | `/login` | 全員（未認証） | `LoginCommand` | — |
| ダッシュボード | `/` | 全ロール | ロール別の作業一覧 | ポーリング |
| 荷主一覧 / 登録 | `/shippers` | 営業 | `FindShipperQuery`, `RegisterShipperCommand` | 登録後 invalidate。メール重複は `projection_rejection` を作業一覧に出す |
| 見積作成 | `/quotations/new` | 営業 | `CreateQuotationCommand` | 202 + 後追い |
| 予約一覧 / 登録 / 詳細 | `/bookings`, `/bookings/new`, `/bookings/:id` | 営業、経路設計、追跡 | `FindCargoSummariesQuery`, `BookCargoCommand`, `ConfirmBookingCommand` ほか | 202 + 後追い、楽観的更新 |
| 経路設計作業一覧 | `/routing/worklist` | 経路設計 | `FindRoutingWorklistQuery` | ポーリング |
| 経路設計（候補選択） | `/routing/bookings/:id` | 経路設計 | `FindRouteCandidatesQuery`, `AssignRouteCommand` | 候補算出は同期 |
| 航海スケジュール一覧 / 登録 / 更新 | `/voyages` | 経路設計 | `FindVoyagesQuery`, `RegisterVoyageCommand` | invalidate |
| 追跡一覧 / 詳細 | `/tracking`, `/tracking/:trackingNumber` | 追跡、荷主（自社のみ） | `FindTrackingQuery`, `UpdateTransportStatusCommand` | ポーリング |
| 例外一覧 / 起票 / 解決 | `/tracking/exceptions` | 追跡 | `FindOpenExceptionsQuery` | 緊急を先頭。ポーリング |
| 荷役記録 | `/handling/new` | 荷役 | `RegisterHandlingActivityCommand` | 予定外の場所は登録前に警告（US28）。通関未済の引取は拒否理由を表示 |
| 通関申告一覧 / 登録 / 状態更新 | `/customs` | 荷役、追跡 | `FindHeldDeclarationsQuery`, `UpdateCustomsStatusCommand` | 留置 3 日超を強調 |
| キャンセル承認一覧 | `/bookings/cancellations` | 追跡 | `FindCancellationRequestsQuery`, `ApproveCancellationCommand` | ポーリング |
| 請求一覧 / 詳細 / 入金 | `/invoices`, `/invoices/:id` | 経理 | `FindInvoicesQuery`, `RecordPaymentCommand` | 期限超過は今日で判定 |
| 公開追跡照会 | `/track/:trackingNumber` | 認証不要 | `FindPublicTrackingQuery` | ポーリング |

### 導線設計の原則

| 原則 | 内容 |
| :--- | :--- |
| ロール別の作業入口 | 画面が受入基準を満たしていても、そのロールがダッシュボードと navbar から到達できなければ完成ではない。ロール × 画面の到達性を E2E で固定する |
| 状態軸の到達性 | 「その状態のレコードから次の操作へ行けるか」を確かめる。ボタンの出し分けは投影の `status` と集約の遷移表を同じ規則にする |
| 認証不要の入口 | 公開追跡照会はログイン画面とポータルにも導線を置く。認証済みの到達性検査では見つからない |
| 共有画面のリンクもロールで出し分ける | 予約詳細を経路設計者に開放したら、その画面の「一覧に戻る」も経路設計者が行ける一覧を指す |
| 気づく手段は次の行動へ繋ぐ | 件数・警告を出したら、そこから対象の画面へ行けるようにする |

## コンポーネント設計

Container（`pages/`）がデータ取得とコマンド送信を担い、Presentational（`components/`）は props だけを受けます。データ取得は Custom Hooks（`useCargoSummaries` など）に閉じ、Hooks が TanStack Query を包みます。

```tsx
// features/booking/hooks/useCargoDetail.ts
export function useCargoDetail(bookingId: string) {
  return useQuery({
    queryKey: ['booking', 'cargo', bookingId],
    queryFn: () => getCargoDetail(bookingId),          // 202 なら { pending: true } を返す
    refetchInterval: (query) => (query.state.data?.pending ? 1000 : false),
  });
}
```

```tsx
// features/booking/pages/CargoDetailPage.tsx
export function CargoDetailPage() {
  const { bookingId } = useParams();
  const { data, isLoading } = useCargoDetail(bookingId!);
  if (isLoading) return <Spinner />;
  if (data?.pending) return <PendingBanner message="登録内容を反映しています" />;
  return <CargoDetail cargo={data!.cargo} actions={<CargoActions cargo={data!.cargo} />} />;
}
```

## 日時の扱い

- 表示と入力は業務タイムゾーン（`Asia/Tokyo`）。`toISOString()` を直接使わず、`shared/lib/businessDate.ts` の関数を通す
- 期限（`arrivalDeadline`、`dueOn`）は日付。時刻付きの値と比べない
- E2E のテストデータも同じヘルパで作る。CI（UTC）で日付がずれる事故を防ぐ

## テスト戦略（概要）

| 層 | ツール | 対象 |
| :--- | :--- | :--- |
| ユニット | Vitest + Testing Library | Presentational、Hooks（`202` → `pending` の変換、ポーリング停止） |
| 統合 | Vitest + MSW | Container + API。**MSW のモックは本物より甘くしない**（型・日付形式を OpenAPI に合わせる） |
| E2E | Playwright | ロール × 画面の到達性、状態軸の到達性、公開追跡、投影の遅延を待つヘルパ |
| 型 | `tsc -b` | `tsc --noEmit` はプロジェクト参照構成では検査しない |

詳細は `test_strategy.md` で定めます。

## 技術スタック（調査時点 2026-09-02）

| 区分 | 技術 | 備考 |
| :--- | :--- | :--- |
| 言語 | TypeScript 5 系 | |
| UI | React 19 系 | |
| ビルド | Vite 6 系 | |
| ルーティング | React Router 7 系 | |
| サーバー状態 | TanStack Query 5 系 | |
| クライアント状態 | Zustand 5 系 | |
| フォーム | React Hook Form 7 系 + Zod | |
| スタイル | Tailwind CSS 4 系 + shadcn/ui | |
| API 型 | openapi-typescript | 各サービスの OpenAPI から生成 |
| テスト | Vitest、Testing Library、MSW、Playwright | |

確定は `tech_stack.md` で行います。

## 参照

- [バックエンドアーキテクチャ](architecture_backend.md)
- [ドメインモデル設計](domain-model.md)（`BookingStatus` / `TransportStatus` の遷移表）
- [ユーザーストーリー](../../requirements/user_story.md)
- [アーキテクチャ設計ガイド](../../reference/アーキテクチャ設計ガイド.md)
- 参照元：`tmp/take-4/docs/design/architecture_frontend.md`、[java-3 フロントエンドアーキテクチャ](../../article/source/java-3/docs/design/architecture_frontend.md)
