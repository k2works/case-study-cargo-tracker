# 第 2 章：IT1 荷主登録と貨物予約の基盤

## このイテレーションのゴール

> 荷主登録（個人・法人）と貨物予約登録の基本フローが動作すること

最初のイテレーションで作るのは、**縦に一本通ったウォーキングスケルトン**です。画面から入力した荷主が DB に保存され、その荷主を指定して貨物予約が登録され、予約番号が返る。この一本が通れば、以降のイテレーションは横に広げるだけになります。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | 10 |
| Java テスト | 60 件 |
| Playwright E2E | 27 件 |
| 命令カバレッジ | 89% |
| ブランチカバレッジ | 65% |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US02 | 荷主を登録する | 3 |
| US03 | 法人荷主を登録する | 2 |
| US04 | 貨物予約を登録する | 5 |

US03 の受入基準には「契約番号・割引率が保存される」、US04 には「荷主 ID を指定して貨物予約を登録し、予約番号が発行される」が含まれます。

## Java 実装

### 値オブジェクトから始める

最初に書いたのは集約ではなく値オブジェクトです。Java 17 以降の `record` はコンパクトコンストラクタで不変条件を検査でき、値オブジェクトの表現に向いています。

```java
// shipper/domain/model/valueobjects/Email.java
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("email format is invalid");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
```

`Email` 型が存在する時点で、その値は検証済みです。これが値オブジェクトを導入する唯一の理由と言ってよく、以降どのレイヤでもメールアドレスの再検査は不要になります。

割引率も同じ形です。

```java
// shipper/domain/model/valueobjects/DiscountRate.java
public record DiscountRate(BigDecimal value) {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("0.30");

    public DiscountRate {
        if (value == null) {
            throw new IllegalArgumentException("discountRate must not be null");
        }
        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("discountRate must be between 0.0 and 0.30");
        }
        value = value.stripTrailingZeros();
    }
}
```

金額比率に `double` ではなく `BigDecimal` を使っているのは、精算（IT6）で金額計算に持ち込むためです。ここで `double` を選ぶと、5 イテレーション後に丸め誤差の修正が必要になります。

なお、この `MAX_VALUE = 0.30` は **IT1 時点では `0.15` でした**。受入基準の「0〜30%」に対して実装が 15% だったことが IT1 のふりかえりで検出され、IT2 で修正されています。詳細は後述します。

### 荷主集約：個人と法人をどう分けるか

US02（個人）と US03（法人）は、同じ「荷主」でありながら法人だけが契約番号と割引率を持ちます。この差を Java ではどう表現したか。

```java
// shipper/domain/model/aggregates/Shipper.java（抜粋）
public class Shipper {

    private final ShipperId id;
    private final ShipperCode code;
    private final ShipperName name;
    private final Email email;
    private final Phone phone;
    private final Address address;
    private final ShipperType shipperType;

    public static Shipper individual(
            ShipperId id, ShipperCode code, ShipperName name,
            Email email, Phone phone, Address address
    ) {
        return new Shipper(id, code, name, email, phone, address, ShipperType.INDIVIDUAL);
    }

    public static Shipper corporateBase(
            ShipperId id, ShipperCode code, ShipperName name,
            Email email, Phone phone, Address address
    ) {
        return new Shipper(id, code, name, email, phone, address, ShipperType.CORPORATE);
    }

    public CorporateShipper asCorporate(ContractNumber contractNumber, DiscountRate discountRate) {
        return new CorporateShipper(this, contractNumber, discountRate);
    }
}
```

採用したのは **継承ではなく合成** です。`CorporateShipper` は `Shipper` を継承せず、`Shipper` を内包して契約情報を足します。`ShipperType` 列挙が種別を保持し、法人固有の情報は別クラスが持つ、という二段構えです。

この設計には弱点があります。`ShipperType.CORPORATE` である `Shipper` インスタンスが、`CorporateShipper` に包まれずに存在しうる、という点です。型の上では「法人なのに契約情報がない荷主」を作れてしまいます。`corporateBase` というファクトリ名は、それが中間状態であることを名前で示している、というだけの防御です。

この弱点は後述の言語比較で最も鮮明に出ます。

### 貨物予約集約

`Cargo` 集約は、予約の状態と貨物仕様を統括します。IT1 の時点では状態は `PRELIMINARY`（仮受付）のみで、遷移はまだありません。

```java
// booking/domain/model/aggregates/Cargo.java（IT1 時点の骨子）
public Cargo(
        BookingId bookingId,
        ShipperId shipperId,
        CargoType cargoType,
        BigDecimal weight,
        RouteSpecification routeSpecification
) {
    this(bookingId, shipperId, cargoType, weight,
         state(routeSpecification, BookingStatus.PRELIMINARY, null, null));
}
```

重量の検査は集約のコンストラクタに置きました。

```java
if (this.weight.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("weight must be greater than zero");
}
```

`Weight` 値オブジェクトを作らず `BigDecimal` を直接持たせたのは IT1 時点の割り切りです。他の言語実装では `Weight` を値オブジェクトにしているものが多く（F#・Rust・Go・TypeScript）、そちらの方が一貫しています。

### コマンドサービス

ユースケースはアプリケーション層のコマンドサービスが担います。

```java
// booking/application/internal/commandservices/CargoBookingCommandService.java（抜粋）
public BookingId bookCargo(BookCargoCommand command) {
    ShipperId shipperId = new ShipperId(UUID.fromString(command.shipperId()));
    if (!shipperExistenceChecker.exists(shipperId)) {
        throw new ShipperNotFoundException(shipperId);
    }
    // ... 値オブジェクトの組み立て
    Cargo cargo = new Cargo(new BookingId(UUID.randomUUID()), shipperId, /* ... */);
    cargoRepository.save(cargo);
    return cargo.getBookingId();
}
```

注目すべきは `shipperExistenceChecker` です。Booking Context は荷主が実在するか確認する必要がありますが、**Shipper Context のリポジトリを直接呼んではいけません**。呼ぶのは「存在するか」だけを問うポートです。

```java
// booking/application/internal/outboundservices/ShipperExistenceChecker.java
public interface ShipperExistenceChecker {
    boolean exists(ShipperId shipperId);
}
```

ただし、**IT1 の実装はこうなっていませんでした**。当初は `ShipperRepository` を直接注入しており、これがふりかえりで境界違反として検出されます。上のコードは IT2 での修正後の姿です。

## 他言語ではどう書いたか

### 荷主の個人／法人をどう表現したか

このイテレーションで最も設計が割れたのが、「法人だけが契約番号と割引率を持つ」という条件付き必須の表現です。3 つの流派に分かれました。

#### 流派 1：和型（sum type）で法人が契約情報を内包する

F#・Rust・Haskell がこれです。

```fsharp
// F#: CargoTracker.Shipper/Domain.fs
/// 荷主種別。継承ではなく DU で表現し、法人は契約番号と割引率を「必ず」持つ。
type ShipperKind =
    | Individual
    | Corporate of ContractNumber * DiscountRate
```

```rust
// Rust: crates/domain-shipper/src/aggregate.rs
/// 荷主種別。法人は契約情報を型として内包し、「法人なら契約情報必須」を型で強制する。
pub enum ShipperKind {
    Individual,
    Corporate(CorporateProfile),
}
```

```haskell
-- Haskell: Cargotracker/Shipper/Domain/Model/Shipper.hs
data ShipperKind
  = Individual
  | Corporate !CorporateNumber !ContractRank
```

この 3 つでは、**「法人なのに契約番号がない荷主」という値が言語の型システム上つくれません**。Java の `corporateBase` が抱えていた中間状態が、そもそも表現できないのです。

Haskell だけは割引率ではなく `ContractRank`（Bronze / Silver / Gold）を持ち、割引率をランクから導出しています。契約ランクという業務概念を型にした分、Java 版の「0〜30% の任意の数値」より制約が強く、割引率の入力ミスが起こりえない設計になっています。

#### 流派 2：単一クラス + オプショナルフィールド

Scala・C#・Go がこれです。

```scala
// Scala: shipper/domain/model/aggregates/Shipper.scala
/** 荷主集約ルート。個人・法人を 1 つのクラスで表現する（variant フィールドで分岐）。
  *   - 個人: `contractNumber = None`、`discountRate = DiscountRate.zero`
  *   - 法人: `contractNumber = Some(_)`、`discountRate` は 0〜0.30
  */
final case class Shipper private (
    shipperId: ShipperId,
    // ...
    shipperType: ShipperType,
    contractNumber: Option[String],
    discountRate: DiscountRate,
    version: Int = 0
)
```

```go
// Go: internal/shipper/domain/shipper.go
type Shipper struct {
	id          ShipperId
	// ...
	shipperType ShipperType
	contract    *CorporateContract
}
```

Scala は `Option`、Go は nil 許容ポインタ、C# は null 許容参照型（`ContractNumber?`）で「あるかもしれない」を表現します。整合性（種別が法人ならフィールドが埋まっている）はファクトリメソッドが保証しますが、型そのものは保証しません。

Scala は `enum`・ADT を持つ言語なのに和型を選ばなかった点が興味深いところです。ドキュメントのコメントに「variant フィールドで分岐」と明記されており、意識的な選択です。理由は永続化にあります。DB の `shipper` テーブルは 1 つで、種別カラムと nullable な契約カラムを持つ。テーブルの形に集約の形を寄せた、という判断です。

#### 流派 3：動的型付けで実行時に守る

Ruby と TypeScript は、条件付き必須をファクトリメソッド内の検証で守ります。TypeScript は静的型を持ちますが、荷主の個人／法人には判別可能ユニオンを使わずオプショナルプロパティを選んでいます。

Flix は和型を持つ言語ですが、Shipper Context については種別 enum + オプショナルフィールドで実装しています。**同じ言語でも、コンテキストによって表現を変えている**例です（Flix の Booking Context では `BookingStatus` を 8 値の enum で定義し、パターンマッチの網羅性検査を効かせています）。

### 貨物予約集約の共通形

`Cargo` については、10 言語すべてがほぼ同じ形に落ちました。

- 集約ルートは不変（Java・Scala・Rust・Haskell・F#）または内部可変（C#・Go・Ruby・TypeScript）
- 生成はファクトリ（`book` / `Create` / `RegisterCargo` / `mkCargo`）
- 状態は `PRELIMINARY` 相当から開始
- 荷主参照は ID（または業務コード）のみを持ち、`Shipper` オブジェクトは持たない

最後の点は全言語共通で守られています。集約間の参照を ID に限るのは DDD の基本ですが、ここでは同時に **Bounded Context をまたぐ参照を細くする**役目も果たしています。Go 実装はさらに踏み込み、UUID ではなく業務識別子で持たせています。

```go
// Cargo は予約コンテキストの集約ルート。
// 荷主参照は BC 独立性のため業務識別子 ShipperCode で保持する（ADR-0005）。
type Cargo struct {
	bookingID   BookingId
	shipperCode shared.ShipperCode
	// ...
}
```

内部 ID（UUID）ではなく業務コードを持つことで、Shipper Context の内部識別子体系が変わっても Booking Context が影響を受けません。ADR に記録されている通り、これは意識的な設計判断です。

### エラーの返し方

例外か戻り値かは、言語の慣習にきれいに沿いました。

| 方式 | 言語 | 例 |
| :--- | :--- | :--- |
| 例外 | Java・C#・Ruby・TypeScript | `throw new IllegalArgumentException(...)` |
| `Result` / `Either` | F#・Rust・Scala・Haskell・Flix | `Error(ValidationError("Weight", "..."))` |
| 多値返却 | Go | `return nil, ErrHazardousDeclRequired` |

`Result` 系の 5 言語では、値オブジェクトの生成そのものが `Result` を返します。

```fsharp
// F#: Weight の生成は必ず Result を返す
let create (value: decimal) : Result<Weight, DomainError> =
    if value <= 0m then
        Error(ValidationError("Weight", "重量は正の値でなければなりません。"))
    elif value > maxWeightKg then
        Error(ValidationError("Weight", "重量は 30,000 kg（コンテナ最大積載相当）以下でなければなりません。"))
    else
        Ok(Weight value)
```

この差は、**バリデーションエラーをまとめて返せるか**に効きます。Java 版はコンストラクタが最初のエラーで例外を投げるため、フォームの全項目のエラーを一度に返すには別途 Bean Validation が必要です。F# 版は `FsToolkit.ErrorHandling` の `validation` コンピュテーション式で適用的にエラーを収集し、ドメイン層の検証だけで全エラーを一度に返せます。入力フォームの UX を考えると、この差は小さくありません。

## このイテレーションの学び

IT1 のふりかえりでは、**受入基準と実装の乖離が 3 件**検出されました。

| 検出内容 | 内容 |
| :--- | :--- |
| 住所フィールド欠落 | US02 受入基準「氏名/社名・住所・連絡先」で住所が未実装 |
| 割引率上限の乖離 | US03 受入基準「0〜30%」に対し実装は 15% |
| 寸法・個数・品名の欠落 | US04 受入基準の 5 項目のうち 3 項目が未実装 |

原因分析はこう記録されています。

> Red フェーズでのユーザーストーリー受入条件の読み込みが不十分だった。TDD サイクルのテスト設計時に受入基準を網羅的にチェックリスト化する習慣が欠けていた。

テストが全件緑でも、**書いたテストが受入基準を写していなければ意味がない**という、TDD の一番古い落とし穴です。60 件のテストは全部通っていました。

もう 1 つ、アーキテクチャ上の問題が 3 件出ています。

- Booking Context が Shipper Context の `ShipperRepository` に直接依存（境界違反）
- `ShipperId` が共有カーネルではなく Shipper Context 内に残存
- **ArchUnit テストの不足** — コンテキスト間分離ルールが自動テストになっておらず、CI で違反を検出できなかった

3 つ目が本質です。1 つ目と 2 つ目は「守るつもりだったが守れていなかった」だけで、それを検出する仕組みがなかったことが根本原因です。IT2 で ACL 導入・共有カーネル移動と同時に ArchUnit ルールを追加し、以降このクラスの違反は再発していません。

**設計原則は、機械が検証していない限り守られていないと考えるべき**、というのがこのイテレーション最大の学びです。

---

- 前の章：[第 1 章：モノリスアーキテクチャの全体像](01-architecture.md)
- 次の章：[第 3 章：IT2 特殊貨物と予約確定](03-iteration-02.md)
