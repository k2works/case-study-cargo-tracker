# ADR-0016: 既存 @ProcessingGroup の一斉改名と token 移行手順 + ArchUnit 構造ガード

ADR-0014 で `@ProcessingGroup` の命名規約（`cross-` / `local-` / `outbound-` の 3 種類 prefix）を制定したが、既存 10 グループは大半が旧名のまま残存している。グループ名を変更すると Axon `token_entry` テーブルで新規 token が発行され Kafka offset が初期化されるため、本番環境では event store リプレイによる二重投影リスクがある。本 ADR では (1) 既存 10 グループの **完全な改名マッピング表**、(2) **token 移行手順**（環境別: local-h2 / local-docker / Heroku 本番）、(3) **ArchUnit 構造ガード** で命名規約違反を CI で検出する方針を確定する。

日付: 2026-06-04

## ステータス

提案中（IT7 着手時）

## コンテキスト

ADR-0014（IT6 T3）で命名規約を制定した時点では、「既存グループの改名は IT6 では非対応。改名すると Axon の `token_entry` テーブルでトークンが新規発行され、Kafka の re-consume が発生して event store リプレイで二重投影リスクがある。IT7 以降に『グループ改名 + token 移行手順』を ADR-0016（仮）として別途起票する」と記述し、改名作業は本 ADR に持ち越された。

IT6 では一部 EventHandler（`local-tracking-exception-projection`）が新規規約準拠で命名されたが、IT5 以前のグループは旧名のままで、prefix 混在が続いている。さらに IT7 で billingms 新設（ADR-0015、cross-billing / local-billing / outbound-billing-notification）が加わると、新規は規約準拠・既存は非準拠という分断状態になり、認知負荷が増す。

### 現状の @ProcessingGroup 棚卸し（IT7 T2 完了時点、10 グループ）

| # | サービス | 現グループ名 | クラス | 役割 |
|---|---------|-------------|--------|------|
| 1 | bookingms | `booking-saga` | `BookingSagaManager` | Saga（cross-service イベントを受けて Command 発行）|
| 2 | bookingms | `route-confirmed` | `RouteConfirmedEventHandler` | cross 購読（routingms → bookingms）|
| 3 | handlingms | `handling-cross-service-publish` | `HandlingActivityCrossServicePublisher` | outbound publisher（local イベント → shared kernel 変換）|
| 4 | handlingms | `cargo-snapshot` | `CargoSnapshotProjectionEventHandler` | cross 購読（bookingms → handlingms）|
| 5 | trackingms | `local-tracking-exception-projection` | `TrackingExceptionProjectionEventHandler` | local 投影 ✅ 既準拠 |
| 6 | trackingms | `tracking-local-projection` | `TrackingSummaryProjectionEventHandler` | local 投影 |
| 7 | trackingms | `tracking-issuance-requests` | `TrackingIssuanceRequestedEventHandler` | cross 購読（bookingms → trackingms）|
| 8 | trackingms | `tracking-notifications` | `TrackingNotificationEventHandler` | local 通知 ACL 呼び出し |
| 9 | trackingms | `handling-activity-events` | `HandlingActivityRegisteredEventHandler` | cross 購読（handlingms → trackingms）|
| 10 | routingms | `route-design-requests` | `RouteDesignRequestEventHandler` | cross 購読（bookingms → routingms）|

> IT7 T2 で `cargo-delivered-publisher` グループ（`CargoDeliveredEventPublisher`）は削除済み。現在は 10 グループ。

### 課題

| 課題 | 内容 |
| :--- | :--- |
| **A. 命名分断** | IT6 以降の新規（`local-tracking-exception-projection`）+ IT7 billingms（`cross-billing` 等、ADR-0015）は規約準拠だが、IT5 以前の 9 グループは旧名のまま |
| **B. token 移行リスク** | グループ名変更で `token_entry` の主キーが変わり、Axon は新規 consumer として扱う → Kafka offset 初期化 → event store の `last_processed` 位置がリセット → 既処理イベントを再消費 → 二重投影 |
| **C. CI 検知不能** | 規約違反グループが新規追加されても、コードレビューで見落とすと CI が止まらない |
| **D. 環境差** | local-h2（in-memory）/ local-docker（PostgreSQL）/ Heroku 本番（Heroku Postgres + Aiven Kafka）でリスクと対処が異なる |

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **(A) 一斉改名 + token 移行手順 + ArchUnit ガード（採用）** | 規約準拠 100% 達成、CI で恒久ガード、本番影響を手順で抑制 | 本番 token 移行手順の運用コスト |
| (B) 改名せず命名規約だけ ArchUnit で監視 | 改名作業不要 | 既存非準拠が「合法的例外」として残り続ける、新規メンバーの認知負荷 |
| (C) サービス内 EventBus 設計を抜本変更し ProcessingGroup を廃止 | 根本解決 | Axon Framework のコアを書き換える大規模変更 |
| (D) prefix を撤廃して任意名称に戻す | 改名不要、柔軟性 | ADR-0014 を廃止することになり、規約による認知効果がゼロ |

## 決定

### 1. 既存 10 グループの完全な改名マッピング表

ADR-0014 規約と IT7 T2 時点の実態に基づき、以下の改名を実施する。

| # | サービス | 旧 | 新 | 種別 | 備考 |
|---|---------|-----|----|------|------|
| 1 | bookingms | `booking-saga` | `cross-booking-saga` | cross | Saga は cross-service イベントを受信して Command 発行するため cross- prefix |
| 2 | bookingms | `route-confirmed` | `cross-route-confirmed-events` | cross | |
| 3 | handlingms | `handling-cross-service-publish` | `outbound-handling-activity-events` | outbound | shared kernel イベントへの変換 publisher |
| 4 | handlingms | `cargo-snapshot` | `cross-cargo-snapshot` | cross | |
| 5 | trackingms | `local-tracking-exception-projection` | （変更なし）| local | 既準拠 |
| 6 | trackingms | `tracking-local-projection` | `local-tracking-summary-projection` | local | |
| 7 | trackingms | `tracking-issuance-requests` | `cross-tracking-issuance-requests` | cross | |
| 8 | trackingms | `tracking-notifications` | `local-tracking-notifications` | local | NotificationAcl 呼び出しは同サービス内 |
| 9 | trackingms | `handling-activity-events` | `cross-handling-activity-events` | cross | |
| 10 | routingms | `route-design-requests` | `cross-route-design-requests` | cross | |

#### Saga prefix 判定の補足

ADR-0014 では 3 種類の prefix だけを定義したが、Saga（`BookingSagaManager`）は通常の EventHandler とは性質が異なる（複数イベントを関連付け、長寿命）。本 ADR では **Saga も cross- prefix** に含めると決定する。理由:

- Saga は実体として **cross-service イベントを購読する tracking processor** で稼働する
- 4 つ目の prefix（例: `saga-`）を追加すると ArchUnit ガードの正規表現が複雑化し、認知負荷も増す
- Saga 自体はクラス名（`*SagaManager`）で識別できるため、prefix で区別する必要が薄い

将来 Saga が増えて identified なグルーピングが必要になったら、`cross-*-saga` のように **接尾辞 `-saga`** を慣習化することで対処（ArchUnit 追加ルールなしで運用可能）。

### 2. token 移行手順（環境別）

#### 2.1 local-h2 環境（開発者ローカル、Spring Boot test を含む）

- `axon.eventhandling.tokenstore.persisted` が false（H2 in-memory）のため **手順不要**
- アプリケーション再起動で全 token がリセットされ、event store も初期化されるため二重投影リスクなし

#### 2.2 local-docker 環境（Docker Compose、PostgreSQL）

- アプリケーション停止後、`token_entry` テーブルを **手動で TRUNCATE** する
- 起動時に新グループ名で token が新規発行され、event store の先頭から再消費（全データを Read Model 投影）
- 投影は MERGE / UPDATE 用の冪等化 SQL（例: `ON CONFLICT DO UPDATE`）が既に組まれているため、二重投影しても Read Model は一貫

```sql
-- local-docker 適用例
TRUNCATE TABLE token_entry CASCADE;
```

#### 2.3 Heroku 本番環境（Heroku Postgres + Aiven Kafka）

本番環境での改名は **メンテナンスウィンドウ + 段階的適用** で実施する。

1. **事前準備**
   - 全アプリケーション dyno をメンテナンスモード（`heroku maintenance:on`）
   - Aiven Kafka 側で各 topic の latest offset を記録（`kafka-consumer-groups.sh --describe`）
   - `pg_dump` で `token_entry` をバックアップ

2. **token 名のマイグレーション SQL（Flyway V<N>__rename_processing_group_tokens.sql）**

```sql
-- 例: bookingms の booking-saga → cross-booking-saga
UPDATE token_entry
   SET processor_name = 'cross-booking-saga'
 WHERE processor_name = 'booking-saga';
-- 全 10 グループ分（変更が必要な 9 グループ）を 1 マイグレーションにまとめる
-- 詳細は IT7 タスク 0.4 で作成するマイグレーションスクリプトを参照
```

3. **デプロイ + 再起動**
   - アプリケーションをデプロイ
   - 各サービスを順次再起動（routingms → bookingms → handlingms → trackingms の依存順）
   - 各起動後にログで「Processor 'X' starting at <segment>」を確認

4. **検証**
   - Kafka offset の進行を 10 分間モニタリング
   - Read Model の件数を改名前後で比較（INSERT が想定外に発生していないか）

5. **メンテナンスモード解除**
   - `heroku maintenance:off`

#### 2.4 ロールバック手順

問題発生時の戻し方:

```sql
-- 全 9 件を旧名に逆 UPDATE する Flyway 逆マイグレーション
UPDATE token_entry SET processor_name = 'booking-saga' WHERE processor_name = 'cross-booking-saga';
-- ... 残り 8 件
```

合わせてアプリケーションコードを 1 つ前のリビジョンへロールバックする。

### 3. ArchUnit 構造ガードテスト

`shared/src/test/java/com/example/shared/architecture/ProcessingGroupNamingArchTest.java` を新規作成（または各サービスの ArchUnit テストに追加）。

```java
@AnalyzeClasses(packagesOf = ApplicationMarker.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ProcessingGroupNamingArchTest {

    private static final Pattern PREFIX = Pattern.compile("^(cross|local|outbound)-.+$");

    @ArchTest
    static final ArchRule processing_group_must_have_prefix =
        classes().that().areAnnotatedWith(ProcessingGroup.class)
            .should(new ArchCondition<JavaClass>("@ProcessingGroup value must start with cross- / local- / outbound-") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String value = (String) item.getAnnotationOfType(ProcessingGroup.class).value();
                    if (!PREFIX.matcher(value).matches()) {
                        events.add(SimpleConditionEvent.violated(item,
                            String.format("@ProcessingGroup(\"%s\") on %s does not match ADR-0014/0016",
                                value, item.getName())));
                    }
                }
            })
            .because("ADR-0014/0016: @ProcessingGroup の値は cross- / local- / outbound- prefix で始まる必要がある");
}
```

ArchUnit テストはサービス横断で適用するため、各サービスの `archunit-junit5` 依存を確認し、なければ追加する（既存サービスは全て依存済みのはず）。

### 4. 改名適用順序（IT7 内での実施）

1. **タスク 0.4 (本 ADR)**: 起票完了
2. **タスク 1.x（billingms 立ち上げ）**: 新規規約準拠（cross-billing / local-billing / outbound-billing-notification）
3. **タスク 0.4 サブタスク（実装）**:
   1. ArchUnit ガードテストを **失敗する状態** で先に追加（Red）
   2. 既存 9 グループを順次改名（Green）
   3. local-h2 で `./gradlew check` PASS を確認
   4. local-docker スタックで動作確認
5. **タスク 5.x（仕上げ）**: SonarQube Quality Gate PASS + cross-service E2E PASS で本番デプロイ準備

> 本番への適用は IT7 リリース時。IT7 で実装完了 → ステージング動作確認 → 本番メンテナンスウィンドウで token マイグレーション実施。

## 影響

### 適用対象

- **bookingms / handlingms / trackingms / routingms**: 9 グループの改名
- **billingms（IT7 新設）**: ADR-0015 通り `cross-billing` / `local-billing` / `outbound-billing-notification` で命名
- **shared**: ArchUnit テスト追加（または各サービスへ展開）
- **本番運用手順**: SRE / 運用ドキュメントに「ProcessingGroup 改名時の token 移行手順」を追記

### 追加依存

なし（既存 archunit-junit5 を活用）。

### 受け入れテスト

- **構造テスト**: ArchUnit `processing_group_must_have_prefix` が PASS（改名前は FAIL、改名後 PASS）
- **動作テスト**: local-docker スタックで全サービス起動後、Kafka offset が新グループ名で正常進行することを確認
- **回帰テスト**: cross-service E2E（IT5 で追加）が改名後も PASS

### 既存 ADR との関係

- **ADR-0014（命名規約）**: 本 ADR は ADR-0014 を実装に展開する派生
- **ADR-0009 cross-service Saga**: Saga グループの prefix は cross- とする方針を本 ADR で決定
- **ADR-0015 billingms ACL**: 新設 billingms グループ 3 つはすべて本 ADR 規約準拠
- **ADR-0010 / 0011**: token 移行で Kafka 再消費が発生する場合、ADR-0011 の「ホワイトリスト方式」が再消費時にも適用されることを確認

## コンプライアンス

- 新規 `@ProcessingGroup` 追加時、ArchUnit ガードが PASS することを CI で保証
- 本番 token 移行は SRE が **メンテナンスウィンドウ + バックアップ + 段階的再起動** の 3 点セットで実施
- ロールバック手順は SRE 訓練で年 1 回検証

## 備考

- 著者: k2works (IT7 計画時)
- 関連 Issue: take-5 IT5 ふりかえり Try T3、IT6 review M1、IT6 ふりかえり Try T3
- 関連 ADR: ADR-0009 / 0010 / 0011 / 0014 / 0015
- 関連コミット: 6837f495（IT7 T2 / `cargo-delivered-publisher` 削除でグループ数 11 → 10）
- 関連 PR / Issue: IT7 タスク 0.4 で本 ADR + 実装をまとめてコミット
- 将来見直し条件:
  - prefix 4 種類目（例: `saga-`）が必要になる業務拡張
  - ArchUnit が JDK バージョン更新で動作不能（直近 IT5 P5 で 1.4 以上必須）
