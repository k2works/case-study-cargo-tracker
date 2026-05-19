# ADR-0014: shared モジュールへの Event クラス昇格

IT7 TI07「Event 駆動 ACL」の実装にあたり、`bookingms` が発行する `CargoBookedEvent` 等を複数のマイクロサービスが購読する必要が生じた。既存の `shared` モジュールに Event クラスを配置する方針を確定する。

日付: 2026-05-19

## ステータス

承認済み（2026-05-19 TI08-6 で策定）

## コンテキスト

IT6 で `trackingms` を新設し、`bookingms` の `POST /api/v1/tracking/_internal/initialize` を REST 経由で呼び出す「IT6 暫定実装」を採用した（`TrackingController._internal/initialize`）。

IT7 TI07 では Event 駆動化を行い、`bookingms` が `CargoBookedEvent` を発行し `trackingms` が Axon `@EventHandler` で購読する ACL（Anti-Corruption Layer）パターンに移行する計画である。

### 問題

Axon Framework の `@EventHandler` がイベントを受信するには、イベントクラスが **送信側・受信側の両 classpath に存在**する必要がある。

現状：

- `CargoBookedEvent` は `bookingms` ドメインの内部クラスとして定義されている
- `trackingms` は `bookingms` に依存しておらず、イベントクラスを参照できない

### 既存の shared モジュール

ADR-0005「shared モジュール導入」で `Location`・`UnLocode` の値オブジェクト共有が承認されている。`shared/src/main/java/com/example/cargotracker/shared/` 配下にパッケージが存在する。

ADR-0005 の適用範囲は「インフラ横断的な値オブジェクト」に限定されており、Event クラスの追加は明示的に承認されていなかった。

### 検討した代替案

| 案 | 評価 |
|----|------|
| A: bookingms を trackingms の classpath に追加 | サービス間に循環依存を生む。採用しない |
| B: 別途 events-api モジュールを新設 | shared モジュールが既にあり、過剰な分割。採用しない |
| C: shared モジュールに events パッケージを追加 | 既存の共有基盤を活用し変更範囲が最小。採用 |
| D: Axon Distributed Command Bus（コマンドのみ）で代替 | Event 駆動の非同期化という IT7 目標と合わない。採用しない |

## 決定

`shared` モジュールに `com.example.cargotracker.shared.events` パッケージを追加し、サービス間で共有する Event クラスをここに配置する。

### 昇格対象クラス（IT7 TI07 時点）

| クラス | 発行元 | 購読元 |
|-------|--------|--------|
| `CargoBookedEvent` | bookingms | trackingms |
| `CargoRoutedEvent` | bookingms | trackingms |
| `TrackingNumberIssuedEvent` | bookingms | trackingms |
| `CargoTrackedEvent` | bookingms | trackingms |

### パッケージ構造

```
shared/src/main/java/com/example/cargotracker/shared/
├── model/          # 既存: Location, UnLocode
│   └── valueobjects/
└── events/         # 新規追加
    ├── CargoBookedEvent.java
    ├── CargoRoutedEvent.java
    ├── TrackingNumberIssuedEvent.java
    └── CargoTrackedEvent.java
```

### Event クラスの設計原則

1. **イミュータブル**: `record` または全フィールド `final` の POJO
2. **Axon 依存なし**: `shared` モジュールは Axon Framework に依存しない。Event クラスは plain Java
3. **シリアライズ可能**: Jackson `ObjectMapper` でシリアライズ/デシリアライズできること（デフォルトコンストラクタまたは `@JsonCreator`）
4. **後方互換**: フィールド追加はデシリアライズ側で無視される。フィールド削除・リネームは Event Upcaster を使用する

## 影響

### 採用される構成

| 観点 | 設計 |
|------|------|
| パッケージ | `com.example.cargotracker.shared.events` |
| クラス形式 | Java `record`（Axon 依存なし） |
| 依存方向 | bookingms → shared ← trackingms |
| 既存 REST 暫定実装 | IT7 TI07 完了後に `_internal/initialize` エンドポイントを Deprecated 化 |

### 利点

1. **疎結合の維持**: サービス間に直接依存を持たずイベントクラスのみ共有
2. **Axon の宣言的 Handler**: `@EventHandler` アノテーションで購読を宣言でき、コードが簡潔
3. **既存 shared 基盤の活用**: 新規モジュール追加なしに対応可能
4. **テスト容易性**: plain Java record のため単体テストが容易

### トレードオフ

1. **shared モジュールの肥大化リスク**: Event クラスが増えると shared が巨大になる。追加時は本 ADR 更新を必須とする
2. **Event バージョニング**: スキーマ変更時に Axon Event Upcaster が必要。ADR 更新と合わせてアップキャスター実装を追記すること
3. **共有 classpath の制約**: shared を参照するすべてのサービスのリビルドが必要になる

### 申し送り

- [ ] IT7 TI07 完了後に bookingms 側の旧内部 Event クラスを削除し、shared の Event クラスへ切り替える
- [ ] `_internal/initialize` REST エンドポイントを Deprecated 化し、Sunset: 2026-08-30 を設定する（TI08-8 参照）
- [ ] Event スキーマ変更が発生した際は本 ADR に Upcaster の実装方針を追記する

## コンプライアンス

1. **ArchUnit**: `shared.events` パッケージが Axon Framework クラスを import していないこと
2. **依存方向テスト**: `trackingms` が `bookingms` を直接 import していないこと
3. **シリアライズテスト**: 全 Event クラスが Jackson でラウンドトリップできること

## 関連

- [ADR-0005 shared モジュール導入](0005-shared-module.md)
- [ADR-0012 handlingms と trackingms の責務分離](0012-handlingms-trackingms-responsibility-separation.md)
- [IT7 イテレーション計画](../development/iteration_plan-7.md)

## 備考

- 著者: AI Agent
- 関連イテレーション: IT7
- 関連タスク: TI07（Event 駆動 ACL）、TI08-6（本 ADR 策定）
