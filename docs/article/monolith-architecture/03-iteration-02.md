# 第 3 章：IT2 特殊貨物と予約確定

## このイテレーションのゴール

> IT1 品質改善を完了し、危険物・冷凍貨物の予約登録と予約確定フローが動作すること

IT2 は半分が **IT1 の返済**です。目標 SP 10 のうち 4 SP が改善作業に割かれています。新機能を積む前に、境界違反と受入基準の乖離を潰す判断です。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10（US05: 3 + US13: 3 + IT1 改善: 4） |
| 実績 SP | 10 |
| Java テスト | 166 件（IT1 の 60 件から +106） |
| Playwright E2E | 31 件 |
| 命令カバレッジ | 93% |
| ブランチカバレッジ | 81% |

テストが 60 件から 166 件へ 2.8 倍に増えています。特殊貨物の条件分岐と状態遷移が入ったことで、テストすべき組み合わせが一気に増えました。

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 |
| US13 | 予約を確定する | 3 |

US05 の受入基準は「貨物種別に危険物を選択すると危険物申告情報の入力が必須となる」「冷凍・冷蔵を選択すると温度管理条件の入力が必須となる」。US13 は「予約状態を仮受付 → 予約確定に遷移できる」です。

## IT1 の返済

新機能に入る前に、IT1 のふりかえりで挙がった 4 項目を処理しています。

| 項目 | 対処 |
| :--- | :--- |
| `ShipperId` の配置 | Shipper Context から共有カーネル（`shared.domain.model`）へ移動 |
| Booking → Shipper の直接依存 | `ShipperExistenceChecker` ポート + `ShipperExistenceCheckerAdapter` を導入 |
| 割引率上限の乖離 | `DiscountRate` の `MAX_VALUE` を 0.15 → 0.30 に修正 |
| 住所フィールド欠落 | `Address` 値オブジェクトを追加し、荷主に持たせる |

ACL の実体は次の 2 ファイルです。ポートは Booking Context のアプリケーション層に、実装はインフラ層に置きます。

```java
// booking/application/internal/outboundservices/ShipperExistenceChecker.java
public interface ShipperExistenceChecker {
    boolean exists(ShipperId shipperId);
}
```

ポートの定義が「荷主が存在するか」だけである点が重要です。`Shipper` オブジェクトを返すポートにしてしまうと、Shipper Context のドメインモデルが Booking Context に漏れ出します。**ポートの粒度は、呼び出し側が本当に必要とするものだけに絞る**のが ACL の要諦です。

## Java 実装

### 条件付き必須をどう検査するか

US05 の核心は「貨物種別が危険物なら危険物申告が必須」という条件付き必須です。Java 実装は集約のコンストラクタで検査します。

```java
// booking/domain/model/aggregates/Cargo.java
if (this.cargoType == CargoType.HAZARDOUS && this.hazardousDeclaration == null) {
    throw new IllegalArgumentException("hazardousDeclaration is required for HAZARDOUS cargo");
}
if (this.cargoType == CargoType.REFRIGERATED && this.temperatureRequirement == null) {
    throw new IllegalArgumentException("temperatureRequirement is required for REFRIGERATED cargo");
}
```

`CargoType` は種別だけを持つ列挙で、追加情報は別フィールドです。したがって「種別は危険物だが申告が null」という状態は **型としては作れてしまい、コンストラクタの実行時検査だけが防いでいます**。

追加情報そのものは値オブジェクトです。

```java
// booking/domain/model/valueobjects/HazardousDeclaration.java
public record HazardousDeclaration(String hazardousClass, String unNumber, String properShippingName) { ... }

// booking/domain/model/valueobjects/TemperatureRequirement.java
public record TemperatureRequirement(BigDecimal minTemperature, BigDecimal maxTemperature, TemperatureUnit unit) { ... }
```

### コマンドから値オブジェクトへの変換

コマンドサービスは、フラットな DTO を値オブジェクトに組み立てます。「3 つ揃っていれば作る、欠けていれば null」という素朴な変換です。

```java
// booking/application/internal/commandservices/CargoBookingCommandService.java
private HazardousDeclaration toHazardousDeclaration(BookCargoCommand command) {
    if (hasText(command.hazardousClass()) && hasText(command.unNumber()) && hasText(command.properShippingName())) {
        return new HazardousDeclaration(command.hazardousClass(), command.unNumber(), command.properShippingName());
    }
    return null;
}
```

null を返し、集約が「種別と突き合わせて」検査する構造です。検査の責任が集約側に一元化されているのは良い点ですが、null が層をまたいで流れる設計でもあります。

### 状態遷移の追加

US13 で `BookingStatus` に遷移が入ります。

```java
public Cargo confirm() {
    requireStatus(BookingStatus.PRELIMINARY);
    return new Cargo(bookingId, shipperId, cargoType, weight, currentStateWith(BookingStatus.CONFIRMED));
}
```

**集約は不変で、遷移は新しいインスタンスを返す**方式を採っています。`this.status = CONFIRMED` と書き換えるのではなく、次の状態の `Cargo` を作って返す。呼び出し側は返り値をリポジトリに渡します。

```java
Cargo confirmed = cargo.confirm();
cargoRepository.updateStatus(confirmed);
eventPublisher.publishEvent(new BookingConfirmedEvent(confirmed.getBookingId()));
```

この時点の `requireStatus` はまだ抽出されておらず、各メソッドに `if (status != ...) throw` が散っていました。抽出は IT5 です。

イベント発行はアプリケーション層で行っています。ドメイン層で発行を溜めて、リポジトリ保存時にまとめて発火する（C# の `AggregateRoot.AddDomainEvent` 方式）ほうが漏れにくいのですが、Java 実装はこの選択をしていません。結果として **IT6・IT10 で「イベント発行が抜けている」指摘が実際に出ています**。

## 他言語ではどう書いたか

### 条件付き必須：型で不可能にするか、検査するか

この章の主題です。「種別が危険物なら申告必須」を 10 言語がどう表現したかで、型システムの差が最も鮮明に出ます。

#### 和型に追加情報を持たせる：Haskell

```haskell
-- Cargotracker/Booking/Domain/Model/Value/CargoType.hs
data CargoType
  = General
  | Hazardous !HazardousDeclaration
  | Refrigerated !TemperatureRequirement
```

モジュールのドキュメントコメントにこう書かれています。

> スマートコンストラクタが Domain 不変条件を保証するため、「種別 = 危険物だが宣言なし」のような無効な組み合わせは型エラーとなる。

`Hazardous` を作るには `HazardousDeclaration` を渡すしかありません。Java が実行時に検査していたものが、Haskell では **コンパイラが検査します**。`Cargo` 側には条件付き必須の検査コードが 1 行もありません。

F# も同じ発想ですが、`CargoType` ではなく `ShipperKind` と同様のパターンでスマートコンストラクタと `Result` を組み合わせています。

#### 種別 enum + オプショナルフィールド + 実行時検査

Java・C#・Rust・Go・TypeScript・Ruby・Scala・Flix がこちらです。ただし検査の厳しさに差があります。

Java は「必須なのに無い」だけを見ますが、Go は **逆方向も見ています**。

```go
// internal/booking/domain/cargo.go
func validateSpecialCargo(cargoType CargoType, hazardous *HazardousDeclaration, temperature *TemperatureRequirement) error {
	switch cargoType {
	case CargoTypeHazardous:
		if hazardous == nil {
			return ErrHazardousDeclRequired
		}
		if temperature != nil {
			return ErrSpecialInfoNotAllowed
		}
	case CargoTypeRefrigerated:
		if temperature == nil {
			return ErrTemperatureReqRequired
		}
		if hazardous != nil {
			return ErrSpecialInfoNotAllowed
		}
	case CargoTypeGeneral:
		if hazardous != nil || temperature != nil {
			return ErrSpecialInfoNotAllowed
		}
	}
	// ...
}
```

「一般貨物なのに温度条件が付いている」を拒否しています。Java 実装はこれを許容してしまいます（無視されて保存される）。**和型なら考える必要すらない**ケースを、Go は明示的に潰しに行った形です。

Rust は Java と同じく「欠落」だけを見ますが、エラーは `Result` で返します。

```rust
// crates/domain-booking/src/aggregate.rs
pub fn book(command: BookCargoCommand) -> Result<Self, BookingError> {
    match command.cargo_type {
        CargoType::Hazardous if command.hazardous_declaration.is_none() => {
            return Err(BookingError::MissingHazardousDeclaration);
        }
        CargoType::Refrigerated if command.temperature_requirement.is_none() => {
            return Err(BookingError::MissingTemperatureRequirement);
        }
        _ => {}
    }
    // ...
}
```

Rust は和型を持つ言語なので Haskell 方式も取れましたが、取っていません。理由は `sqlx` による永続化にあります。DB のテーブルは `cargo_type` カラム + nullable な特殊貨物カラムという形で、和型を素直にマッピングできません。Haskell 実装は手書き SQL でこの変換を引き受けており、その分マッピングコードが厚くなっています。

**型の表現力と永続化の素直さはトレードオフの関係にある**、というのがこの比較から読み取れることです。

#### Flix：拡張点だけ用意する

Flix の `CargoType` にはコメントが残っています。

```flix
/// 貨物種別。
/// `Hazardous` / `Refrigerated` は IT5（US05）で申告情報・温度条件を伴う。
/// **IT4 では種別だけを持てる**ようにし、条件フィールドの拡張点を用意する。
pub enum CargoType with Eq, ToString {
    case General
    case Hazardous
    case Refrigerated
}
```

Flix は 12 イテレーションと最も細かく刻んでおり、US05 は IT5 で扱われます。IT4 の段階では種別だけを定義し、追加情報は後で足す設計にしています。**和型なら後からフィールドを足しても、パターンマッチ側をコンパイラが全部指摘してくれる**ため、この段階的アプローチが安全に取れます。

### 状態遷移の書き方

US13 の予約確定で、各言語の状態遷移の作法が出そろいます。

Scala は状態型に遷移表を持たせ、集約はそれを問い合わせます。

```scala
// booking/domain/model/aggregates/Cargo.scala
def confirm(): Either[Cargo.Error, Cargo] =
  if status.canTransitionTo(BookingStatus.Confirmed) then Right(copy(status = BookingStatus.Confirmed))
  else Left(Cargo.InvalidStatusTransition(status, BookingStatus.Confirmed))
```

Haskell も同様に `canTransitionTo` を純粋関数として状態モジュールに置き、ドキュメントで「SSoT（信頼できる唯一の情報源）」と明記しています。

対して Java・C#・Go・Ruby・TypeScript は集約メソッド内で個別にガードします。IT2 の時点では 5 言語とも遷移条件が集約メソッドに散っている状態です。Java はこれを IT5 で `requireStatus` として抽出しました。

### 状態の数

面白いことに、状態の数と名前が言語ごとに違います。

| 言語 | 状態数 | 特徴 |
| :--- | :--- | :--- |
| Java | 8 | `PRELIMINARY` / `ROUTE_PROPOSED` / `CONFIRMED` / `TRACKING_ISSUED` / `IN_TRANSIT` / `DELIVERED` / `SETTLED` / `CANCELLED` |
| TypeScript | 9 | Java + `ROUTING_IN_PROGRESS`（経路設計中） |
| Haskell | 8 | `Draft` / `Submitted` / `RouteProposed` / `RouteAssigned` / `Confirmed` / `Settled` / `Cancelled` / `Closed` |
| Scala | 8 相当 | Haskell に近い（`RouteProposed` と `RouteAssigned` を分ける） |
| Flix | 8 | Java と同一 |

Haskell・Scala は「経路が提案された」と「経路が確定して紐付いた」を別状態に分けています。Java はどちらも `ROUTE_PROPOSED` で表し、旅程フィールドの有無で区別します。

この差は IT5（経路の選択・確定・紐付け）で効いてきます。Java 実装は `assignItinerary` が `ROUTE_PROPOSED` から `ROUTE_PROPOSED` への自己遷移になり、状態だけを見ても経路が紐付いたかどうか分かりません。Haskell・Scala は状態を見れば分かります。

**状態機械の粒度は、後のイテレーションで払うコストを決める**という例です。

Flix の `BookingStatus` には、8 値すべてを最初から定義した理由がコメントで残っています。

> 8 値すべてを定義するのは、パターンマッチの網羅性検査を効かせるためであり、状態を追加したときにコンパイラが考慮漏れを検出する。

未使用の値を先に定義しておくのは一見無駄ですが、網羅性検査がある言語では**将来の追加時に漏れを検出させるための投資**になります。

## このイテレーションの学び

IT2 のふりかえりで挙がった問題は、性質が IT1 と変わっています。受入基準の乖離は消え、代わりに **プロセスと基盤の問題**が出ました。

- **SonarQube Quality Gate が未確認** — IT1 に続き IT2 でもスキャン未実行
- **H2 DB の状態問題** — Spring DevTools のホットリスタート後も JVM が生き残るため、`DB_CLOSE_DELAY=-1` の設定でインメモリ DB が不整合状態になり、E2E テストが 500 エラーを起こす
- **設計ドキュメントの更新遅れ** — IT2 で実装した特殊貨物の属性・状態遷移・ファクトリメソッドが設計ドキュメントに反映されていない

1 つ目は以降 IT5 まで持ち越され、**4 イテレーション連続で未確認**という記録になります。2 つ目の H2 問題は IT8・IT9 でも再発しており、根本対策（PostgreSQL への移行）は最後まで先送りされました。

ここから読み取れるのは、**「後でやる」と決めた項目は自然には消化されない**という単純な事実です。IT2 のふりかえりで「次のイテレーションで対応」とした項目のうち、実際に IT3 で消化されたのは設計ドキュメントの同期だけでした。

対照的に、Ruby 実装のふりかえりでは同種の繰り越しに対して **「受入基準の項目落ち・偽陽性テストの防止を次 IT のプロセスに組み込む」**という形で、個別タスクではなくプロセス側に手を入れています。個別に積み残すのではなく、同じ漏れが起きない仕組みにする方が確実です。

---

- 前の章：[第 2 章：IT1 荷主登録と貨物予約の基盤](02-iteration-01.md)
- 次の章：[第 4 章：IT3 輸送見積と経路設計への引き渡し](04-iteration-03.md)
