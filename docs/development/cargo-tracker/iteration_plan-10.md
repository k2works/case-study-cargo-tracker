---
type: Plan
title: "イテレーション 10 計画"
tags: [plan]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-08T14:47:08Z }
---

# イテレーション 10 計画

| 項目 | 内容 |
| :--- | :--- |
| イテレーション | IT10（Release 1.0 追跡と荷役・**中盤の最後**） |
| 対象 | US16 引取作業を記録する（3）・US19 遅延例外を処理する（4） |
| SP | 7 + **負債枠 1**（SP 対象外） |
| 局面 | 中盤（インサイドアウト。集約から作り込む） |
| 前提 | IT9 クローズ済み（7/7 SP・引き継ぎ 9 件） |

## ゴール

**荷受人の確認を取って引取を記録すると「引取済」になり、精算が始まります。** 輸送中の遅延を起票すると状態が「例外発生」になり、解決すると**例外前の状態へ戻ります**。

## 対象ストーリー

| ID | ストーリー | SP | 対応 UC |
| :--- | :--- | :--: | :--- |
| US16 | 引取作業を記録する | 3 | UC13 |
| US19 | 遅延例外を処理する | 4 | UC16 |

## 受入基準（**1 項目ずつ表にする**。IT9 の Try T1）

**実装を始める前に作ります。** IT9 では tester のレビューが表を作って初めて US15 §3 の未達が見つかりました。空欄のまま残れば、それが未達です。

### US16 引取作業を記録する

| # | 受入基準 | 満たす手段 | 検査の所在 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| §1 | 作業種別「引取」を選ぶと荷受人確認フィールド（**署名または確認コード**）が出る | S50 に `CLAIM` を選択肢へ戻し、選んだときだけ確認欄を出す | `HandlingRecordPage.test.tsx` | **達成** |
| §2 | 荷受人確認が取得されると引取作業が記録される | `HandlingActivity.register` の `CLAIM` ガードを**確認の有無に置き換える** | `HandlingActivityTest`・`HandlingControllerIT` | **達成** |
| §3 | 記録後、貨物状態が「引取済」に更新される | `TransportStatus.afterHandling("CLAIM")` は実装済み。**反応ハンドラの経路**を検査する | `TrackingActivityTest`・クラスタ E2E | **達成** |
| §4 | 「引取済」は配送完了を意味し、精算処理の開始条件となる | `CargoDeliveredEvent(trackingNumber, bookingId, deliveredAt, location)`（**契約**）を発行し、**billingms（`BillingReactionHandler` 開始）と bookingms（`MarkDeliveredCommand` → `BookingDeliveredEvent`）の 2 つ**が購読できる形にする（`domain-model.md:1228`） | ゴールデン JSON・`BookingReactionHandlerTest`・クラスタ E2E | **達成**（往復テストは T1b の判断どおり購読側の配線と同じ変更で入れた） |

### US19 遅延例外を処理する

| # | 受入基準 | 満たす手段 | 検査の所在 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| §1 | 追跡番号と例外種別「遅延」・発生状況（場所・日時・理由）を記録できる | `RegisterTrackingExceptionCommand` と S43 | `TrackingActivityTest`・`ExceptionScreens.test.tsx`・受け入れテスト | **達成** |
| §2 | 記録後、貨物状態が「例外発生」に更新される | 集約が `statusBeforeException` を覚えて `EXCEPTION` へ | `TrackingActivityTest`・受け入れテスト | **達成** |
| §3 | 荷主に遅延発生の通知が送信される | **送信基盤はスコープ外**（`ui_design.md:120`）。通知した事実を **trackingms 自身の `ExceptionShipperNotifiedEvent`** として記録し、`tracking_event`（`event_type = EXCEPTION`）と S41 の履歴に出す。**`ShipperNotifiedEvent` は使えない**——bookingms の `Cargo` の内部イベントで契約に無く（11 件のロスター）、trackingms からは発行も購読もできない | `TrackingActivityTest`・`TrackingProjectionIT`・`TrackingDetailPage.test.tsx` | **記録で達成**（送信基盤はスコープ外。注 N1 を設計へ反映済み） |
| §4 | 対応内容（新しい到着予定日・対応方針）を入力して**荷主に**対応報告を送信できる（送信は §3 と同じく記録で満たす） | `StartExceptionResponseCommand` / `ResolveTrackingExceptionCommand` と S41 | `TrackingActivityTest`・`TrackingDetailPage.test.tsx`・受け入れテスト | **記録で達成**（送信基盤はスコープ外） |
| §5 | 例外対応履歴が記録される | `tracking_exception` 投影と S42 一覧 | `TrackingProjectionIT`・`ExceptionScreens.test.tsx`・受け入れテスト | **達成** |

### 注（設計への反映が必要）

検証（`validating-iteration-plan` / `validating-design`）で見つかった、**設計ドキュメント側の欠落**です。本 IT の中で設計に反映します。

| # | 欠落 | 反映先 | 反映するタスク |
| :--- | :--- | :--- | :--- |
| N1 | `ui_design.md:120` の「記録と手作業の組で満たす」US 一覧に **US19 が入っていない**。かつ記録先が bookingms の `ShipperNotifiedEvent` に固定されていて、trackingms から出す例外通知の置き場が無い | `ui_design.md:120`・`domain-model.md`（trackingms のイベント表に `ExceptionShipperNotifiedEvent` を追加） | T7 |
| N2 | S42・S43 は表と図の行にはあるが、`ui_design.md` に **`###` の節が無い**（画面項目・操作手順が未記述） | `ui_design.md`（S42・S43 の節を新設） | T6 |
| N3 | `ui_design.md:151` は S50 が通関未済の引取を「判定時点で」断ると書いているが、**通関の検査は US29（IT12）**。本 IT は荷受人確認で代替する（R1） | `ui_design.md:151` に但し書きを足す | T2 |
| N5 | 引取待ち（H.8 の 2 つ目のタブ）に対応する**画面 ID が `ui_design.md` の画面一覧に無い**。航海起点（S50）では辿り着けない画面で、下部タブとダッシュボードから開く | **反映済み**（T2）。`ui_design.md` に **S54 引取待ち**を追加（S52・S53 は通関で使用済み）。`data-model.md` に `handling_activity.voided_by` を追加。N1・N3 も同じ変更で反映した | T2 |
| N4 | `data-model.md:541-560` の `tracking_exception` には**到着期限までの残日数を導ける列が無い**。不変条件 7 の並び順は `tracking_summary.estimated_arrival`（IT9 で追加済み）との JOIN で出す | `data-model.md:573`（並び順の導出元を明記） | T5 |

## 成功基準

IT9 のふりかえり Try 7 件をすべて落とし込みます。

- [x] デモ項目の受け入れテストがすべて緑
- [x] `TZ=UTC ./gradlew build` が緑（JaCoCo の層別閾値を含む）
- [x] フロントの `npm run test`・`npx tsc -b`・`npm run build` が緑
- [x] `./gradlew :acceptance-tests:test` が緑（4 スイート + 例外の新スイート）
- [x] **受入基準の表を、US の実装を始める前に作った**（Try T1。上の表を空欄のまま残さない）
- [x] **値を足したら「集約 → イベント → 投影 → 読み口 → 画面」を 1 本読み直した**（Try T2。**各タスクの完了条件**にする）
- [x] **画面を触った US では、その US の中でクラスタ E2E を 1 度回した**（Try T3。**US ごとに独立したタスク行 T2e・T6e を立てた**——IT8・IT9 は「終盤にまとめる」形だったので 2 回続けて守れていない）
- [x] **「〜する」「〜しない」と書いたコメント・javadoc には、同じ変更の中で赤にできる検査を書いた。書けないものは書かなかった**（Try T4。IT9 で 7 件出た）
- [x] **モジュール単位の作業の終わりに `shared` の規約テストと ArchUnit も回した**（Try T5。`./gradlew :shared:test :<module>:test`）
- [x] **適用済みマイグレーションを編集していないことを検査に落とした**（Try T6。`V0xx` の内容ハッシュを固定するテスト。IT9 でクラスタだけが起動しなくなった）
- [ ] **レビュー依頼の文面で出力の形を指定した**（Try T7。「高と中だけ・1 件 1 行の表・3000 字以内」）
- [x] **US を終えるたびに SonarQube を回した**（T0 として独立のタスク行に立てる）
- [ ] **US を終えたコミットのメッセージに、回した品質ゲートの結果を 1 行書いた**（IT9 未達 → **IT10 も未達**。回したテストの結果は書いたが、SonarQube の結果を書いたのは品質ゲートを直したコミットだけで、US を閉じたコミットには書いていない。2 IT 続けて守れていないので、ふりかえりで「習慣にする」以外の手立て——コミットテンプレートか検査——を決める）
- [x] **タスクに着手する前に、その名前で `grep -r` して既にあるか探した**
- [x] **注釈マッパーで `SELECT *` を書いていない**
- [x] **イベントに載せる値を「購読側の投影が作れるか」で決めた**
- [x] **利用者に見せる文字列を、設計の要素表と突き合わせる canon テストで固定した**（`ExceptionType`・`ResponseStatus` が本 IT で出る）
- [x] **内部の列挙名を利用者に見せていない**
- [x] **クラスタ E2E が自分の作ったデータを名指しで探していない**
- [x] SonarQube の Quality Gate がバックエンド・フロントエンドとも PASS
- [x] `npx gulp okf:check` が ERROR 0
- [x] ユーザーマニュアルの該当章が更新され、画面キャプチャが再生成されている
- [ ] **並列レビューをクローズの最初に起動した**
- [ ] **全体ビルドで落ちたテストは、単独で再実行して切り分けたうえで記録した**
- [ ] **クローズで計画を更新するとき、文書内の `- [ ]` を全部数えてから埋めた**

## 引き継ぎ枠（SP 対象外・**IT の序盤に独立コミットで消化**）

IT9 から 9 件を受けています。**負債枠 1** と合わせ、重いものから 4 件を序盤で返します。残りはタスク行に統合します。

| # | 内容 | 扱い |
| :--- | :--- | :--- |
| H.1 | **不変条件 4（通関未済の引取を拒否）と US16** | **本 IT の T2**（引取そのもの）。通関の検査は US29（IT12）なので、IT10 は**荷受人の確認で代替する**（`release_plan.md:205`） |
| H.3 | **購読側のコマンドが冪等でない**（`Cargo.recordHandling` が activityId を見ない） | **引き継ぎ枠 A**。Event Processor は at-least-once で、**リプレイでイベントストアに重複が積まれる** |
| H.4 | **荷役エンドポイントの認可がメソッド指定なし・肯定否定テストなし** | **引き継ぎ枠 B**。本 IT で S43・S42 の経路を足すので、同じ形の穴を増やす前に直す |
| H.7 | **クラスタ E2E 1 件が投影の反映待ちで不安定** | **引き継ぎ枠 C**。本 IT で E2E を増やすので、待ち方を先に堅くする |
| H.2 | `ApiExceptionHandler` の 4 サービス複製 | **負債枠**（1 コミット。`shared` へ移す） |
| H.5 | `isOffRoute` の判定がフロントに書き直されている | **T2 に統合**（S50 をもう一度触るため） |
| H.6 | 荷役の時刻が港のローカル時刻でなく JST 固定 | **IT11 へ送る**。時刻の扱いを変える変更で、US16・US19 のどちらとも独立。本 IT に入れると 2 つの変更が混ざる |
| H.8 | モバイル幅の下部タブ（荷役ロール） | **T2 に統合**（S50 を触るときに。タブは「本日の航海」と「引取待ち」の 2 つ——**US16 で引取待ちが実体を持つ**） |
| H.9 | レビュー中低 12 件 | 下表 |

### IT9 レビュー中低の行き先

| # | 指摘 | 行き先 |
| :--- | :--- | :--- |
| M3 | 表示上限 200 が「記録済みか」の正しさを変える | **負債枠**（件数を数える経路を上限のないクエリに分ける） |
| M5 | `ON CONFLICT DO NOTHING` の PostgreSQL 方言が方言スモークで未確認 | **枠 B で確認済み・対処不要**。このプロジェクトに第二方言（H2 等）は無く、全統合テストが Testcontainers の PostgreSQL 16 で走る。`ON CONFLICT` は**本番と同じ方言で実行されている**。方言スモークを置く動機（本番とローカルで解釈が違う）が発生していない |
| M6 | 遷移表が許さない荷役を `advance` が無言で捨てる | **T4 に統合**（例外の投影を作るので、届かなかった荷役も同じ受け皿に残せる） |
| M7 | 受け入れ／IT の前提データが購読配線を判別しない | **T1b に統合**（契約を 1 本足すので、実バスを通す往復テストを同じ枠で） |
| M8 | 未来日時の境界（`completedAt == now`）が未固定 | **引き継ぎ枠 B**（1 行） |
| M9 | 「直近 24 時間」の時間窓がテストから無効化 | **引き継ぎ枠 B**（1 行） |
| M10 | 荷役エンドポイントの認可テスト | **引き継ぎ枠 B**（H.4 と同じ） |
| M13 | 履歴の取消行に「誰がいつ取り消したか」が出ない | **T2 に統合** |
| M14 | 一覧の「記録済」が種別を区別しない | **T2 に統合**（引取が入ると同一港で荷降し→引取が起きる。**本 IT で実害が出る**） |
| M15 | 対象一覧の行から記録を始められない | **T2 に統合** |
| M16 | 荷主は「N 件変わった」あと、どれが変わったか追えない | **IT11 へ送る**（追跡一覧の絞り込みを足す変更。US16・US19 と独立） |
| L1〜L5 | フィールド宣言の位置ほか | **負債枠**（余れば） |

## タスク

| # | タスク | ストーリー | 見積 |
| :--- | :--- | :--- | :--: |
| T0 | **US を終えるたびに SonarQube を回す**（IT9 で守れた。継続） | — | 2h |
| A | **引き継ぎ枠 A**：`Cargo.recordHandling` / `revertHandling` を activityId で冪等にする。**リプレイで重複が積まれることを赤で見てから直す** | — | 3h |
| B | **引き継ぎ枠 B**：荷役の認可にメソッド指定と肯定否定テスト。未来日時の境界（`== now`）。時間窓のテスト（`withinHours` を既定に戻す） | — | 3h |
| C | **引き継ぎ枠 C**：クラスタ E2E の投影待ちを堅くする（**一定時間保ち続ける形**にする。最初に成功した時点で抜けると偽陰性） | — | 2h |
| T1 | **`TrackingException` エンティティと `ExceptionType` / `ResponseStatus`**。**緊急かどうかは種別が答える**（不変条件 7。属性に持たない）。要素表と突き合わせる canon テスト | US19 | 4h |
| T1b | **契約 `CargoDeliveredEvent(trackingNumber, bookingId, deliveredAt, location)` を先に置く**（`development_strategy.md` の Phase 0）。**購読側は billingms と bookingms の 2 つ**。発行側と購読側の両方にゴールデンと Axon Server 経由の往復テスト。**契約ロスターの件数（11 → 12）を固定する ArchUnit も同じ変更で直す**。**M7 の「実バスを通す」もここで** | US16 | 4h |
| T2 | **S50 に引取を戻す**（`CLAIM` のガードを荷受人確認の有無に置き換える）。**H.5・H.8・M13・M14・M15 を同じ変更で**——判定をサーバへ、下部タブ（本日の航海 / 引取待ち）、取消行に誰がいつ、記録済を種別で区別、行から記録を始める | US16 | 7h |
| T3 | **`CargoDeliveredEvent` の発行**（`AdvanceTrackingCommand(CLAIM)` が状態更新と契約の 2 つを出す）。**1 つのイベントに両方の役割を持たせない**。**bookingms 側の受け口**（`BookingReactionHandler` → `MarkDeliveredCommand` → `BookingDeliveredEvent`。UC14） | US16 | 4h |
| T2e | **US16 のクラスタ E2E**（Try T3。イメージを作り直して載せ直し、S50 で引取を記録して「引取済」まで見る）。**US16 を閉じる前に回す** | US16 | 2h |
| T4 | **例外の起票・対応開始・解決**（`registerException` / **`startResponding`** / `resolveException`。`RegisterTrackingExceptionCommand`・`StartExceptionResponseCommand`・`ResolveTrackingExceptionCommand`。`domain-model.md:765`）と `TrackingActivity` の不変条件 5・6。**`statusBeforeException` へ戻る**。**M6（届かなかった荷役）も同じ受け皿に** | US19 | 6h |
| T5 | **`tracking_exception` 投影**（`INDEX(response_status, urgent DESC, occurred_at)`）と **`tracking_event` への追記**（`event_type` = `EXCEPTION` / `RESOLVED`。`data-model.md:572`。**S41 の履歴に例外が出る受け皿はここ**。M6 の「届かなかった荷役」も同じ表へ）と `tracking_summary` の `open_exception_count` / `urgent_exception_count` / `status_before_exception` と `INDEX(urgent_exception_count DESC, last_status_changed_at)`（`data-model.md:571`）。**一覧が `tracking_exception` を数えない**。**追記系は元イベントの識別子を PK にする**（リプレイで行を増やさない） | US19 | 5h |
| T6 | **`FindOpenExceptionsQuery()`**（`domain-model.md:1381`。`ExceptionType#urgent` を先頭、以降は **`tracking_summary.estimated_arrival` との JOIN で残日数が少ない順**）と **S43 例外起票**（`/tracking/:trackingNumber/exceptions/new`）・**S42 例外一覧**（`/tracking/exceptions`。既定で解決済を外す）。**`navigation.ts` と `navigationMatchesUiDesign.test.ts` を同じ変更で更新する**。**N2（S42・S43 の節）を `ui_design.md` に足す** | US19 | 7h |
| T6e | **US19 のクラスタ E2E**（Try T3。起票 → 対応開始 → 解決で例外前の状態へ戻るところまで）。**US19 を閉じる前に回す** | US19 | 2h |
| T7 | **S41 に例外の対応開始・解決を足す**（楽観的更新）。**`ExceptionShipperNotifiedEvent` で通知した事実を記録**（送信基盤はスコープ外）。**N1 を `ui_design.md:120` と `domain-model.md` に反映する** | US19 | 4h |
| T8 | 認可の宣言（例外の経路）と HTTP の配線。**メソッド込みで宣言し、そのロール以外が 403 になることを検査する** | US19 | 3h |
| T9 | **負債枠**：`ApiExceptionHandler` を `shared` へ移す（4 サービスの複製 139 行）。M3（表示上限が正しさを変える）。余れば L1〜L5 | — | 4h |
| T10 | 受け入れテスト（デモ 9 件）・マニュアル（**14 章 引取と例外**）・全体のクラスタ E2E（T2e・T6e で US ごとに回した後の通し） | — | 8h |
| **合計** | | | **73h** |

### 既にあるもの（**着手前に `grep` で確かめた**）

| 対象 | 状態 | 本 IT での扱い |
| :--- | :--- | :--- |
| `TrackingException`・`ExceptionType`・`ResponseStatus` | **実装 0 件** | T1 で新設 |
| `RegisterTrackingExceptionCommand` ほか 2 本 | **実装 0 件** | T4 で新設 |
| `CargoDeliveredEvent` | **実装 0 件**（契約に無い） | T1b で新設（**契約**） |
| `tracking_exception` テーブル | **実装 0 件** | T5 で新設 |
| `TransportStatus.afterHandling("CLAIM")` | **実装済み**（`DELIVERED` を返す） | T3 でその先を繋ぐ |
| `HandlingType.requiresConsigneeConfirmation()` | **実装済みだが本番未使用**（IT9 で `CLAIM` を断るガードにだけ使用） | T2 で**確認の有無を見る形**に置き換える |
| `handling_activity.consignee_name` | **投影に列がある**（IT9 で作成済み） | T2 で書き手を繋ぐ |
| `ShipperNotifiedEvent` | **実装済み**（IT6・US12）だが **bookingms の内部イベント**（契約ロスター 11 件に無い） | **再利用しない**。T7 で trackingms 自身の `ExceptionShipperNotifiedEvent` を新設（N1） |
| `tracking_event` | **実装済み**（IT8） | T5 で `EXCEPTION` / `RESOLVED` を書き足す |
| `tracking_summary.estimated_arrival` | **実装済み**（IT9） | T6 の並び順（残日数）の導出元 |
| `FindOpenExceptionsQuery` | **実装 0 件** | T6 で新設 |

## 設計

### ドメインモデル図（本 IT のスコープ）

```plantuml
@startuml
title IT10 スコープのドメインモデル（Tracking / Handling）

class TrackingActivity <<AggregateRoot>> {
  - trackingNumber: TrackingNumber
  - status: TransportStatus
  - statusBeforeException: TransportStatus [0..1]
  + registerException(cmd): TrackingExceptionRegisteredEvent
  + startResponding(cmd): ExceptionResponseStartedEvent
  + resolveException(cmd): TrackingExceptionResolvedEvent
  + notifyShipperOfException(cmd): ExceptionShipperNotifiedEvent
}

class TrackingException <<Entity>> {
  - exceptionId: TrackingExceptionId
  - type: ExceptionType
  - responseStatus: ResponseStatus
  - occurredAt: Instant
  - location: Location [0..1]
  - description: String
  - resolution: String [0..1]
}

enum ExceptionType {
  DELAY
  DAMAGE
  LOSS
  MISROUTE
  CUSTOMS_HOLD
  + urgent(): boolean
}

enum ResponseStatus {
  REPORTED
  RESPONDING
  RESOLVED
}

class HandlingActivity <<AggregateRoot>> {
  - type: HandlingType
  - consigneeName: String [0..1]
  + register(cmd): HandlingActivityRegisteredEvent
}

TrackingActivity *-- "0..*" TrackingException
TrackingException *-- ExceptionType
TrackingException *-- ResponseStatus
@enduml
```

### 状態遷移図（本 IT で通る経路）

```plantuml
@startuml
title IT10 で通る TransportStatus の経路

AWAITING_CLAIM --> DELIVERED : CLAIM（荷受人の確認あり）
DELIVERED --> [*] : 精算の開始条件（CargoDeliveredEvent）

RECEIVED --> EXCEPTION : 遅延の起票
LOADED --> EXCEPTION
IN_TRANSIT --> EXCEPTION
UNLOADED --> EXCEPTION
AWAITING_CLAIM --> EXCEPTION

EXCEPTION --> RECEIVED : 解決（statusBeforeException へ戻る）
EXCEPTION --> LOADED : 解決
EXCEPTION --> IN_TRANSIT : 解決
EXCEPTION --> UNLOADED : 解決
EXCEPTION --> AWAITING_CLAIM : 解決

note right of EXCEPTION
  **戻る先は集約が覚えている。**
  起票時に statusBeforeException へ
  写し、解決で戻す（不変条件 5）。
  起票中の例外がすべて RESOLVED で
  なければ戻さない。
end note
@enduml
```

### ER 図（本 IT で足す表）

```plantuml
@startuml
title tracking_read_db（IT10 で足す分）

entity "tracking_summary" as ts {
  * **tracking_number**: VARCHAR(25) <<PK>>
  --
  transport_status: VARCHAR(30) NOT NULL
  status_before_exception: VARCHAR(30)
  open_exception_count: INTEGER NOT NULL DEFAULT 0
  urgent_exception_count: INTEGER NOT NULL DEFAULT 0
  delivered_at: TIMESTAMPTZ
  .. 既存（IT7-IT9） ..
  shipper_id / booking_id / current_unlocode
  estimated_arrival / last_status_changed_at
}

entity "tracking_event" as te {
  * **event_id**: VARCHAR(36) <<PK>>
  --
  tracking_number: VARCHAR(25) NOT NULL
  event_type: VARCHAR(20) NOT NULL
  occurred_at: TIMESTAMPTZ NOT NULL
}

entity "tracking_exception" as tx {
  * **exception_id**: VARCHAR(36) <<PK>>
  --
  tracking_number: VARCHAR(25) NOT NULL <<FK>>
  exception_type: VARCHAR(30) NOT NULL
  response_status: VARCHAR(30) NOT NULL
  urgent: BOOLEAN NOT NULL
  unlocode: VARCHAR(5)
  description: TEXT NOT NULL
  resolution: TEXT
  occurred_at: TIMESTAMPTZ NOT NULL
  resolved_at: TIMESTAMPTZ
  projected_at: TIMESTAMPTZ NOT NULL
}

ts ||--o{ tx
ts ||--o{ te
@enduml
```

**`urgent` は `ExceptionType#urgent` の結果を写します**（`data-model.md:573`）。判定を投影に書き直しません。件数を `tracking_summary` に非正規化するのは、**一覧が `tracking_exception` を数えないため**です。索引は `INDEX(urgent_exception_count DESC, last_status_changed_at)`（`data-model.md:571`）。`tracking_event` は **IT8 で作ってある既存の表**で、本 IT では `event_type` に `EXCEPTION` / `RESOLVED` を書き足すだけです（`data-model.md:572`）——**S41 の履歴に例外が出る受け皿はここ**で、`tracking_exception` は一覧（S42）用です。

**例外一覧の並び順**は `tracking_exception` だけでは出せません（残日数を導ける列が無い）。`tracking_summary.estimated_arrival` と JOIN して `urgent DESC, (estimated_arrival - 今日) ASC` で並べます（N4）。

### 画面遷移図（本 IT のスコープ）

```plantuml
@startuml
title IT10 の画面遷移

state "S02 ダッシュボード" as S02
state "S40 追跡一覧" as S40
state "S41 追跡詳細・管理" as S41
state "S42 例外一覧" as S42
state "S43 例外起票" as S43
state "S50 荷役作業記録" as S50
state "S51 荷役履歴" as S51

S02 --> S42 : 未解決の例外 N 件
S02 --> S50 : 作業のある航海
S40 --> S41
S41 --> S43 : 例外を起票する
S43 --> S41 : 起票直後の詳細へ
S42 --> S41 : 対象の追跡へ
S41 --> S41 : 対応開始・解決（楽観的更新）
S50 --> S51 : 追跡番号
@enduml
```

**S02 に「未解決の例外 N 件」を出し、S42 へ繋ぎます**（IT4 の教訓「気づく手段は次の行動へ繋ぐ」）。追跡管理者の入口です。

## デモ項目（9 件。**すべて受け入れテストに落とす**）

| # | デモ | US |
| :--- | :--- | :--- |
| 1 | 荷受人の確認なしで引取を送ると断られる | US16 §1・§2 |
| 2 | 確認を入れて引取を記録すると「引取済」になる | US16 §2・§3 |
| 3 | 引取が精算へ伝わる（`CargoDeliveredEvent` が billingms に届く） | US16 §4 |
| 3b | 引取が予約へ伝わる（bookingms の予約が「引取済」になる。UC14） | US16 §4 |
| 4 | 遅延を起票すると「例外発生」になる | US19 §1・§2 |
| 5 | 通知した事実が S41 の履歴（`tracking_event`）に残る | US19 §3 |
| 7 | 対応内容を入れて解決すると、**例外前の状態へ戻る** | US19 §4 |
| 8 | 未解決の例外が残っているあいだは戻らない | 不変条件 5 |
| 9 | 例外一覧が**紛失 → 残日数が少ない順**に並び、解決済は既定で出ない | US19 §5 |

## リスク

| # | リスク | 対応 |
| :--- | :--- | :--- |
| R1 | **`CLAIM` のガードを外すとき、不変条件 4（通関未済の拒否）が無いまま `DELIVERED` へ進む経路が開く** | IT9 で「検査できない段階で開けない」ためにガードを入れた。**外すのと同時に荷受人確認の検査を入れる**。通関は US29（IT12）で足す——`release_plan.md:205` の決定どおり。**`ui_design.md:151` は S50 が通関未済を判定時点で断ると書いているので、但し書きを足す**（N3） |
| R2 | `CargoDeliveredEvent` は **billingms が購読する契約**。追記専用で後戻りできない | T1b で先に置き、**購読側の投影が作れる分**を運ぶことを確かめる（`trackingNumber`・`bookingId`・`deliveredAt`・`location`。**購読側は billingms と bookingms の 2 つ**） |
| R3 | 例外は `TrackingActivity` の中のエンティティ。**集約が大きくなる** | 例外は追記のみで解決しても消えない（不変条件 6）。件数が増えたら投影で読む。**集約は起票中の例外の解決状態だけを持つ** |
| R4 | 通知の送信基盤がスコープ外なので、§3 が「記録するだけ」になる | `ui_design.md:120` の決定どおり。**受入基準の表に「記録で満たす」と書く**——満たしたことにせず、何で満たしたかを残す。ただし `ui_design.md:120` の US 一覧に US19 が無く、記録先も bookingms のイベントに固定されている。**trackingms 自身のイベントを新設し、設計にも反映する**（N1） |

## DoD

- [x] US16・US19 の受入基準（上の表）を 1 項目ずつ埋めた。**未達は理由を書いた**
- [x] デモ項目 9 件の受け入れテストがすべて緑。**対応はテスト名でなく本文のアサーションで確かめる**
- [x] 引き継ぎ枠 A・B・C と負債枠が消化されている、または送った理由がふりかえりに書かれている（A・B・C・負債枠すべて消化。H.6 は計画どおり IT11 へ、M16 も IT11 へ）
- [x] 本 IT で足した検査を壊して赤を見た
- [x] **`ExceptionType#urgent` が緊急を答える**（属性に持たない・投影は結果を写す）
- [x] **`statusBeforeException` へ戻ることを検査した**（起票中の例外が残っていれば戻らないことも）
- [ ] **契約 `CargoDeliveredEvent` の両側にゴールデンと Axon Server 経由の往復テストがある**（**一部未達**: ゴールデンは両側にある。**実バスの通過はクラスタ E2E で実証**した——trackingms が出した `CargoDeliveredEvent` を billingms と bookingms が受け、予約が「配送完了」になるところまで画面から見た。`ContractEventRoundTripIT` への追加は見送った: この harness は 2 サービスを起動して片方の DB を見る形で、`CargoDeliveredEvent` の購読側（bookingms）は集約の存在を前提にするため、予約の全経路を IT の中で作り直すことになる。**同じことをクラスタ E2E がより実物に近い形で確かめている**。IT11 で harness に前提づくりの共通部品を入れるか、この項目を落とすかを判断する）
- [x] **新しい投影（`tracking_exception`）がリプレイと再配送で行を増やさない**
- [x] `./gradlew build` が緑・`TZ=UTC ./gradlew build` が緑（**フルビルドが本物の欠陥を 2 件出した**——共有カーネルへ移した対応表の検査漏れと、US19 で足した 3 クラスの分岐不足。どちらもモジュール別のテストでは緑だった）
- [x] フロントの `npm run test`・`npx tsc -b`・`npm run build` が緑
- [x] **新しい経路が `RoleAuthorization` にメソッド込みで宣言され、そのロール以外は 403 になることを検査した**（IT9 未達）
- [x] UI 設計・navbar・ダッシュボード・到達性テストの 4 点が一致している
- [x] **追跡管理者が S42・S43 にたどり着ける**。**荷役ロールの下部タブが「本日の航海」と「引取待ち」の 2 つ**（US16 で引取待ちが実体を持つ）
- [x] **内部の列挙名を利用者に見せていない**
- [x] **kind クラスタで動く**：イメージを作り直して載せ直し、載ったことを確かめ、全 Pod が Ready
- [x] **クラスタに対して E2E が緑**（**US ごとに 1 度**。T2e・T6e のタスク行で回した。終盤に集めない）。**通しでも 18 件すべて緑**——IT9 は 15/16 だった
- [x] `npx gulp okf:check` が ERROR 0
- [x] SonarQube の Quality Gate がバックエンド・フロントエンドとも PASS
- [x] **ユーザーマニュアルが更新されている**（14 章 引取と例外）。**キャプチャは本文が説明する要素を写す**
- [x] **注 N1〜N4 を設計ドキュメントに反映した**（`ui_design.md`・`domain-model.md`・`data-model.md`）
- [x] **適用済みマイグレーションを編集していないことが検査で固定されている**（Try T6）
- [ ] ふりかえり（`retrospective-10.md`）と完了報告書（`iteration_report-10.md`）を作成した

## 関連ドキュメント

- [リリース計画](release_plan.md)・[開発戦略](development_strategy.md)
- [IT9 ふりかえり](retrospective-9.md)・[IT9 完了報告書](iteration_report-9.md)・[IT9 実装レビュー](../../review/cargo-tracker/IT9実装_review_20260908.md)
- [ユーザーストーリー](../../requirements/user_story.md)（US16:383・US19:441）
- [ドメインモデル](../../design/cargo-tracker/domain-model.md)（Tracking の不変条件 5・6・7）
- [データモデル](../../design/cargo-tracker/data-model.md)（`tracking_exception`:541）
- [UI 設計](../../design/cargo-tracker/ui_design.md)（S42:146・S43:147）

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-09-08 | `validating-iteration-plan` / `validating-design` の指摘を反映（**設計が正**）。`CargoDeliveredEvent` を正典どおり 4 項目にし、**bookingms も購読側**であることを受入基準・T1b・T3・デモに足した。`startResponse` → **`startResponding`**。`tracking_event` への書き込み（`EXCEPTION` / `RESOLVED`）を T5 に、`FindOpenExceptionsQuery` を T6 に立てた。例外一覧の並び順を `tracking_summary.estimated_arrival` との JOIN と明記。**Try T6（適用済みマイグレーションの検査）と M5（方言スモーク）の行き先**を足した。**US ごとのクラスタ E2E を独立タスク T2e・T6e に立てた**（Try T3。IT8・IT9 と同型の未達を避ける）。**`ShipperNotifiedEvent` は bookingms の内部イベントで trackingms から使えない**ため、US19 §3 の手段を `ExceptionShipperNotifiedEvent` の新設に差し替え、設計側の欠落を注 N1〜N4 として明記。見積 62h → 73h | claude-code/claude-opus-5 |
| 2026-09-08 | IT10 計画を作成。**受入基準の表を着手前に作った**（IT9 の Try T1）。IT9 の引き継ぎ 9 件とレビュー中低 12 件の行き先を 1 件ずつ書いた。**着手前の `grep` で、US16・US19 の主要な部品が実装 0 件であることを確かめた**（`TrackingException`・`ExceptionType`・`CargoDeliveredEvent`・`tracking_exception`）。`HandlingType.requiresConsigneeConfirmation` と `handling_activity.consignee_name` は IT9 で作ってあり、T2 で書き手を繋ぐ | claude-code/claude-opus-5 |
