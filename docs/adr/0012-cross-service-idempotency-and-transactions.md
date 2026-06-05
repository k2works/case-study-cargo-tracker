# ADR-0012: cross-service イベントの冪等性とトランザクション境界

IT5 で cross-service Saga（bookingms ↔ trackingms ↔ handlingms）を稼働させた結果、event store のリプレイ・Axon Kafka の at-least-once 配信・local-h2 のインメモリ store リセットといった複数の経路で「同一イベントが複数回処理される」状況が常態化することが分かった。IT6 以降のサービス追加時に「いつ冪等であるべきか・いつトランザクション境界をまたぐべきか」の指針がないと、設計者ごとに対処が分散し、二重発火・二重投影・サイレント不整合が再発する。本 ADR で **「冪等性は『投影テーブルのフラグ列』 + 『集約のガード』の二段で確保する」「cross-service の発火点は集約発火型に限定し、二段イベントは避ける」** を統一規約とする。

日付: 2026-05-29

## ステータス

提案中（IT6 着手前）

## コンテキスト

IT5 のマルチパースペクティブレビュー（`docs/review/IT5_review_20260529.md`）で以下の高優先度指摘が出た。

- **H3**：`CargoDeliveredEventPublisher` が `TransportStatusUpdatedEvent` を購読して `CargoDeliveredEvent` を二段で発行している。event store リプレイ時に DELIVERED 遷移を二度処理すると Kafka へ二度発行される。
- **H2**：handlingms `HandlingActivityProjectionEventHandler` が `CargoSnapshot` 未到着時に `UNKNOWN-BOOKING` のフォールバック値で投影してしまう。cross-service 順序問題を黙って受け入れる設計負債。

加えて、本リポジトリの auto-memory にも以下が記録されている。

- `cross-service-command-handler-aggregate-not-found.md`：「集約ロードする cross-service ハンドラは AggregateNotFoundException も冪等スキップ。local-h2 インメモリ store リセット + Kafka 永続で replay 時に発生」
- `cross-service-tracking-processor-profile-config.md`：「Kafka tracking 追加時は local-docker と local-h2 両方に consumer.event-processor-mode=tracking。スモークテストは検出しない」

これらはいずれも「同一イベントを複数回受け取った時に、二重発火・二重投影・サイレントスキップのどれかが起きる」という共通根を持つ。ADR-0011 でエラーハンドリングの方針（ホワイトリスト方式）は決めたが、**「成功経路の冪等性をどう確保するか」** は方針化されていない。

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **集約発火型 + 投影フラグ列（採用）** | event store リプレイで自然に冪等、二段イベント不要、設計負荷が局所化 | 既存 publisher（`CargoDeliveredEventPublisher`）の書き換えが必要 |
| トランザクション境界拡張（Saga で 1 トランザクション化）| 強整合 | Saga が長期化、cross-service で分散トランザクションが必要 |
| Outbox パターン | 配信保証が高い | テーブル追加・配信ワーカー実装・運用負荷 |
| 何もしない（at-least-once 受容）| 実装最小 | レビュー H2/H3 の再発 |

## 決定

cross-service イベントの冪等性とトランザクション境界について、以下の **3 つの規約** を全サービス共通で適用する。

### 1. cross-service イベントは集約発火型に限定する

外部サービスに購読される shared イベントは、**集約の `apply()` 呼び出しで直接発行する**。`@EventHandler` で受け取った内部イベントから別の shared イベントを再発行する「二段イベント」は原則禁止。

| パターン | 例 | 採否 |
|----------|-----|------|
| 集約発火型（採用）| `TrackingActivity.handle(UpdateTransportStatusCommand)` 内で `AggregateLifecycle.apply(TransportStatusUpdatedEvent)` + `apply(CargoDeliveredEvent)` を順次呼ぶ | OK |
| 二段イベント（廃止）| `TransportStatusUpdatedEventHandler` が DELIVERED 遷移時に `eventGateway.publish(CargoDeliveredEvent)` を発行 | NG（IT6 で H3 改修対象）|

集約発火型に統一することで、event store リプレイ時に新規 publish は発生せず、`@Disallow Replay` を `@EventHandler` 側に付けるだけで投影と外部発信を一括制御できる。

### 2. 投影テーブルに「公開済みフラグ列」で冪等化する

集約発火型でも、Axon Kafka の at-least-once 配信により Kafka 側で重複到達する可能性は残る。投影側の重複は **投影テーブルのフラグ列** で吸収する。

| テーブル | フラグ列 | 役割 |
|----------|----------|------|
| `tracking_summary` | `delivered_published_at TIMESTAMPTZ` | DELIVERED 後に外部通知を 1 回だけ送ったことを記録（IT5 H3 既設）|
| `tracking_exception` | `escalated_notified_at TIMESTAMPTZ`（IT6 追加予定）| escalation 通知を 1 回だけ送ったことを記録 |
| 一般化 | `<action>_at TIMESTAMPTZ` | 副作用ごとに 1 列追加して `IS NULL` ガードで冪等化 |

通知 / Saga 進行 / 他サービスへの command 発行 などの「外部副作用」を伴うイベントハンドラは、必ず「フラグ列を読み取り → 未公開なら実行 → 同一トランザクションでフラグ列を更新」のパターンで書く。

### 3. cross-service ハンドラの未着事象は「待避テーブル」+ retro-update で扱う

cross-service の到達順序が逆転した場合（例：`HandlingActivityRegisteredEvent` が `CargoBookedEvent` より先に到着）、フォールバック値（`UNKNOWN-BOOKING`）で投影する代わりに **待避テーブル** に保留する。

| サービス | 待避テーブル | retro-update タイミング |
|----------|-------------|------------------------|
| handlingms | `pending_handling_activity` | `CargoBookedEvent` 到着時に CargoSnapshot を埋めて handling_activity へ移送 |
| 一般化 | `pending_<aggregate>_<event>` | 前提集約イベント到着時に retro-update |

待避テーブルが滞留した場合、Micrometer Counter（`<service>.projection.pending`）で件数を可視化し、運用時に検知可能にする。

### 4. トランザクション境界は「集約 + その同期投影 + 待避テーブル更新」までで切る

cross-service の command 発行は **同期コミット境界の外** で行う。投影と外部 command を同一トランザクションで包むと、外部サービスの遅延がローカル投影の遅延につながる。

```text
[集約 apply] → [Read Model 投影 commit] → [外部 command 発行（別トランザクション）]
                                       ↓
                                [失敗時は Kafka retry に委ねる]
```

待避テーブルの retro-update は、前提イベントを受信した投影トランザクションと同一境界で処理する（同じ DB 内なので分散トランザクションは不要）。

## 影響

### 適用対象

- **trackingms**: `CargoDeliveredEventPublisher` を廃止し、`TrackingActivity` 集約内で `CargoDeliveredEvent` を直接 apply するように変更（IT6 タスク 0.2 改修）
- **handlingms**: `HandlingActivityProjectionEventHandler` の `UNKNOWN-BOOKING` フォールバックを撤去し、`pending_handling_activity` 待避テーブル + `CargoSnapshotEventHandler` の retro-update に変更（IT6 タスク 0.5）
- **bookingms**: `BookingSagaManager` の Saga 終了タイミングは既に `CargoTrackedEvent` 受信時に `@EndSaga` で集約発火型のため変更不要
- **routingms**: 既存 publisher は集約発火型のため変更不要

### 受け入れテスト

- event store リプレイで Kafka publish が 1 回のみ実行されることをテストする（`CargoDeliveredEventPublishedOnceTest` 新設）
- 待避テーブル → retro-update → 投影更新の 3 段階を統合テストで貫通検証する

### 既存 ADR との関係

- **ADR-0009 cross-service Saga**: 本 ADR は ADR-0009 の運用規約として位置付け
- **ADR-0010 local-h2 Kafka 初期化**: 本 ADR で「孤児イベントは AggregateNotFoundException で WARN スキップ」は引き続き有効
- **ADR-0011 Kafka tracking エラーハンドリング**: 本 ADR の「フラグ列冪等化」は ADR-0011 の「ホワイトリスト方式」と直交（成功経路 vs 失敗経路）

### 派生・適用 ADR

- **[ADR-0015 billingms cross-service + ShipperInfo ACL](0015-billingms-cross-service-and-shipper-acl.md)**: 本 ADR の集約発火型を billingms に適用。IT7 review H1 で「内部 event + 派生 publisher」の二段イベントを廃止し、自己整合チェックリストを本 ADR に追記する契機となった
- **[ADR-0019 PaymentDetailRecorded 補完イベント](0019-payment-detail-recorded-event.md)**: 本 ADR §2 集約発火型を維持しつつ、shared event 最小契約と内部運用情報の分離を実現する設計（IT8 で実装）

## コプライアンス

- 新規 shared イベントを追加するレビュー時、本 ADR の 4 規約をチェックリストとして適用
- 投影 EventHandler の Pull Request では、副作用列のフラグ列ガードがあるかをレビュー観点に追加
- `architecture_backend.md` に「cross-service 冪等性チェックリスト」を追記

## 自己整合チェックリスト（IT7 review H1 教訓）

新規 cross-service イベントを追加するサービスを設計する際、着手前に以下のチェックリストを通すこと。IT7 で billingms に「内部 PaymentRecordedEvent + SharedPaymentRecordedEventPublisher」の二段イベントを導入してしまい、本 ADR §2 集約発火型に違反した教訓（review H1）を反映する。

### 着手前チェック（設計時）

- [ ] **C1: shared event を集約から直接 apply できるか？**
  - 集約が `AggregateLifecycle.apply(sharedEvent)` を呼べる構造か？
  - shared event に集約が知らない情報（外部 API 由来など）が含まれていないか？
  - 含まれている場合、その情報は本当に集約の状態に必要か（不要なら shared event から除外）？

- [ ] **C2: 内部 event と shared event を別々に持とうとしていないか？**
  - 「内部 event は安定化のため」「shared event は cross-service 契約のため」という理由で 2 種類定義しようとしていないか？
  - 2 種類になる場合は本 ADR §2 違反（二段イベント）として ADR で例外を明示すること
  - 集約からは 1 種類の event のみを発火し、変換が必要なら upcaster を使うこと

- [ ] **C3: 内部 publisher / EventHandler が shared event を再 publish していないか？**
  - 内部 event を購読して `EventGateway.publish(sharedEvent)` する `@EventHandler` を書いていないか？
  - 書いている場合、本 ADR §2 違反として削除し、集約発火に統合すること
  - trackingms `CargoDeliveredEventPublisher`（IT6 で廃止）、billingms `SharedPaymentRecordedEventPublisher`（IT7 review H1 で廃止）が反例

- [ ] **C4: 受信側 cross-service ハンドラは ADR-0011 ホワイトリストに準拠しているか？**
  - `AggregateNotFoundException` と `CommandExecutionException` のみを WARN スキップしているか？
  - その他の例外はログを吐かずに伝播させ、tracking プロセッサのエラーハンドラに委ねているか？

### コミット前チェック（実装時）

- [ ] **R1: Aggregate Test で shared event の apply が `expectEvents` で検証されているか？**
  - `@CommandHandler` が `apply(sharedEvent)` を呼ぶことを Axon Test Fixture で検証する
  - billingms `InvoiceAggregateTest#US23_入金記録` が好例

- [ ] **R2: 投影 / 通知 / cross-service ハンドラは同一 shared event を購読しているか？**
  - 同一 FQCN（shared/events パッケージ）の event を購読しているか？
  - 内部 event の購読がプロジェクト内に残っていないか（`grep`）？

- [ ] **R3: event store リプレイで二重発火が起きないか？**
  - shared event 自体は 1 イベント = 1 集約 apply のため、リプレイで再 apply されても集約状態は冪等
  - 副作用ハンドラ（投影・通知）は本 ADR §3 フラグ列 + IS NULL ガードで保護されているか？

### レビュー時チェック（PR レビュー）

- [ ] **PR1: 新規 `@EventHandler(SharedEvent.class)` で `EventGateway.publish` を呼んでいる箇所がないか？**
  - 二段イベントの再導入を即座に検出するための `grep` パターン: `grep -rn "EventGateway.*publish" src/main`
  - ADR 例外として認める場合はコミットメッセージで本 ADR 自己整合チェックリストの C2 と紐づけて明記

- [ ] **PR2: cross-service ハンドラの `@ProcessingGroup` が ADR-0014/0016 prefix 規約に準拠しているか？**
  - `cross-` / `local-` / `outbound-` のいずれかか？
  - ArchUnit `processing_group_prefix_convention` テストが PASS しているか？

## 備考

- 著者: k2works (IT6 計画時)
- 関連 Issue: take-5 #185-#188 のうち IT6 タスク 0.2 に紐付け
- レビュー指摘: IT5 review H2 / H3、auto-memory cross-service-command-handler-aggregate-not-found / cross-service-tracking-processor-profile-config
