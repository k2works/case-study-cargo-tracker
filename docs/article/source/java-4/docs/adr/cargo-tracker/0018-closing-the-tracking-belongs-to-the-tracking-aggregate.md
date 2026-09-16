---
type: ADR
title: "ADR-0018 追跡を閉じるのは追跡の集約で、閉じたことは契約にしない"
description: "キャンセルの陸揚げで追跡を閉じる判断は trackingms の集約の中に置き、TrackingClosedEvent を契約から内部イベントへ格下げする。外から閉じさせると、送り手が投影を読むことになる。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-14T07:30:00Z }
---

# ADR-0018 追跡を閉じるのは追跡の集約で、閉じたことは契約にしない

追跡を閉じる判断は **`TrackingActivity` の中**に置く。`CloseTrackingCommand` は作らず、`TrackingClosedEvent` は**契約（`shared/contract/event`）ではなく trackingms の内部イベント**とする。

日付: 2026-09-14

## ステータス

2026-09-14 提案。IT15（US30）で実装済み、IT16 で起票（**決定を文章のまま残さないため**）。

## コンテキスト

US30 で「輸送中のキャンセルを承認したら、指定した港で荷降しを記録した時点で追跡を閉じる」という不変条件（不変条件 9）が要りました。

**正典は当初、別の形を書いていました。** `domain-model.md` は `CloseTrackingCommand`（bookingms → trackingms）と、契約イベントとしての `TrackingClosedEvent`（trackingms → bookingms）を並べていました。実装に入って 2 つとも成り立たないと分かりました。

**閉じる条件を知っているのは追跡の集約だけです。** 「指定した港か」を判断するには `cancellationDischargeUnLocode` と、いま届いた荷役の港を突き合わせる必要があります。どちらも `TrackingActivity` が持っています。外からコマンドで閉じさせるには、**送り手が投影を読んで**「この荷役はあの港か」を判断することになります。投影が追いついていないあいだは判断できず、追いついたかどうかを送り手が知る手段もありません。

**閉じたことを購読する相手がいませんでした。** 契約にするのは「他のサービスが読むから」です。bookingms は承認の時点で予約を `CANCELLED` にしており、閉じたことを知る必要がありません。読む相手のいない契約は、**版を上げられない重荷**になります（契約イベントは Upcaster 無しに形を変えられない）。

## 決定

1. **閉じる判断は `TrackingActivity` の中に置く。** 荷役を適用する流れの中で、陸揚げ地と一致したら `TrackingClosedEvent` を出す
2. **`CloseTrackingCommand` を作らない。** 正典からは消さず、取り消し線と「作らなかった理由」を残す
3. **`TrackingClosedEvent` は trackingms の内部イベントとする。** 契約に置かない
4. **閉じた追跡には荷役を重ねない。** 届いた荷役は状態を動かさず `HandlingNotAppliedEvent` として履歴に残す（無言で捨てない）

## 検査

| 決定 | 検査 |
| :--- | :--- |
| 1 | `TrackingCancellationTest`（指定港で閉じる／途中の港では閉じない／キャンセルされていなければ閉じない／二度閉じない） |
| 2・3 | `ContractEventRosterTest#contractEventsMatchTheRoster`（契約イベントの名簿を名前で固定する。`TrackingClosedEvent` を契約へ移すと赤） |
| 4 | `TrackingCancellationTest#doesNotApplyHandlingAfterClosing`（状態を動かさず、届いた事実だけ残す） |

**決定 2 の検査は本 ADR で足します。** IT15 では「契約は 11 本」という本数の固定しかなく、**名前の名簿は無かった**ので、うっかり契約へ移しても本数さえ合えば通りました。

## 代替案

**(a) 正典どおり `CloseTrackingCommand` を作る。** 送り手（bookingms）が投影を読んで判断することになります。投影が追いついていないあいだは閉じられず、追いついたかを知る手段もありません。**判断を持たない側に判断させる**形です。

**(b) `TrackingClosedEvent` を契約のままにする。** 読む相手がいません。契約は版を上げるのに Upcaster が要るので、**読まれないものを契約に置くのは将来の負債**です。あとで読む相手ができたときに契約へ移せます（`architecture_backend.md` の方針どおり、移すのではなくコピーして Upcaster で繋ぐ）。

## 結果

**良い面.** 閉じる判断が 1 か所に収まり、投影の追いつきに依存しなくなりました。契約イベントは 11 本のままです。

**悪い面.** 正典（`domain-model.md`）を 7 か所直すことになりました。**設計が実装に先行して書かれていると、実装で成り立たないことが分かる**——このずれ自体は避けられませんが、**同じ変更で正典を直す**規律がないと乖離が残ります。

**残っている窓.** 閉じたことを他のサービスが知りたくなったら、契約へコピーして Upcaster で繋ぎます。**そのとき初めて**契約に置きます。
