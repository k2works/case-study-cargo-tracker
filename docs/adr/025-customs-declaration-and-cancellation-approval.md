# ADR-025: 通関申告とキャンセル承認

通関という関門を引取の前に置き（US29）、輸送中の予約に承認フローを与える（US30）ときに、bookingms が「輸送中」をどう知るか、公開画面に緊急を出すかを決める。

日付: 2026-08-24

## ステータス

承認済み（決定 1・2）／**決定 3〜9 は未決**（IT9 Day 4・タスク 1.1 で決める）

## コンテキスト

IT9 は 2 つの「進ませない仕組み」を入れる。**通関が下りるまで引き取らせない**（US29-3）ことと、**承認なしに輸送中の予約をキャンセルさせない**（US30-5）ことである。

着手前に決めておくべきことが 9 つある（[IT9 計画](../development/iteration_plan-9.md)）。本 ADR はまず**先送りできない 2 つ**に答える。

### A: bookingms は「輸送中」を知らない

US30 は予約が `IN_TRANSIT` かどうかで振る舞いを分ける。輸送開始前なら営業担当者の操作で即時キャンセル、輸送中なら申請と承認である（US30-1・US30-2）。

しかし **`BookingStatus` の実装は `TRACKING_ISSUED` までで終わっている**。設計（[domain-model.md](../design/domain-model.md)）は `IN_TRANSIT` / `DELIVERED` / `SETTLED` / `CANCELLED` を含む 9 値を定めているが、実装は 5 値しかない。

さらに **`cargo.transport_status` 列は IT2 から存在するのに、誰も更新していない**。[ADR-009](009-cargo-status-columns-from-the-start.md) は「まだ動いていないは値の無い状態ではなく、意味のある状態である」として 3 つの状態を最初から列に持たせたが、荷役の進行を bookingms へ反映する経路は 7 イテレーション作られなかった。**予約の一覧を開くと、船に載った貨物も `NOT_RECEIVED` と表示される。**

放置したまま US30 を実装すると、`IN_TRANSIT` はテストの中でしか作れない状態になる。**受入基準の主役である状態に、業務からの到達経路が無い。**

### E: 公開画面に緊急を出すか（IT8 から持ち越し）

[ADR-024](024-tracking-manual-update-and-exceptions.md) 決定 3 は「紛失だけが緊急である」と定め、公開応答に `urgent` を載せた。画面は次のように描いている。

```text
至急のご連絡が必要です。詳しくはご依頼元の営業担当へお問い合わせください。
```

[IT8 のクローズレビュー](../review/イテレーション8_review_20260823.md)で、**逆向きの指摘が出た**。

- **利用者代表（#28）**: 「至急のご連絡が必要です」は荷主に**不安が先に立つ**。何が起きたか分からないまま急かされる
- **テスター（#11 の周辺）**: 決定 3 は**層をまたいで届くことを一度も確かめていない**。`ExceptionTypeTest#onlyLostIsUrgent` は列挙の値を見ているだけで、公開応答まで届くかは見ていない

レビューの統合判断は「出したまま、検査を足す。**IT9 で利用者代表と決め直す**」だった。

IT9 は `CUSTOMS_HOLD`（税関保留）を公開画面に出すイテレーションであり、**例外が荷主に見える経路が 1 種類増える**。先送りすると、決めていない基準のまま種別が増える。

## 決定

### 1. bookingms は、荷役のイベントを購読して「輸送中」を知る（決めること A）

**handlingms が発行済みの `HandlingActivityRegisteredEvent` を、bookingms も購読する。** 新しいイベントを足さず、新しい向きの同期呼び出しも引かない。

- 交換機は既存の `cargoHandlingChannel`（TopicExchange・durable・`alternate-exchange` 付き）をそのまま使う
- bookingms は**自分のキュー**（`bookingms.handling-activity-registered`）とデッドレターを宣言し、既存のルーティングキー `cargo.handling-activity-registered` に結びつける
- 受け取ったら追跡番号で貨物を引き当て、`transport_status` を更新する。**`BookingStatus` は `transport_status` から導く**——最初の `LOAD` で `IN_TRANSIT` へ、`CLAIM` で `DELIVERED` へ進む

**なぜ ACL（bookingms → trackingms の REST）を引かないか。** キャンセルのボタンは予約詳細だけでなく**予約一覧にも出る**（US30-2 の出し分け）。同期で引くと一覧 1 画面あたり trackingms への呼び出しが行数だけ増え、trackingms が落ちていると**キャンセルできないだけでなく予約一覧が開かなくなる**。[ADR-024](024-tracking-manual-update-and-exceptions.md) 決定 4 が公開照会について ACL を退けたのと同じ理由である。

**なぜ trackingms からの新しいイベントにしないか。** 追跡は荷役から作られる派生であり、**輸送が始まった事実の一次情報は荷役にある**。trackingms を経由させると、同じ事実が 2 ホップ遅れて届き、途中で失われたときにどちらの責任か分からなくなる。

**なぜ 2 つ目の購読者を足してよいか。** 交換機は Topic であり、**キューと結びつけを足す操作は既存の宣言を変えない**。既存環境で宣言し直せずに止まるのは交換機の**引数**を変えたときであり、購読者の追加はそれに当たらない（[ADR-022](022-domain-event-contract.md) 決定 4 の宣言と同じ形）。

> **ただし bookingms 側で交換機を宣言するときは、handlingms・trackingms と同じ引数（`alternate-exchange`）で宣言する。** 引数が食い違うと `PRECONDITION_FAILED` で落ち、**後続のキュー宣言まで止まる**。これは Testcontainers では出ず、kind で初めて出る形である。
>
> **受け手は冪等にする**（[ADR-022](022-domain-event-contract.md) 決定 5）。同じ荷役が 2 回届いても状態は 1 度しか進まない。すでに `IN_TRANSIT` の貨物に 2 度目の `LOAD` が来ても、`DELIVERED` の貨物に `LOAD` が来ても、**巻き戻さない**。
>
> **既存の行を壊さない。** IT8 までに入った貨物の `transport_status` は `NOT_RECEIVED` のまま正しい。復元では検査せず、**新規に受け入れる遷移だけを検査する**。

**この決定は US30 の前提を作ると同時に、[ADR-009](009-cargo-status-columns-from-the-start.md) が 7 イテレーション空のまま持っていた列に、初めて意味を与える。**

### 2. 緊急は公開応答に載せ続ける。ただし「急かす言葉」をやめ、届くことを検査で固定する（決めること E）

[ADR-024](024-tracking-manual-update-and-exceptions.md) 決定 3 は維持する。**書き直さない。**

対立の中身は「出すか隠すか」ではない。**出したものが荷主にとって行動につながるか**である。

- 利用者代表が問題にしたのは**言葉**である。「至急のご連絡が必要です」は、荷主に何をすればよいか伝えずに緊急だけを渡す
- テスターが問題にしたのは**検証**である。決定 3 は列挙の値しか見ておらず、公開応答まで届くことを確かめていない

したがって、**両方を別々に直す**。

1. **文言を、急かす形から案内する形へ変える。** 「至急のご連絡が必要です」をやめ、**荷主が次に何をするか**を書く。種別は出さない（[ADR-024] 決定 3 の周辺で「紛失」という言葉を公開画面から外した判断を維持する）
2. **層をまたいで届くことを検査する。** 集約 → 応答 → モック → 画面のどこで潰しても赤になる 1 本を持つ（[IT8 Try 4](../development/retrospective-8.md)）

**なぜ隠さないか。** 隠せるのは「荷主が何もしなくてよい」ときだけである。`LOST` は荷主が補償と再手配を判断する事象であり、**知らせないほうが害が大きい**。追跡画面は荷主が自分で見に来る唯一の窓口である（US18）。

**なぜ IT9 でこの形にできるか。** IT8 の時点では「営業へお問い合わせください」は行き止まりだった——[IT8 ふりかえり](../development/retrospective-8.md)が書いたとおり、**営業のダッシュボードに例外に気づく手段が無く、電話を受けた営業は追跡管理者を探すことになる**。IT9 の返済枠 0.9 で営業に件数と導線が入る。**案内した先に人がいる状態になって初めて、案内は案内になる。**

**`CUSTOMS_HOLD` は緊急にしない。** 通関の審査は輸送の通常の一部であり、留置も荷主の行動を直ちに要さない。公開画面には「通関手続き中」と出す（[ui_design.md](../design/ui_design.md)）。**緊急が `LOST` だけであることは、種別が増えても変わらない**——これは決定 3 の `escalationFlag` を `ExceptionType` が答える形が守る。

> **留置が 3 日を超えたときも、公開画面では緊急にしない。** 督促の相手は税関と社内であり、荷主ではない。督促は追跡管理者のダッシュボード（US29-6）で行う。

### 3〜9. 未決（IT9 Day 4・タスク 1.1 で決める）

**決めていないことを、決めたように見せない。** 以下は本 ADR の起票時点で未決である。実装の着手前に本 ADR へ追記する。

| # | 計画の記号 | 内容 |
| :--- | :--- | :--- |
| 3 | B | `CargoCancelledEvent` を発行するか（billingms は IT11 まで存在しない） |
| 4 | C | 陸揚げ地の候補をどこから引くか（現在地は trackingms、旅程は bookingms） |
| 5 | D | 「荷降しの手配」（US30-6）をどこまでで果たしたと呼ぶか |
| 6 | F | 通関状態を更新できるロール（登録は荷役作業員、更新は追跡管理者） |
| 7 | G | 同一貨物への 2 通目の通関申告を許すか |
| 8 | H | `CustomsDeclaration` の識別子を値オブジェクトにするか |
| 9 | I | [ADR-023](023-handling-activity-validation.md) 決定 5 の代替（荷受人の明示的な確認）を、ガード導入後も残すか |

## 影響

### 決定 1 の影響

- **bookingms が初めてメッセージを受け取る側になる。** これまで発行だけだった（`RabbitCargoEventNotifier`）。`ArchitectureTest` の「メッセージ基盤を知るのはインフラ層だけ」という規則が bookingms の購読側にも適用される
- **`BookingStatus` に 4 値が入る。** `IN_TRANSIT` / `DELIVERED` / `SETTLED` / `CANCELLED`。設計に追いつく変更であり、設計変更ではない
- **予約一覧の表示が変わる。** 船に載った貨物が `NOT_RECEIVED` と出ていた状態が直る。**これは US30 の副産物であり、受入基準には無い**——完了報告書に書く
- **`SETTLED` へ進める経路は IT9 では作らない**（精算は US23・IT12）。値だけを置く。**置くだけであることを検査に落とす**（[ADR-023](023-handling-activity-validation.md) 決定 5 と同じ形）
- kind で交換機の引数の食い違いが出る可能性がある。**Phase 6 で実際に通す**

### 決定 2 の影響

- 公開画面の文言が変わる。**マニュアル 09 章の該当箇所も同じ変更で直す**
- `PublicTrackingControllerTest#neverExposesInternalFields` が緊急フラグの到達も見る形になっていたが、**これは種別を出さないことの裏返しでしかなく、届くことを見ていない**。専用の検査を足す
- モックも同じ形にする（返済枠 0.3。**モックを本物より甘くしない**）

## コンプライアンス

**決定の数だけ検査を用意する。** 「〜を確かめる」ではなく、**検査の場所を書く**（[IT7 Try 3](../development/retrospective-7.md)）。

**そのうえで、表を書いた時点では未完了とする。** [IT8](../development/retrospective-8.md) は 11 決定のうち 3 件が空振りだった——表が指す検査が、その決定を守っていなかった。**1 件ずつ実装を壊し、赤になることを確かめてから「済」にする**（[IT8 Try 1](../development/retrospective-8.md)）。

| 決定 | 検査 | 壊して赤を確認 |
| :--- | :--- | :--- |
| 1. 荷役のイベントで輸送中を知る | `HandlingActivityRegisteredRoundTripTest#advancesTheBookingWhenTheEventArrives`（実 RabbitMQ の往復）。冪等は `#doesNotAdvanceTwiceForTheSameActivity`、巻き戻さないことは `#neverRegressesTheTransportStatus`。**ACL を引いていない**ことは `ArchitectureTest#serviceIsolationRule`。**交換機の引数が 3 サービスで一致している**ことは `CargoHandlingExchangeArgumentsTest`——引数を 1 つ external に変えると赤 | 未 |
| 2. 緊急は載せ続け、言葉を変え、届くことを固定する | `PublicTrackingControllerTest#deliversTheUrgentFlagToTheResponse`（応答まで）・`tracking-lookup-page.test.tsx#紛失のときだけ次の行動を案内する`（画面まで）。**急かす言葉を使わない**ことは同テストが文言で見る。`CUSTOMS_HOLD` が緊急でないことは `ExceptionTypeTest#onlyLostIsUrgent` の `@EnumSource` が新しい種別を含めて回す。モックの一致は `tracking.ts` の契約テスト | 未 |
| 1 の付随: `SETTLED` へ進めない | `BookingStatusTest#hasNoTransitionIntoSettled`——**遷移の呼び出し箇所を数える**（[ADR-024] 決定 8 と同じ形） | 未 |

## 備考

- 起票: IT9 開始準備（2026-08-24）
- 決定 1 は [IT9 計画](../development/iteration_plan-9.md)の「決めること A」、決定 2 は「決めること E」に対応する
- 関連: [ADR-009](009-cargo-status-columns-from-the-start.md)（状態の列を最初から持つ）・[ADR-022](022-domain-event-contract.md)（イベント契約）・[ADR-023](023-handling-activity-validation.md)（荷役の検証・通関ガードの代替）・[ADR-024](024-tracking-manual-update-and-exceptions.md)（緊急フラグ・公開照会）
