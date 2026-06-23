# 0014 集約の reconstruct / register に Snapshot ADT を導入する

IT6 SonarQube 解析で検出された MAJOR Code Smell 4 件 (関数パラメータ過多、上限 7 超過) を、集約の不変条件と Repository 永続化の両立を保ちつつ解消する。`Snapshot` という値オブジェクト ADT に永続化フィールドを集約し、`reconstruct(snapshot)` と `register(snapshot)` を 1 引数化する。

日付: 2026-06-23

## ステータス

2026-06-23 提案 (IT7 タスク冒頭バンドル候補)。受理後に IT7 でドメイン集約 4 種に適用。

## コンテキスト

IT6 完了時の SonarQube 解析で **MAJOR Code Smell 4 件** がすべて「集約の `register` / `reconstruct` メソッドのパラメータ数が 7 を超える」として検出された。

| 集約 | メソッド | パラメータ数 |
| :--- | :--- | :--- |
| `Cargo` | `reconstruct` | 8 |
| `HandlingActivity` | `register` | 8 |
| `HandlingActivity` | `reconstruct` | 9 |
| `Invoice` | `reconstruct` | 10 |

これらは **DDD 集約の本質に根差した課題** であり、表面的なルール緩和では「動作するきれいなゴミ」の予兆を見過ごすことになる。

### 原因

1. **不変条件の集約**: `case class Invoice private (...)` でフィールドを公開せず、生成は `apply` / `reconstruct` / `register` 経由に限定している (smart constructor パターン)
2. **永続化からの全フィールド再現**: Repository (`ScalikeJdbcInvoiceRepository.rowTo`) が DB の全カラムを `reconstruct(field1, field2, ..., fieldN)` に渡す。フィールドが増えると引数も増える
3. **DDD のルール「集約は完全な状態で生成・復元」**: 部分復元を許容するとイベントソーシングや CQRS の Write Model 不整合リスクが上がる

### 既存コードの典型例

```scala
// Invoice.reconstruct (10 引数)
def reconstruct(
    invoiceId: InvoiceId,
    cargoBookingId: BillingBookingId,
    shipperId: BillingShipperId,
    baseAmount: Money,
    discountRate: DiscountRate,
    finalAmount: Money,
    paymentStatus: PaymentStatus,
    issuedAt: Instant,
    paidAt: Option[Instant],
    version: Int
): Invoice = ...

// Repository での呼出
Invoice.reconstruct(
  InvoiceId.unsafeFrom(rs.string("invoice_number")),
  BillingBookingId.unsafeFrom(rs.string("booking_id")),
  BillingShipperId(rs.string("shipper_id"), rs.boolean("is_corporate")),
  Money.unsafeFrom(rs.long("base_amount")),
  DiscountRate.unsafeFrom(rs.bigDecimal("discount_rate")),
  Money.unsafeFrom(rs.long("final_amount")),
  status,
  rs.zonedDateTime("issued_at").toInstant,
  rs.zonedDateTimeOpt("paid_at").map(_.toInstant),
  rs.int("version")
)
```

引数列挙の保守性 (順序ミス) と可読性の両方が劣化。Scala 3 `name params` で順序ミスは緩和できるが、SonarQube が示すリーディングコスト (`Cargo.reconstruct` を初見で理解する難度) は残る。

## 決定

各集約に **`Snapshot` 型の ADT (sealed trait / case class)** を新設し、`reconstruct` / `register` の引数を 1 個 (`snapshot: Snapshot`) に統一する。

### パターン定義

```scala
// 1. Snapshot を集約と同じファイルに定義
object Invoice:

  /** Invoice 集約の永続化スナップショット。
    * Repository が DB 行から組み立て、ドメイン側で集約に再構成する。
    * 不変条件の検証は reconstruct 内で実行される。
    */
  final case class Snapshot(
      invoiceId: InvoiceId,
      cargoBookingId: BillingBookingId,
      shipperId: BillingShipperId,
      baseAmount: Money,
      discountRate: DiscountRate,
      finalAmount: Money,
      paymentStatus: PaymentStatus,
      issuedAt: Instant,
      paidAt: Option[Instant],
      version: Int
  )

  def reconstruct(s: Snapshot): Invoice =
    new Invoice(
      s.invoiceId, s.cargoBookingId, s.shipperId,
      s.baseAmount, s.discountRate, s.finalAmount,
      s.paymentStatus, s.issuedAt, s.paidAt, s.version
    )

// 2. Repository では Snapshot を組み立てて 1 引数で渡す
private def rowTo(rs: WrappedResultSet): Option[Invoice] =
  for status <- PaymentStatus.fromName(rs.string("payment_status"))
  yield Invoice.reconstruct(
    Invoice.Snapshot(
      invoiceId = InvoiceId.unsafeFrom(rs.string("invoice_number")),
      cargoBookingId = BillingBookingId.unsafeFrom(rs.string("booking_id")),
      shipperId = BillingShipperId(rs.string("shipper_id"), rs.boolean("is_corporate")),
      baseAmount = Money.unsafeFrom(rs.long("base_amount")),
      discountRate = DiscountRate.unsafeFrom(rs.bigDecimal("discount_rate")),
      finalAmount = Money.unsafeFrom(rs.long("final_amount")),
      paymentStatus = status,
      issuedAt = rs.zonedDateTime("issued_at").toInstant,
      paidAt = rs.zonedDateTimeOpt("paid_at").map(_.toInstant),
      version = rs.int("version")
    )
  )
```

### 適用範囲 (IT7)

| 集約 | 新設 Snapshot | リファクタ箇所 |
| :--- | :--- | :--- |
| `Invoice` | `Invoice.Snapshot` | `ScalikeJdbcInvoiceRepository.rowTo` |
| `Cargo` | `Cargo.Snapshot` | `ScalikeJdbcCargoRepository`、テスト 1 件 (BillingCommandServiceSpec の `cargoIn`) |
| `HandlingActivity` | `HandlingActivity.RegisterRequest` (register) + `HandlingActivity.Snapshot` (reconstruct) | `ScalikeJdbcHandlingActivityRepository.rowToActivity`、`HandlingCommandService.register` 呼出 |
| `TrackingActivity` | `TrackingActivity.Snapshot` (将来追加分の予防) | 現状 6 引数で閾値未超過のため必須ではないが、一貫性のため適用検討 |

> `HandlingActivity.register` は業務操作 (新規記録) なので、永続化用の `Snapshot` とは別に `RegisterRequest` 値オブジェクトで意図を明確化する。

### Smart Constructor の不変条件はどこに残るか

`reconstruct(snapshot)` 内で **必ず** 既存の `require(...)` / 内部検証ロジックを実行する。Snapshot 自体は単なるデータ箱で、ドメインの不変条件を強制しない。

```scala
def reconstruct(s: Snapshot): Invoice =
  // 既存の不変条件はここで強制 (例: finalAmount = baseAmount × (1 - discountRate))
  require(
    s.finalAmount.value == s.baseAmount.multiplyByRate(BigDecimal(1) - s.discountRate.value).value,
    s"finalAmount inconsistent with baseAmount × discount"
  )
  new Invoice(...)
```

## 検討した代替案

### 案 A: SonarQube ルールで集約クラスを除外

`sonar-project.properties` で `**/aggregates/*.scala` を function-parameter ルールから除外。

- **メリット**: 既存コード無変更、即座に Code Smell 0 件に到達
- **却下理由**: 課題の隠蔽。新しい集約を追加するたびに同じ問題が再発し、警告ノイズが消えて検出機能が機能しなくなる。**「動作するきれいなゴミ」予兆を見過ごすリスク**

### 案 B: Builder パターン

```scala
Invoice.builder()
  .invoiceId(id)
  .baseAmount(...)
  ...
  .build()
```

- **メリット**: 流暢な API、Scala 3 `extension method` で実装可能
- **却下理由**: Builder の中間状態 (必須フィールド未設定) を型システムで防げない (Phantom Type で可能だがコスト過大)。Snapshot の方が「完全な状態を一度に渡す」DDD 流儀に合致

### 案 C: 個別フィールドへの `setter` (mutable case class)

- **却下理由**: イミュータブル原則違反 (Cargo Tracker のコア設計方針)

### 案 D: Macro 生成

- **却下理由**: Scala 3 macro 学習コスト + IDE 補完劣化のリスク。Snapshot は単なる case class で十分

## 帰結

### 正の帰結

- SonarQube **MAJOR Code Smell 4 件解消** (集約系の本質的課題に正面対応)
- Repository のリーディングコスト低下 (`reconstruct(Snapshot(field = value, ...))` で名前付きアクセス強制)
- 将来の集約追加時に同パターンを継承するだけで Smell 増加を防止
- 永続化スキーマと集約構造の対応が `Snapshot` の各フィールドで明示される (ドキュメント効果)
- イベントソーシング / Snapshot 復元への将来拡張余地

### 負の帰結

- 既存コードのリファクタリングが必要 (4 集約 + 4 Repository + 関連テスト)
- `case class` が `Aggregate` と `Snapshot` の 2 つに増える (ファイル行数増加)
- `Snapshot` を Repository 経由でしか作らない場合、ドメインからの直接アクセス用途とのバランス調整が必要

### リスク

- Snapshot 経由で不変条件をバイパスする誘惑が生じる
  - 対策: `reconstruct(Snapshot)` 内で `require(...)` を必ず実行、レビュー観点として明示
- 旧 API (`reconstruct(field1, field2, ..., fieldN)`) との並存期間が発生する場合は呼出元のリファクタ抜けに注意
  - 対策: 旧 API は `@deprecated` 付与 → 翌イテレーションで削除

### 関連

- ADR 0007 「楽観ロック Either API」: `version` フィールドは Snapshot 必須プロパティとして継承
- IT6 self-review (developing-review) MAJOR Code Smell 4 件: 本 ADR で解消方針確定
- IT7 計画タスク: 「テスト補強バンドル」と並行してアーキ堅牢化バンドルの一部として実施

## 適用順序 (IT7)

1. `Invoice.Snapshot` を導入し `ScalikeJdbcInvoiceRepository` をリファクタ (最小、リスク低)
2. `Cargo.Snapshot` を導入し `ScalikeJdbcCargoRepository` + 関連テスト 1 件をリファクタ
3. `HandlingActivity.Snapshot` + `RegisterRequest` を導入し Repository + Command Service をリファクタ
4. SonarQube 再スキャンで MAJOR 4 件 → 0 件を確認
5. ステータスを「承認」に更新
