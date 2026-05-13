# ADR-0005 shared モジュールは共有カーネルとして Location・UnLocode のみを提供する

`apps/backend/shared` Gradle サブプロジェクトの責務を **共有カーネル（Shared Kernel）** として限定し、`Location`・`UnLocode` 値オブジェクトのみを提供する。他の値オブジェクト・ドメインクラスを shared に追加しない。

日付: 2026-05-14

## ステータス

承認済み

## コンテキスト

Phase 0 Walking Skeleton のコードレビューで、`shared` モジュールの役割と境界が文書化されていないという指摘（Phase 0 レビュー タスク 1.4）があった。

マイクロサービスアーキテクチャでは、複数のサービスが共通のモデルを参照したいケースが頻繁に発生する。共有範囲を明確にしないと、次のリスクが生じる。

- `shared` モジュールが肥大化し、変更時の影響範囲が全サービスに及ぶ
- コンテキスト間の結合度が高まり、独立したデプロイができなくなる
- 「共通化できそう」という理由で安易にクラスを追加し、境界が曖昧になる

現状の `shared` モジュールには `Location`（UN/LOCODE で識別される港湾）と `UnLocode`（5 文字の港湾コード値オブジェクト）が存在する。これらは国連が定める国際標準データであり、全コンテキストで同一の概念として使用される正当な共有候補である。

## 決定

**`shared` モジュールは `Location` と `UnLocode` の 2 クラスのみを提供する共有カーネルとする。それ以外のドメインクラスを shared に追加することを禁止する。**

### 共有カーネルに含めるクラス

| クラス | 型 | 理由 |
| :--- | :--- | :--- |
| `Location` | 値オブジェクト | UN/LOCODE で識別される港湾。全コンテキストで同一概念 |
| `UnLocode` | 値オブジェクト | `^[A-Z]{5}$` の形式検証を持つ。全コンテキストで同一の検証ルール |

### 共有カーネルに含めないクラス

次のクラスは「共通に見えるが、各コンテキストで意味が異なる」ため shared に追加しない。

| クラス | 理由 |
| :--- | :--- |
| `VoyageNumber` | Booking・Routing・Tracking・Handling の各コンテキストで独自の型として定義する |
| `Money` | Booking・Billing コンテキストで独自の通貨・計算ルールを持つ |
| `TrackingNumber` | Booking・Tracking コンテキストで独自の生成ルールを持つ |
| `BookingId` / `ShipperId` 等 | 各コンテキストの集約識別子。コンテキスト外では文字列で参照する |

### コンテキスト間の識別子の受け渡し

他コンテキストの集約識別子は、型ではなく **プリミティブ（`String`）** として受け渡す。受信側コンテキストが独自の値オブジェクトに変換する。

```java
// NG: Booking の型を Tracking が直接使用
public class TrackingActivity {
    private BookingId bookingId; // BookingId は Booking Context の型
}

// OK: String で受け取り、Tracking 独自の値オブジェクトに変換
public class TrackingActivity {
    private BookingId bookingId; // BookingId は Tracking Context 内で定義した値オブジェクト
}
```

### 代替案

**すべての共通型を shared に集約する**
`Money`・`TrackingNumber`・`VoyageNumber` 等を shared に追加する方法。共通化によって重複は減るが、コンテキストごとのビジネスルールの差異を表現できなくなる。また shared の変更が全サービスの再ビルド・再デプロイを要求するため却下。

**shared を廃止し、各コンテキストで完全に独立させる**
`Location`・`UnLocode` も各コンテキストが独自定義する方法。UN/LOCODE は国際標準であり同一の検証ルールを持つため、重複定義は混乱を招く。また Routing Context が管理する `location_master` テーブルとの整合性も取りやすいため却下。

## 影響

### ポジティブ

- shared の変更影響範囲が最小限（Location と UnLocode のみ）に限定される
- 各コンテキストが独立したビジネスルールを持てる
- コンテキスト間の結合度が低く保たれ、独立デプロイが容易になる
- shared の役割が明確になり、開発者が「どこに書くか」で迷わない

### ネガティブ

- `VoyageNumber`・`Money` 等が各コンテキストで重複定義される（意図的な重複）
- コンテキスト間で同一概念が異なる型として存在するため、変換コードが必要になる場面がある

## コンプライアンス

ArchUnit で次のルールを自動検証する。

```java
// shared への追加禁止クラスを検出
classes().that().resideInAPackage("..shared..")
    .should().beAssignableTo(
        com.example.shared.domain.model.Location.class,
        com.example.shared.domain.model.UnLocode.class
    );
```

コードレビューのチェックリストに「shared への新規クラス追加は ADR-0005 の例外承認が必要」を追加する。

## 備考

- 著者: 開発チーム
- 関連 ADR: ADR-0001（Axon Framework 採用）、ADR-0004（マイクロサービス分割方針）
- 参照: `docs/design/architecture_backend.md`、`docs/design/domain-model.md`（Shared Domain セクション）
