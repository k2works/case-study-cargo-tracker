# 第 7 章：IT6 法人割引と精算処理

## このイテレーションのゴール

> IT5 レビュー高優先度対応と法人割引・精算処理を完成させ、プロジェクト全機能をリリース可能状態にする

Phase 2（経路設計）を中断して Phase 3（精算）に飛びます。追跡機能（US14〜US18）を後回しにして先に精算を作る、という順序です。理由は Release 1.0 の内容にあります。「予約 → 経路設計 → 精算」という一本の業務フローが完結すれば、追跡がなくても価値のあるリリースになるためです。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | 10 |
| Java テスト | 272 件（+22） |
| Playwright E2E | 67 件（+11） |
| 命令カバレッジ | 81%（IT5 の 88% から -7%） |
| リリース | **Release 1.0** |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US22 | 法人割引を適用する | 3 |
| US23 | 精算を処理する | 5 |
| IT5-改善 | 受入条件充足・イベント発行・パターン統一 | 3 |

## Java 実装

### 金額を扱うコンテキスト

Billing Context が新設されます。ここで初めて **金額**という、丸め誤差と監査要件を伴う値を扱います。

```java
// billing/domain/model/aggregates/Invoice.java
public class Invoice {

    private final InvoiceId invoiceId;
    private final String bookingId;
    private final int totalAmountValue;
    private final DiscountPolicy discountPolicy;
    private final int discountedAmountValue;
    private PaymentStatus paymentStatus;
    private final LocalDate dueDate;

    public Invoice(
            InvoiceId invoiceId, String bookingId, int totalAmountValue,
            DiscountPolicy discountPolicy, LocalDate dueDate
    ) {
        // ... null / 負値チェック
        this.discountedAmountValue = discountPolicy.apply(totalAmountValue);
        this.paymentStatus = PaymentStatus.PENDING;
        this.dueDate = dueDate;
    }
}
```

金額は `int`（円単位の整数）です。`BigDecimal` ではなく整数を選んだのは、日本円に小数がないためで、この題材では妥当な判断です。ただし通貨を持たないため、多通貨対応が必要になった時点で全面的な変更を強いられます。

割引後金額 `discountedAmountValue` は **コンストラクタで計算して保持**しています。毎回計算せず確定値として持つのは、請求書は発行時点の金額を固定すべきものだからです。後から割引ポリシーが変わっても、発行済み請求書の金額は変わりません。

### 割引ポリシー

割引は値オブジェクトです。

```java
// billing/domain/model/valueobjects/DiscountPolicy.java
public record DiscountPolicy(DiscountType type, BigDecimal rate) {

    public DiscountPolicy {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (rate == null) throw new IllegalArgumentException("rate must not be null");
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate must be between 0 and 1");
        }
    }

    public static DiscountPolicy none() {
        return new DiscountPolicy(DiscountType.NONE, BigDecimal.ZERO);
    }

    public static DiscountPolicy corporate(BigDecimal rate) {
        return new DiscountPolicy(DiscountType.CORPORATE, rate);
    }

    public int apply(int baseAmount) {
        if (type == DiscountType.NONE || rate.compareTo(BigDecimal.ZERO) == 0) {
            return baseAmount;
        }
        BigDecimal discount = BigDecimal.valueOf(baseAmount).multiply(rate).setScale(0, RoundingMode.DOWN);
        return baseAmount - discount.intValue();
    }
}
```

丸めは `RoundingMode.DOWN`（切り捨て）を明示しています。金額計算で丸めモードを書かないと、`BigDecimal.setScale` は例外を投げるか、実装依存の挙動になります。**丸めは業務判断であり、デフォルトに委ねてはいけない**という基本が守られています。

切り捨てを選んだことで、割引額が業務上わずかに小さくなります（顧客に有利ではない側に倒れる）。この判断自体は記録に残っていませんが、金額を扱うコードでは丸め方向の選択理由を ADR に残すべき類の判断です。

### コンテキストをまたぐ割引率の取得

法人割引を適用するには、荷主の契約割引率が必要です。しかし Billing Context は Shipper Context のドメインモデルを知りません。

```java
// billing/application/internal/outboundservices/acl/ShipperDiscountPort.java
public interface ShipperDiscountPort {
    DiscountPolicy getDiscountPolicyForShipper(String shipperId);
}
```

ポートが返すのは **Billing Context 自身の型**（`DiscountPolicy`）です。`Shipper` でも `DiscountRate`（Shipper Context の値オブジェクト）でもありません。アダプター側で変換します。

```java
// billing/infrastructure/services/ShipperDiscountAdapter.java（構造）
// Shipper Context の DiscountRate → Billing Context の DiscountPolicy に変換
```

これが ACL（腐敗防止層）の本来の姿です。外部の概念を、自コンテキストの語彙に翻訳して受け取る。ポートの戻り値を `DiscountRate` にしてしまうと、Shipper Context の値オブジェクトが Billing Context のドメインモデルに侵入します。

同じ構造が予約側にもあります。

```java
// billing/application/internal/outboundservices/acl/BookingSettlementPort.java
// 精算完了を Booking Context に通知するポート
```

Billing → Booking の通知も、直接 `CargoBookingCommandService` を呼ばずポート越しに行います。

### 支払状態の遷移

`Invoice` は独自の状態機械を持ちます。

```java
public void confirmPayment() {
    if (paymentStatus != PaymentStatus.PENDING) {
        throw new IllegalStateException("精算済みまたは期限超過の請求書は確認できません。");
    }
    this.paymentStatus = PaymentStatus.CONFIRMED;
}
```

`Cargo` と違い、`Invoice` は **可変**です（`paymentStatus` に `final` が付いていません）。同じプロジェクトの中で、集約によって不変・可変が分かれています。

意図的な使い分けではなく、実装の揺れと見るのが妥当です。`Cargo` は IT1 から不変で作られ、`Invoice` は IT6 で別のタイミングに書かれました。**一貫性は、明示的なガイドラインがない限り時間とともに失われる**という典型例です。

## 他言語ではどう書いたか

### 金額の表現

金額型の選択は、実装ごとに明確に分かれました。

| 言語 | 金額の型 | 通貨 |
| :--- | :--- | :--- |
| Java | `int`（円） | 持たない |
| C# | `decimal` | 持たない |
| F# | `decimal` | 持たない |
| Scala | `BigDecimal` | 持たない |
| Rust | 整数（最小単位） | 持たない |
| Go | `Money` 値オブジェクト | 持たない |
| Haskell | 整数（最小単位） | 持たない |
| TypeScript | `number`（円） | 持たない |
| Ruby | `BigDecimal` | 持たない |

全実装が単一通貨前提です。国際貨物輸送というドメインで通貨を持たないのは、業務的には不自然ですが、スコープの割り切りとして全実装が一致しています。

TypeScript の `number`（IEEE 754 倍精度浮動小数点）だけは注意が必要です。円単位の整数であれば 2^53 まで正確なので実害は出ませんが、割合計算を挟むと誤差が入ります。TypeScript 実装のふりかえりには、実際に金額まわりのバグが記録されています。

> 割引率逆算による請求書復元クラッシュの解消（High・重大バグ）

割引後金額と割引率から元金額を逆算する処理でクラッシュしたという内容です。**割引後金額を保持せず逆算で求める設計**が原因で、Java 実装が `discountedAmountValue` を確定値として持っているのと対照的です。

Go 実装では別種の金額バグが検出されています。支払期限（DATE 型・00:00）と到着時刻（TIMESTAMP）を素朴に比較したため、期限当日に到着した貨物を期限超過と判定していました。対処は日付単位での比較への正規化と、「当日時刻付き到着」を含む境界テストの追加です。

**金額と日付の境界は、テストケースを意識的に作らないと通り抜ける**という点で、この 2 件は同じ性質の問題です。

### 期限超過の検出をどう駆動するか

US23 の受入基準に「支払期限を超過したら経理に未払い通知を送る」が含まれます。これは **誰が起動するのか**という問題を生みます。ユーザー操作で起きるイベントではないためです。

各実装の対処は次の通りです。

| 言語 | 期限超過チェックの駆動 |
| :--- | :--- |
| Java | 一覧表示時に動的判定 |
| Rust | 手動駆動エンドポイント `/billing/invoices/check-overdue` + 一覧ボタン |
| Haskell | `OverdueCheckCommand` を実装・テスト済みだが **`Main` に未配線** |
| Ruby | 別イテレーション（IT8）で実装 |

Haskell 実装のイテレーション報告に、この状態が正直に記録されています。

> 5. 期限超過時に未払い通知 → **未達 (H-02)**: `OverdueCheckCommand` は実装・テスト済だが Main 未配線 (起動主体不在)。IT9 へ

コマンドは書かれ、単体テストも通っている。しかし**誰も呼んでいない**。テストは全件緑で、カバレッジも下がりません。それでも受入基準は満たされていません。

Rust 実装は同じ問題をクローズ前のレビューで検出し、返済しています。

> 5 視点レビューで 3 視点が重複指摘した「CheckOverdueService 未配線（受入基準 5 が実運用で駆動しない）」をクローズ前に手動駆動導線＋HTTP 実証で返済した。

対処が「手動駆動エンドポイント + 一覧ボタン」であることに注目してください。バッチスケジューラを入れるのではなく、**経理担当者が押せるボタン**を作りました。業務として回る最小の形です。そのうえで HTTP テストで OVERDUE 遷移と通知の 1:1 対応を実証しています。

**「実装した」と「業務として駆動する」は別物**であり、単体テストは前者しか保証しません。

### 集約の可変・不変

Java が `Cargo`（不変）と `Invoice`（可変）で揺れたのに対し、Rust・Haskell・F# は言語の性質上ほぼ強制的に不変です。

Rust では `&mut self` を取るメソッドを書けば可変にできますが、実装は `Result<Self, Error>` を返す形で統一されています。所有権システムがあるため、可変にすると呼び出し側で借用の取り回しが増え、不変のほうが素直に書けるためです。

C# は `AggregateRoot` 基底クラスを持ち、その中でドメインイベントを溜める設計です。

```csharp
// C#: Cargo.Create の末尾
cargo.AddDomainEvent(new CargoBookedEvent(cargo.BookingId));
return cargo;
```

イベントを集約が保持し、リポジトリ保存時に MediatR へまとめて発行します。**Java がアプリケーション層で明示的に `eventPublisher.publishEvent` を呼んでいたのに対し、C# は集約側に溜める**方式です。

この差は IT6 の Java 側の問題として現れました。ふりかえりの申し送りに「ドメインイベント発行の一貫性確保」が含まれており、**発行を忘れた箇所があった**ことを意味します。IT10 の成功基準には「H-2: `assignItinerary` 完了時に `CargoRoutedEvent` が発行されている（テストで明示）」が残っています。

集約側に溜める設計なら、「集約の状態が変わったのにイベントがない」を集約の単体テストで検出できます。アプリケーション層で発行する設計では、コマンドサービスのテストを書かない限り検出できません。

## このイテレーションの学び

### Release 1.0 に到達

IT6 完了時点でリリース条件が満たされました。

- 全テストがパス（Java 272 件・E2E 67 件）
- SonarQube Quality Gate PASS

「予約 → 経路設計 → 精算」の一本が通り、追跡機能なしでもリリース可能な状態です。追跡は IT7・IT8 で追加され、Release 2.0 となります。

**フェーズを飛ばして先に価値のある一本を通す**という判断が、ここで回収されました。Phase 2 を順番に消化していたら、Release 1.0 は 2 イテレーション遅れていたことになります。

### カバレッジは新規コンテキスト追加で必ず下がる

命令カバレッジ 88% → 81%。Billing Context 追加が原因です。第 5 章で見た規則性が再現しました。

同時に、ブランチカバレッジの追跡が不十分だったことも記録されています。

> 命令カバレッジ 81% に対しブランチカバレッジの追跡が十分でなかった。精算コンテキストの異常系（割引率境界値・支払い期限超過）のブランチが未テストの可能性がある。

割引率の境界値（0%・30%）と支払期限超過は、まさに他言語で実際のバグが出た箇所です。TypeScript の割引率逆算クラッシュ、Go の DATE/TIMESTAMP 境界バグ。**同じ箇所で複数の実装が転んでいる**という事実は、そこがこのドメインの本質的な難所であることを示しています。

Java 実装では、この領域の E2E 異常系テスト（バリデーションエラー・OVERDUE シナリオ）が IT7 に持ち越されました。

---

- 前の章：[第 6 章：IT5 経路の選択・確定・紐付け](06-iteration-05.md)
- 次の章：[第 8 章：IT7 追跡番号発行と荷役作業記録](08-iteration-07.md)
