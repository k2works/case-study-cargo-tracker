---
type: ADR
title: "ADR-0003 荷主の個人情報は crypto-shredding で削除可能にする"
description: "個人情報（荷主の氏名・メール・電話・住所）を荷主ごとの KMS 鍵で暗号化してイベントに載せ、削除要求には鍵の破棄で応じる決定。対象イベントの名簿、投影列の NULL 許容、ゴールデン JSON の論理形と物理形、導入時期（US02 の IT）と検査。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-03T12:50:10Z }
verified:
  - { by: human:kakimomokuri, at: 2026-09-02T08:13:46Z }
---

# ADR-0003 荷主の個人情報は crypto-shredding で削除可能にする

Event Store は追記専用で書き換えられないため、荷主の個人情報（氏名・メール・電話・住所）を載せるイベントは**荷主ごとの鍵で暗号化して書き**、削除要求には鍵を破棄して応える（crypto-shredding）。鍵は AWS KMS のエイリアス `alias/cargo-tracker/shipper/<shipperId>` で決定的に引き、対応表を持たない。投影の個人情報列は `NULL` 許容にし、鍵破棄後のリプレイが止まらないようにする。

日付: 2026-09-02

## ステータス

2026-09-02 提案されました

## コンテキスト

[ADR-0001](0001-cqrs-es-with-axon-in-microservices.md) で `Shipper` 集約を Event Sourcing にした。イベントは集約の永続化フォーマットであり、一度 Event Store に書いたイベントは書き換えも削除もしない（`architecture_backend.md`「イベント契約」）。一方で荷主は個人事業主を含み、氏名・メール・電話・住所は個人情報である。削除要求（個人情報保護法の利用停止・消去請求）に対して「イベント列にあるので消せません」とは答えられない。

設計レビュー（`docs/review/cargo-tracker/設計_review_20260902.md` H4）は、`non_functional.md` の 1 行に置かれていた crypto-shredding が次の 3 点で既存設計と両立しないことを指摘した。

1. 投影テーブル `shipper.name` が `NOT NULL` のままでは、鍵破棄後にリプレイすると復号できない行の `INSERT` で投影が止まる
2. ゴールデン JSON の契約テストが平文のフィールドを固定していると、暗号化した物理形と一致しない
3. 鍵の対応表をどこに持つか、`shipper:shred` に何時間かかるか、画面に何を出すかが決まっていない

横断的な決定であり、決定の数だけ検査を持つ必要があるため、独立した ADR にする。

### 決めるべきこと

1. どのイベントのどのフィールドを暗号化するか
2. 鍵をどこに置き、荷主とどう対応づけるか
3. 鍵破棄後に投影・復元集約・契約テストがどう振る舞うか
4. 削除の手順と所要時間、導入時期

## 決定

### 1. 暗号化するイベントは 2 本、フィールドは 4 つ

| イベント | 発行 | 暗号化するフィールド | 平文のまま |
| :--- | :--- | :--- | :--- |
| `ShipperRegisteredEvent`（契約） | bookingms | `name`, `email`, `phone`, `address` | `shipperId`, `shipperCode`, `contractType`, `registeredAt` |
| `ShipperContactUpdatedEvent` | bookingms | `name`, `email`, `phone`, `address`（変更のあったもの） | `shipperId`, `updatedAt` |

個人情報を載せてよいイベントはこの 2 本に限る。`CargoBookedEvent` や `TrackingNumberIssuedEvent` は `shipperId` だけを持ち、荷主名を非正規化して持つ投影（`cargo_summary.shipper_name`）は `ShipperRegisteredEvent` を購読して自分で埋める。他のイベントに個人情報のフィールドを足すことは ArchUnit の名簿で禁止する。

暗号化はイベントのシリアライズ時に行う。**変換の実体は `shared/infrastructure/crypto` に置く**（IT2 で bookingms から移した。契約イベントを読む側も同じ変換が要る。billingms が持たないと、契約スナップショットの氏名にエンベロープの JSON がそのまま入る）。Axon のシリアライザ（Jackson 3）の前段に `ShipperDataEncryptingConverter` を置き、対象フィールドを **エンベロープ**（`{ "alg": "AES-256-GCM", "keyRef": "alias/cargo-tracker/shipper/<shipperId>", "iv": "...", "ciphertext": "..." }`）に置き換える。復号は逆順で、鍵が無ければフィールドを `null` にして例外を投げない。

### 2. 鍵は KMS のエイリアスで決定的に引く。対応表を持たない

荷主ごとのデータキーは AWS KMS のエイリアス **`alias/cargo-tracker/shipper/<shipperId>`** で管理する。`shipperId` からエイリアスが一意に決まるので、`shipper_id → key_id` の対応表を DB に持たない。対応表は「削除したはずの荷主の痕跡」そのものになり、それ自体が個人情報の残存になるためである。

| 操作 | 実装 |
| :--- | :--- |
| 鍵の作成 | `RegisterShipperCommand` の処理で、イベントを追記する**前**に KMS でキーとエイリアスを作る。作成に失敗したらコマンドを拒否する |
| 暗号化・復号 | エンベロープ暗号。KMS の `GenerateDataKey` で得たデータキーを AES-256-GCM に使い、暗号化したデータキーをエンベロープに含める。KMS の呼び出しは荷主ごとにキャッシュする（TTL 5 分） |
| 鍵の破棄 | エイリアスの削除と `ScheduleKeyDeletion`（待機 7 日）。待機中は取り消せる。破棄が完了した時点で復号は不可能になる |
| ローカル・CI | KMS の代わりに `LocalKeyStore`（ファイル）を同じポート `ShipperKeyRepository` の実装として置く。契約テストは物理形の一致で両実装を検査する |

### 3. 鍵破棄後の振る舞い

| 場所 | 振る舞い |
| :--- | :--- |
| 復元集約 `Shipper` | `@EventSourcingHandler` は `null` を受け入れ、`name` などを `Optional.empty()` で持つ。判断を書かないので例外が出ない。以後の `UpdateShipperContactCommand` は「削除済み」として `409` で拒否する |
| 投影 `shipper`（bookingms） | `name` / `email` / `phone` / `address` を **`NULL` 許容**にする。`UNIQUE(email)` は `NULL` を複数許す（PostgreSQL の既定）。`shipper_code` は個人情報でないので `NOT NULL` のまま |
| 投影 `cargo_summary.shipper_name`、`shipper_contract_snapshot`（billingms）、`shipper_cargo_snapshot`（trackingms） | 同じく `NULL` 許容。billingms は `ShipperRegisteredEvent` を購読して写すので、鍵破棄後のリプレイでは `NULL` が入る |
| 画面 | `NULL` の個人情報は既定値 **「（削除済み）」** で表示する（`ui_design.md` の画面共通規約）。請求書・追跡照会は荷主コードと予約番号で業務を続けられる |
| リプレイ | 鍵破棄後に Group 全件をリプレイしても、上の `NULL` 許容によって止まらない。リプレイで投影から個人情報が消える |
| 契約テスト（ゴールデン JSON） | **論理形**（平文の `record`）と**物理形**（暗号化後のエンベロープ）を別のゴールデンファイルで固定する。論理形は「フィールドの集合が変わらないこと」、物理形は「エンベロープの形（`alg` / `keyRef` / `iv` / `ciphertext`）と平文のままのフィールドが変わらないこと」を検査する。`ciphertext` の値は毎回変わるので形だけ比べる |

### 4. 削除の手順と所要時間

削除要求は運用タスク `gulp shipper:shred --shipper-id <id>` で行う（`operation.md`）。

1. 該当荷主に未精算の予約が無いことを確認する（あれば拒否。請求は個人情報でなく荷主コードで追える）
2. `ShipperShredRequestedEvent(shipperId, requestedBy, requestedAt)` を追記する（誰がいつ削除したかは残す。個人情報は載せない）
3. KMS のエイリアスを削除し `ScheduleKeyDeletion` を予約する
4. 個人情報を写している Processing Group（`booking-shipper-projection`・`booking-cargo-projection`・`billing-projection`・`tracking-projection`）のトークンをリセットして**全件リプレイ**する
5. 投影と復元集約に個人情報が残っていないことを検査して完了を記録する

所要時間はリプレイが支配する。設計上の見積もりは **100 万イベントで 1.5 時間**（`non_functional.md` の投影再構築の実測目標から）。削除要求への回答期限（法令上の「遅滞なく」を社内規程で 30 日）に対して十分である。リプレイ中は該当 Group の投影が古くなるので、業務時間外に行う。

部分リプレイ（該当荷主のイベントだけ再生する）は採らない。Axon の Token は Group 全体の位置であり、荷主単位のリセットは無い。

### 5. 導入時期

初回リリース前、**US02（荷主登録）を実装するイテレーション**で導入する。荷主登録より後に足すと、平文で書いたイベントが Event Store に残り、それを消す手段が無くなる。「リリース前に入れる」ではなく、`ShipperRegisteredEvent` を初めて発行する IT の完了条件にする。

## 影響

### 得るもの

- 追記専用の Event Store のまま、個人情報の削除要求に応えられる
- 個人情報を持つイベントが 2 本に限定され、名簿で守れる
- 削除の事実（誰がいつ）はイベントとして残り、個人情報だけが読めなくなる

### 払うもの

- イベントの読み書きに KMS の呼び出しが入る。荷主登録は KMS のキー作成を待つ（数百 ms）。復号はキャッシュで吸収する
- ローカルと CI に `LocalKeyStore` が要り、本番と実装が分かれる。物理形の契約テストで両者を揃える
- 投影の個人情報列が `NULL` 許容になり、画面は「（削除済み）」を扱う分岐を持つ
- 削除 1 件につき Group 全件のリプレイ（1.5 時間）が要る
- Axon Server のバックアップ（S3 エクスポート）には暗号化済みのイベントが入る。鍵を破棄すればバックアップからも読めない。逆に、鍵のバックアップは KMS に任せ、自前で鍵を複製しない

### 設計ドキュメントへの波及

| ドキュメント | 内容 |
| :--- | :--- |
| `domain-model.md` | `Shipper` の個人情報フィールドを `Optional` にし、`ShipperShredRequestedEvent` を加える |
| `data-model.md` | `shipper` / `cargo_summary.shipper_name` / `shipper_contract_snapshot` / `shipper_cargo_snapshot` の個人情報列を `NULL` 許容にする。設計判断 6 を「ADR-0003 で解決」にする |
| `ui_design.md` | 個人情報が `NULL` のときの表示既定値「（削除済み）」を画面共通規約に置く |
| `test_strategy.md` | ゴールデン JSON を論理形と物理形に分ける。鍵破棄→リプレイの統合テストを加える |
| `operation.md` | `shipper:shred` の手順と所要時間、KMS の鍵削除待機（7 日）の扱い |
| `architecture_infrastructure.md` | KMS のキーポリシーとエイリアス命名、ローカルの `LocalKeyStore` |
| `non_functional.md` | 1 行の記述を本 ADR への参照に置き換える |

## コンプライアンス

| 決定 | 検査 |
| :--- | :--- |
| 個人情報を載せるイベントは 2 本だけ | ArchUnit（名簿）：`name` / `email` / `phone` / `address` を持つイベントの `record` が `ShipperRegisteredEvent` と `ShipperContactUpdatedEvent` 以外に無いこと。載っていないものを通さない向きで書く |
| 暗号化して書いている | 契約テスト（物理形）：Axon Server を経由して書いたイベントの JSON に平文の `name` / `email` が含まれず、エンベロープの形が固定されていること |
| 鍵破棄後に個人情報が残らない | 統合テスト：荷主を登録 → 予約を作る → 鍵を破棄 → 4 つの Group をリプレイ → 投影の該当列が `NULL`、復元した `Shipper` の個人情報が `Optional.empty()`、`cargo_summary.shipper_name` が `NULL` であること |
| 鍵破棄後もリプレイが止まらない | 同上のテストで、リプレイ後にトークンが最新位置まで進んでいること |
| 投影の個人情報列は `NULL` 許容 | Flyway の DDL 検査：`shipper.name` などの列定義に `NOT NULL` が無いこと。将来 `NOT NULL` を足したマイグレーションを赤にする |
| 論理形と物理形を分けて固定 | ゴールデンファイルが `contract/event/ShipperRegisteredEvent.logical.json` と `.physical.json` の 2 つあり、両方の契約テストが存在すること |
| 対応表を持たない | 設定ファイルと DDL の走査：`key_id` / `kms_key` を名前に含む列が無いこと |
| 導入時期 | US02 の IT の受入基準に「`ShipperRegisteredEvent` が暗号化されて書かれること」を引用する |

## 備考

- 著者: claude-code/claude-fable-5-1（設計レビュー H4 の反映）
- 関連: [ADR-0001](0001-cqrs-es-with-axon-in-microservices.md)（Event Sourcing の適用範囲）、[ADR-0002](0002-event-store-axon-server-and-postgresql-read-models.md)（Event Store の追記専用とバックアップ）
- レビュー: `docs/review/cargo-tracker/設計_review_20260902.md` H4（アーキテクト・PM）、L6（ADR の分割）、L8（`data-model.md` 設計判断 6）
- 参考: Axon Framework の Event Store は削除を提供しない。AxonIQ が案内する方式も暗号化鍵の破棄（crypto-shredding）である。KMS の `ScheduleKeyDeletion` は待機期間 7〜30 日で、待機中は取り消せる
- 参照元: `take-4` は crypto-shredding を扱っていない。`java-3` は状態保存のため `UPDATE` で消せた（本設計が新たに払う代金）
