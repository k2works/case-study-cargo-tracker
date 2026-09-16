---
type: ADR
title: "ADR-0017 請求書の有効／決着は billing_status を正とし、void_marker は派生列とする"
description: "「有効な請求書は予約ごとに 1 通」を守るための部分ユニークが列を要求する。状態を 2 か所で表すことになるので、どちらが正かを決めて検査に落とす。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-12T02:30:00Z }
---

# ADR-0017 請求書の有効／決着は `billing_status` を正とし、`void_marker` は派生列とする

請求書が有効か決着したかは **`billing_status`（`CALCULATED` / `INVOICED` / `PAID` / `VOID`）が正**とする。`void_marker` は UNIQUE 制約を成立させるためだけの派生列で、**判断には使わない**。

日付: 2026-09-12

## ステータス

2026-09-12 提案。IT14（US23）。

## コンテキスト

**同じことを 2 か所が表しています。** 「有効な請求書は予約ごとに 1 通」（`domain-model.md` の不変条件 2）は集約では守れません——1 請求書 1 集約なので、同じ予約の他の請求書を知らないからです。三段で守っています。

1. 作成前の存在確認（application 層）
2. **投影の部分ユニーク** `UNIQUE(booking_id, void_marker)`
3. 弾いた事実の記録（`attention_item`）

問題は 2 です。PostgreSQL の UNIQUE は NULL を「互いに違う値」として扱うので、素直に `UNIQUE(booking_id)` とすると取り消した請求書が残っているあいだ新しい請求書を作れません。そこで**有効中は空文字、取り消したら請求書 ID を入れる列**（`void_marker`）を置き、有効な行だけが `(booking_id, '')` で衝突するようにしています。

**その結果、「取り消したか」を `billing_status = 'VOID'` と `void_marker <> ''` の 2 通りで書けるようになりました**（IT13 のレビュー programmer #4）。どちらで書いても動くので、**書き手ごとに違う判定が混ざります**——片方だけを直したとき、もう片方が古い判断を続けます。

**IT14 で書き手が増えました。** 取消（`VoidInvoiceCommand`）が入り、投影が両方の列を動かすようになりました。決める前に読み手が増えると、あとから揃えるのが高くつきます。

## 決定

**決定 1. `billing_status` を正とする。** 業務の判断（発行できるか・入金を記録できるか・取り消せるか・一覧の既定から外すか）は**すべて `billing_status` で行う**。`BillingStatus` の述語（`acceptsIssue` / `acceptsPayment` / `acceptsVoid` / `isSettled`）がその判断を 1 か所に集める。

**決定 2. `void_marker` は投影が `billing_status` から導く。** 取消を写すとき、`billing_status = 'VOID'` と `void_marker = invoice_id` を**同じ UPDATE で**書く。別々に書くと、片方だけ入った行（取消なのに `void_marker` が空）が UNIQUE をすり抜けて二重の請求書を許す。

**決定 3. `void_marker` を読んで判断しない。** 読んでよいのは PostgreSQL の索引だけである。アプリケーションのコードが `void_marker` を条件に書いてよいのは、**有効な請求書を引く 1 か所**（`findActiveByBooking`）に限る——ここは「索引を使わせる」ための条件であり、業務の判断ではない。

## 検査

**決定の数だけ検査を用意する。** 検査に落とさなかった決定は守られません（ADR-0009 が 7 IT のあいだ半分守られなかったのと同じ形）。

| 決定 | 検査 |
| :--- | :--- |
| 1 | `InvoiceTest#doesNotReissueAVoidedInvoice`・`#doesNotVoidAPaidInvoice`・`#doesNotRecordPaymentBeforeIssuing`（判断が `BillingStatus` の述語で行われている。述語を緩めると赤になる） |
| 2 | `InvoiceProjectionIT#marksTheVoidedInvoice`（取消を写したあと**同じ予約に新しい請求書を作れる**ことで確かめる。`void_marker` が動いていなければ部分ユニークに弾かれて赤になる——「両方書いたか」を直接見るより、**業務として何ができるようになるか**で見るほうが、実装を変えても意味が残る） |
| 3 | `BillingVocabularyTest#voidMarkerIsNotReadOutsideTheActiveLookup`（`void_marker` を条件に書いている箇所が `findActiveByBooking` だけであることを、ソースを走査して固定する） |

## 代替案

| 案 | 内容 | 却下の理由 |
| :--- | :--- | :--- |
| `void_marker` を正とする | 取消の判断を `void_marker <> ''` で行う | 状態は 5 つあり、取消はそのうちの 1 つでしかない。有効／決着だけを別の列で表すと、**残りの 4 状態は `billing_status` で読むことになり、結局 2 か所を読む** |
| 部分ユニークインデックスにする | `CREATE UNIQUE INDEX ... WHERE billing_status <> 'VOID'` | **こちらのほうが素直で、列が要らない。** ただし `invoice` は IT13 で本番相当のクラスタに適用済みで、**適用済みマイグレーションは編集できない**（CI は緑のまま、適用済みクラスタだけが checksum mismatch で起動しない）。移行のマイグレーションを足せば可能だが、**列を消す作業に IT14 の枠を使うより、決めて検査に落とすほうが先**である。IT15 以降の負債として残す |
| 列を両方見て一致を検査する | 読むたびに `billing_status` と `void_marker` の整合を確かめる | 読むたびの検査は本番の負荷になり、**食い違いを見つけても直せない**（どちらが正か決まっていないため）。決めることが先である |

## 結果

**よくなること。** 「取り消したか」の判定が 1 つになる。読み手が増えても、`BillingStatus` の述語を足すだけで済む。

**代償。** `void_marker` という「業務に意味の無い列」が残る。**なぜあるのかを知らない人が読む**ので、`data-model.md` と本 ADR に理由を書き、検査で読み手が増えないようにする。部分ユニークインデックスへの移行は IT15 以降の負債とする。
