# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

すべて **提案中**（人の承認待ち）です。設計書の ADR 候補を決定記録の形にしたもので、承認されるまで決定として扱いません（リスク台帳 R-01・R-02・R-08）。

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001: バックエンドのパターン・モジュール構成・Unit 間連携](./001-pattern-and-module-structure.md) | U-02〜U-05 をイベント履歴式ドメインモデル + CQRS、U-01 をレイヤード 3 層とし、Unit を最上位モジュールにして契約（contract）で連携する。 | 提案中 |
| [ADR-002: イベントストアと配信方式](./002-event-store-and-delivery.md) | ウォーキングスケルトンでは参照実装どおり（DynamoDB Journal + Streams + Lambda）で始め、最初の Unit 間の反応（Bolt 4）の前に PoC で A′／B へ移るかを決める。 | 提案中 |
| [ADR-003: コマンドの冪等性と識別子](./003-command-idempotency-and-identifiers.md) | コマンドは冪等性キー + PostgreSQL の表で二重処理を防ぎ、集約 ID は ULID をクライアント／ゲートウェイが生成し、業務番号は集約 ID と分ける。 | 提案中 |
| [ADR-004: 個人情報の扱い（荷主を CRUD にし、イベントに載せない）](./004-personal-data-handling.md) | 荷主（個人情報）はイベントソーシングにせず PostgreSQL の表（更新・削除可）に置き、イベントには荷主 ID と割引率の写しだけを載せる。 | 提案中 |
| [ADR-005: 時刻と業務日付](./005-clock-and-business-date.md) | 現在時刻は Clock ポートから取り、期限（日付）と到着（時刻付き）の比較は日付単位で行う。業務タイムゾーンの基準は人が決める。 | 提案中 |
| [ADR-006: API 方式（GraphQL）と公開照会の分離](./006-graphql-and-public-endpoint.md) | API は GraphQL（Sangria + circe）とし、ログイン無しの追跡照会は別エンドポイント・別スキーマ・別バンドル・別ターゲットグループに分離する。 | 提案中 |
| [ADR-007: 言語・実行環境・ビルド（Scala 3 LTS・JDK 21・sbt、ZIO は採らない）](./007-language-runtime-and-build.md) | Scala 3.9 LTS + JDK 21（Temurin）+ sbt 1.12 を採り、実行時のエフェクトは ZIO でなく Future + Either とし、Protobuf は ScalaPB のみを使う。 | 提案中 |
| [ADR-008: データストアの構成とスキーマ規約](./008-data-store-layout-and-conventions.md) | PostgreSQL に Unit ごとのスキーマ + 共有の reference・platform を置き、列挙値は text + CHECK、実行できる操作を読み取りモデルの列に持つ。 | 提案中 |
| [ADR-009: フロントエンドの構成（React + TypeScript + Vite の SPA）](./009-frontend-stack.md) | React + TypeScript + Vite の SPA とし、サーバー状態は TanStack Query + graphql-request + graphql-codegen、認証方式は AuthProvider の内側に閉じ込める。 | 提案中 |
| [ADR-010: クラウド・実行基盤・IaC・デプロイ戦略（AWS・ECS Fargate・Terraform・ローリング更新）](./010-cloud-runtime-and-iac.md) | AWS の ECS Fargate を実行基盤にし、Terraform で ECS・RDS・DynamoDB 等を IaC 化、ECR とローリング更新（読む側を先に配る）で配備する。 | 提案中 |
| [ADR-011: テスト戦略（形状・水準・DB・E2E）](./011-test-strategy.md) | U-02〜U-05 をピラミッド型、U-01 をダイヤモンド型とし、水準は Unit 別（Comprehensive／Standard）、DB 統合テストは PostgreSQL の実物、受入条件とテスト名を対応させる。 | 提案中 |
| [ADR-012: 税関と電子連携せず、通関結果を手入力で記録する](./012-customs-manual-entry.md) | 税関との電子連携は行わず、通関申告の結果は担当者の手入力で記録する（通関済でなければ引取不可）。 | 提案中 |
| [ADR-013: 非機能要件の初期目標と測定（SLO・RPO/RTO・保持）](./013-nonfunctional-baseline.md) | 規模の仮置き（年 12,000 件）から、反映遅延 p95 5 s／p99 15 s、公開照会 99.9%、業務 99.5%、RPO 15 分／RTO 4 時間などを出発点とし、ウォーキングスケルトンで実測して確定する。 | 提案中 |

## まだ ADR にしていない候補

選択肢の提示だけで、人の選択や PoC の結果が要るもの、または時期が先のものです。決まり次第、ADR にします。

| 候補 | 内容 | 起票の条件 |
| :--- | :--- | :--- |
| BE-09・FE-03 の方式 | 認証・認可の方式（セッション／トークン、パスワードの保存、ロック時間） | 人が選択肢から選ぶ（R-02）。Bolt 2 の前 |
| BE-12・NF-05 | クラスターモードか（同一集約への同時書き込みの扱い） | NFR-A17 の PoC の結果 |
| BE-03 の二次判断（A′／B） | 購読者追加後の配信方式 | Bolt 4 の前の PoC（ADR-002 を置換） |
| TS-03・TS-05 の残り | 永続化ライブラリの確定、DAO 生成の採否 | ADR-002 の PoC |
| TS の版の固定 | Flyway・TanStack Query・Terraform・Vite・React のマイナー | 着手時に確認 |
| FE-05・FE-06 | モバイル（オフライン）・デザインシステム | 現場の確認、UI 設計 |
| IF-04・IF-06・IF-07・IF-08 | event-processor の実行形態、復旧水準、公開照会の防御の詳細、運用タスク | PoC・非機能要件の合意後 |
| TST-04・TST-08 | 変異テスト、性能・負荷テストのツール | Bolt 5 の前／非機能要件の合意後（外部ツールは承認が要る） |
| NF-04・NF-05・NF-08 | セキュリティの基準と具体値、同時書き込み、経路算出の打ち切り | BE-09、PoC、Bolt 5 の前 |
| OP-01〜OP-08 | 運用体制、監視・通知の道具、変更の窓、復元の方式、縮退運転、システム管理者ロール | 体制の確定、MVP リリースの前 |
| DB-04〜DB-07 | 公開ビュー、非正規化、金額と通貨 | 各 Bolt の前 |

ADR の作成には `creating-adr` スキルを使用してください。
