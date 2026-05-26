# ADR-0010: 開発環境（local-h2）のインメモリ event store と Kafka トピックを整合させる（トピック初期化と冪等な孤児イベント処理）

`local-h2` ではインメモリ event store / token store が再起動で消える一方 Docker Kafka トピックは永続するため、フルリセット時は Kafka トピックを初期化して整合させ、cross-service の購読側は孤児イベントを冪等にスキップします。

日付: 2026-05-26

## ステータス

承認済み（IT4 で確認。コード修正は commit `a85c28c0` でマージ済み）

## コンテキスト

IT4（経路設計、ADR-0009）で routingms → bookingms の cross-service 経路確定（US11）を実装し、ライブ cross-service E2E（`cross-service.spec.ts`、`CROSS_SERVICE_E2E=1`）を実行したところ、bookingms で `AggregateNotFoundException` の ERROR スタックトレースが多発し、routingms の経路設計待ちリスト（`route_design_request`）に過去の全テスト実行分が蘇る事象が観測されました。

原因は **エフェメラルなインメモリストアと永続 Kafka トピックの不整合** です。

- **`local-h2` のストアはすべてインメモリ**: `jdbc:h2:mem:bookingdb` / `jdbc:h2:mem:routingdb`（`DB_CLOSE_DELAY=-1`）に event store・`token_entry`・`saga_entry`・読み取りモデルが同居し、**フル JVM 再起動で一括消失**します（devtools restart は同一 JVM のためインメモリ DB は残存。消えるのは「プロセスを落として起動し直す」フル再起動）。
- **Kafka `cargo-events` トピックは永続**: Docker（`apps/docker-compose.yml`、`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`）のため、アプリ再起動を跨いでメッセージが残ります。
- **Axon Kafka の位置管理は Kafka consumer group ではなくアプリ側 TokenStore**: `StreamableKafkaMessageSource` + Axon `TokenStore`（アプリ DB）で進捗を持つため、`local-h2` ではトークンもインメモリ。フル再起動でトークンが消えると tracking プロセッサが **トピックを先頭から replay** します。
- **結果**:
  - bookingms `RouteConfirmedEventHandler` が、Cargo 集約の消えた過去の `RouteConfirmedEvent` を replay → `AssignRouteToCargoCommand` の集約ロードに失敗（`AggregateNotFoundException`）。当初ハンドラは `CommandExecutionException` しか catch しておらず、別系統の本例外が伝播し `LoggingErrorHandler` が ERROR を量産（処理ブロックはしないがログ汚染）。
  - routingms `route_design_request` 投影が、replay された全 `RouteDesignRequestedEvent` から再構築され、待ちリストに過去分が再出現（`requested_at` が再投影時刻に揃う）。
- **`local-docker` / `heroku` では非該当**: event store・TokenStore が Postgres（永続）のため、再起動後もトピックと整合し replay 不整合は起きません。本件は **`local-h2`（エフェメラル）と、意図的なフルリセット** に固有の課題です。

したがって、「エフェメラルなインメモリストア」と「永続トピック」をどう整合させるか、開発環境の方針を確定する必要があります。

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| トピック初期化 + 冪等な孤児イベント処理（採用） | インメモリストアとトピックを整合、ERROR 汚染を解消、replay・重複配信に堅牢 | リセット手順の運用が必要、孤児スキップが設計バグを隠す懸念（明示 WARN で緩和） |
| `auto.offset.reset=latest` / head トークンで起動 | 起動時に過去イベントを読み飛ばし replay を回避 | 正当な未処理イベントも取りこぼす恐れ、整合性・再現性が脆い |
| event store を永続化（`local-docker`/Postgres を既定に） | ストアとトピックが恒久的に整合、本番と同条件 | `local-h2` の軽量・高速 TDD の利点を失い Docker 必須になる |
| 何もしない（堅牢化なし・リセットなし） | 手間ゼロ | 再起動ごとに ERROR フラッド・読み取りモデル汚染、E2E が誤判定する |

## 決定

**`local-h2` では Kafka トピックを「リセット可能な開発状態」として扱い、フルリセット時に初期化する。あわせて cross-service の購読側を孤児イベントに対して冪等にする。**

具体的には以下のとおりとします。

1. **トピック初期化を `local-h2` のリセット手順に含める**: インメモリストアを消す（フル再起動／クリーン）際は `cargo-events` トピックも初期化し、イベントログを空のストアと整合させる。
   - フルリセット（正規）: `gulp local-docker:clean`（`docker compose -f apps/docker-compose.yml down -v`）で Kafka ボリュームごと破棄。
   - 部分リセット: `docker exec cargo-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic cargo-events`（`AUTO_CREATE_TOPICS_ENABLE=true` のため空で再作成される）。
2. **cross-service の command 発行ハンドラを冪等・孤児イベント耐性にする**: 集約をロードして command を発行する購読ハンドラは、`AggregateNotFoundException`（対象集約が存在しない＝古い/再生イベント）と `CommandExecutionException`（状態ガード違反＝重複配信）を **個別に catch して WARN + スキップ** する。ERROR で再スローせず tracking プロセッサをブロックしない。`AxonException` 等の広すぎる catch は transient エラーの再試行を奪うため避ける。
3. **読み取りモデル投影は冪等 upsert を維持**: 投影（routingms `route_design_request` 等）は replay で再構築される「トピックの射影」であり、永続の真実ではない。トピックを空にすれば投影も空になる。
4. **永続性が必要な検証は `local-docker` を使う**: `local-h2` はエフェメラルな高速 TDD 用と位置づけ、再起動を跨いだ状態保持が必要な検証は Postgres event store を持つ `local-docker` で行う。

### 変更箇所

- `apps/backend/bookingms/.../interfaces/events/RouteConfirmedEventHandler.java`: `AggregateNotFoundException` を個別 catch して冪等スキップ（commit `a85c28c0`）。
- `apps/backend/bookingms/.../interfaces/events/RouteConfirmedEventHandlerTest.java`: 対象予約不在時にスキップして伝播しないテストを追加。
- 運用手順: `cargo-events` トピック初期化（`kafka-topics --delete` / `local-docker:clean` の `down -v`）を `local-h2` リセット手順として明文化。

### 代替案

- 代替案 1: `auto.offset.reset=latest` または head トークンで tracking プロセッサを起動し、過去イベントを読み飛ばす
  却下理由: 「未処理だが正当なイベント」も取りこぼす恐れがあり、いつ起動したかで挙動が変わるため再現性・整合性が脆い。replay 可能性は Axon tracking の利点であり、これを潰すのは過剰。
- 代替案 2: event store を永続化（`local-docker` / Postgres を既定の開発プロファイルにする）
  却下理由: ストアとトピックが恒久整合する利点はあるが、`local-h2` の「Docker 不要・高速起動」での TDD ループという利点を失う。永続が必要な検証用に `local-docker` を残す現行の使い分けで十分。
- 代替案 3: 実行ごとに一意なトピック名（例 `cargo-events-<runId>`）を使う
  却下理由: 設定が複雑化し、本番（固定トピック）と乖離する。テスト分離は Testcontainers（per-test 隔離 Kafka）で既に担保している。
- 代替案 4: 堅牢化のみ（孤児スキップだけ実装し、トピックは初期化しない）
  却下理由: ERROR は WARN に下がるが、再起動のたびに孤児イベントの WARN と読み取りモデルの過去分再構築が残り、E2E や手動確認のノイズになる。トピック初期化と併用して初めてクリーンになる。

## 影響

### ポジティブ

- フル再起動時の `AggregateNotFoundException` の ERROR フラッドを解消（孤児イベントは WARN 1 行で冪等スキップ）。
- インメモリストアとトピックを整合させる明確なリセット手順が確立し、E2E の誤判定・読み取りモデルの過去分汚染を防ぐ。
- cross-service の購読側が replay・重複配信に堅牢化し、ADR-0009 の tracking 方針をより安全に運用できる。

### ネガティブ

- 孤児イベントの WARN スキップは、設計バグ由来の「本来存在すべき集約が無い」ケースも握り潰し得る（明示的な WARN ログで検知可能に留める。IT4 レビュー M2 の懸念を継承）。
- `local-h2` ではトピックリセットの運用知識が必要（手順の文書化で緩和）。
- 投影が「イベント発生時刻」を保持しない場合、replay で値が変わる（例: `route_design_request.requested_at` が DB デフォルトで再投影時刻になる）。データ忠実性の改善余地として残るが、本 ADR の範囲外。

## コンプライアンス

次を満たすことで、決定の実装完了を確認します。

- `RouteConfirmedEventHandler` が `AggregateNotFoundException` / `CommandExecutionException` を WARN スキップし、例外を伝播しないユニットテストが PASS すること（`RouteConfirmedEventHandlerTest`）。
- `cargo-events` トピックを空にして bookingms / routingms をフル再起動したとき、ERROR スタックトレースが出ず、`route_design_request` 等の読み取りモデルがトピックの内容（空なら `[]`）と一致すること。
- `local-docker`（Postgres event store）では再起動後も event store とトピックが整合し、replay 不整合が起きないこと。
- トピック初期化手順（`local-docker:clean` の `down -v` / `kafka-topics --delete`）がドキュメント化され、実行後に `cargo-events` のオフセットが 0 になること。

## 備考

- 著者: k2works
- 関連コミット: `a85c28c0`（cross-service ハンドラの孤児イベント冪等スキップ）、`cc0b6dcd`（`local-h2` の route-confirmed プロセッサ起動不具合修正）
- 関連 ADR: ADR-0001（Axon Kafka + Aiven 採用）、ADR-0007（`local-h2` 含む DB 初期化を Flyway に統一）、ADR-0009（cross-service イベント連携と Axon Saga・Kafka tracking モード）
- 関連ドキュメント: `apps/docker-compose.yml`（Kafka 設定）、`apps/backend/*/src/main/resources/application-local-h2.yml`、`ops/scripts/develop.js`（`local-docker:clean` / `dev:*:start`）
