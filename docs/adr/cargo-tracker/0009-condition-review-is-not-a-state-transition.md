---
type: ADR
title: "ADR-0009 営業への差し戻しを状態遷移にしない"
description: "条件では組めないことを営業へ返すとき、経路設計の状態を戻さず記録で表す。差し戻せる状態と、条件調整が経路設計をやり直しにすることも併せて決める。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-06T01:46:26Z }
---

# ADR-0009 営業への差し戻しを状態遷移にしない

経路設計者が「この条件では組めない」と営業へ返すとき、`RoutingStatus` を戻さず、差し戻した記録（いつ・誰が・なぜ）で表す。

日付: 2026-09-06

## ステータス

2026-09-06 提案されました。[ADR-0007](0007-route-search-cutoff.md)（経路探索の打ち切り）の次に来る、同じ UC08 の判断。

## コンテキスト

US10「経路条件を調整して再算出する」の受入基準 4 は、**条件を変えても組めないときに営業へ差し戻せる**ことを求める。差し戻したあとの状態をどう表すかを決める必要がある。

素直に考えると「経路設計の手番ではなくなったのだから `NOT_ROUTED`（未設計）へ戻す」となる。しかしそうすると、**一度も設計に入っていない予約と、設計を試して組めなかった予約が同じ値になる**。この 2 つは業務上まったく違う。

- 経路設計作業一覧（S30）は `ROUTING_REQUESTED` の予約を出す。`NOT_ROUTED` へ戻すと一覧から消え、営業が条件を直したあと**誰も設計を再開しない**
- 逆に一覧に出したままにすると、経路設計者は同じ予約を何度も開いて同じ結論に達する
- 誤配（`MISROUTED`）の扱いにも波及する。誤配からの再設計（US28・IT11）は「一度組んだ経路が外れた」ことなので、差し戻しと混ぜると US28 の設計が縛られる

さらに、差し戻しと対になる操作（条件の調整）についても、経路が決まったあとに条件を変えたとき経路設計をやり直しにするのかを決めていなかった。

### 検討した案

| 案 | 内容 | 判断 |
| :--- | :--- | :--- |
| A | `NOT_ROUTED` へ戻す | 却下。一度も設計していない予約と混ざり、S30 から消えて再開されない |
| B | 差し戻し専用の `RoutingStatus` を足す（`CONDITION_REVIEW` など） | 却下。経路設計の進み具合を表す軸に「営業の手番」を混ぜることになる。`BookingStatus` との二重管理にもなる |
| C | **状態を動かさず記録で表す**（採用） | 差し戻しは「経路設計の進み具合」を変えない。変わったのは**誰の手番か**で、それは記録と導線（営業の S02 に出す）で表せる |

## 決定

**決定 1: 差し戻しを状態遷移にしない。** `ConditionReviewRequestedEvent` を出し、投影の `condition_review_requested_at` / `condition_review_reason` に写す。`RoutingStatus` は `ROUTING_REQUESTED` のまま動かさない。営業のダッシュボード（S02）に「条件の見直し依頼」として出し、そこから予約詳細へ行けるようにする。

**決定 2: 差し戻せるのは `ROUTING_REQUESTED` のときだけ。** `MISROUTED` は含めない。誤配は「荷物が経路から外れた」ことで、条件では組めないこととは別である。差し戻せると、荷物が動いている予約が営業の手番に見える。誤配からの再設計は US28（IT11）が持つ。`ROUTED` も含めない（組めているのだから見直しは要らない。変えたいなら先に条件を調整する）。

**決定 3: 条件の調整は集約に記録し、`RoutingStatus` を `ROUTED` から戻す。** 条件が変われば、確定済みの経路はその条件で組んだものではなくなる。`ROUTED` のままだと経路設計者はもう一度確定できない。**ただし確定済みの旅程（`cargo_leg`）は消さない**——再設計で入れ替わるまで残す。消すと「何を組み直すのか」が分からなくなる。

### 決定ごとの検査

**決定の数だけ検査を用意する。** 決定が 3 つで検査が 1 つなら、残りは文章のままになる。

| 決定 | 検査 |
| :--- | :--- |
| 1 | `CargoConditionAdjustmentTest#conditionReviewDoesNotResetRoutingStatus`（差し戻したあとも条件を調整できる = 状態が戻っていない） |
| 2 | `CargoConditionAdjustmentTest#onlyRoutingRequestedCanBeSentBack`（`RoutingStatus` の全値を回す。値を足したら赤くなる）、`#rejectsConditionReviewAfterRouting` |
| 3 | `CargoConditionAdjustmentTest#adjustingAfterRoutingReopensDesign`（経路が決まった予約でも、条件を調整すれば確定し直せる） |

## 影響

- **良い影響**: 一度も設計していない予約と、組めなかった予約が区別できる。S30 の一覧の意味が変わらない。誤配（US28）の設計が縛られない
- **悪い影響**: 「営業の手番かどうか」が `RoutingStatus` を見ても分からない。読む側は `condition_review_requested_at` を見る必要がある。この分かりにくさは、営業の S02 に導線を出すことで補う
- **やり直せるか**: やり直せる。あとから状態を足す判断に変えるなら、記録から導ける

## 関連

- [ADR-0007 経路探索の打ち切り](0007-route-search-cutoff.md) — 同じ UC08 の判断
- [ドメインモデル](../../design/cargo-tracker/domain-model.md) — `BookingStatus` / `RoutingStatus` 状態遷移（正典）
- [IT6 計画](../../development/cargo-tracker/iteration_plan-6.md)
