---
type: ADR
title: "ADR-0010 サービスをまたぐ連鎖の調整役を Reaction Handler に一本化する"
description: "予約から追跡開始までの連鎖を BookingReactionHandler + processstate で表し、追跡番号の採番と発行者、そして補償の粒度を決める。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-06T22:39:06Z }
---

# ADR-0010 サービスをまたぐ連鎖の調整役を Reaction Handler に一本化する

予約から追跡開始までの連鎖を `BookingReactionHandler` + `process_state` で表し、追跡番号の採番と発行者、そして補償の粒度を決める。

日付: 2026-09-06

## ステータス

2026-09-06 提案されました。[ADR-0001](0001-cqrs-es-with-axon-in-microservices.md)（Axon による CQRS/ES）決定 6 を実装で具体化するもの。

## コンテキスト

IT7 は**サービスをまたぐ最初の連鎖**である。予約を確定して追跡番号を発行すると、trackingms に追跡が作られる。これまでのサービス間はイベントの一方向で、コマンドを送る向きは初めて通る。

着手前の検証で、設計ドキュメントどうしが食い違っていることが分かった。

- `architecture_backend.md` の「Saga パターン（Axon Saga）」節と `release_plan.md:208` は `@Saga` / `@StartSaga` / `@EndSaga` を前提に書かれていた
- しかし **ADR-0001 決定 6 は「Axon 5 に Saga の API が無い」ことをスパイクで確認済み**で（5.0.0 / 5.1.0-RC2 / 5.3.1 のどの jar にも `Saga` / `SagaLifecycle` が 0 件）、`SagaIsStillAbsentTest` が「Saga が現れたら赤にする」検査として置かれていた

**選び直す余地は無い。** `@Saga` はそもそもコンパイルできないので、計画のとおりに着手していれば初日に破綻していた。決めるべきは「古い記述をどう畳むか」と、**実装で初めて決まること**である。

加えて 3 つの判断が要る。

1. **追跡番号を誰が採るか。** 集約が `MAX + 1` を採ると、同時に 2 件発行したときに同じ番号が出る
2. **誰が発行するか。** `ui_design.md:351` は `CONFIRMED : [追跡番号を発行]（経路設計者）` と書き、`domain-model.md:566` は「経路設計者 / Reaction Handler」の両方を許している
3. **連鎖が失敗したらどうするか。** trackingms が落ちていて追跡開始が届かないとき、どこまで再試行し、何を戻し、誰に見せるか

## 決定

### 決定 1: 古い Saga の記述を ADR-0001 に追随させる（選び直さない）

`architecture_backend.md` の「Saga パターン」節を「連鎖の調整（Reaction Handler + `process_state`）」に書き換え、`release_plan.md` の `BookingSaga` を `BookingReactionHandler` に直す。**Saga の各機能の置き換え表**を同じ節に置く。

| Saga の機能 | 置き換え |
| :--- | :--- |
| `@StartSaga` | 1 段目が `process_state` に `RUNNING` の行を作る |
| `SagaLifecycle.associateWith()` | `process_type` + `process_id`（予約なら `bookingId`） |
| `@EndSaga` | 最後の段で `status = 'COMPLETED'`。**行は消さない** |
| Deadline | `RUNNING` かつ 24 時間より古い行を `gulp reaction:stuck` で走査 |
| Saga Store | `process_state`。**止まった位置がそのまま SQL で読める** |

`saga_entry` / `association_value_entry` は作らない。

### 決定 2: 追跡番号の採番は投影側

`booking_number`・`shipper_code` と同じくデータベースのシーケンス（`tracking_number_seq`）で採る。**集約は「発行してよいか」だけを判断し、番号は渡されたものを載せる。**

採番の窓口は `TrackingNumberGenerator` ポートにする。発行の入口（Controller）が投影のマッパーを直に触ると、何を頼んでいるのかが型から読めなくなる。

**採ってから断られることがある**（二重発行を集約が断る）。そのとき採った番号は使われずに飛ぶ。番号が連続しないことより、同じ番号が 2 つ出ないことを優先する。

### 決定 3: 発行は経路設計者の操作にする（本 IT では自動発行を作らない）

`POST /api/v1/booking/bookings/{bookingId}/tracking-number`（ROLE_ROUTING）。**連鎖は発行された「あと」から始まる。**

`domain-model.md:566` は Reaction Handler による自動発行も許しているが、本 IT では作らない。自動にすると `ui_design.md:351` が定める経路設計者の操作が消え、**US14 の「として: 経路設計者」が受入基準 1 と結びつかなくなる**。自動発行が要る業務（自動化の要望）が出てから足す。

確定したまま発行を忘れた予約は、S02（経路設計）に**行で**出す。US13 §受入基準 3 の「経路設計者への通知」は送信基盤がスコープ外なので、この受け皿で代える。

### 決定 4: 段の区切りと補償の粒度

連鎖は **2 段**（`INITIALIZE_TRACKING` → `TRACKING_INITIALIZED`）。

- **起票してからコマンドを送る。** 送ってから起票すると、trackingms の応答のほうが先に届いて「行が無いのに 2 段目が来る」
- **送れたところまでを 1 段目の完了とする。** 届いていないのに次の段へ進めると、滞留の走査から漏れる
- **再試行は 3 回まで**。1・2 回目は例外を投げ直して Event Processor に再試行させる。回数は `process_state` の `metadata.attempts` に持つ（プロセスをまたぐので変数では数えられない）
- **上限を超えたら補償する。** `RevertTrackingNumberCommand` を送って予約を `CONFIRMED` に戻し、`process_state` を `COMPENSATED` にして、`attention_item`（**`ROLE_ROUTING`**）に出す。**追跡管理者ではなく経路設計者に宛てる**——発行し直せるのは経路設計者だけで、気づく手段はその人が次に取れる行動へ繋がらなければ意味がない。**キャンセルではない**ので、経路設計者がもう一度発行できる
- **滞留は 24 時間**。`RUNNING` のまま古い行を `gulp reaction:stuck` が走査する

## 影響

### よい影響

- **止まった連鎖が SQL で読める。** Saga のストアに直列化して埋めるのと違い、滞留の一覧化も管理画面もふつうの SQL で書ける
- **設計ドキュメントの食い違いが畳まれる。** `@Saga` を前提にした記述はコンパイルできないので、残しておくと次に読んだ人が同じ計画を書く
- **同時発行で番号が衝突しない。** 採番をデータベースに任せた

### 悪い影響・制約

- **調整役は投影と別の Processing Group に置かなければならない**（パッケージで分ける。`@ProcessingGroup` は Axon 5 に無い）。同じにすると投影のリプレイでコマンドが再送され、追跡が作り直される
- **再試行の回数を自分で数える。** Saga のインフラが隠していたものを明示的に持つぶん、書く量は増える
- **番号が連続しない**ことがある（採ってから断られた場合）

## 検査

**決定の数だけ検査を対応させる。** 文章のまま残った決定は守られない（IT5・IT6 の教訓）。

| 決定 | 検査 |
| :--- | :--- |
| 1 | `SagaIsStillAbsentTest`（Saga が現れたら赤）。`ReplayIT#projectionsCannotSendCommands`（投影が `CommandGateway` を持たない＝リプレイで連鎖が走り直さない） |
| 2 | `CargoTrackingNumberTest#rejectsBlankTrackingNumber`（集約は採らない）。`BookingControllerIT`（発行すると番号が付く） |
| 3 | `EveryServiceEndpointIsRoutedAndProtectedTest`（`POST /tracking-number` に ROLE_ROUTING の宣言がある）。`BookingDetailPage.test.tsx`「営業には発行の操作を出さない」 |
| 4 | `BookingReactionHandlerTest#startsTheProcessBeforeSending`（送る前に起票）・`#rethrowsUntilTheLimit`（上限までは投げ直す）・`#compensatesAfterTheLimit`（補償して要確認一覧に出す） |

## 代替案

| 案 | 内容 | 却下の理由 |
| :--- | :--- | :--- |
| Axon の Saga を使う | `@Saga` + `SagaLifecycle` | **API が存在しない**（ADR-0001 決定 6）。コンパイルできない |
| 途中経過を集約に持つ | `Cargo` に「連鎖のどこまで進んだか」を持たせる | 予約の業務状態と連鎖の技術的な進捗が混ざる。予約は `CONFIRMED` のまま連鎖だけが止まる、という状態を表せない |
| 追跡番号を集約で採る | `Cargo` が `MAX + 1` | 同時に 2 件発行したときに同じ番号が出る |
| 追跡番号を自動発行する | `BookingConfirmedEvent` を受けて Reaction Handler が発行 | `ui_design.md` が定める経路設計者の操作が消え、US14 の主語が受入基準と結びつかなくなる |
| 補償で予約をキャンセルにする | `CancelBookingCommand` | 荷物も予約も生きている。キャンセルは荷主の意思表示（US30）であって、システムの都合ではない |

## 関連

- [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) — 決定 6（Axon 5 に Saga が無い）
- [ADR-0009](0009-condition-review-is-not-a-state-transition.md) — 直前の UC08 の判断
- [バックエンドアーキテクチャ](../../design/cargo-tracker/architecture_backend.md)「連鎖の調整」
- [データモデル](../../design/cargo-tracker/data-model.md)「連鎖の途中経過（process_state）」
- [イテレーション 7 計画](../../development/cargo-tracker/iteration_plan-7.md)
