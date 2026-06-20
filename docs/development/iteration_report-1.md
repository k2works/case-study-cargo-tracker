---
title: イテレーション 1 完了報告書
date: 2026-06-20
---

# イテレーション 1 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT1 |
| 期間 | 2026-06-22 〜 2026-07-05（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | 認証基盤と Shipper・Estimate・Cargo の DDD ドメイン基盤を構築 |
| 計画 SP | 12（ストレッチ） |
| 実績 SP | 12 |
| 達成率 | 100% |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US26 | システムにログイン・ログアウトする | ✅ 完了 | 2 | 2 |
| US02 | 荷主を登録する | ✅ 完了 | 2 | 2 |
| US03 | 法人荷主を登録する | ✅ 完了 | 2 | 2 |
| US01 | 輸送見積を作成する | ✅ 完了 | 3 | 3 |
| US04 | 貨物予約を登録する | ✅ 完了 | 3 | 3 |
| **合計** | | | **12** | **12** |

## バーンダウン

```mermaid
xychart-beta
    title "IT1 バーンダウン（実績）"
    x-axis ["開始", "Day 1"]
    y-axis "残 SP" 0 --> 12
    line "計画" [12, 0]
    line "実績" [12, 0]
```

## 成果物

### コミット履歴（9 件）

```text
dcee1e90 feat(booking): Cargo 集約 + CargoRepository + BookingController + 予約画面を実装
337544fa feat(estimation): 共有カーネル + Estimate 集約 + PricingService + 見積画面を実装
729f0a58 feat(shipper): ShipperController + 一覧・登録画面と E2E テストを実装
80ddbeca feat(shipper): V2 マイグレーションと ShipperRepository を実装
f3b35917 feat(auth): ログイン・ログアウトの画面と AuthController を実装
56dd1405 feat(auth): ScalikeJDBC ベースの UserRepository を実装
da466fca feat(auth): Flyway V1 で users と user_roles テーブルを作成
bd5e9f20 feat(auth): User 集約と Role 列挙を実装
172a846b feat(auth): bcrypt ベースの PasswordHash 値オブジェクトを実装
```

### 新規 ADR

- ADR 0002: 認証のパスワードハッシュに bcrypt、セッションを Play Session で管理
- ADR 0003: 料金計算ドメインサービスを Estimation と Billing で共通化
- ADR 0004: US26（認証・認可）を UC 横断ストーリーとして扱う

### Flyway マイグレーション

| バージョン | 内容 |
|-----------|------|
| V1 | users, user_roles |
| V2 | shipper |
| V3 | estimate, route_candidate |
| V4 | cargo |

### コードベース構成

```
app/cargotracker/
├── auth/              # Auth Context（横断）
│   ├── domain/        # User, Role, PasswordHash, UserRepository
│   ├── infrastructure/ # ScalikeJdbcUserRepository
│   └── interfaces/web/ # AuthController, AuthenticatedAction
├── shared/            # 共有カーネル
│   └── domain/        # Money, Location, CargoType, Weight, ShipperId, ShipperType
│       └── pricing/   # PricingService（ADR 0003）, InMemoryPricingService
├── shipper/           # Shipper Context（US02, US03）
│   ├── domain/        # Shipper, DiscountRate, ShipperRepository
│   ├── infrastructure/ # ScalikeJdbcShipperRepository
│   └── interfaces/web/ # ShipperController
├── estimation/        # Estimation Context（US01）
│   ├── domain/        # Estimate, EstimateId, RouteCandidate, EstimateStatus
│   ├── infrastructure/ # ScalikeJdbcEstimateRepository
│   └── interfaces/web/ # EstimateController
└── booking/           # Booking Context（US04）
    ├── domain/        # Cargo, BookingId, BookingStatus, RouteSpecification,
    │                  # CargoSpec, HazardousDeclaration, ShipperExistenceChecker
    ├── infrastructure/ # ScalikeJdbcCargoRepository,
    │                   # ShipperRepositoryBackedExistenceChecker
    └── interfaces/web/ # BookingController
```

## 品質メトリクス

| メトリクス | 目標 | 実績 | 評価 |
|-----------|------|------|------|
| ベロシティ | 10-12 SP | 12 SP | ✅ 達成 |
| テスト pass 率 | 100% | 100% (70/70) | ✅ |
| ScalafmtCheck | pass | pass | ✅ |
| ScalafixAll | pass | pass | ✅ |
| テストカバレッジ | 80% | 未測定 | ⚠️ IT2 で計測導入 |
| ArchUnit ルール | pass | 未実装 | ⚠️ IT2 で導入 |
| SonarQube QG | PASS | 未測定 | ⚠️ IT2 で導入 |

## テスト実績（70 件全パス）

### ドメインユニット（44 件）

| Spec | テスト数 |
|------|---------|
| PasswordHashSpec | 5 |
| UserSpec | 7 |
| ShipperIdSpec | 5 |
| ShipperSpec | 6 |
| MoneySpec | 5 |
| InMemoryPricingServiceSpec | 4 |
| EstimateSpec | 4 |
| BookingIdSpec | 5 |
| CargoBookingSpec | 3 |

### リポジトリ統合（7 件、Testcontainers PostgreSQL）

| Spec | テスト数 |
|------|---------|
| ScalikeJdbcUserRepositorySpec | 3 |
| ScalikeJdbcShipperRepositorySpec | 4 |

### E2E（17 件）

| Spec | テスト数 |
|------|---------|
| AuthEndpointSpec | 4 |
| ShipperEndpointSpec | 5 |
| EstimateEndpointSpec | 4 |
| BookingEndpointSpec | 3 |
| HealthEndpointSpec | 2 |

## リリース計画への影響

- **Phase 1（IT1-2）目標 22 SP**: IT1 で 12 SP 完了、IT2 残 10 SP（US05・US06・US24・US25 + Release 0.1 リリース準備）
- **総スコープ 91 SP**: 12 SP 完了（13%）

## 申し送り事項

詳細は [retrospective-1.md](./retrospective-1.md) を参照。重要項目:

1. **認証フィルタ適用**: ShipperController・EstimateController・BookingController に `AuthenticatedAction` を IT2 で適用
2. **シードユーザー**: 開発環境ログイン用に Flyway V5（または Module）でシードユーザーを投入
3. **ArchUnit ルール**: IT2 で 4 ルール導入
4. **SonarQube**: IT2 でセットアップ

## 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 1 計画](./iteration_plan-1.md)
- [イテレーション 1 ふりかえり](./retrospective-1.md)
