---
type: ADR
title: "ADR-0014 書けないイベントは退避して、処理を止めない"
description: "投影が書けなかったイベントを dead_letter_entry へ退避し、Event Processor を生かしたままにする。1 件の不正イベントで後続が全部届かなくなる形をやめる。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-10T04:43:03Z }
---

# ADR-0014 書けないイベントは退避して、処理を止めない

投影が書けなかったイベントを、原因つきで `dead_letter_entry` に退避する。Event Processor は止めない。

日付: 2026-09-09

## ステータス

2026-09-09 提案。IT12 の引き継ぎ枠 A（IT11 レビュー 高）。

## コンテキスト

**IT11 で、1 件のイベントが全部を止めました。** 誤配の自動起票が `exception_id VARCHAR(36)` に 40 文字を入れようとして落ち、trackingms の投影がその 1 件を延々と retry し続けました。**後続のイベントは Event Store に積まれたまま、誰にも届きませんでした。**

見つかり方が悪い形でした。集約の検査もマッパーの検査も投影の検査も、すべて緑のままです。投影の検査は `projection.on(...)` を直接呼ぶので、**運ぶ側が止まるかどうかを判別しません**。気づいたのはクラスタ E2E で、それも「なぜか反映されない」という症状からの逆算でした。

同じ形は繰り返します。列の桁、NOT NULL、外部キー、列挙に足した値——**投影が書けなくなる理由は、イテレーションごとに増えます**。IT12 は契約イベントをもう 1 本足すので、増えた分だけ確率が上がります。

## 決定

**決定 1. 書けなかったイベントは `dead_letter_entry` へ退避し、原因を残す。** 黙って捨てない。捨てると、反映されていないことに誰も気づけないまま業務が進みます。残すのは元のイベントと、`cause_type` / `cause_message`——**直すために要るのは「何が」ではなく「なぜ」**です。直したあとは退避先から処理し直せます（Event Store 全体のリプレイが要りません）。

**決定 2. 退避先は Processor ごとに `application.yml` で明示する。** `"[..default]"`（`EventProcessorSettings.DEFAULT`）はこの版では効きません（実測。設定は読まれるが、Processor に届かない）。**書き忘れを人の注意で防ぎません**——列挙した Processing Group に `dlq` が付いているかを検査が見ます。付け忘れた Processor だけが IT11 の壊れ方に戻るので、緑のまま本番だけ違う挙動になります。

**決定 3. 退避先への書き込みは、失敗した処理とは別の接続で行う。** PostgreSQL は 1 つの文が落ちるとトランザクション全体を中断し、以降を `current transaction is aborted` で拒みます。Axon の `JdbcTransactionalExecutorProvider` は処理中の接続を使い回すので、**退避しようとした瞬間にその接続はもう死んでいます**（実測）。要確認一覧を別トランザクションで書くのと同じ理由です（`AttentionItemRecorder` の `REQUIRES_NEW`）——落ちた処理は巻き戻ってよいが、**落ちた事実は残す**。

**決定 4. 処理の列は業務の識別子で分ける（IT13 で追加）。** 退避先は「同じ列のイベントは順序どおりに」を守るので、列の切り方がそのまま**被害の範囲**になります。既定では列が全体で 1 本で、1 件の毒で無関係の貨物のイベントまで退避されました（IT12 の T7e で実測。4 件のうち 3 件が巻き添え）。`@EventHandler` を持つクラスに `@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "…")` を宣言し、**貨物・予約・荷主・申告・航海のいずれかで切ります**。それより細かくは切りません——同じ貨物の中では順序が要る（訂正は登録より後に効かなければならない）ためです。**書き忘れを人の注意で防ぎません**——`@EventHandler` を持つクラス全部を走査し、宣言の無いものを赤にします。

**決定 4 の但し書き。** IT12 では「`@SequencingPolicy` は方針を作りはするが `sequenceIdentifierFor` が呼ばれない」と記録しましたが、**IT13 の実測では働きました**（クラス宣言 + `PropertySequencingPolicy`）。当時の測り方が何を見ていたかは追えていません。いま効いていることは `DeadLetterQueueIT#anotherCargoIsNotParkedByAPoisonEvent` が固定しており、**効かなくなれば赤になります**。

## 引き受けていないこと（**未達を未達と書く**）

**~~処理の列はまだ全体で 1 本です。~~**（**IT13 で解消**。決定 4 を参照。以下は当時の記録） 退避先は「同じ列のイベントは順序どおりに」を守るので、1 件が退避されるとその列の**後続も退避されます**。追跡番号ごとに列を分ければ他の貨物は流れますが、この版では手立てがありません——

- `application.yml` の `sequencing-policy` は設定として読めますが、**どこからも使われていません**（`EventProcessorProperties$ProcessorSettings.sequencingPolicy()` の呼び出し元が jar に 1 つも無い）
- `@SequencingPolicy` は方針を**作りはします**（構築が 10 回走ることを実測）が、`sequenceIdentifierFor` が**一度も呼ばれません**。Processor 側が別の component に列を尋ねています

したがって本 ADR が実際に変えたのは次の 3 つで、**「後続が届く」ことはまだ引き受けていません**。

| IT11 の壊れ方 | 本 ADR の後 |
| :--- | :--- |
| Processor が retry を続けて止まる | 退避して次へ進む。トークンも進む |
| 反映されないことに気づく手段が無い | `projection:dead-letters` に原因つきで出る |
| 直したあとは Event Store 全体のリプレイ | 退避先から処理し直す |
| 後続のイベントが届かない | **同じ列の後続はやはり届かない**（退避先に溜まる） |

列を分ける手立ては **IT13 で入りました**。見込んでいた `EventProcessorDefinition` + `SequenceOverridingEventHandlingComponent` ではなく、**`@SequencingPolicy` のクラス宣言**で足りました。上の表の最終行は次のとおり改まります——**別の業務識別子（別の貨物・別の予約）のイベントは、毒があっても届きます**。同じ識別子の後続はやはり届きません（順序を守るため。これは意図した動作です）。

## 使ってみて分かったこと（IT12 の T7e で実測）

**この ADR の仕組みは、置いた同じ IT で実際に働きました。** T5 で書いた通関の連鎖に
欠陥があり（留置を経ていない申告に解決コマンドを送っていた）、集約が断った例外が
退避されました。クラスタ E2E が症状（追跡番号発行の連鎖が止まる）を出し、
`projection:dead-letters` が原因（`例外 … は起票されていません`）を 1 コマンドで
出し、退避されたペイロードが `previousStatus: PENDING, status: CLEARED` という
**再現条件そのもの**を示しました。層ごとの検査では出ない欠陥でした。

**「引き受けていないこと」も実データで裏づけられました。** 退避された 4 件のうち
毒は 1 件で、残り 3 件は**別の貨物の荷役イベント**でした。`sequence_identifier` は
4 件すべて `FULL_SEQUENTIAL_POLICY` で、同じ列にいたために巻き添えになっています。

**足りないものが 1 つ見つかりました。** 直したあとに<b>退避先から処理し直す入口</b>が
ありませんでした（Axon の `SequencedDeadLetterProcessor` を呼ぶ運用タスク）。IT12 では開発
環境の使い捨てデータだったので `DELETE` で片づけましたが、**これは「黙って捨てる」
ことであり、この ADR の決定 1 に反します**。本番では消せません。

**IT13 で入りました。** `/actuator/deadletters`（`DeadLetterRetryEndpoint`）と
`npx gulp projection:dead-letters:retry` です。**消す手段は置いていません**——直って
いなければまた退避されるだけで、消えはしません。実測で 1 つ分かったことがあります:
**投影が書く先は 1 つとは限らない**ので、原因を片方だけ直すと処理し直しても通らず、
「0 列を処理し直した」という答えが返ります（何も起きなかったことが分かる形にしてあります）。

## 検査

**決定の数だけ検査を用意する。** 検査に落とさなかった決定は守られません。

| 決定 | 検査 |
| :--- | :--- |
| 1 | `DeadLetterQueueIT#parkedEventIsReprocessedAfterTheCauseIsFixed`（**原因を直してから処理し直すと反映される**。入口が無ければ消して片づけることになる）。`EventSourcedServicesHaveTheSameShapeTest#everyServiceWithADeadLetterQueueCanReprocess`（退避先を持つサービス全部に入口があるか。**@Import と actuator への露出の両方**を見る——片方だけでは呼べない） |
| 1 | `DeadLetterQueueIT#unwritableEventIsParkedWithItsCause`（**IT11 の事象を同じ原因で再現**し、退避先に原因つきで残ることを見る）。`DeadLetterQueueIT#theProcessorKeepsWorkingAfterParking`（1 件目で止まっているなら 2 件目は退避先にも現れない） |
| 2 | `EventSourcedServicesHaveTheSameShapeTest#everyEnumeratedProcessorHasADeadLetterQueue`（**列挙されている Processing Group を全部拾ってから**それぞれを見る。`dlq` の付いたものだけを数えると、付け忘れたものほど漏れる。1 つ外すと赤になることを確認済み） |
| 4 | `DeadLetterQueueIT#anotherCargoIsNotParkedByAPoisonEvent`（**毒と別の貨物のイベントが投影される**ことを見る。列を 1 本に戻すと赤になる）。`EventSourcedServicesHaveTheSameShapeTest#everyEventHandlingClassDeclaresItsSequence`（**`@EventHandler` を持つクラス全部を走査**し、宣言の無いものを赤にする。宣言しているものだけを数えると、書き忘れたものほど漏れる） |
| 3 | `OwnConnectionExecutorProviderTest`（DataSource から取り直す・成功で確定・失敗で巻き戻して失敗として返す・接続が取れなければ失敗として返す） |

## 代替案

| 案 | 内容 | 却下の理由 |
| :--- | :--- | :--- |
| 投影で例外を握りつぶす | 書けなかったら log を出して次へ | **黙って捨てる**ことになる。反映されていない貨物が誰にも見えないまま残る |
| 要確認一覧（`attention_item`）に出す | 弾いた事実を業務の一覧に載せる | `attention_item` は BC ごとの表で、trackingms・handlingms・billingms には無い。3 サービスへの新設は本枠に収まらない。**退避の事実は運用の読み口（`projection:dead-letters`）に置き、業務一覧への掲出は送る** |
| Axon の自動設定に任せる | `JdbcDeadLetterQueueAutoConfiguration` をそのまま使う | `@ConditionalOnBean(DataSource.class)` だが、自動設定は Spring Boot の DataSource 自動設定より先に評価されるので条件が成立しない。DLQ を有効にすると**起動時に落ちる**（実測） |
| 表を Axon に作らせる | `GenericDeadLetterTableFactory` で自動生成 | この DB のスキーマは Flyway が正典（data-model.md）。外で作ると、適用済みマイグレーションの台帳と食い違う |

## 結果

**よくなること。** 投影が書けない 1 件で、そのサービスの反映が丸ごと止まることが無くなる。止まったこと・原因・元のイベントが `projection:dead-letters` から読める。直したあとの復旧が、Event Store 全体のリプレイから退避先の処理し直しに変わる。

**代償。** 表が 1 つ増える（5 サービス）。**退避されたことに気づくのは運用の読み口だけ**で、業務の一覧には出ない——見にいかなければ気づけない。

**守りきれなくなる兆し。** 退避先に溜まった件数が増え続けるなら、列を分ける手立て（IT13）を前倒しする。`dlq` を書き忘れた Processor が検査をすり抜けたら、検査が拾う範囲（`application.yml` の列挙）そのものを疑う。

## 関連

- [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) 決定 6（投影と Reaction Handler を別 Group にする）
- [データモデル](../../design/cargo-tracker/data-model.md)「Axon 管理テーブル」
- [運用](../../design/cargo-tracker/operation.md)（`projection:dead-letters`）
