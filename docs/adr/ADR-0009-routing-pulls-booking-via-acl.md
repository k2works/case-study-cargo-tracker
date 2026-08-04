# ADR-0009: 経路設計は Routing が Booking から引く（ACL の方向）

経路候補の算出において、**Routing Context が Booking Context の予約を引く**。
設計ドキュメントが当初示していた「Booking が Routing を呼ぶ」向きを反転させる。

日付: 2026-08-04

## ステータス

2026-08-04 承認されました（IT7・US08 の実装と同時）

**2026-08-04 一部 supersede**: US09 の実現手段（イベント方式）は
[ADR-0011](ADR-0011-routing-writes-booking-through-its-aggregate.md) が置き換えました。
ACL の方向（Routing が Booking から引く）は有効です。

## コンテキスト

[ドメインモデル設計](../design/domain-model.md) の「ドメインイベントフロー」は、
経路の照会を次の向きで描いていた。

```
booking -> routing : 経路照会（ExternalRoutingServicePort）
routing -> booking : CargoItinerary 返却
```

これは **Booking が主導し、Routing（あるいは外部の経路最適化システム）へ問い合わせる**
形である。「外部システム ACL Ports」の表にも `ExternalRoutingServicePort`
（外部経路最適化システム）が載っている。

IT7（US08）で経路候補の算出を実装するにあたり、次の 3 つが判明した。

1. **画面が Routing 側にある**。経路割り当て（`/bookings/{bookingId}/route`）は
   経路設計者の専門業務であり、`Role.Router` のみが開く
   （[バックエンドアーキテクチャ](../design/architecture_backend.md) のロール表）
2. **外部の経路最適化システムは使わない**。到達可能性の判定は Datalog による
   自前実装とし、`TS07`（外部 ACL 基盤）は着手条件付きで延期している（[ADR-0007](ADR-0007-defer-external-acl-and-scope-v1.md)）
3. **Booking は経路候補を必要としない**。Booking が要るのは確定した旅程
   （`CargoItinerary`）だけであり、候補の一覧は Routing の関心である

したがって「Booking が Routing へ問い合わせて候補を受け取る」形は、
**画面の所在・データの流れ・責務のいずれとも一致しない**。

IT7 の実装はこの向きを反転させたが、**ADR を起票せず設計ドキュメントも更新しなかった**。
その結果、正典（イベントフロー図）と実装が逆を向いた状態が生まれた。
IT7 のレビュー（architect）でこれが指摘された。

> **US09 を直接ブロックする。** 「経路を確定して `CONFIRMED` へ遷移させる」のが
> どちらの BC の責務かが、現時点で 2 通りに読める。

## 決定

**経路候補の算出において、Routing が Booking から依頼を引く（pull）。**

- Routing 側にポート `RoutingBookingRouteRequest.BookingRouteRequest` を定義する
- 実装（`RoutingJdbcBookingRouteRequest`）が `cargo` テーブルを直接引き、
  **Routing の言葉へ翻訳して**返す（`cargo_type` → `CargoCapability`、
  `booking_status` → 依頼として成立するか）
- Booking のモジュール（`BookingModel` / `CargoRepo`）は参照しない
  （`arch-lint` 規約 4）。結合は SQL の 1 文に閉じる
- `ExternalRoutingServicePort` は **v1.0.0 では使わない**。ADR-0007 の延期対象である

この向きは `BookingJdbcShipperExistence`（Booking → Shipper）と**対称**である。
すなわち本プロジェクトの ACL の一般形は「**必要とする側が、必要な形に翻訳して引く**」となる。

### 経路の確定（US09）の向き

> **【2026-08-04 更新】本節の後半（イベント方式）は
> [ADR-0011](ADR-0011-routing-writes-booking-through-its-aggregate.md) が置き換えた。**
> IT9 の着手前に「更新される集約は `Cargo` 1 つだけ」であることが判明し、
> 1 トランザクション 1 集約の規約が本操作に適用されないと分かったためである。
> **書き込みは Booking のアプリケーションサービスを同期的に呼ぶ**形に変えた。
> 「`cargo` / `leg` テーブルへ直接書かない」という判断は**変えていない**。

**確定も Routing が主導する**（経路設計者の画面が Routing 側にあるため）。
ただし `Cargo` の状態遷移（`ROUTE_PROPOSED → CONFIRMED`）と `CargoItinerary` の
永続化は **Booking の責務**である。集約をまたぐ更新は行わない
（[バックエンドアーキテクチャ](../design/architecture_backend.md) のトランザクション規約）。

したがって US09 では **Routing が「選ばれた経路」をドメインイベントとして発行し、
Booking の購読者が `Cargo` を更新する**。Routing が `cargo` / `leg` テーブルへ
書き込む形は採らない。

> **書き込み方向の越境を許すと、ここまでの BC 独立性の投資が一気に無駄になる**
> （IT7 レビュー・architect 指摘）。読み取りの ACL は「相手のスキーマに依存する」
> だけだが、書き込みの ACL は「相手の不変条件を壊しうる」。

## 影響

### 良い影響

- 経路設計者の作業が Routing 内で完結し、画面・ユースケース・ドメインの所在が一致する
- 外部の経路最適化システムに依存しない（ADR-0007 の延期判断と整合する）
- ACL の一般形（必要とする側が引く）が 2 例で揃い、次の BC でも迷わない

### 悪い影響・引き受けるリスク

- **Routing が Booking のスキーマと状態語彙に依存する**。
  `JdbcBookingRouteRequest.findSql` は `booking_status = 'ROUTE_PROPOSED'` という
  **Booking の列挙値をリテラルで持つ**。Booking が状態名を変えると、Routing は
  例外も出さず静かに `None` を返し、画面は「経路設計の対象ではありません」と表示する
- **`arch-lint` はこの結合を検出できない**。規約 4 はモジュール参照を見るため、
  SQL に降りた結合は素通しである（[arch-lint 規約仕様](../design/arch_lint_rules.md) の既知の穴）
- データ層では Booking ⇄ Routing の相互依存が成立している
  （Booking → Shipper、Routing → Booking）

### リスクへの対処

`RouteAssignHttpTest.testPreliminaryBookingIsNotAvailable` が
「引き渡し前の予約は対象外」を HTTP 経由で固定する。状態名を変えればこのテストが落ちる。
**機械検査ではなくテストで守る**という選択であり、その旨を
`arch_lint_rules.md` に明記する。

## この判断を見直す条件

以下のいずれかが起きたら再検討する。

1. **外部の経路最適化システムを導入する**（ADR-0007 の着手条件が満たされる）。
   その時点で `ExternalRoutingServicePort` が復活し、
   「Routing が外部へ問い合わせる」層が 1 段増える
2. **Booking 側から経路候補を必要とするユースケースが生まれる**
   （例: 営業が見積時に概算経路を見る US01）。
   IT8 で US01 を実装する際、Estimation / Booking が Routing を呼ぶ向きが要るなら、
   **双方向になる前に責務を再配置する**
3. **Routing が `cargo` の 5 列以上を必要とするようになる**。
   ACL が「翻訳」ではなく「相手のモデルの写し」に近づいた合図であり、
   共有カーネルへの昇格か、読み取り専用ビューの導入を検討する

## コンプライアンス

| 確認手段 | 内容 |
| :--- | :--- |
| `arch-lint` 規約 4 | `routing/**` が `booking/**` のモジュールを参照しないこと |
| `RouteAssignHttpTest.testPreliminaryBookingIsNotAvailable` | 引き渡し前の予約が対象外になること（状態名の結合を固定） |
| `RouteAssignHttpTest.testDirectVoyageIsOffered` | 引き渡し済みの予約なら依頼が引けること |
| [ドメインモデル設計](../design/domain-model.md) | イベントフロー図と ACL ポート表が本 ADR と一致すること |

## 備考

- 起票の契機: IT7 のマルチパースペクティブレビュー（architect）。
  **実装が先行し ADR が後追いになった**。判断の理由はモジュールコメントに
  良質な形で書かれていたが、コメントはコードと一緒に消える。
  ADR は決定が生き残るための形式である
- 関連: [ADR-0007](ADR-0007-defer-external-acl-and-scope-v1.md)（外部 ACL の延期）
