---
type: Design
title: "フロントエンドアーキテクチャ - 国際貨物輸送管理システム（CQRS / Event Sourcing 版）"
description: "CQRS / Event Sourcing 版 Cargo Tracker のフロントエンドアーキテクチャ。React SPA で Command / Query を分けた API クライアントを持ち、投影の遅延を 202 Accepted の「反映中」として扱う UX とロール別・状態別の到達性を定める。"
tags: [design,architecture,frontend,react,cqrs]
status: draft
generated: { by: claude-code/claude-fable-5-1, at: 2026-09-02T07:46:35Z }
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
| 認証状態（トークン・ロール） | Zustand（`sessionStorage` 永続化） | タブを閉じたら消える。無操作 15 分で警告、20 分で破棄（荷役ロールの画面は 60 分） |
| 受付済みで未反映の識別子 | `sessionStorage`（`shared/api/pending.ts`） | コマンド応答の識別子と入力値を持ち、一覧に「反映中」の行を差し込む。投影に出たら消す |
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
│   │   │   ├── hooks/              # useBookCargo, useCargoSummaries, useCargoDetail, useShipperBookings ...
│   │   │   ├── components/         # Presentational
│   │   │   ├── pages/              # Container（ルートに対応）
│   │   │   └── types.ts            # API の応答型（OpenAPI から生成）
│   │   ├── routing/
│   │   ├── tracking/
│   │   ├── handling/
│   │   └── billing/
│   ├── shared/
│   │   ├── api/client.ts           # queryClient / commandClient
│   │   ├── api/pending.ts          # 202 の扱い・後追い確認・受付済み行の差し込み
│   │   ├── api/lag.ts              # Gateway が返す投影の遅れ指標（ヘッダの案内・待ち上限の切り替え）
│   │   ├── components/             # Button, Table, StatusBadge, PendingBanner, ConflictAlert ...
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
| クエリ（詳細） | `GET` | `200 OK`、または **`202 Accepted`（投影がまだ無い）** | `202` なら「反映中」を表示し、短い間隔で再取得する。上限は 30 秒 |
| 状態の競合 | `409 Conflict` | 本文に `lastEvent {action, actor, at}` と `allowedActions[]` | 「田中さんが 14:05 に『経路設計へ戻す』を実行しました。再読込すると『経路設計へ』が押せます」と出す（`ConflictAlert`、`role="alert"`） |
| 業務規則違反 | `422 Unprocessable Entity` | 本文に理由。通関未済の引取は `customsStatus` と `customsStatusAsOf` | 理由をそのまま出す。判定時点があれば「直近で変わった可能性があります」と再確認ボタン |
| 認可エラー | `403` | — | 画面に「権限がありません」。入力検証より先に返る |
| 認証切れ | `401` | — | 認証ストアを破棄してログインへ |

### 結果整合性への UX 対応

| 場面 | 戦略 | 実装 |
| :--- | :--- | :--- |
| フォーム送信後に一覧へ戻る | invalidate + 受付済み行の差し込み | `useMutation.onSuccess` で `invalidateQueries` し、応答の識別子と入力値から**「反映中」バッジ付きの行を一覧の先頭に差し込む**（`pending.ts`。`sessionStorage` に持つ）。投影に同じ識別子の行が出たら差し替える。上部の案内「登録しました。一覧への反映には数秒かかります」は置き換わるまで出す |
| 登録直後に詳細へ遷移する | 202 + 後追い確認 | `POST` の応答の識別子で `GET` し、`202` の間は `PendingBanner` を出して 1 秒間隔で再取得。**上限 30 秒**で「登録内容が一覧に出るまで少し時間がかかっています（登録自体は受け付けています）」に切り替え、受付内容と再読込ボタンを出す。**ヘッダの遅れ表示中は最初の `202` で切り替える** |
| 状態を進めるボタン | 適用基準（次表）に従う | 単独作業は楽観的更新（`onMutate` で状態バッジを次の値にし `onError` で戻す）。複数ロールが触る集約の遷移は「送信中…」→ コマンド応答で確定表示。どちらもボタンは `aria-disabled`（`disabled` にしない）。**遷移してよいかの判定は集約が持つ**ので、失敗は普通に起きる前提で戻し方を作る |
| 荷役記録（連続記録） | 待たない | コマンド応答 `201` で「送信済み」に積み、投影を待たない。`activityId`（クライアント生成 UUID）を冪等キーにし、未応答分は再開時に再送する |
| 追跡照会・作業一覧 | ポーリング | `refetchInterval` 10 秒。画面が非表示なら止める |
| 投影の遅れが長引く | ヘッダの案内 | Gateway が返す遅れ指標（`lag.ts`、各 Processing Group の遅れの最大値）が閾値（30 秒）を超え続けたら、全画面のヘッダ直下に「登録内容が一覧に出るまで少し時間がかかっています（登録自体は受け付けています）。業務はそのまま進めてかまいません」を出す。一度出したら最低 5 分は出す（点滅させない）。ダッシュボードの件数は投影から数え、反映中は別枠 |
| SSE / WebSocket | 採らない | 現時点で要件に無い。要るなら `trackingms` からの配信を ADR で起こす |

#### 楽観的更新の適用基準

| 操作の性質 | 表示 | 該当する操作 |
| :--- | :--- | :--- |
| 単独作業（同じ集約を同時に触るロールが他に無い） | 楽観的更新 | 荷役記録、例外の起票・対応開始・解決、状態の手動更新、航海の登録・更新 |
| 複数ロールが触る集約の遷移 | 「送信中…」→ 確定表示 | S22 の確定 / 経路設計へ戻す / 荷主へ通知 / キャンセル申請、S23 の承認・却下、S31 の経路確定、S53 の通関状態更新、S61 の入金記録・取消 |

**表示は投影・判定は集約**の分業を画面でも守ります。ボタンの出し分けは投影の `status` を読みますが、押した結果は集約の判定に従います。投影が古くてボタンが出ていても、集約が拒否したら `409 Conflict` の本文（`lastEvent`・`allowedActions`）から「誰が・いつ・何を」と「再読込後に押せる操作」を出します。「状態が変わっています」だけで終わらせません。

### 認証

| 項目 | 方針 |
| :--- | :--- |
| トークン | `authms` が発行する JWT を `Authorization: Bearer` で送る。保存先は `sessionStorage` |
| ロール | JWT のクレームから `Role[]` を取り出し、`RequireRole` でルートを守る |
| 無操作タイムアウト | 15 分で警告モーダル、20 分で認証ストアを破棄してログインへ。荷役ロールの画面（`/handling/*`、`/customs/*`）は 60 分 |
| ログアウト | ヘッダの `[ログアウト]` → `/logout`。認証ストアと `sessionStorage` を破棄し、authms に記録してからログインへ。業務画面は `Cache-Control: no-store`、ブラウザバックは `RequireRole` が弾く（US27） |
| ログイン失敗 | 失敗理由を問わず同一メッセージ。ロック中も同じ（US31） |

## 画面構成

### 主要画面一覧

| 画面 | ルート | ロール | 主なクエリ / コマンド | 結果整合の扱い |
| :--- | :--- | :--- | :--- | :--- |
| ログイン | `/login` | 全員（未認証） | `LoginCommand` | — |
| ログアウト | `/logout` | 全ロール（ヘッダから） | `LogoutCommand` | — |
| ダッシュボード | `/` | 全ロール | ロール別の作業一覧。荷役はモバイル幅（本日入港予定の航海 / 当港で引取待ち） | ポーリング。件数は投影、反映中は別枠 |
| 荷主一覧 / 登録 | `/shippers` | 営業 | `FindShipperQuery`, `RegisterShipperCommand` | 登録後 invalidate + 反映中の行を差し込む。メール重複は `attention_item` を要確認一覧（`/worklist/attention`）に出し、「修正して再登録」で受付内容入りの登録画面へ |
| 見積作成 | `/quotations/new` | 営業 | `CreateQuotationCommand` | 202 + 後追い |
| 予約一覧 / 登録 / 詳細 | `/bookings`, `/bookings/new`, `/bookings/:id` | 営業、経路設計、追跡 | `FindCargoSummariesQuery`, `BookCargoCommand`, `ConfirmBookingCommand` ほか | 202 + 後追い。状態遷移は送信中表示。誤配バナーと通知履歴（`ShipperNotifiedEvent`） |
| 経路設計作業一覧 | `/routing/worklist` | 経路設計 | `FindRoutingWorklistQuery` | ポーリング |
| 経路設計（候補選択） | `/routing/bookings/:id` | 経路設計 | `GET /api/v1/routing/route-candidates`（`departFrom` 指定で誤配の再設計。`overdueDays` を含む）, `AssignRouteCommand` | 候補算出は同期。確定は送信中表示 |
| 航海スケジュール一覧 / 登録 / 更新 | `/voyages` | 経路設計 | `FindVoyagesQuery`, `RegisterVoyageCommand` | invalidate |
| 追跡一覧 / 詳細 | `/tracking`, `/tracking/:trackingNumber` | 追跡、荷主（自社のみ） | `FindTrackingQuery`, `UpdateTransportStatusCommand` | ポーリング。手動更新は楽観的更新 |
| 自社予約一覧 / 進み具合 | `/shipper/bookings`, `/shipper/bookings/:id` | 荷主 | `FindShipperBookingsQuery`, `FindShipperBookingProgressQuery` | ポーリング。状態・進み具合・確定旅程・通知履歴のみ（金額・社内メモ無し） |
| 例外一覧 / 起票 / 解決 | `/tracking/exceptions` | 追跡 | `FindOpenExceptionsQuery` | 緊急を先頭。ポーリング |
| 荷役記録（航海起点・連続記録） | `/handling/voyages/:voyageNumber` | 荷役 | `FindCargosOnVoyageQuery`, `RegisterHandlingActivityCommand`（`activityId` 冪等キー）, `VoidHandlingActivityCommand` | 投影を待たない。予定外の場所は場所選択直後にインライン警告（US28）。通関未済の引取は判定時点つきで拒否し再確認ボタン |
| 通関申告一覧 / 登録 / 状態更新 | `/customs` | 荷役、追跡 | `FindHeldDeclarationsQuery`, `UpdateCustomsStatusCommand` | 留置 3 営業日超を強調。状態更新は送信中表示 |
| キャンセル承認一覧 | `/bookings/cancellations` | 追跡 | `FindCancellationRequestsQuery`, `ApproveCancellationCommand` | ポーリング。承認・却下は送信中表示。陸揚げ地は現在地または残りの寄港地 |
| 請求一覧 / 詳細 / 入金 | `/invoices`, `/invoices/:id` | 経理 | `FindInvoicesQuery`, `RecordPaymentCommand` | 期限超過は今日で判定。入金記録・取消は送信中表示。見積時概算との差額と理由 |
| 自社請求書 | `/shipper/invoices/:id` | 荷主 | `FindShipperInvoiceQuery` | ポーリング。自社分のみ |
| 要確認一覧 | `/worklist/attention` | 営業、経理、追跡 | `FindAttentionItemsQuery` | 既定は自ロール宛。3 日超を強調 |
| 公開追跡照会 | `/track/:trackingNumber` | 認証不要 | `FindPublicTrackingQuery` | ポーリング。見つからないときは案内と問い合わせの出口。連打は Gateway で `429` |

### 導線設計の原則

| 原則 | 内容 |
| :--- | :--- |
| ロール別の作業入口 | 画面が受入基準を満たしていても、そのロールがダッシュボードと navbar から到達できなければ完成ではない。ロール × 画面の到達性を E2E で固定する |
| 状態軸の到達性 | 「その状態のレコードから次の操作へ行けるか」を確かめる。ボタンの出し分けは投影の `status` と集約の遷移表を同じ規則にする |
| 認証不要の入口 | 公開追跡照会はログイン画面とポータルにも導線を置く。認証済みの到達性検査では見つからない |
| 共有画面のリンクもロールで出し分ける | 予約詳細を経路設計者に開放したら、その画面の「一覧に戻る」も経路設計者が行ける一覧を指す |
| 気づく手段は次の行動へ繋ぐ | 件数・警告を出したら、そこから対象の画面へ行けるようにする |
| 荷役はモバイル幅で航海起点 | 荷役ロールはサイドナビでなく下部タブ。ダッシュボードで航海を選び、スキャンで貨物を特定し、同じフォームに戻って連続記録する |

## コンポーネント設計

Container（`pages/`）がデータ取得とコマンド送信を担い、Presentational（`components/`）は props だけを受けます。データ取得は Custom Hooks（`useCargoSummaries` など）に閉じ、Hooks が TanStack Query を包みます。

```tsx
// features/booking/hooks/useCargoDetail.ts
const PENDING_POLL_MS = 1_000;
const PENDING_LIMIT_MS = 30_000;

export function useCargoDetail(bookingId: string) {
  const startedAt = useRef(Date.now());
  const { lagging } = useProjectionLag();                 // shared/api/lag.ts。ヘッダの遅れ表示と同じ出典
  const query = useQuery({
    queryKey: ['booking', 'cargo', bookingId],
    queryFn: () => getCargoDetail(bookingId),             // 202 なら { pending: true } を返す
    refetchInterval: (q) => {
      if (!q.state.data?.pending) return false;           // 200 が返ったら止める
      if (lagging) return false;                          // 遅れ表示中は待たずに案内へ切り替える
      return Date.now() - startedAt.current < PENDING_LIMIT_MS ? PENDING_POLL_MS : false;
    },
  });
  const elapsedMs = Date.now() - startedAt.current;
  const timedOut = !!query.data?.pending && (lagging || elapsedMs >= PENDING_LIMIT_MS);
  return { ...query, elapsedMs, timedOut };
}
```

```tsx
// features/booking/pages/CargoDetailPage.tsx
export function CargoDetailPage() {
  const { bookingId } = useParams();
  const { data, isLoading, elapsedMs, timedOut, refetch } = useCargoDetail(bookingId!);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (data && !data.pending) heading.current?.focus(); }, [data]);  // 反映中 → 表示でフォーカスを見出しへ
  if (isLoading) return <Spinner />;
  if (data?.pending) {
    return timedOut
      ? <PendingBanner
          message="登録内容が一覧に出るまで少し時間がかかっています（登録自体は受け付けています）"
          accepted={data.accepted}                           // 受付内容（予約番号と入力値）
          onReload={() => refetch()} />
      : <PendingBanner message="登録内容を反映しています" elapsedMs={elapsedMs} />;  // 経過秒数は live region の外
  }
  return <CargoDetail ref={heading} cargo={data!.cargo} actions={<CargoActions cargo={data!.cargo} />} />;
}
```

`PendingBanner` の本文は `aria-live="polite"`、経過秒数はその外に置きます。30 秒の上限とヘッダの遅れ表示は同じ `lag.ts` から決め、画面ごとに別の秒数を持ちません。

## 日時の扱い

- 表示と入力は業務タイムゾーン（`Asia/Tokyo`）。`toISOString()` を直接使わず、`shared/lib/businessDate.ts` の関数を通す
- 期限（`arrivalDeadline`、`dueOn`）は日付。時刻付きの値と比べない
- E2E のテストデータも同じヘルパで作る。CI（UTC）で日付がずれる事故を防ぐ

## テスト戦略（概要）

| 層 | ツール | 対象 |
| :--- | :--- | :--- |
| ユニット | Vitest + Testing Library | Presentational、Hooks（`202` → `pending` の変換、ポーリング停止、30 秒上限と遅れ表示中の切り替え、受付済み行の差し込みと差し替え） |
| 統合 | Vitest + MSW | Container + API。**MSW のモックは本物より甘くしない**（型・日付形式を OpenAPI に合わせる） |
| E2E | Playwright | ロール × 画面の到達性、状態軸の到達性、公開追跡、投影の遅延を待つヘルパ、キーボードのみで `409` を受けて予告された操作を押す 1 本 |
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
- [UI 設計](ui_design.md)（反映中の規約・楽観的更新の適用基準・アクセシビリティ・色トークン）
- [ユーザーストーリー](../../requirements/user_story.md)
- [アーキテクチャ設計ガイド](../../reference/アーキテクチャ設計ガイド.md)
- 参照元：`tmp/take-4/docs/design/architecture_frontend.md`、[java-3 フロントエンドアーキテクチャ](../../article/source/java-3/docs/design/architecture_frontend.md)
