# ADR-029: 荷主向け追跡境界と無操作タイムアウト

荷主の担当者はログイン後に自社貨物だけを追跡し、共用端末を放置した画面は一定時間で閉じる。

日付: 2026-08-27

## ステータス

承認済み（決定 1〜6）

## コンテキスト

IT13 は US33 と TD-01 を扱う。US18 の公開追跡照会は、追跡番号を知っている人が
ログインせずに状態を引くための入口である。一方、US33 は `ROLE_SHIPPER` の利用者が
ログインし、**自社の貨物だけ**を一覧で見る入口である。公開追跡と同じ URL や同じ DTO に
寄せると、認証ありの自社境界と認証なしの公開境界が混ざる。

ADR-008 は「利用者と荷主の紐付けが無い間、`ROLE_SHIPPER` に予約参照を開かない」と
決めた。IT13 ではこの先送りを解き、利用者と荷主の紐付けを持つ。ただし bookingms の
`shipper` に authms の利用者 ID を混ぜると、認証文脈が予約文脈へ漏れる。

TD-01 は共用端末のログアウト忘れを扱う。ADR-005 は `sessionStorage` を採用済みであり、
タブを閉じればトークンは消える。しかしタブを開いたまま席を離れると、次の利用者が前任者の
権限で操作できる。入力中のフォームもあるため、突然閉じるだけでは「入力が保存されたのか」
が分からない。

> **決定ごとに「破られたら何が起きるか」を先に書く。** ADR-028 と同じく、検査名だけを
> 並べると決定の一部分しか守っていないことに気づけない。

## 決定

### 決定 1: 利用者と荷主の紐付けは authms に持つ

`user_shipper_link` を authms に置き、利用者 ID から荷主 ID を引く内部 API を提供する。
bookingms の `shipper` には authms の利用者 ID を持ち込まない。bookingms / trackingms は
Gateway が付けた利用者 ID を受け取り、必要なときに authms へ問い合わせる。

内部 API は `system:bookingms` と `system:trackingms` だけに開く。人のロールでは開かない。

**破られたら**: 荷主名やメールアドレスの文字列一致で「自分の貨物」を決めることになり、
同名・変更・表記揺れで他社貨物が見える。逆に bookingms の `shipper` が認証の利用者 ID に
依存し、荷主マスタを認証の都合で書き換えることになる。

### 決定 2: 荷主向け追跡一覧は trackingms が返す

荷主が読むのは追跡の状態・現在地・到着予定・未解決例外であるため、一覧と詳細の Read API は
trackingms に置く。自社貨物の判定に必要な荷主 ID は、bookingms の
`/api/v1/bookings/shipper-snapshots/{trackingNumber}` を ACL 経由で引く。

bookingms の Snapshot は `system:trackingms` だけが読める。trackingms は Snapshot を
保存せず、追跡番号ごとに荷主 ID を照合する。

**破られたら**: trackingms の画面が bookingms の予約一覧を直接組み立てるか、bookingms が
追跡の状態を持つことになる。どちらも Bounded Context の責務をまたぎ、状態・例外・到着予定の
表示が複数箇所に分かれる。

### 決定 3: 公開追跡と荷主向け追跡は URL を分ける

公開追跡は `/api/v1/public/tracking/{trackingNumber}`、荷主向け追跡は
`/api/v1/shipper/tracking` と `/api/v1/shipper/tracking/{trackingNumber}` に分ける。
荷主向け API は `ROLE_SHIPPER` の認可を必須とし、Gateway でも trackingms へ振り分ける。

**破られたら**: 公開経路に自社判定を足して、追跡番号を知っているだけの人に認証済み荷主向けの
項目を返す。あるいは荷主向け経路を公開除外に入れてしまい、ログインせずに自社貨物 API を
叩ける。

### 決定 4: 他社貨物の詳細は 404 にする

荷主向け詳細は、自社貨物でなければ追跡が存在しても 404 を返す。公開追跡とは違い、
「この番号はあるが、あなたのものではない」を教えない。

**破られたら**: 荷主が追跡番号を順に試すだけで、他社貨物の存在を確認できる。
403 でも「番号は実在する」と読めるため、未知番号と他社番号を分けてはいけない。

### 決定 5: 紐付いていない利用者は 200 + `linked=false` で案内する

認証は通っているが荷主との業務上の紐付けが無い利用者には、403 ではなく
`linked=false` と問い合わせ先を返す。空配列だけにもしてはいけない。

**破られたら**: 利用者は「自社貨物が無い」のか「アカウント設定が足りない」のかを区別できず、
公開追跡へ戻って 1 件ずつ入力する運用を続ける。

### 決定 6: 無操作 15 分で警告、20 分でログアウトする

業務画面は 15 分の無操作で警告を出し、20 分で `sessionStorage` の認証状態を破棄して
ログイン画面へ戻す。警告には「入力中の内容は保存されません」を含める。操作は
`pointerdown`、`keydown`、`focus` で数え直す。

タブを閉じた場合は ADR-005 のとおり `sessionStorage` に任せる。入力中データの自動保存は
この ADR では採用しない。

**破られたら**: 共用端末で前任者の画面が残り、次の利用者が前任者の権限で予約・荷役・精算を
操作できる。突然ログアウトだけすると、入力中の内容が保存されたと誤解される。

## 影響

### 良い影響

- 荷主はログイン後に自社貨物だけを一覧で見られる。
- authms / bookingms / trackingms の責務が分かれ、文字列一致による自社判定を避けられる。
- 共用端末の放置で、前任者の権限が使われ続ける時間を 20 分に抑えられる。

### 悪い影響・受け入れるリスク

- trackingms の一覧は候補の追跡を読み、bookingms Snapshot で絞るため、貨物数が増えると
  N+1 になる。IT13 では `LIST_LIMIT` を置き、必要になったら bookingms 側に荷主 ID から
  tracking number を引く内部 API を追加する。
- authms または bookingms が落ちていると、荷主向け追跡は使えない。これは「自社境界が
  確認できない」状態であり、見える範囲を広げて代替しない。
- タイムアウト警告は全業務画面共通であり、入力中の個別フォーム状態までは見ない。
  自動保存は別の業務決定として扱う。

## コンプライアンス

| 決定 | 破られたら何が起きるか | それが起きないことの検査 |
| :--- | :--- | :--- |
| 1 authms に紐付けを持つ | 文字列一致で他社貨物が見える / bookingms に認証文脈が漏れる | `AuthIntegrationTest`（`shipper01` の紐付けを DB から読む）・`UserShipperLinkControllerTest`（`system:bookingms` / `system:trackingms` だけが読める）・`FindUserShipperLinkUseCaseTest` |
| 2 trackingms が荷主向け追跡を返す | 予約文脈が追跡状態を持つ / 追跡画面が予約一覧を組み立てる | `CargoLookupControllerTest`（bookingms Snapshot は `system:trackingms` だけ）・`RestShipperCargoSnapshotFinderTest`（trackingms が system principal で Snapshot を引く）・`ShipperTrackingQueryUseCaseTest`（Snapshot の `shipperId` で自社貨物だけに絞る） |
| 3 URL を分ける | 公開経路に認証済み項目が漏れる / 荷主 API が公開除外になる | `ShipperTrackingControllerTest`（`ROLE_SHIPPER` だけが `/api/v1/shipper/tracking` を使える）・`AuthenticatedUserHeaderRequiredTest`（荷主 API は名乗らないと 401）・`GatewayRouteCoverageTest`（Gateway が荷主 API を trackingms へ振る）・`apps/frontend/src/pages/__tests__/shipper-tracking-page.test.tsx` |
| 4 他社貨物詳細は 404 | 追跡番号の実在が他社に分かる | `ShipperTrackingQueryUseCaseTest`（他社 Snapshot は空）・`ShipperTrackingControllerTest`（自社貨物でなければ 404 と本文を返す）・`apps/frontend/src/pages/__tests__/shipper-tracking-page.test.tsx` |
| 5 未紐付けは案内する | 自社貨物なしと設定不足を区別できない | `ShipperTrackingQueryUseCaseTest`（未紐付けでは候補を読まず `linked=false`）・`ShipperTrackingControllerTest`（200 + 問い合わせ文）・`apps/frontend/src/pages/__tests__/shipper-tracking-page.test.tsx` |
| 6 無操作タイムアウト | 共用端末で前任者の権限が残る / 入力が保存されたと誤解される | `apps/frontend/src/layouts/__tests__/app-layout.test.tsx`（15 分警告、20 分ログアウト、操作で延長） |

## 関連

- [ADR-005](005-token-storage-in-session-storage.md)（トークンの保管先）
- [ADR-007](007-authenticated-user-header-required.md)（利用者ヘッダの必須化）
- [ADR-008](008-no-user-shipper-link-in-it2.md)（利用者と荷主の紐付けの先送り。本 ADR で先送りを解く）
- [ADR-024](024-tracking-manual-update-and-exceptions.md)（公開追跡照会）
- [ADR-028](028-settlement-and-quotation.md)（破られたら何が起きるかを先に書く形式）
