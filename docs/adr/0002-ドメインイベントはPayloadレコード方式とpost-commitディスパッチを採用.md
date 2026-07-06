# ADR-0002: ドメインイベントは Payload レコード方式 + post-commit ディスパッチを採用

ドメインイベントの型定義方式（Payload レコード + Shared 配置）とディスパッチタイミング（トランザクションコミット後）の決定

日付: 2026-07-06

## ステータス

2026-07-06 承認済み

## コンテキスト

Cargo Tracker では Booking・Routing・Tracking・Handling・Billing の各境界づけられたコンテキスト（BC）間の連携を、同一プロセス内の同期ドメインイベント（判別共用体 `DomainEvent` + 関数リストによるディスパッチ、MediatR 不使用）で実現します。ここで 2 つの設計判断が必要です。

1. **イベント型の定義方式**: 各 BC の具体型（`Cargo`、`HandlingEvent` 等）を直接参照するケースを持つ DU にするか、プリミティブ / 共有型のみを持つ Payload レコードで構成するか。
2. **ディスパッチタイミング**: ワークフロー内で集約操作の直後にディスパッチするか、Unit of Work のトランザクションコミット成功後にディスパッチするか。

F# は .fsproj に記述されたファイル順で上から順にコンパイルされます（ADR-0001）。全 BC の具体型を参照する巨大 DU を定義すると、`DomainEvent` は全 BC より後に置く必要がある一方、各 BC の集約関数は `DomainEvent` を戻り値として返すため `DomainEvent` より後に置く必要があり、「BC → Event → 全 BC」の循環参照が発生してコンパイル不能になります。

また、永続化前にイベントをディスパッチすると、トランザクションがロールバックした場合に未コミットデータに基づく通知が他コンテキストへ届いてしまい、コンテキスト間のデータ不整合を引き起こします。

## 決定

**DomainEvent は Payload レコード方式で定義し、post-commit ディスパッチを採用します。**

1. **Payload レコード方式**: `DomainEvent` の各ケース（`CargoBooked`、`HandlingActivityRegistered` 等）は各 BC の具体型を直接参照せず、プリミティブ / 共有型のみを持つ Payload レコード（`CargoBookedPayload` 等）で構成し、`CargoTracker.Shared` に配置します。Shared は全 BC より前に置かれるため（ADR-0001 のファイル順規約）、循環参照が構造的に発生しません。
2. **post-commit ディスパッチ**: イベントは集約操作の戻り値（`State * DomainEvent list`）として蓄積し、Unit of Work のトランザクション（`IDbTransaction`）コミット成功後にディスパッチします。ワークフロー内から永続化前にディスパッチ関数を呼び出すことは禁止します。

根拠は以下のとおりです。

1. **F# のファイル順コンパイルとの整合**: 全 BC 型を参照する巨大 DU は「BC → Event → 全 BC」の循環参照になりコンパイル不能です。Payload レコード方式であれば `DomainEvent` は共有型のみに依存し、Shared 配置が可能になります。
2. **未コミットデータへの通知防止**: post-commit ディスパッチにより、ロールバックされたトランザクションのイベントが他コンテキストに届くことがなく、コンテキスト間の整合性が保たれます。
3. **BC 間の疎結合**: イベントの消費側は発生元 BC の内部型を知る必要がなく、Payload の共有型のみに依存するため、コンテキストの独立性（ADR-0001）が維持されます。

## 代替案

- **Transactional Outbox パターン**: イベントを同一トランザクション内で outbox テーブルに永続化し、別プロセスが配信する方式。プロセスクラッシュ時のイベント喪失も防げますが、現時点の同一プロセス内同期イベントには過剰です。高可用性要件が上がった際の移行先として記録します。

## 影響

- イベントハンドラは `DomainEvent -> Async<unit>` の関数リストとして合成ルートで登録し、`EventDispatcher.create` で合成します（`docs/design/architecture_backend.md` 参照）。
- 集約関数のシグネチャは `State * DomainEvent list`（または `State * DomainEvent`）を返す形に統一します。
- Payload レコードの追加により `CargoTracker.Shared` が肥大化する可能性がありますが、イベント Payload に限定することで許容範囲に抑えます。

## コンプライアンス

- コードレビューで「ワークフロー内から永続化前にディスパッチ関数を呼び出していないこと」を確認すること。
- ArchUnitNET で「BC 間は Shared 経由以外で参照しない」ルール（ADR-0001）が CI で常時実行されること。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス）、`docs/design/architecture_backend.md` の「ドメインイベント一覧」「関数合成による実装方針」節。
