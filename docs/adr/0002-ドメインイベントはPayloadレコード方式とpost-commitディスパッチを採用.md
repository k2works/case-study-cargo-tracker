# ADR-0002: ドメインイベントは Payload レコード方式 + post-commit ディスパッチを採用

ドメインイベントの型定義方式（Payload レコード + Shared 配置）とディスパッチタイミング（トランザクションコミット後）の決定

日付: 2026-07-06

## ステータス

2026-07-06 承認済み
2026-09-19 改訂（IT5）— イベント型の定義方式を「Shared Payload レコード」から「**BC ローカルイベント DU**」へ、ディスパッチ機構を「`UnitOfWork.execute`」から「**アプリケーション層 + 合成層の post-commit ディスパッチ**」へ変更。post-commit の不変条件（コミット後のみ発火・ロールバック時非発火）は維持。詳細は下記「決定の改訂（IT5）」を参照。

## 決定の改訂（IT5）

IT2〜IT5 の実装を通じて、当初の決定のうち **(1) Payload レコード方式** と **(2) `UnitOfWork.execute` によるディスパッチ** は実装実態と乖離し、後者は結線されないデッドコードとして 3 イテレーション継続した（IT4 developing-review・xp-architect 指摘）。IT5 で以下に改訂して決着する。

1. **BC ローカルイベント DU を採用**（Shared Payload レコードは不採用）: 各 BC は自コンテキストのイベント DU を BC 内に定義する（`BookingEvent`〔Booking〕・`TrackingEvent`〔Tracking〕・`HandlingEvent`〔Handling〕）。各イベントは**自 BC の値オブジェクト/識別子と共有型のみ**を参照し、他 BC の型は参照しない。したがって「BC → Event → 全 BC」の循環は発生せず、Shared への集約も不要。消費側は合成層の ACL（例: `BookingEventConsumer`）で自 BC 型へ変換する。これにより BC の独立性（ADR-0001）がより強く保たれる。
2. **post-commit ディスパッチはアプリケーション層 + 合成層で実装**（`UnitOfWork.execute` は不採用・削除）: 各リポジトリが自前トランザクションでコミットするため、ワークフロー（例: `RouteAssignment.applyCommand`）は `repo.Update` が `Ok` を返した後＝**コミット済み**の時点でのみイベントを発火する。発火はベストエフォート（`Async.Catch`）とし、確定済みの結果を巻き戻さない。BC 間連携（`BookingConfirmed`→追跡番号発行等）は Web 合成層の実消費ディスパッチャ（`BookingEventConsumer`）が担う。generic な `UnitOfWork.execute` ヘルパはどこからも使われないデッドコードのため削除する。

**改訂の根拠**: BC ローカル DU は Payload レコードより BC 所有権が明確で、イベント語彙の変更が自 BC に閉じる。ディスパッチをアプリ/合成層に置くことで、リポジトリが自前トランザクションを持つ現行の永続化設計（ADR-0004）と自然に整合し、専用の UoW 抽象を持ち込まずに post-commit 不変条件を満たせる。将来の高可用要件では代替案の Transactional Outbox へ移行する（この改訂はその移行を妨げない）。

## コンテキスト

Cargo Tracker では Booking・Routing・Tracking・Handling・Billing の各境界づけられたコンテキスト（BC）間の連携を、同一プロセス内の同期ドメインイベント（判別共用体 `DomainEvent` + 関数リストによるディスパッチ、MediatR 不使用）で実現します。ここで 2 つの設計判断が必要です。

1. **イベント型の定義方式**: 各 BC の具体型（`Cargo`、`HandlingEvent` 等）を直接参照するケースを持つ DU にするか、プリミティブ / 共有型のみを持つ Payload レコードで構成するか。
2. **ディスパッチタイミング**: ワークフロー内で集約操作の直後にディスパッチするか、Unit of Work のトランザクションコミット成功後にディスパッチするか。

F# は .fsproj に記述されたファイル順で上から順にコンパイルされます（ADR-0001）。全 BC の具体型を参照する巨大 DU を定義すると、`DomainEvent` は全 BC より後に置く必要がある一方、各 BC の集約関数は `DomainEvent` を戻り値として返すため `DomainEvent` より後に置く必要があり、「BC → Event → 全 BC」の循環参照が発生してコンパイル不能になります。

また、永続化前にイベントをディスパッチすると、トランザクションがロールバックした場合に未コミットデータに基づく通知が他コンテキストへ届いてしまい、コンテキスト間のデータ不整合を引き起こします。

## 決定（当初・IT5 で改訂）

> **注記（2026-09-19 改訂）**: 本節の (1) Payload レコード方式・(2) `UnitOfWork.execute` ディスパッチは
> IT5 で置換済み。現行の正は上記「決定の改訂（IT5）」節。以下は経緯として保存する。
> **post-commit の不変条件（コミット後のみ発火・ロールバック時非発火）のみ現行でも有効**。

**DomainEvent は Payload レコード方式で定義し、post-commit ディスパッチを採用します。**

1. **Payload レコード方式**: `DomainEvent` の各ケース（`CargoBooked`、`HandlingActivityRegistered` 等）は各 BC の具体型を直接参照せず、プリミティブ / 共有型のみを持つ Payload レコード（`CargoBookedPayload` 等）で構成し、`CargoTracker.Shared` に配置します。Shared は全 BC より前に置かれるため（ADR-0001 のファイル順規約）、循環参照が構造的に発生しません。
2. **post-commit ディスパッチ**: イベントは集約操作の戻り値（`State * DomainEvent list`）として蓄積し、Unit of Work のトランザクション（`IDbTransaction`）コミット成功後にディスパッチします。ワークフロー内から永続化前にディスパッチ関数を呼び出すことは禁止します。

根拠は以下のとおりです。

1. **F# のファイル順コンパイルとの整合**: 全 BC 型を参照する巨大 DU は「BC → Event → 全 BC」の循環参照になりコンパイル不能です。Payload レコード方式であれば `DomainEvent` は共有型のみに依存し、Shared 配置が可能になります。
2. **未コミットデータへの通知防止**: post-commit ディスパッチにより、ロールバックされたトランザクションのイベントが他コンテキストに届くことがなく、コンテキスト間の整合性が保たれます。
3. **BC 間の疎結合**: イベントの消費側は発生元 BC の内部型を知る必要がなく、Payload の共有型のみに依存するため、コンテキストの独立性（ADR-0001）が維持されます。

## 代替案

- **Transactional Outbox パターン**: イベントを同一トランザクション内で outbox テーブルに永続化し、別プロセスが配信する方式。プロセスクラッシュ時のイベント喪失も防げますが、現時点の同一プロセス内同期イベントには過剰です。高可用性要件が上がった際の移行先として記録します。

## 影響（当初・IT5 で改訂）

> **注記（2026-09-19 改訂）**: 以下は当初想定の影響。現行実装は「決定の改訂（IT5）」に置換済み。

- ~~イベントハンドラは `DomainEvent -> Async<unit>` の関数リストとして合成ルートで登録し、`EventDispatcher.create` で合成します~~ → 現行は BC ローカルイベント DU を合成層の消費ディスパッチャ（`BookingEventConsumer`）で消費。
- ~~集約関数のシグネチャは `State * DomainEvent list` を返す形に統一~~ → 現行はワークフロー（`applyCommand`）が `repo.Update` 成功後に BC ローカルイベントを post-commit 発火。
- ~~Payload レコードの追加により `CargoTracker.Shared` が肥大化~~ → 現行はイベント DU を各 BC に配置するため Shared は肥大化しない。

## コンプライアンス

- コードレビューで「ワークフロー内から永続化前にディスパッチ関数を呼び出していないこと」を確認すること。
- ArchUnitNET で「BC 間は Shared 経由以外で参照しない」ルール（ADR-0001）が CI で常時実行されること。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス）、`docs/design/architecture_backend.md` の「ドメインイベント一覧」「関数合成による実装方針」節。
