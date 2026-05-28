# ADR-0011: Kafka tracking プロセッサのエラーハンドリング統一方針（ホワイトリスト方式の継続と伝播先処理の標準化）

IT5 で trackingms / handlingms を追加するにあたり、cross-service Kafka tracking プロセッサのエラーハンドリングを **ホワイトリスト冪等スキップ方式** で全サービス統一する。冪等スキップ対象外の例外は伝播させ、tracking プロセッサ既定の `LoggingErrorHandler` に委ねる。DLQ・本格的な再試行ポリシーは本番接続前（IT8）まで延期する。

日付: 2026-05-28

## ステータス

承認済み（IT5 着手前）

## コンテキスト

ADR-0010 で `local-h2` の孤児イベント問題に対し、bookingms `RouteConfirmedEventHandler` を `AggregateNotFoundException` / `CommandExecutionException` の 2 種に限定して冪等 WARN スキップする「ホワイトリスト方式」で実装した（commit `a85c28c0`）。直近の `developing-review`（cad796dd レビュー、2026-05-28）で、この方針について以下が指摘された。

- **xp-architect M5**：H1 で「伝播する」が固定された以上、伝播先の処理（DLQ・再試行ポリシー・運用可視化）を IT5 着手前に ADR で方針化する必要がある。trackingms 追加で同一 `cargo-events` トピックの consumer が増えるため、サイレントスキップ vs 顕在エラーのハンドリング統一が遅れると negative テストが緑のまま意味を失う。
- **xp-user-representative**：US11 の冪等スキップは「黙ってスキップする」を仕様として強化する副作用がある。IT8 で「紐付け済みのはずが進んでいない予約」を経路設計者・営業が検知できる導線が必要。

IT5 では次の新規 cross-service ハンドラが追加される（`iteration_plan-5.md` §3）。

| 方向 | イベント | ハンドラ |
|------|----------|----------|
| bookingms → trackingms（Saga） | `TrackingIssuanceRequestedEvent` | trackingms `BookingTrackingInitializerEventHandler`（仮称） |
| trackingms → bookingms（Saga 完了） | `CargoTrackedEvent` | bookingms `BookingSagaManager`（@EndSaga） |
| handlingms → trackingms | `HandlingActivityRegisteredEvent` | trackingms `HandlingTransportStatusEventHandler`（仮称） |
| trackingms → billingms（IT7 以降） | `CargoDeliveredEvent` | billingms（未着手） |

これらいずれも「ホワイトリスト方式」を踏襲しないと、replay・重複配信時に同じ問題（ERROR フラッド、tracking プロセッサのブロック・ログ汚染）を引き起こす。一方で、ホワイトリスト方式は「真の基盤障害」を WARN スキップする副作用があり、現状はそれを検出する仕組みが存在しない。

ADR で方針を確定する前に放置すると、IT5 で新規ハンドラの実装者が以下のいずれかに走るリスクがある。

- catch 範囲を不用意に広げ、`catch (Exception)` 等で全例外を WARN スキップしてしまう（顕在化させない）
- 逆に catch を一切せず、`AggregateNotFoundException` 発生時に ERROR フラッドを再現させてしまう
- 各サービスごとに独自方針を採用し、運用時に「どのサービスがどう振る舞うか」が読めなくなる

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **ホワイトリスト方式の継続（採用）** | ADR-0010 で実証済み、ERROR フラッド回避、replay 堅牢、規約が単純 | サイレントスキップで真の障害が見えにくい（WARN ログ・メトリクス補強で緩和） |
| DLQ + 自動再試行を即時導入 | 真の障害を顕在化、自動復旧 | 運用負荷が高い（DLQ 監視・リトライ閾値・滞留検知の整備）、IT5 のスコープを超える |
| 全例外を伝播し LoggingErrorHandler に一任 | 規約が最小 | ADR-0010 の問題が再発（ERROR フラッド、ログ汚染、ブロックリスク） |
| Dead Letter Queue（Kafka 別トピック）専用 consumer | 失敗イベントを別経路で観測 | Kafka 設定追加・運用ツール追加が必要、Aiven 接続前の段階で Premature |

## 決定

**全サービスの cross-service Kafka tracking プロセッサで「ホワイトリスト冪等スキップ方式」を統一規約とする。冪等スキップ対象外の例外は伝播させ、tracking プロセッサ既定の `LoggingErrorHandler` に委ねる。本格的な DLQ・自動再試行ポリシーは本番接続前（IT8）まで延期する。**

具体的には以下のとおりとする。

### 1. 冪等スキップ許容例外（ホワイトリスト）

集約をロードして command を発行する cross-service ハンドラでは、以下 2 種の例外のみを **個別 `catch` ＋ WARN ログ ＋ スキップ** する。

| 例外 | 発生原因 | 対応 |
|------|----------|-----|
| `org.axonframework.modelling.command.AggregateNotFoundException` | 対象集約が event store に存在しない（古い／再生されたイベント、`local-h2` の孤児） | WARN ログを残し冪等スキップ |
| `org.axonframework.commandhandling.CommandExecutionException` | 集約のガード違反（既に状態遷移済み等の重複配信・冪等性違反） | WARN ログを残し冪等スキップ |

それ以外の例外（`RuntimeException` 直系、`Error` 系、`AxonException` 等の広い型）は **握り潰さず伝播** させ、tracking プロセッサ既定の `LoggingErrorHandler`（ERROR ログ + Axon プロセッサのリトライ）に処理を委ねる。これにより：

- 真の基盤障害（Postgres ダウン、Kafka 接続切れ、JVM OOM 等）は ERROR として顕在化する
- `AxonException` のような広い型を catch すると Axon の transient エラー再試行が奪われるため、必ず狭い型に限定する

### 2. 実装パターン（コード規約）

新規 cross-service ハンドラの実装テンプレート：

```java
@EventHandler
public void on(SomeCrossServiceEvent event) {
    // ... event → command 変換 ...
    try {
        commandGateway.sendAndWait(command);
    } catch (AggregateNotFoundException ex) {
        // 古い/再生されたイベント（local-h2 孤児など）。冪等にスキップ。
        log.warn("コマンド実行をスキップしました。対象集約が存在しません（id={}）。"
                + "再生された古いイベントの可能性があります。", aggregateId);
    } catch (CommandExecutionException ex) {
        // 状態ガード違反（既に処理済みなど）。重複配信を想定し冪等にスキップ。
        log.warn("コマンド実行をスキップしました（id={}）: {}",
                aggregateId, ex.getMessage());
    }
}
```

クラス Javadoc には **ホワイトリスト方針を明文化** し、新たに冪等スキップを許容したい例外型が現れた場合は本 ADR と該当ハンドラのネガティブテストを同時に更新する規律を残す（bookingms `RouteConfirmedEventHandler` を参照実装とする）。

### 3. ネガティブテストの必須化

cross-service ハンドラには **2 種のテスト** を必ず作成する。

- **Positive**：ホワイトリスト 2 種（`AggregateNotFoundException` / `CommandExecutionException`）で例外を伝播しないことを検証する
- **Negative**：それ以外（`RuntimeException`、`IllegalStateException` 等）で例外を伝播することを検証する

これにより `catch (Exception)` への退行を構造的に検知する（bookingms `RouteConfirmedEventHandlerTest` を参照実装とする）。

### 4. 当面の運用可視化

DLQ・本格的な再試行ポリシーが整備されるまでは、以下で運用可視化を担保する。

- WARN ログに **対象集約 ID** と **発生原因（再生／重複）** を含めて grep 可能にする
- IT8 で「紐付け済みのはずが進んでいない予約」を経路設計者・営業が検知できる導線（待ちリスト残留表示等）を実装する（cad796dd レビュー user-representative 懸念事項）
- スキップ件数のメトリクス化（Micrometer counter）は IT8 で追加検討

### 5. IT8 へ延期する事項

| 事項 | 延期理由 |
|------|----------|
| DLQ（Kafka 別トピック）+ 専用 consumer | Aiven Kafka 本番接続前の段階で Premature。本番運用要件が固まる IT8 で再評価 |
| Axon プロセッサの自動再試行ポリシーカスタマイズ | 既定の `LoggingErrorHandler` で当面十分。本番 SLO 確定後に調整 |
| WARN スキップ件数のメトリクス化 | Heroku Metrics / Micrometer 設定整備とセットで IT8 |
| 経路紐付け失敗の業務側検知導線 | IT8 のユーザーストーリーとして起票（cad796dd review user-representative） |
| サブクラスを含むホワイトリスト型の厳密検証 | IT8 で型階層レベルのテストを追加（cad796dd review H4 / programmer スコープ外発見） |

### 適用範囲

以下のハンドラに本規約を適用する。

- bookingms：`RouteConfirmedEventHandler`（既存、ADR-0010 で実装済）
- bookingms：`BookingSagaManager` の cross-service @SagaEventHandler（IT5 で `CargoTrackedEvent` 購読を追加）
- trackingms：新規追加するすべての cross-service ハンドラ（IT5）
- handlingms：新規追加するすべての cross-service ハンドラ（IT5）
- billingms：将来追加する cross-service ハンドラ（IT7 以降）

### 代替案

- **代替案 1：DLQ + 自動再試行を即時導入**
  却下理由：Kafka 別トピック作成、DLQ consumer 実装、滞留検知ダッシュボード、リトライ閾値設計が必要となり、IT5 のスコープを大幅に超える。Aiven Kafka 本番接続前の段階では Premature であり、運用要件が固まる IT8 で再評価する方が ROI が高い。
- **代替案 2：全例外を伝播し LoggingErrorHandler に一任**
  却下理由：ADR-0010 で実証済みの問題（ERROR フラッド、tracking プロセッサのログ汚染、`local-h2` での `AggregateNotFoundException` 多発）が再発する。`local-h2` の高速 TDD ループを維持するためにはホワイトリストが必要。
- **代替案 3：各サービスが個別方針を選択**
  却下理由：運用時に「どのサービスがどう振る舞うか」が読めなくなる。cross-service イベント連携の規約は一元化されているべき（ADR-0009 の延長線）。
- **代替案 4：エラーハンドリングを Axon 設定（`ListenerInvocationErrorHandler`）で一律実装**
  却下理由：宣言的で魅力的だが、ハンドラごとに「どの例外が冪等スキップ可能か」が異なるため一律設定では適切に判定できない。ハンドラ単位で `try-catch` する現方式の方が意図が明確。将来のリファクタリング候補としては有力だが、IT5 では現方式を継続。

## 影響

### ポジティブ

- 全 cross-service ハンドラのエラーハンドリングが統一され、新規サービス（trackingms / handlingms）追加時の実装ブレを防げる
- ホワイトリスト方式が ADR として明文化され、`catch (Exception)` への退行を構造的に検知できる（ネガティブテスト必須化）
- DLQ 導入を IT8 へ明示的に延期することで、IT5 のスコープが明確になる
- ADR-0009（cross-service イベント連携と Saga）+ ADR-0010（local-h2 孤児イベント）と本 ADR で、cross-service の信頼性方針が三本柱として揃う

### ネガティブ

- 冪等スキップ対象例外が今後増えた場合、本 ADR と全ハンドラのテストを同時更新する規律が必要（クラス Javadoc に同時更新規律を記載することで緩和）
- WARN スキップは「真の障害」を WARN レベルに留めるため、ログ集約ツール（Heroku Logs / Papertrail）で WARN を見落とすと顕在化が遅れる（grep 可能なログ形式で緩和、IT8 でメトリクス化）
- DLQ・自動再試行が IT8 まで延期されるため、本番運用開始直後は手動運用に頼る期間が発生する（本番運用 = Release 2.0 のため、IT8 完了が前提）
- 経路紐付け失敗が業務側で見えないため、IT8 までは運用ログによる検知に頼る期間が発生する（cad796dd review user-representative 懸念事項）

## コンプライアンス

次を満たすことで、決定の実装完了を確認する。

- IT5 で追加される全 cross-service ハンドラ（trackingms / handlingms 内）が、本 ADR §1〜§3 の規約に従って実装されていること
- 各 cross-service ハンドラに **Positive（ホワイトリスト 2 種）** と **Negative（ホワイトリスト外）** の両方のテストが存在し、`gradle check` で PASS すること
- 各 cross-service ハンドラのクラス Javadoc に「ホワイトリスト方式」の意図と、新規スキップ許容例外型を追加する場合の同時更新規律が明文化されていること
- IT8 のバックログに以下が起票されていること：
  - DLQ + 専用 consumer の本格導入検討
  - WARN スキップ件数のメトリクス化（Micrometer counter）
  - 経路紐付け失敗の業務側検知導線（待ちリスト残留表示等）
  - サブクラスを含むホワイトリスト型の厳密検証テスト
- `local-h2` フルリセット → `cargo-events` トピック replay 時に、IT5 で追加されたハンドラを含む全 cross-service プロセッサが ERROR を出さず WARN スキップで完走すること

## 備考

- 著者: k2works
- 関連 ADR: ADR-0001（Axon Kafka + Aiven 採用）、ADR-0009（cross-service イベント連携と Axon Saga・Kafka tracking モード）、ADR-0010（local-h2 のインメモリ event store と Kafka トピック整合）
- 関連レビュー: `docs/review/cad796dd_review_20260528.md`（developing-review M5・user-representative 懸念事項）
- 関連コミット: `a85c28c0`（cross-service ハンドラの孤児イベント冪等スキップ）、`6c00c048`（RouteConfirmedEventHandler Javadoc に方針明文化）
- 関連ドキュメント: `docs/development/iteration_plan-5.md`（IT5 計画書）、`apps/backend/bookingms/src/main/java/com/example/bookingms/interfaces/events/RouteConfirmedEventHandler.java`（参照実装）
- 関連メモリ: `cross-service-tracking-processor-profile-config`、`cross-service-command-handler-aggregate-not-found`
