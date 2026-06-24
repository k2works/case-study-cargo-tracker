# 0017 Booking Context に公開 Port `BookingPublicApi` を導入する

他コンテキスト（Handling / Tracking / Billing）が Booking 内部実装 (`BookingCommandService` クラス) に直接依存する状態を解消し、Booking Context が「公開する API」を `BookingPublicApi` trait として明示的に切り出す。

日付: 2026-06-24

## ステータス

2026-06-24 承認・適用済 (IT8 タスク 0.3)。

## コンテキスト

### 既存の依存構造（IT7 完了時点）

`apps/cargo-tracker/app/cargotracker/handling/infrastructure/acl/BookingAdapter.scala` (IT7 0.3 で導入) は以下のように Booking Context の **application service クラスそのもの** に Inject していた:

```scala
@Singleton
class BookingAdapter @Inject() (bookingCommandService: BookingCommandService) extends BookingNotificationPort:
  // bookingCommandService.logHandlingNotification / .completeDelivery を呼ぶ
```

これにより:

1. **内部実装変更が Handling に波及する**: `BookingCommandService` に新メソッドが増減したり既存メソッドのシグネチャが変わると、Handling 側のコンパイル / テストが影響を受ける
2. **ArchUnit ルール 3（コンテキスト境界）をすり抜ける**: Adapter 経由とはいえ、依存先の型が他コンテキスト application service クラスである点で「Handling 外の application を直接参照しない」精神に反する
3. **Booking が「公開する API」と「内部実装」の区別が曖昧**: `BookingCommandService` の全 public メソッド (`book` / `cancel` / `confirm` / `issueTracking` / `logHandlingNotification` / `completeDelivery` / `escalateLost` / ...) のうち、どれが他コンテキストから呼んで良いものなのか、コードからは判別不能

### IT7 実装レビューでの指摘

- **H3 (xp-architect)**: 「Booking 側に公開 Port (`BookingPublicApi` trait) を切り、Adapter はそれを呼ぶ。現状は ArchUnit ルール 3 をすり抜けているだけで、Booking の内部実装変更が Handling に波及する」

### IT8 で同様の必要性

IT8 US23 では Billing Context から Booking Context に対して **`Cargo.markSettled` 状態遷移** を呼び出す必要がある (ADR 0019)。これも公開 API として `BookingPublicApi` 経由にしておけば、Billing からの依存も同じパターンで管理できる。

## 決定

**Booking Context の application 層に `BookingPublicApi` trait を新設し、他コンテキストの Adapter は本 trait のみに依存する構造に変更する。**

### 採用構造

```
booking/
├── application/
│   ├── api/
│   │   └── BookingPublicApi.scala        # ← 公開 API trait（他 Context から see する唯一の入口）
│   └── commandservices/
│       ├── BookingCommandService.scala   # BookingPublicApi を implement
│       ├── NotifyRouteCommandService.scala
│       └── ...
└── infrastructure/
    └── ...

handling/
├── domain/model/ports/
│   └── BookingNotificationPort.scala    # Handling 視点の出力ポート（既存維持）
└── infrastructure/acl/
    └── BookingAdapter.scala              # BookingPublicApi のみに依存（@Inject）
```

### Guice 設定

`Module.scala` で trait → 実装 class の bind を追加:

```scala
bind(classOf[BookingPublicApi]).to(classOf[BookingCommandService])
```

### IT8 で先行公開するメソッド

| メソッド | 用途 | 利用元 |
| :--- | :--- | :--- |
| `logHandlingNotification(bookingId, trackingNumber, eventType, location): Either[String, Unit]` | 荷役発生通知の記録 | Handling |
| `completeDelivery(bookingId, trackingNumber, location, recipientConfirmation): Either[String, Cargo]` | 引取完了 + 配送完了通知 | Handling |

### IT8 US23 で追加予定（先取り設計）

| メソッド | 用途 | 利用元 |
| :--- | :--- | :--- |
| `markSettled(bookingId): Either[String, Cargo]` | 入金確認後の Cargo.Settled 状態遷移 | Billing (タスク 2.5) |

US23 タスク 2.5 着手時に `BookingPublicApi` に `markSettled` を追加すれば、Billing 側の `BillingCommandService` から `BookingPublicApi` 経由で呼び出せる。Module bind は既に存在するので追加作業不要。

### 既存パターンとの整合性

`BillingCargoQueryPort` (IT6) や `TrackingLookupPort` (IT7) が「他 Context から自 Context を見るための **入力 Port** + ACL Adapter」だったのに対し、`BookingPublicApi` は「**自 Context が他 Context に公開する API**」という方向が逆になる。

- **入力 Port (BillingCargoQueryPort)**: Billing が「Booking から Cargo 情報を取得したい」場合、Billing 側に Port を定義し、Booking 側で Adapter 実装
- **公開 API (BookingPublicApi)**: Booking が「他から呼んで良い API はこれだけ」と明示、他 Context は trait のみに依存

両方向の Port パターンを併用する形でヘキサゴナル境界を強化する。

## 影響

### IT8 内変更

- `apps/cargo-tracker/app/cargotracker/booking/application/api/BookingPublicApi.scala` 新設
- `BookingCommandService` を `extends BookingPublicApi` に変更（既存メソッドのシグネチャ変更なし、無破壊）
- `BookingAdapter` の @Inject 引数を `BookingCommandService` → `BookingPublicApi` に切替
- `Module.scala` に `bind(classOf[BookingPublicApi]).to(classOf[BookingCommandService])` 追加

### 既存テスト

- `HandlingOrchestratorSpec` の InMemory 系テストは BookingNotificationPort のみを mock する構造のため影響なし
- `BookingCommandServiceSpec` は内部実装テストとして継続維持

### 他 Context への波及（先取り）

- IT8 US23: Billing → Booking の `markSettled` 呼び出しも `BookingPublicApi` 経由で実装
- IT9 以降: Tracking → Booking の何らかの通知が必要になっても `BookingPublicApi` に追加 + Tracking 側 Adapter 新設で対応

### ArchUnit ルール強化（IT9 候補）

将来的に「`booking.application.commandservices.*` を `booking` パッケージ外から参照することを禁ずる」ルールを追加できる。`booking.application.api.*` のみが外部参照を許容するインタフェースとなる。

### 帰結

- **境界尊重の強化**: Booking 内部実装の変更が他コンテキストに伝播しない構造
- **意図的な API 設計**: `BookingPublicApi` に列挙されたメソッドのみが公開、他は内部実装扱い
- **新規 trait の保守コスト**: メソッド追加時に trait と class 両方に追加する必要がある（小コスト、IDE 補助で吸収可）

## コンプライアンス

- `BookingAdapter` の Inject 型が `BookingPublicApi` であることを確認（grep で BookingCommandService 直接依存ゼロ）
- IT8 完了時に `architecture_backend.md` の「ヘキサゴナル境界」セクションに本 ADR への参照を追記 (0.12 で実施)
- IT9 で ArchUnit ルール「Handling / Billing / Tracking infrastructure が `booking.application.commandservices.*` を参照しない」を追加検討

## 備考

- 起票者: AI Agent (IT8 タスク 0.3、IT7 H3 解消)
- 関連 ADR: 0008 (queryservices 命名規約)、0016 (HandlingOrchestrator tx 境界)、0019 (Payment 集約方針 = markSettled 利用元の前提)
- 関連レビュー: `docs/review/it7_implementation_review_20260623.md` H3
- 関連 commit: IT8 0.3 commit (本 ADR と同時)
