# ADR-0014: Axon @ProcessingGroup 命名規約

IT5 で trackingms / handlingms を追加した結果、Axon の `@ProcessingGroup` が 8 つに増えて prefix が混在し、新規追加時の判断負荷が高くなった。IT6 以降のサービス追加・例外処理追加で更に増える見込みのため、本 ADR で **3 種類の prefix（`cross-` / `local-` / `outbound-`）** による命名規約を統一する。

日付: 2026-05-29

## ステータス

提案中（IT6 着手前）

## コンテキスト

IT5 のマルチパースペクティブレビュー（`docs/review/IT5_review_20260529.md`）で以下の中優先度指摘が出た。

- **M4 (architect)**：「現状 8 グループで混在 prefix。新規追加時の判断負荷を下げる」

現状の `@ProcessingGroup` を棚卸しすると以下のとおり混在している（IT5 終了時点）。

| サービス | グループ名 | 役割 |
|----------|------------|------|
| bookingms | `route-confirmed-events` | cross-service 購読（routingms → bookingms） |
| bookingms | `cargo-snapshot` | local 投影 |
| bookingms | `cargo-summary` | local 投影 |
| bookingms | `tracking-issuance-requests` | cross-service 発信 (publisher) |
| trackingms | `tracking-issuance-requests` | cross-service 購読（bookingms → trackingms） |
| trackingms | `handling-activity-events` | cross-service 購読（handlingms → trackingms） |
| trackingms | `tracking-summary` | local 投影 |
| handlingms | `cargo-snapshot` | cross-service 購読（bookingms → handlingms） |
| handlingms | `handling-activity` | local 投影 |

prefix が「役割を表す」「集約名を表す」「イベント名を表す」で混在しており、grep で「このグループは何の役割か」を判定できない。

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **3 種類の prefix（採用）** | 命名で役割が即座に分かる、新規追加時の判断負荷低い、grep で網羅検索可能 | 既存グループの改名が必要（context が同一なら冪等） |
| 役割サフィックス（`-cross` / `-local` / `-outbound`）| 一覧表示時にアルファベット順でアグリゲートされる | grep フィルタが弱い |
| サービス名 prefix（`bookingms-` 等）| サービス所在が分かる | サービス内の役割は読み取れない |
| 何もしない（現状維持）| 変更コストゼロ | 新規 ADR を起票しない限り混乱が継続 |

## 決定

`@ProcessingGroup` の命名規約として **3 種類の prefix** を全サービス共通で適用する。

### 1. prefix 一覧

| prefix | 用途 | 例 |
|--------|------|-----|
| `cross-` | **他サービスのイベントを購読する tracking プロセッサ**（cross-service consumer）| `cross-tracking-issuance-requests`、`cross-handling-activity-events`、`cross-cargo-snapshot` |
| `local-` | **同サービス内のイベントを購読する subscribing プロセッサ**（投影・サブ集約連携）| `local-tracking-summary`、`local-cargo-summary`、`local-handling-activity` |
| `outbound-` | **自サービスのイベントを Kafka に発信する publisher プロセッサ**（KafkaPublisher 設定）| `outbound-tracking-issuance-requests`、`outbound-cargo-delivered` |

prefix の後は **イベント名のケバブケース**（例：`HandlingActivityRegisteredEvent` → `handling-activity-events`）または **投影テーブル名**（例：`tracking_summary` → `tracking-summary`）で表現する。

### 2. EventProcessor モード との対応

| prefix | Axon EventProcessor モード | local-h2 設定 | local-docker 設定 |
|--------|--------------------------|---------------|-------------------|
| `cross-` | tracking (Kafka StreamableKafkaMessageSource) | `axon.kafka.consumer.event-processor-mode=tracking` | 同左 |
| `local-` | subscribing（同プロセス内 EventBus） | デフォルト | デフォルト |
| `outbound-` | （publisher 設定。EventProcessor ではないが命名統一のため）| `axon.kafka.publisher.enabled=true` | 同左 |

### 3. 既存グループの改名指針

| 旧 | 新 | サービス | 役割 |
|----|-----|---------|------|
| `route-confirmed-events` | `cross-route-confirmed-events` | bookingms | cross |
| `cargo-snapshot`（bookingms） | `local-cargo-snapshot` | bookingms | local |
| `cargo-summary` | `local-cargo-summary` | bookingms | local |
| `tracking-issuance-requests`（bookingms） | `outbound-tracking-issuance-requests` | bookingms | publisher |
| `tracking-issuance-requests`（trackingms） | `cross-tracking-issuance-requests` | trackingms | cross |
| `handling-activity-events`（trackingms） | `cross-handling-activity-events` | trackingms | cross |
| `tracking-summary` | `local-tracking-summary` | trackingms | local |
| `cargo-snapshot`（handlingms） | `cross-cargo-snapshot` | handlingms | cross |
| `handling-activity`（handlingms） | `local-handling-activity` | handlingms | local |

### 4. 改名適用タイミング

- **IT6 では新規追加分（US18 / US19 / US20）から本規約を適用**：例えば例外通知 EventHandler は `local-tracking-exception-notification`、公開トークン発行イベントがあれば `outbound-tracking-token-issued` のように命名する
- **既存 9 グループの改名は IT6 では非対応**：改名すると Axon の `token_entry` テーブルでトークンが新規発行され、Kafka の re-consume が発生して event store リプレイで二重投影リスクがある。IT7 以降に「グループ改名 + token 移行手順」を ADR-0016（仮）として別途起票する

## 影響

### 適用対象

- **新規 EventHandler / Publisher**: IT6 以降は本規約必須
- **既存 EventHandler**: 当面そのまま（IT7 以降に一斉改名）
- **architecture_backend.md**: 「@ProcessingGroup 命名規約」セクションに本 ADR への参照を追記

### 受け入れテスト

- 新規 PR レビュー時に `@ProcessingGroup` の prefix がいずれかに合致することをチェック
- ArchUnit テストで「`@ProcessingGroup` 値が `cross-` / `local-` / `outbound-` のいずれかで始まる」ルールを追加（IT7 で実装）

### コンプライアンス

- 新規 `@ProcessingGroup` 追加時、本 ADR の prefix 規約に従っているかをレビュー観点に含める
- 既存グループの改名は ADR-0016（IT7 で予定）まで凍結

## 備考

- 著者: k2works (IT6 計画時)
- 関連 Issue: take-5 IT5 ふりかえり Try T3 / IT5 review M4
- 関連 ADR: ADR-0009 cross-service Saga、ADR-0010 local-h2 Kafka 初期化、ADR-0011 ホワイトリスト方式
