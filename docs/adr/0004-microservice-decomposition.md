# ADR-0004 マイクロサービスをバウンデッドコンテキスト単位で分割する

国際貨物輸送管理システムのマイクロサービス分割方針として、**DDD のバウンデッドコンテキスト（BC）を単位とし、Gradle マルチプロジェクト構成で独立したデプロイ単位**とする決定を記録する。

日付: 2026-05-14

## ステータス

承認済み

## コンテキスト

Phase 0 Walking Skeleton のコードレビューで、マイクロサービスの分割根拠が文書化されていないという指摘（Phase 0 レビュー タスク 1.3）があった。

現在のプロジェクト構成では `apps/backend/` 配下に次のサブプロジェクトが存在する。

| サブプロジェクト | 対応バウンデッドコンテキスト |
| :--- | :--- |
| `authms` | Auth Context（認証・認可） |
| `bookingms` | Booking Context（予約・荷主・見積） |
| `routingms` | Routing Context（航海スケジュール・経路算出） |
| `trackingms` | Tracking Context（追跡・例外管理） |
| `handlingms` | Handling Context（荷役作業記録） |
| `billingms` | Billing Context（請求・精算） |
| `gatewayms` | API Gateway（JWT 検証・ルーティング） |
| `shared` | 共有カーネル（`Location`・`UnLocode`） |

分割方針を明確にしないと、次のリスクが生じる。

- 開発者が境界を誤解し、コンテキストをまたぐ直接参照を追加する
- テストやデプロイの粒度が曖昧になる
- 将来の機能追加時に、どのサービスに実装すべきか判断できない

## 決定

**各バウンデッドコンテキストを独立した Gradle サブプロジェクト（Spring Boot アプリケーション）として分割し、それぞれを独立したデプロイ単位とする。**

### 分割基準

1. **ドメインの独立性**: コンテキスト内のモデルは外部コンテキストの型に直接依存しない
2. **データの独立性**: 各サービスは専用の PostgreSQL データベース（Read Model）を持つ（Database per Service パターン）
3. **デプロイの独立性**: 各サービスは独自の `Dockerfile`・`application.yml`・Flyway マイグレーションを持つ
4. **通信の非同期優先**: サービス間通信は Axon Server（Event Bus）経由の非同期イベントを優先する。同期通信（REST ACL）は経路候補取得など例外的な場合のみ許可する

### 境界ルール

| 許可 | 禁止 |
| :--- | :--- |
| Axon Event Bus 経由のイベント購読 | 他サービスの Aggregate クラスへの直接依存 |
| REST ACL（`outboundservices/acl/`）経由の同期呼出 | 他サービスのデータベースへの直接アクセス |
| `shared` ライブラリの値オブジェクト参照 | `shared` 以外の他サービスのドメインクラス参照 |

### 代替案

**モノリシックアーキテクチャ**
単一アプリケーションとして実装する方法。初期開発速度は高いが、国際貨物輸送ドメインの複雑な状態遷移（予約 → 経路 → 追跡 → 荷役 → 精算）と Axon Framework の Event Sourcing / Saga パターンを活かすには、コンテキスト境界を明確にする必要がある。将来の規模拡大時に分割コストが高くなるため却下。

**機能単位での分割**
CRUD 操作単位でサービスを分割する方法。ドメインの知識が分散し、Saga による業務プロセス調整が困難になるため却下。

## 影響

### ポジティブ

- コンテキスト境界が明確になり、新規コードの配置場所が自明になる
- 各サービスを独立してスケールアウトできる
- サービス障害の影響範囲が限定される
- Axon Framework の Event Sourcing・Saga パターンを各コンテキストで最大活用できる

### ネガティブ

- 分散システムの複雑さ（Eventual Consistency・Saga 補償アクション）が生じる
- 初期セットアップコスト（各サービスの Dockerfile・DB・Flyway）が高い
- ローカル開発に Docker Compose が必要になる
- テストで Testcontainers（Axon Server + PostgreSQL）が必要になる

## コンプライアンス

ArchUnit で次のルールを自動検証する（各マイクロサービスの `*ArchitectureTest.java` で定義）。

```java
// 他サービスのパッケージへの依存禁止
noClasses().that().resideInAPackage("..bookingms..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..authms..", "..routingms..", "..trackingms..", "..handlingms..", "..billingms..");
```

また、CI パイプラインで ArchUnit テストを必須ゲートとして実行する。

## 備考

- 著者: 開発チーム
- 関連 ADR: ADR-0001（Axon Framework 採用）、ADR-0005（shared モジュールの役割）
- 参照: `docs/design/architecture_backend.md`、`docs/design/domain-model.md`
