---
type: ADR
title: "ADR-0012 CargoSnapshot は TrackingInitializedEvent から作る"
description: "handlingms が予定ルートの判定に使う CargoSnapshot の元イベントを、購読できる契約イベントに決め直す。正典が指定していた TrackingNumberIssuedEvent は bookingms の内部イベントで購読できない。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-07T12:27:56Z }
---

# ADR-0012 CargoSnapshot は TrackingInitializedEvent から作る

handlingms が予定ルートの判定に使う `CargoSnapshot` の元イベントを、購読できる契約イベントに決め直す。

日付: 2026-09-07

## ステータス

2026-09-07 提案し、IT9 で実装して採用しました。[ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md)（読み取りモデルの方針）の範囲内で、`data-model.md` と `domain-model.md` の指定を訂正するもの。

## コンテキスト

IT9 で handlingms が動き出します。荷役の記録では、**作業場所が予定ルートに含まれるか**を判定します（US15 §受入基準 7）。判定には貨物の旅程が要り、`CargoSnapshot`（ACL の読み取りモデル）がそれを持ちます。

**正典が指定していた元イベントは購読できません。**

| 項目 | 内容 |
| :--- | :--- |
| 正典 | `data-model.md:648`「`cargo_snapshot` / `cargo_snapshot_leg` は `TrackingNumberIssuedEvent`, `CargoCancelledEvent`（**いずれも契約**）から作る」 |
| 実態 | `TrackingNumberIssuedEvent` は **bookingms の内部イベント**（`booking/domain/model/events/`）で、`shared/contract/event` に無い。`CargoCancelledEvent` は**存在しない**（US30・IT15） |
| 影響 | `CargoSnapshot` が作れないと予定外の判定ができず、**US15 §7 が満たせない** |

**IT8 で同じ形を踏んでいます。** `shipper_cargo_snapshot` も「購読できないイベントを元にする表」で、あのときは**表そのものを畳みました**（同じ情報が `tracking_summary.shipper_id` にあったため）。今回は表が要る（予定外の判定に使う）ので、**畳むのではなく元イベントを決め直します**。

**IT8 のふりかえりで Try に挙げた「正典の元イベントは、その BC から購読できるかを着手前に `grep` で確かめる」が効きました。** 着手前に見つけたので、実装の手戻りはありません。

## 決定

### 決定 1: `TrackingInitializedEvent`（契約）から作る

`shared/contract/event/TrackingInitializedEvent` は **`CargoSnapshot` が要る値をすべて持っています**。

| `CargoSnapshot` の項目 | `TrackingInitializedEvent` |
| :--- | :--- |
| `trackingNumber` | `trackingNumber` |
| `bookingId` | `bookingId` |
| `origin` / `destination` | `originUnLocode` / `destinationUnLocode` |
| `cargoType` | `cargoType` |
| `legs`（航海番号・積港・降港） | `legs`（`voyageNumber`・`loadUnLocode`・`unloadUnLocode`） |

**新しい契約を増やしません。** `TrackingNumberIssuedEvent` を昇格させると、同じ事実を運ぶ契約が 2 本になります（`TrackingInitializedEvent` は既に同じ内容を運んでいます）。**同じ事実を 2 か所に持つと、片方だけ直る形の食い違いが生まれます**（IT8 の `shipper_cargo_snapshot` と同じ判断）。

**契約は追記専用で、増やしたら減らせません。** 昇格は「その内容を運ぶ経路が他に無い」ときに行います。

### 決定 2: 発行者が trackingms になることを正典に反映する

`domain-model.md:215` は「`CargoSnapshot` は **Booking の契約イベント**を購読して Handling 側が作る」と書いています。実際の発行者は trackingms です。

**購読側から見ると発行者は問題になりません。** 必要なのは「その内容が契約として流れていること」で、どの BC が出したかではありません。**正典の記述を実態に合わせます**（`data-model.md:648`・`domain-model.md:215`）。

### 決定 3: `cancelled` 列は作るが、書き手は IT15 まで居ない

`data-model.md:591` の `cargo_snapshot.cancelled` は `CargoCancelledEvent` が書きます。このイベントは US30（IT15）で作ります。

**列は作ります**（`NOT NULL DEFAULT FALSE`）。中身の無い列を先に作らない決まりに対して、この列は**既定値が業務上正しい**（キャンセルされていない）ため、空欄を画面に出すことにはなりません。**画面には出しません**（本 IT の S50 はキャンセルを扱わない）。

### 決定 4: 旅程の時刻は写さない

`data-model.md:598-604` の `cargo_snapshot_leg` に時刻の列はありません。**予定の時刻は追跡側（`tracking_leg`）が持ちます。**

荷役が要るのは「**どの航海がどの港で積み降ろすか**」だけです。時刻まで写すと、同じ事実が 2 つの BC に増えます。

## 検査

**決定の数だけ検査を対応させる。**

| 決定 | 検査 |
| :--- | :--- |
| 1 | `CargoSnapshotProjectionIT#buildsFromTrackingInitialized`（契約イベントから作られる）。`ContractEventGoldenTest`（`TrackingInitializedEvent` の形が固定されている） |
| 2 | `CargoSnapshotProjectionIT`（trackingms が出したイベントを handlingms が購読できる。**実 Axon Server を通す**） |
| 3 | `CargoSnapshotProjectionIT#defaultsToNotCancelled`（既定が `false`） |
| 4 | `CargoSnapshotProjectionIT#keepsOnlyPortsAndVoyages`（区間の型に時刻が無く、写るのは航海と港だけ）。`ContractCarriesOnlyPlainValuesTest`（契約の形） |

## 代替案

| 案 | 内容 | 却下の理由 |
| :--- | :--- | :--- |
| `TrackingNumberIssuedEvent` を契約へ昇格 | 正典どおりの元イベントにする | 同じ事実を運ぶ契約が 2 本になる。**契約は追記専用で減らせない** |
| bookingms が `cargo_snapshot` を作って handlingms に読ませる | 表の置き場を変える | BC をまたいで DB を読むことになる（[ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md) に反する） |
| handlingms が bookingms へ問い合わせる（Query Bus） | 荷役のたびに聞く | 荷役は 1 隻から 20〜50 本を連続で記録する。**1 本ごとに他 BC へ往復すると現場が待つ** |

## 影響

- **`TrackingNumberIssuedEvent` は bookingms の内部イベントのままです。** 契約への昇格は、その内容を運ぶ経路が他に無いときに改めて判断します
- `CargoSnapshot` は**追跡が始まってから**作られます。予約を確定して追跡番号を発行するまで、荷役の対象になりません（業務の順序どおり）
- 正典を訂正します（`data-model.md` の表と Processing Group 一覧、`domain-model.md` の BC 関連図・イベント一覧・連鎖図）。**IT9 のレビューで、図と表に旧イベント名が残っていたことが分かり、すべて直しました**——図を読んで次のストーリーを設計すると、同じ「購読できないイベント」を踏みます

## 関連

- [ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md) — 読み取りモデルの方針
- [ADR-0010](0010-reaction-handler-as-the-only-coordinator.md) — サービスをまたぐ連鎖
- [データモデル](../../design/cargo-tracker/data-model.md)「handling_read_db」
- [ドメインモデル](../../design/cargo-tracker/domain-model.md)「Handling Context」
- [イテレーション 9 計画](../../development/cargo-tracker/iteration_plan-9.md) — T1
