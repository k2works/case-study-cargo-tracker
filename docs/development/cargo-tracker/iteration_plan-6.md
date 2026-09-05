---
type: Plan
title: "イテレーション 6 計画 - 条件調整と荷主への通知"
tags: [plan]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-05T15:00:59Z }
---

# イテレーション 6 計画 - 条件調整と荷主への通知

## 概要

| 項目 | 内容 |
| :--- | :--- |
| イテレーション | IT6（Release 0.2 経路設計と予約確定・**中盤**） |
| 期間 | 2 週間（開発 Day 1-10）+ クローズ Day 11-14 |
| ゴール | 経路が組めないときに**条件を変えて探し直すか、営業へ差し戻せる**ようにし、決まった経路を**荷主へ通知した事実を残せる**ようにする |
| 目標 SP | 8 SP（US10 3・US11 2・US12 3）+ **引き継ぎ枠（SP 対象外）**（[リリース計画](release_plan.md)） |
| 局面 | **中盤（インサイドアウト）**。[開発戦略](development_strategy.md) を参照 |

**IT5 で「候補が無い」と言えるようになりました。本 IT はその先を作ります。** 条件を変えて探し直す（US10）、営業へ差し戻す（US10 §受入基準 4）、通知した事実を残す（US12）。IT5 で未達として記録した **US09 §受入基準 4 は US10 の完成で満たされます**。

**サービス越しの新しいメッセージはありません。** 契約の名簿（イベント 11・コマンド 2・クエリ 1）は変わりません。IT5 で通した `FindRouteCandidatesQuery` に条件を足すだけです。

## ゴール

### イテレーション終了時の達成状態

1. **条件を変えて探し直せる。** 出発港と除外港を変えて再算出でき、現在の制約条件が画面から読める（US10）
2. **組めないときは営業へ差し戻せる。** 差し戻した予約は経路設計の作業一覧から外れ、**営業のダッシュボードに「条件の見直し依頼」として現れる**（US10 §受入基準 4）
3. **確定経路が予約に紐づいていることが検査で固定されている**（US11）。**IT5 で実装済みの振る舞いに、受入基準と対応する検査を置く**（下記「US11 の扱い」）
4. **荷主へ通知した事実が残る。** 通知内容（経由港・所要日数・到着予定日）を確認して通知すると `ROUTE_NOTIFIED` になり、通知履歴が予約詳細に出る（US12）
5. **通知した予約は経路設計へ戻せる。** `ROUTE_NOTIFIED → ROUTE_PROPOSED` の逆向きの遷移が働く

### 成功基準

- [ ] デモ項目の受け入れテストがすべて緑
- [ ] `TZ=UTC ./gradlew build` が緑（JaCoCo の層別閾値を含む）
- [ ] フロントの `npm run test`・`npx tsc -b`・`npm run build` が緑
- [ ] `./gradlew :acceptance-tests:test` が緑
- [ ] **層別カバレッジと SonarQube を、US ごとに 1 度回した**（IT5 の T4。**IT5 で守れなかったので、Day 4・Day 5・Day 8 のタスクとして置く**）
- [ ] **実環境で症状を見たときに、テストを直す前に実装を疑った**（IT5 の T1）。E2E に回避を書くなら、その回避が実装の欠陥を隠していない理由を 1 行で書く
- [ ] **javadoc・設計文書・テスト名に「〜する」と書いたら、同じ変更でその検査を書いた**（IT5 の T2）
- [ ] **画面から呼ぶ API を、HTTP の層で 1 本は通した**（IT5 の T3。正常系と、その API 固有の異常系）
- [ ] **受入基準を 1 行ずつ「本 IT で満たす / 未達（前提は何）」に割り当てた**（IT5 の T5。下記「受入基準の割り当て」）
- [ ] **追加・変更した画面を、それを見る他のロールから何が読めるか確かめた**（IT5 の T6）
- [ ] **内部の列挙名を利用者に見せていない**（IT5 の T7。マニュアルの注釈で埋め合わせない）
- [ ] **クラスタ E2E を Day 9 に 1 度、クローズ前にもう 1 度回した**
- [ ] SonarQube の Quality Gate がバックエンド・フロントエンドとも PASS
- [ ] `npx gulp okf:check` が ERROR 0
- [ ] ユーザーマニュアルの該当章が更新され、画面キャプチャが再生成されている（**05 章の撮り直しを含む**）
- [ ] **並列レビューをクローズの最初に起動し、切れていないか表の最終行を確かめてから統合した**（IT5 の T8）

## ユーザーストーリー

### 対象ストーリー

| ID | ストーリー | SP | 対応 UC | 主なサービス |
| :--- | :--- | :--: | :--- | :--- |
| US10 | 経路条件を調整して再算出する | 3 | UC08 | bookingms / routingms |
| US11 | 経路情報を予約に紐付ける | 2 | UC09 | bookingms |
| US12 | 確定経路を荷主に通知する | 3 | UC10 | bookingms |

### 受入基準の割り当て（IT5 の T5）

**費用のように目立つものだけを挙げると、IT5 の US09 §4 のような見落としが起きます。** 1 行ずつ割り当てます。

| ストーリー | 受入基準 | 本 IT | 備考 |
| :--- | :--- | :--- | :--- |
| US10 | 1. 現在の制約条件を確認できる | **満たす** | S31 の「条件」欄。出発港・除外港・期限・貨物種別 |
| US10 | 2. 条件を調整して再算出を実行できる | **満たす** | 出発港・除外港。**期限延長・貨物種別変更は予約の修正（US32・S24）が正典**なので、S31 からは触らず S24 へ導く |
| US10 | 3. 調整後の条件で新たな候補が算出・提示される | **満たす** | IT5 の探索に条件を渡すだけ |
| US10 | 4. 営業担当者に条件協議を依頼できる | **満たす** | 「営業へ差し戻す」。営業のダッシュボードに現れる |
| US11 | 1. 確定経路と予約番号を確認できる | **満たす（IT5 で実装済み）** | 予約詳細の旅程。本 IT で受入基準と対応する検査を置く |
| US11 | 2. 経路情報を予約に紐付ける操作を実行できる | **満たす（IT5 で実装済み）** | `POST /bookings/{id}/route` |
| US11 | 3. 紐付け後、予約状態が「経路提案中」に更新される | **満たす（IT5 で実装済み）** | 引き渡し（US06）の時点で既に `ROUTE_PROPOSED`。**紐付けで状態は動かない** |
| US12 | 1. 予約番号を指定して紐付けられた経路情報を確認できる | **満たす** | 通知前の確認画面（S22 内） |
| US12 | 2. 通知内容（経由港・所要日数・到着予定日・料金概算）を確認できる | **一部未達** | **料金概算は US21（IT13）が前提で未達**。経由港・所要日数・到着予定日は出す |
| US12 | 3. 荷主への経路通知を送信できる | **記録で満たす** | 送信基盤はスコープ外（[UI 設計](../../design/cargo-tracker/ui_design.md)）。通知した事実を記録し、送信は手作業 |
| US12 | 4. 通知送信記録が登録される | **満たす** | 通知履歴（宛先・要約・記録者・日時） |

**未達は 1 件です**（US12 §2 の「料金概算」）。IT5 の US08 §3・US09 §1 と同じ理由（US21・IT13 が前提）で、**数を合わせるために「完了」にはしません**。

### US11 の扱い（着手前の調査結果）

**US11 の受入基準 3 件は、IT5 で実装済みです。** 旅程の確認（IT5 で予約詳細に追加）・紐付け操作（`Cargo.assignRoute`）・状態（引き渡し時点で既に `ROUTE_PROPOSED`）のいずれも動きます。

**そこで本 IT の US11 は「受入基準に対応する検査を置くこと」を成果とします。** IT5 の学び（P2「書いた保証が空手形だった」）のとおり、**動いていることと固定されていることは別**です。受け入れシナリオを 3 本置き、`経路の紐付け.feature` として残します。

**浮いた工数は IT5 の引き継ぎ枠に充てます**（Day 1）。実績 SP は 8 のまま記録します（受入基準は満たしているため）。

### 依存関係

```mermaid
graph LR
    US08[US08 経路候補算出<br/>IT5 完了] --> US10[US10 条件調整]
    US09[US09 経路選択・確定<br/>IT5 完了] --> US11[US11 経路紐付]
    US09 --> US12[US12 経路通知]
    US11 --> US12
    US12 --> US13[US13 予約確定<br/>IT7]
    US10 -.->|受入基準 4 を満たす| US09
```

**US10 を先に作ります。** IT5 で未達として記録した US09 §受入基準 4（条件調整へ進める）が US10 に依存しているためで、先に片付けないと未達が 2 IT にまたがります。

## 設計

### 対象スコープの設計図

#### ドメインモデル（本 IT で触る部分）

```plantuml
@startuml
title IT6 のドメインモデル（bookingms）

package "Cargo 集約" {
  class Cargo <<AggregateRoot>> {
    + bookingId
    + bookingStatus : BookingStatus
    + routingStatus : RoutingStatus
    + itinerary : CargoItinerary
    --
    + requestConditionReview(cmd) : 営業へ差し戻す（US10）
    + notifyShipper(cmd) : 荷主へ通知（US12）
    + returnToRouting(cmd) : 経路設計へ戻す（US12）
  }

  class ShipperNotification <<ValueObject>> {
    + recipientEmail
    + summary
    + notifiedBy
    + notifiedAt
  }
}

package "イベント" {
  class ConditionReviewRequestedEvent <<Event>>
  class ShipperNotifiedEvent <<Event>>
  class RoutingRequestedEvent <<Event>>
}

Cargo *-- ShipperNotification
Cargo ..> ConditionReviewRequestedEvent : 差し戻し
Cargo ..> ShipperNotifiedEvent : 通知
Cargo ..> RoutingRequestedEvent : 経路設計へ戻す（再利用）
@enduml
```

**「経路設計へ戻す」は既存の `RoutingRequestedEvent` を再利用します。** 引き渡しと同じ事実（経路設計を依頼した）だからです。新しいイベントを足すと、`routing_requested_at` の書き手が 2 つになります。

**再利用したときに何が起きるかを確かめました**（`CargoProjection.java:109-114`）。この投影は `booking_status = ROUTE_PROPOSED`・`routing_status = ROUTING_REQUESTED`・`routing_requested_at = now` を書きます。つまり**経路設計へ戻すと、確定済みだった `routing_status` が `ROUTING_REQUESTED` に戻り、作業一覧に再び出ます**。これは望む振る舞いです。遷移表（`BookingStatus`）も `ROUTE_NOTIFIED → ROUTE_PROPOSED` を許しているので、集約側の変更は要りません。**確定した旅程（`cargo_leg`）は消しません**（再設計で入れ替わるまで残す）。

#### 状態遷移（本 IT で通る経路）

```plantuml
@startuml
title BookingStatus / RoutingStatus（IT6 スコープ）

state "PRELIMINARY 仮受付" as P
state "ROUTE_PROPOSED 経路提案中" as RP
state "ROUTE_NOTIFIED 通知済み" as RN

P --> RP : 経路設計を依頼（US06・IT3）
RP --> RN : 荷主へ通知（US12・**本 IT**）
RN --> RP : 経路設計へ戻す（US12・**本 IT**）
RN --> RN : 再通知（**本 IT**）

note right of RP
  routingStatus は別の軸で動く:
  ROUTING_REQUESTED → ROUTED（US09・IT5）
  ROUTED → NOT_ROUTED は**しない**
  （差し戻しの表し方は ADR-0009）
end note
@enduml
```

**通知できるのは `bookingStatus = ROUTE_PROPOSED` かつ `routingStatus = ROUTED` のときだけです。** 経路が決まっていない予約を通知できると、荷主に空の旅程が届きます。判定は集約の述語に置き、画面はそれを呼びます。

#### ER（本 IT で足すもの）

```plantuml
@startuml
title booking_read_db（IT6 で足す部分）

entity "cargo_summary" as cargo {
  * **booking_id** : VARCHAR(36) <<PK>>
  --
  booking_status : VARCHAR(30)
  routing_status : VARCHAR(30)
  ..IT6 で足す..
  condition_review_requested_at : TIMESTAMPTZ
  condition_review_reason : VARCHAR(200)
  last_notified_at : TIMESTAMPTZ
}

entity "cargo_notification" as note {
  * **booking_id** : VARCHAR(36) <<PK>> <<FK>>
  * **notified_at** : TIMESTAMPTZ <<PK>>
  --
  recipient_email : VARCHAR(255) NOT NULL
  summary : VARCHAR(500) NOT NULL
  notified_by : VARCHAR(50) NOT NULL
}

cargo ||--o{ note
@enduml
```

**`cargo_notification` は新設です**（[データモデル](../../design/cargo-tracker/data-model.md)に無いので、本 IT で反映します。下記「設計への反映が必要な事項」1）。

**主キーに通知日時を含めます。** 採番するとリプレイのたびに行が積み上がります（`cargo_revision` と同じ形。[ADR-0008](../../adr/cargo-tracker/0008-cargo-revision-as-a-projection.md)）。

**`last_notified_at` を `cargo_summary` に持ちます。** 営業ダッシュボードの「荷主へ通知していない経路確定済みの予約」を、履歴テーブルを数えずに絞るためです。

#### 画面遷移（本 IT で触る画面）

```plantuml
@startuml
title IT6 の画面遷移

state "S02 ダッシュボード（営業）" as S02
state "S22 予約詳細" as S22
state "S30 経路設計作業一覧" as S30
state "S31 経路設計ワークベンチ" as S31
state "S24 予約修正" as S24

S02 --> S22 : 荷主へ通知していない予約
S02 --> S22 : 条件の見直し依頼（**本 IT で足す**）
S22 --> S22 : 荷主へ通知 / 再通知 / 経路設計へ戻す（**本 IT**）
S30 --> S31 : 経路を設計する
S31 --> S31 : 条件を変えて再算出（**本 IT**）
S31 --> S02 : 営業へ差し戻す（**本 IT**）
S22 --> S24 : 期限・貨物を直す（US32・IT4）
@enduml
```

**期限と貨物種別は S31 から触りません。** 予約の内容を直すのは S24（予約修正）が正典で、2 か所から直せると「どちらが正か」が読めなくなります。S31 は「その予約の条件では組めない」ことを営業へ返すところまでを持ちます。

### API 設計

| メソッド | パス | 用途 | ロール |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/booking/bookings/{bookingId}/route-candidates?departFrom=&excludePorts=` | 条件を変えた再算出（US10。**IT5 の経路に条件を足す**） | ROLE_ROUTING |
| `POST` | `/api/v1/booking/bookings/{bookingId}/condition-review` | 営業へ差し戻す（US10 §4） | ROLE_ROUTING |
| `POST` | `/api/v1/booking/bookings/{bookingId}/notifications` | 荷主へ通知（US12） | ROLE_SALES |
| `GET` | `/api/v1/booking/bookings/{bookingId}/notifications` | 通知履歴（US12 §4） | ROLE_SALES, ROLE_ROUTING, ROLE_TRACKER |
| `POST` | `/api/v1/booking/bookings/{bookingId}/routing-request` | 経路設計へ戻す（**既存の経路を再利用**） | ROLE_SALES |

**新しい経路は `RoleAuthorization` に宣言し、順序を確かめます**（[ADR-0006](../../adr/cargo-tracker/0006-role-authorization-at-the-gateway.md) 決定 6・IT4 の T5）。**実装を読んで確かめた並びの規則は次のとおりです**（`RoleAuthorization.java:106-107`）。

- **メソッドを絞る宣言は、経路だけの宣言より必ず前に置かれる**（`ordered.add(new Rule("PUT", ...))` をマップの展開より先に積んでいる）。したがって `POST /bookings/*/notifications`（営業だけ）はメソッド込みで宣言すれば順序を気にしなくてよい
- **`GET /bookings/*/notifications` は宣言を足しません。** 広い `/bookings/**` が `{SALES, ROUTING, TRACKER}` で、通知履歴に許したいロールと同じだからです。**同じ集合の宣言を重ねると、片方だけ直したときに食い違います**
- `POST /bookings/*/condition-review`（経路設計者だけ）もメソッド込みで宣言します
- `POST /bookings/*/routing-request`（経路設計へ戻す）は**既にある宣言をそのまま使います**（`SALES`。`RoleAuthorization.java:88`）

**「宣言を足さない」判断も検査で固定します。** `GET /notifications` が営業・経路設計・追跡で通り、それ以外で 403 になることを確かめます（宣言が無いことと、広い宣言に当たっていることは別）。

**条件はクエリパラメータで渡します。** 予約の経路仕様（期限・端点・貨物種別）はサーバが `cargo_summary` から組み、**画面から送るのは「調整分」だけ**です（出発港・除外港）。全部を画面から送ると、予約の期限を直したのに古い期限で探すことが起きます。

### 契約への影響

**ありません。** 契約の名簿（イベント 11・コマンド 2・クエリ 1）は変わりません。`FindRouteCandidatesQuery` は IT5 の時点で `excludePortCodes` と `departFromUnLocode` を持っており、**IT5 では画面から使っていなかっただけ**です。本 IT でその配線を通します。

**「定義済み未使用は配線漏れのサイン」**（IT3 の T5）。契約に置いた 2 項目が 1 IT のあいだ使われていなかったことを、ふりかえりで確認します。

### ADR

**ADR-0009（新規）: 営業への差し戻しを状態遷移にしない。**

差し戻したときに `routingStatus` を `NOT_ROUTED` へ戻すか、記録だけを残すかを決めます。戻すと「一度も設計していない予約」と区別が付かなくなり、S30 の一覧と誤配（`MISROUTED`）の扱いにも波及します。**記録（`condition_review_requested_at` / `reason`）で表し、状態は動かさない**案を軸に、決定と影響を残します。

決定ごとに検査を対応させます（IT5 の architect 指摘。決定が 2 つなら検査も 2 つ）。

### 設計への反映が必要な事項

| # | 反映先 | 内容 |
| :--- | :--- | :--- |
| 1 | `data-model.md` | **`cargo_notification` テーブルが無い**（通知履歴を S22 に出すと決めているのに受け皿が無い）。ER 図と投影の表に追加。`cargo_summary` の 3 列も |
| 2 | `domain-model.md` | **「営業へ差し戻す」のコマンド・イベントが無い**（`ui_design.md` の S31 には `[営業へ差し戻す（条件調整）]` がある）。コマンド一覧・イベント一覧・状態遷移に追加 |
| 3 | `ui_design.md`（648 行付近） | S02 の「条件の見直し依頼」の対応ストーリーが **US10（IT5）** になっている。**IT6** に直す |
| 4 | `ui_design.md`（S02 営業） | 「荷主へ通知していない経路確定済みの予約」「条件の見直し依頼」の行が、実装の対象として計画に挙がっていなかった。本 IT の対象に含める |
| 5 | `domain-model.md` 要素表 | `ShipperNotification` 値オブジェクトを追加 |
| 6 | `architecture_backend.md` | 契約の名簿は変わらないが、**IT5 で契約に置いた `excludePortCodes` / `departFromUnLocode` が本 IT で初めて使われる**ことを一行残す |

### 着手前の検証（ステップ 3・4）

**並列の検証エージェントは 55 分応答しなかったため、要点を直接確かめました。** 確かめた内容と根拠は次のとおりです。

| # | 観点 | 結果 | 根拠 |
| :--- | :--- | :--- | :--- |
| 1 | ストーリー ID・SP・対応 UC | **一致**（US10→UC08・US11→UC09・US12→UC10、3+2+3=8） | `release_plan.md:185`／`user_story.md:28-30` |
| 2 | US11 が IT5 で実装済みか | **実装済み**（旅程・`assignRoute`・`ROUTE_PROPOSED` は引き渡し時点） | `Cargo.java:117,187,289`／`BookingDetailPage.tsx` |
| 3 | `RoleAuthorization` の並び | **メソッド込みの宣言が必ず先**。上記「API 設計」に反映済み | `RoleAuthorization.java:106-107` |
| 4 | `RoutingRequestedEvent` の再利用 | **成立**（投影が状態と日時を書き直す。遷移表も許す） | `CargoProjection.java:109-114`／`BookingStatus.java:51` |
| 5 | `cargo_notification` の不在 | **不在を確認**（ER 図にも投影の表にも無い） | `data-model.md:307-330,395-398` |
| 6 | 「営業へ差し戻す」のコマンド・イベントの不在 | **不在を確認**（画面には `[営業へ差し戻す（条件調整）]` がある） | `domain-model.md`（該当なし）／`ui_design.md:1068` |
| 7 | `ShipperNotification` が要素表に無い | **不在を確認** | `domain-model.md:71` 付近（旅程はあるが通知は無い） |
| 8 | `ui_design.md` の「条件の見直し依頼」 | **US10（IT5）と書かれている**（IT6 に直す） | `ui_design.md:648` |
| 9 | BC 独立性 | **違反なし**。本 IT はサービス越しの新しいメッセージを足さない | `CargoTrackerArchRules.java` の BC 分離規則 |
| 10 | 契約の名簿 | **変わらない**（イベント 11・コマンド 2・クエリ 1） | `architecture_backend.md` |

| 11 | サイドナビの突合表 | **影響なし**。本 IT で足す画面は無く、S31 は一覧から開く画面なので突合表に載らない | `navigationMatchesUiDesign.test.ts:68-79` |
| 12 | テスト戦略との整合 | **1 件直した**。「デモ項目と Feature は 1 対 1」なので、API から観測できるデモ項目にはすべて Gherkin のシナリオを割り当てた（下記デモ項目表） | `test_strategy.md:245` |

**画面でしか観測できないデモ項目（#1）だけは Gherkin を持ちません。** 「条件が画面から読める」は API では判別できないためで、フロントの検査で固定します。この 1 件は `test_strategy.md:245` の 1 対 1 から外れるので、理由をここに残します。

## スケジュール

### Day 1: 引き継ぎ枠（SP 対象外）

**IT5 のふりかえりで高 2 件が出たため、IT5 に続き枠を置きます。** IT5 で有効だった「Day 1 の独立コミットで消化する」形を繰り返します（余力次第にすると固定化する）。

| # | 内容 | 出所 | 見積 |
| :--- | :--- | :--- | :--: |
| H.1 | **05 章のキャプチャを撮り直す。** 「経路確定済みかつ一度修正した予約」の見本データを生成 spec に足す | IT5 引き継ぎ 1（高） | 3h |
| H.2 | **航海キャンセルの影響範囲を出す。** その航海で経路を組んだ貨物の件数と、対象への導線（`cargo_leg.voyage_number` の索引が既にある） | IT5 引き継ぎ 2（高） | 4h |
| H.3 | **時刻表記を業務タイムゾーンに揃える。** S34・S32 が UTC 表記のまま | IT5 引き継ぎ 4（中） | 2h |

**H.2 は US11 の浮いた工数を充てます**（上記「US11 の扱い」）。

### IT6 で扱わない引き継ぎ（行き先を先に決める）

| # | 内容 | 行き先 |
| :--- | :--- | :--- |
| 1 | 誤配の行が S31 に繋がるが扱えない | **US28（IT11）**。誤配からの再設計はそのストーリーの中核 |
| 2 | `/bookings/{id}/revisions` が内部クエリビューを返す | **IT7**。読み口の形を US13 の実装とあわせて揃える |
| 3 | `RouteCandidateQueryIT` の `truncated` アサートが全体状態に依存 | **本 IT で見る。** US10 で候補の条件が増えるので、偽陽性になったら直す |
| 4 | 修正履歴の回帰テストの 50 ms スリープ | **扱わない。** 本質の判定は「`/revisions` を呼ばない」ことに置き換え済みで、スリープは補助 |
| 5 | **港のローカル時刻での入力・表示**（IT2 から 4 回繰越） | **扱わない（決着）。** 港ごとのタイムゾーンは荷役の記録（US15・IT9）で初めて業務上の意味を持つ。**IT9 の範囲に含め、引き継ぎ表からは落とす** |
| 6 | **危険物かつ冷凍の貨物**（IT2 から 4 回繰越） | **扱わない（決着）。** 貨物種別が排他であることは `CargoType` の設計判断（IT2）で、変えるなら ADR が要る。**要件として起票するまで引き継がない**。引き継ぎ表からは落とす |
| 7 | ADR の承認と `verify`（0004〜0009） | **人の署名なので代筆しない。** ユーザーの承認を待つ |
| 8 | 連続入力で寄港地も残す | **US24 の範囲。** 航海登録を触る IT で拾う |

**5・6 は 4 回繰り越していました。** IT5 のふりかえり（「落とした負債は育つ」）に従い、**ここで行き先を決めて引き継ぎ表から落とします**。

### Day 2-4: US10 経路条件を調整して再算出する（3 SP）

インサイドアウト。既にある探索に条件を渡す配線が主なので、集約に足すのは差し戻しだけです。

1. **集約**（Day 2）：`Cargo.requestConditionReview`。差し戻せる状態の判断（`ROUTING_REQUESTED` のときだけ。設計済みの予約は差し戻さない）。`AxonTestFixture` で赤→緑
2. **投影・読み口**（Day 2-3）：`condition_review_requested_at` / `reason`、営業ダッシュボードの件数と一覧
3. **Controller**（Day 3）：`POST /condition-review`、`GET /route-candidates` に条件を足す。**HTTP の層で 1 本通す**（IT5 の T3）
4. **画面**（Day 3-4）：S31 の「条件」欄と `[営業へ差し戻す]`、S02 の「条件の見直し依頼」
5. **品質ゲート**（Day 4）：層別カバレッジと SonarQube を 1 度回す（IT5 の T4）

### Day 5: US11 経路情報を予約に紐付ける（2 SP）

**IT5 で実装済みの振る舞いに、受入基準と対応する検査を置きます。**

1. `経路の紐付け.feature` を 3 シナリオで置く（旅程と予約番号が読める / 紐付け操作 / 紐付け後も状態は経路提案中）
2. **3 番目は「状態が動かないこと」を確かめます。** 動かす実装に変えたら赤になることを確認する
3. 品質ゲートを 1 度回す

### Day 6-8: US12 確定経路を荷主に通知する（3 SP）

1. **集約**（Day 6）：`Cargo.notifyShipper`（`ROUTE_PROPOSED` かつ `ROUTED` のときだけ）・再通知・`returnToRouting`
2. **投影**（Day 6-7）：`cargo_notification`、`last_notified_at`
3. **Controller**（Day 7）：`POST` / `GET /notifications`、`RoleAuthorization` の宣言と順序
4. **画面**（Day 7-8）：S22 の通知内容の確認・`[荷主へ通知]`・`[再通知]`・`[経路設計へ戻す]`・通知履歴、S02 の「荷主へ通知していない予約」
5. **品質ゲート**（Day 8）

### Day 9: クラスタ E2E（1 回目）

イメージを作り直して載せ直し、US10・US12 の通しを 1 度回します。**IT5 では、モックと単体では出ない実害をここで捕まえました。**

### Day 10: 受け入れテストとマニュアル

デモ項目の Gherkin と、マニュアル（05 章のキャプチャ・09 章の条件調整・**新章または既存章への通知の追記**）。

### Day 11-14: クローズ

`closing-iteration` の 7 ステップ。**並列レビューはクローズの最初に起動します。**

### 見積合計

| 区分 | 見積 |
| :--- | :--: |
| 引き継ぎ枠（SP 対象外） | 9h |
| US10（3 SP） | 17h |
| US11（2 SP） | 6h |
| US12（3 SP） | 18h |
| クラスタ E2E・受け入れ・マニュアル | 10h |
| **合計** | **60h** |

**US11 が 6h と軽いぶん、引き継ぎ枠に 9h を置いています。**

## リスクと対策

| リスク | 影響 | 対策 |
| :--- | :--- | :--- |
| **差し戻しの表し方を間違える** | 状態を戻すと「一度も設計していない予約」と混ざり、S30 と誤配の扱いに波及する | ADR-0009 で決め、決定ごとに検査を置く |
| 通知の記録だけで「通知した」と読まれる | 送信基盤が無いことが利用者に伝わらない | 画面に「送信はシステム外。記録のみ」と明記し、マニュアルにも書く。**文言を検査で固定する** |
| `/notifications` の `GET` と `POST` でロールが違う | 順序だけでは絞れず、広い宣言に吸われる | メソッド込みで宣言し、**両方のロールで 403 を検査する**（IT4 の T5） |
| US12 §2 の「料金概算」が出せない | 受入基準の未達 | 計画時点で未達として明記済み（US21・IT13 が前提） |
| 引き継ぎ枠が本体を押し出す | IT5 と同じ 3 件を Day 1 に置く | 独立コミットで Day 1 に閉じる。溢れたら **US12 を削らず引き継ぎ枠を落とす** |

## 完了条件

### Definition of Done

- [ ] US10・US11・US12 の受入基準（`user_story.md`）を満たす。**ただし US12 §受入基準 2 の「料金概算」は未達**（US21・IT13 が前提。理由を完了報告書に記録）
- [ ] **IT5 で未達だった US09 §受入基準 4 が、US10 の完成で満たされている**
- [ ] デモ項目の受け入れテストがすべて緑。**対応はテスト名でなく本文のアサーションで確かめる**
- [ ] 引き継ぎ枠 H.1〜H.3 が返済されている、または送った理由がふりかえりに書かれている
- [ ] 本 IT で足した検査を壊して赤を見た
- [ ] **`cargo_notification` の投影テストは行を丸ごと比べた**。**再通知で行が増えること**と、**リプレイで増えないこと**の両方を含める
- [ ] **画面から呼ぶ API を HTTP の層で 1 本通した**（正常系と、その API 固有の異常系）
- [ ] **通知できない状態（経路未確定）で通知を試すと断られ、画面が 500 にならない**
- [ ] `./gradlew build` が緑・`TZ=UTC ./gradlew cleanTest test` が緑
- [ ] フロントの `npm run test`・`npx tsc -b`・`npm run build` が緑
- [ ] **新しい経路が `RoleAuthorization` にメソッド込みで宣言され、そのロール以外は 403 になることを検査した**
- [ ] **`GET` と `POST /notifications` の両方を、許されないロールで確かめた**
- [ ] UI 設計・navbar・ダッシュボード・到達性テストの 4 点が一致している
- [ ] **追加・変更した画面を、それを見る他のロールから何が読めるか確かめた**（IT5 の T6）
- [ ] **内部の列挙名を利用者に見せていない**（IT5 の T7）
- [ ] **kind クラスタで動く**：イメージを作り直して載せ直し、全 Pod が Ready
- [ ] **クラスタに対して E2E が緑（Day 9 とクローズ前の 2 回）**
- [ ] `npx gulp okf:check` が ERROR 0
- [ ] SonarQube の Quality Gate がバックエンド・フロントエンドとも PASS
- [ ] **ユーザーマニュアルが更新され、05 章のキャプチャが撮り直されている**
- [ ] **並列レビューをクローズの最初に起動し、結果が切れていないか確かめてから統合した**
- [ ] **設計への反映が必要な事項 6 件が `docs/design/` に反映されている**
- [ ] ADR-0009 を作成し、**決定の数だけ検査を対応させた**
- [ ] ふりかえり（`retrospective-6.md`）と完了報告書（`iteration_report-6.md`）を作成した

### デモ項目

イテレーションレビューで実演します。**この 7 件をそのままパスする受け入れテストが、IT6 の受け入れ基準です。**

| # | 見せるもの | 役割 | 何をアサートするか | 対応する検査 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 現在の制約条件が読める | 経路設計 | 出発港・除外港・期限・貨物種別が S31 に出る | `RoutingWorkbenchPage.test.tsx`（**画面でしか観測できないので Gherkin は持たない**） |
| 2 | 除外港を足して再算出すると、その港を通る候補が消える | 経路設計 | 除外前に出ていた候補が出ない | `条件調整.feature` シナリオ 1・`RouteCandidateQueryIT`・クラスタ E2E |
| 3 | 組めないときに営業へ差し戻せる | 経路設計 | 営業のダッシュボードに「条件の見直し依頼」が出る | `条件調整.feature`・`DashboardPage.test.tsx` |
| 4 | 設計済みの予約は差し戻せない | 経路設計 | 集約が断る。**API を直接叩いても守られる** | `条件調整.feature`（画面を通さない）・`CargoTest` |
| 5 | 経路が決まった予約を荷主へ通知すると「経路通知済」になる | 営業 | `ROUTE_NOTIFIED`。通知履歴に 1 行増える | `経路の通知.feature`・`CargoProjectionIT`・クラスタ E2E |
| 6 | 経路が決まっていない予約は通知できない | 営業 | 集約が断る。画面のボタンも出ない | `経路の通知.feature`・`transitions.test.ts` |
| 7 | 通知した予約を経路設計へ戻せる | 営業 | `ROUTE_PROPOSED` に戻り、経路設計の作業一覧に再び出る | `経路の通知.feature`・`RoutingWorklistPage.test.tsx` |

**デモ項目に対応する検査を表に書き出しました**（IT4 の T6）。**実演で緑になるものは、実装済みでも固定されていないことがあります。**

**US12 §受入基準 2 の「料金概算」は未達です。** 料金表は US21（IT13）が正典で、現時点で存在しません。0 を出すと「費用 0 円」と読めるので、通知内容にも欄を置きません。

## 局面の確認（中盤の継続）

IT5 に続き中盤（インサイドアウト）です。移行ではないので 5 観点の確認は不要ですが、**中盤の要点が守られているか**だけ記します。

- **集約から作る。** 差し戻せる条件・通知できる条件・経路設計へ戻せる条件は、いずれも「どういう条件なら許すか」の判断です。画面から導くとコントローラと投影に漏れます
- **イベントが先、テーブルが後。** `ShipperNotifiedEvent` の形が決まってから `cargo_notification` の列を決めます
- **画面は集約の述語を呼ぶ。** ボタンの出し分けを画面に書き直しません

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| :--- | :--- | :--- |
| 2026-09-05 | IT6 計画を作成。US11 が IT5 で実装済みであることを調査し、検査で固定する方針に。4 回繰越の 2 件に行き先を決めて引き継ぎ表から落とす | claude-code/claude-opus-5 |

## 関連ドキュメント

- [リリース計画](release_plan.md)
- [開発戦略](development_strategy.md)
- [IT5 ふりかえり](retrospective-5.md)
- [IT5 完了報告書](iteration_report-5.md)
- [IT5 実装レビュー](../../review/cargo-tracker/IT5実装_review_20260905.md)
- [ユーザーストーリー](../../requirements/user_story.md)
- [ドメインモデル](../../design/cargo-tracker/domain-model.md)・[データモデル](../../design/cargo-tracker/data-model.md)・[UI 設計](../../design/cargo-tracker/ui_design.md)
