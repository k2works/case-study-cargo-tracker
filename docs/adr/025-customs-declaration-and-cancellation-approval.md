# ADR-025: 通関申告とキャンセル承認

通関という関門を引取の前に置き（US29）、輸送中の予約に承認フローを与える（US30）ときに、bookingms が「輸送中」をどう知るか、公開画面に緊急を出すかを決める。

日付: 2026-08-24

## ステータス

承認済み（決定 1〜9）

## コンテキスト

IT9 は 2 つの「進ませない仕組み」を入れる。**通関が下りるまで引き取らせない**（US29-3）ことと、**承認なしに輸送中の予約をキャンセルさせない**（US30-5）ことである。

着手前に決めておくべきことが 9 つある（[IT9 計画](../development/iteration_plan-9.md)）。**先送りできない 2 つ**（A・E）を起票時に決め、残る 7 つを追記した。

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

### 3. `CargoCancelledEvent` は発行する。購読するのは trackingms だけ（決めること B）

[ADR-022](022-domain-event-contract.md) 決定 1・[ADR-023](023-handling-activity-validation.md) 決定 5 は「**購読者がいないイベントは発行しない**」形を取ってきた。`CargoCancelledEvent` は違う——**購読者は IT9 の時点で存在する**。

**公開追跡が開いているからである。** キャンセルが承認された貨物を荷主が追跡番号で引くと、trackingms は何も知らないので**「輸送中」のまま返す**。荷主は自分が申し入れて承認されたキャンセルを、画面で否定されることになる。US30-6 の「荷主に通知される」を通知の代替で果たすなら、**その代替が出る場所は公開追跡である**。

- 発行するのは bookingms、承認が確定したとき（`CancellationRequest` が `APPROVED` になったとき）
- 交換機は既存の `cargoBookingChannel`。ルーティングキーを 1 本足し、trackingms が自分のキューで受ける
- ペイロードは `trackingNumber` / `bookingId` / `dischargeLocationUnLocode` / `reason` は載せず、`cancelledAt` / `occurredAt` を載せる。**理由は載せない**——公開画面に出る可能性のある経路に、社内の判断を流さない

**trackingms は状態を新設しない。** `TrackingStatus` に `CANCELLED` を足すと、進行の並び（`canAdvanceTo` の判定）に「進まない値」がもう 1 つ増える。IT8 で `EXCEPTION` / `UNKNOWN` の 2 値が並び順の外にあることが実バグを生んだばかりである。**お知らせ（IT8 で作った通知の代替の器）に記録して公開画面に出す**。

**billingms への発行は IT11 まで行わない。** キャンセル料の算定は US21 であり、受け口が無い（[リリース計画](../development/release_plan.md)）。**同じイベントに購読者を足すだけで済む形にしておく**（トピック交換機 + 購読側ごとのキュー）。

### 4. 陸揚げ地の候補は bookingms の中で作る（決めること C）

US30-5 は「現在地の港または次の寄港地」と定めている。**どちらも bookingms で作れる。**

- **現在地**: 決定 1 で購読する `HandlingActivityRegisteredEvent` は**地点を運んでいる**（`locationUnLocode`）。状態と一緒に**最後の荷役地点を持つ**
- **次の寄港地**: 旅程（`leg`）の残りの荷降し地。bookingms がすでに持っている

**trackingms へは引かない。** 決定 1 と同じ理由である。加えて、現在地の一次情報は荷役にあり、trackingms もそれを購読して得ている——**同じ事実を 2 ホップ先から取りに行く形になる**。

**全港から選ばせない。** 船が寄らない港を指定できると、荷降しできない約束を荷主にすることになる。**候補に無い港での承認は断る。**

> **設計への反映が要る。** `cargo` に最後の荷役地点と日時の列が無い（`last_handling_location_unlocode` / `last_handling_at`）。[data-model.md](../design/data-model.md) に足す。**足りないものを注に列挙して終わらせない**——返済枠 0.1 の突き合わせ検査が、足していなければ赤にする。

### 5. 「荷降しの手配」は、陸揚げ地を記録して現場が見られる状態までとする（決めること D）

US30-6 の「指定した陸揚げ地への荷降しが手配され」を、**IT9 では「陸揚げ地が決まり、荷役の担当者がそれを見られる」まで**と定める。作業指示そのものは作らない。

**荷役は実績を記録するモデルであり、予定を持っていない。** `HandlingActivity` は「作業が終わった」記録である（[ADR-023](023-handling-activity-validation.md)）。予定を導入すると、US15・US16 の設計に手が入り、「予定と実績の突き合わせ」という新しい業務が生まれる。IT9 の 10 SP に収まらない。

**運用条件を添える**（Release 1.0 の通関書類の目視確認と同じ形）。**承認した追跡管理者が、荷役の担当者へ連絡する**。連絡を受けた担当者は予約詳細で陸揚げ地を確認できる。

> **範囲を切ったことを、完了報告書と[リリース計画](../development/release_plan.md)に書く。** 書かないと US30-6 が「果たした」と読まれる。

### 6. 登録は荷役作業員、状態の更新は追跡管理者。閲覧は両方（決めること F）

正典（US29）のとおりに分ける。**画面は 1 つ、操作はロールで出し分ける。**

- `/customs`（一覧・検索）: `ROLE_HANDLER` と `ROLE_TRACKER` の両方
- `/customs/new`（申告の登録）: `ROLE_HANDLER`
- `/customs/:declarationId` の状態更新: `ROLE_TRACKER`

**荷役作業員に「状態を更新する」を見せない。** 見せて 403 を返すのは、押せる操作を出しておいて断る形である（過去 take の教訓——**共有画面のリンクもロールで出し分ける**）。ただし**閲覧は開く**——荷役作業員は引き取れるかどうかを判断するために通関状態を見る必要があり、見られなければ引取のたびに拒否されて理由を探すことになる。

**認可は入力検証より先に置く**（[ADR-016](016-authorize-before-validate.md)）。

### 7. 未決着の申告があるあいだは、2 通目を受け付けない（決めること G）

[data-model.md](../design/data-model.md) は「CLAIM ガードは `booking_id` で**最新の申告**を参照する」と定めており、**貨物あたり複数の申告があり得る前提**である。一方で「最新」が何を指すかは、同時に 2 件が審査中だと決まらない。

したがって**登録側で絞る**。

- `PENDING` / `HELD` の申告が 1 件でもあれば、**2 通目を断る**
- `REJECTED` のあとは**再申告できる**（書類を直して出し直すのは実務にある）
- `CLEARED` のあとは断る（通関済みの貨物に新しい申告は要らない）

**「最新の 1 件」を暗黙に選ぶ実装にしない。** 未決着が高々 1 件であることを集約の不変条件にすれば、**ガードの「最新」は一意になる**。検査は「最新を取る」ことではなく、**未決着が 2 件にならない**ことを見る。

### 8. 既存の値オブジェクトを使う。新設は `DeclarationNumber` だけ（決めること H）

[ADR-012](012-value-object-granularity.md) の基準（**破りうる規則があるか**）で判断する。

- `cargoBookingId`: 既存の `CargoBookingId` を使う。**素の `String` にしない**——handlingms は ACL でこの型に変換しており、素に戻すと変換の意味が消える
- `trackingNumber`: 既存の `HandlingTrackingNumber` を使う（形式の規則がある）
- `declarationNumber`: **新設する**（形式と一意性の規則がある）
- `declarationId`: **値オブジェクトにしない。** サロゲートキーであり、守る規則が「空でない」だけである

[domain-model.md](../design/domain-model.md) は `trackingNumber: String` / `declarationNumber: String` と描いている。**ここは設計のほうを直す**——同じ意味の値が 2 つの型で流れる状態を、設計が追認している。

### 9. 荷受人の確認は残す。外すのは「通関を仕組みで確かめていない」という但し書きだけ（決めること I）

[ADR-023](023-handling-activity-validation.md) 決定 4 は 2 つのことを同時に行っていた。**片方は残り、片方は誤りになる。**

- **`ConsigneeConfirmation` を必須とすること**: **残す。** これは US16 の受入基準そのもの（「荷受人確認が取得されると引取作業が記録される」）であり、**誰に引き渡したかの記録**である。通関ガードの代用として入れたのではなく、代用を兼ねていただけである
- **「通関の確認が仕組みでは行われない」と画面に書いていること**: **外す。** ガードが入れば文が誤りになる。[ADR-023](023-handling-activity-validation.md) 決定 4 は「書かないと、IT9 で二重にガードを掛けるか、代替のほうを消し忘れる」と予告している。**消し忘れるほうを踏まない**

画面・マニュアル 08 章・ADR-023 のステータス注記を、**同じ変更で直す**（[IT8 Try 2](../development/retrospective-8.md)）。

> **素通りで作られた過去の記録は、さかのぼって検査しない。** IT7・IT8 のあいだ、通関申告の無いまま CLAIM された記録がある（[ADR-023](023-handling-activity-validation.md) の代替案がこの問題を予告している）。**ガードは新しく受け付ける引取にだけ掛ける。** 復元で検査すると、当時は正しかった記録が読めなくなる（過去 take の教訓——**不変条件の追加は既存行を壊す**）。

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

### 決定 3〜9 の影響

- **イベントが 1 本増える**（決定 3）。bookingms → trackingms。IT9 は**購読の追加が 2 本**（決定 1 の handling → booking、決定 3 の booking → tracking）になる。どちらも既存の交換機への追加であり、引数は変えない
- **`cargo` に列が 2 つ増える**（決定 4）。`last_handling_location_unlocode` / `last_handling_at`。[data-model.md](../design/data-model.md) への反映が要る。**返済枠 0.1 の突き合わせ検査が、足していなければ赤にする**
- **US30-6 が部分達成になる**（決定 5）。荷降しの手配は運用で埋める。**完了報告書と[リリース計画](../development/release_plan.md)に書く**
- **`ConsigneeConfirmation` は残るが、画面の説明文が変わる**（決定 9）。[ADR-023](023-handling-activity-validation.md) 決定 4 に「IT9 で但し書きのみ撤去」と追記する
- **IT7・IT8 に作られた申告の無い引取記録は、そのまま残る**（決定 9）。**さかのぼらない**ことを完了報告書に書く

## コンプライアンス

**決定の数だけ検査を用意する。** 「〜を確かめる」ではなく、**検査の場所を書く**（[IT7 Try 3](../development/retrospective-7.md)）。

**そのうえで、表を書いた時点では未完了とする。** [IT8](../development/retrospective-8.md) は 11 決定のうち 3 件が空振りだった——表が指す検査が、その決定を守っていなかった。**1 件ずつ実装を壊し、赤になることを確かめてから「済」にする**（[IT8 Try 1](../development/retrospective-8.md)）。

| 決定 | 検査 | 壊して赤を確認 |
| :--- | :--- | :--- |
| 1. 荷役のイベントで輸送中を知る | `HandlingActivityRegisteredRoundTripTest#advancesTheBookingWhenTheEventArrives`（実 RabbitMQ の往復）。冪等は `#doesNotAdvanceTwiceForTheSameActivity`、巻き戻さないことは `#neverRegressesTheTransportStatus`。**ACL を引いていない**ことは `ArchitectureTest#serviceIsolationRule`。**交換機の引数が 3 サービスで一致している**ことは `CargoHandlingExchangeArgumentsTest`——引数を 1 つ external に変えると赤 | 未 |
| 2. 緊急は載せ続け、言葉を変え、届くことを固定する | `PublicTrackingControllerTest#deliversTheUrgentFlagToTheResponse`（応答まで）・`tracking-lookup-page.test.tsx#紛失のときだけ次の行動を案内する`（画面まで）。**急かす言葉を使わない**ことは同テストが文言で見る。`CUSTOMS_HOLD` が緊急でないことは `ExceptionTypeTest#onlyLostIsUrgent` の `@EnumSource` が新しい種別を含めて回す。モックの一致は `tracking.ts` の契約テスト | 未 |
| 1 の付随: `SETTLED` へ進めない | `BookingStatusTest#hasNoTransitionIntoSettled`——**遷移の呼び出し箇所を数える**（[ADR-024] 決定 8 と同じ形） | 未 |
| 3. キャンセルは trackingms だけが購読する | `CargoCancelledRoundTripTest#showsTheCancellationOnThePublicLookup`（実 RabbitMQ の往復。**公開画面まで**）。**理由を載せていない**ことは `CargoCancelledContract` の項目名簿を DTO から導く契約テストが見る。**billingms へ発行していない**ことは `CargoEventPublishingTest#publishesOnlyTwoKindsOfEvents`——発行の呼び出し箇所を数える。**`TrackingStatus` に値を足していない**ことは `TrackingStatusTest#hasTheSameValuesAsTheDesign` | 未 |
| 4. 陸揚げ地の候補は bookingms が作る | `CancellationApprovalTest#rejectsAPortOutsideTheCandidates`——候補外の港での承認を断る。候補の作り方は `DischargeCandidatesTest#combinesTheLastHandledPortAndTheRemainingLegs`。**ACL を引いていない**ことは `ArchitectureTest#serviceIsolationRule`。**列を足したこと**は返済枠 0.1 の `DataModelMigrationConsistencyTest` | 未 |
| 5. 手配は陸揚げ地の記録まで | `CancellationApprovalTest#doesNotCreateAnyHandlingPlan`——荷役の書き込みが起きない。画面に出ることは `booking-detail-page.test.tsx#承認済みのキャンセルは陸揚げ地を出す`。**運用条件の明記**は `release_plan.md` と完了報告書の突き合わせ | 未 |
| 6. ロールの分け方 | `CustomsControllerTest#onlyHandlerCanRegister` / `#onlyTrackerCanUpdateStatus` / `#bothRolesCanRead` / `#salesCannotRead`。**押せない操作を出していない**ことは `customs-detail-page.test.tsx#荷役作業員には、状態を更新する枠が出ない` と `customs-page.test.tsx#追跡管理者には、新規申告のボタンが出ない`。**認可が検証より先**であることは `#returns403BeforeValidatingTheBody` | **済**（登録を追跡管理者に開く／状態更新を荷役作業員に開く／閲覧を営業に開く／認可を検証の後ろに移す、の 4 つとも赤） |
| 7. 未決着は高々 1 件 | `RegisterCustomsDeclarationUseCaseTest#rejectsASecondPendingDeclaration` / `#rejectsASecondDeclarationWhileHeld` / `#allowsReDeclarationAfterRejected` / `#rejectsAfterCleared`。**最新を取る実装ではなく不変条件を見る**——検査は「2 件にならない」ことをアサートしている。実 DB での引き当ては `CustomsDeclarationPersistenceIntegrationTest#findsOnlyUnsettledDeclarations`。違反時の応答が 409 であることは `CustomsControllerTest#returns409WhenAnotherDeclarationIsUnsettled` | **済**（未決着の検査を外す／通関済のあとも出し直せるようにする／409 を 400 にする、の 3 つとも赤） |
| 8. 既存の値オブジェクトを使う | `ArchitectureTest#customsDeclarationUsesExistingValueObjects`——`CustomsDeclaration` の項目に素の `String` の識別子が現れない。`id`（サロゲートキー）が除外されていることも同じ検査が名指しで持つ。**設計側の型は同じ変更で直した**（[domain-model.md](../design/domain-model.md)） | **済**（追跡番号を素の String に戻す／サロゲートキーの型を変える、の 2 つとも赤） |
| 9. 荷受人確認は残し、但し書きは外す | `HandlingActivityTest#stillRequiresConsigneeConfirmation`（残ること）。**但し書きが消えたこと**は `handling-page.test.tsx#通関を仕組みで確かめていないとは書かない`——文言の不在を見る。**さかのぼらない**ことは `CustomsGuardTest#doesNotReExamineActivitiesRecordedBeforeTheGuard` | 未 |

## 備考

- 起票: IT9 開始準備（2026-08-24）
- 決定 1〜9 は [IT9 計画](../development/iteration_plan-9.md)の「決めること」A・E・B・C・D・F・G・H・I に対応する（決定 1・2 は起票時、決定 3〜9 は追記時）
- 決定 4 は [data-model.md](../design/data-model.md) への列の追加を、決定 8 は [domain-model.md](../design/domain-model.md) の型の修正を伴う。**どちらも同じ変更で直す**
- 関連: [ADR-009](009-cargo-status-columns-from-the-start.md)（状態の列を最初から持つ）・[ADR-022](022-domain-event-contract.md)（イベント契約）・[ADR-023](023-handling-activity-validation.md)（荷役の検証・通関ガードの代替）・[ADR-024](024-tracking-manual-update-and-exceptions.md)（緊急フラグ・公開照会）
