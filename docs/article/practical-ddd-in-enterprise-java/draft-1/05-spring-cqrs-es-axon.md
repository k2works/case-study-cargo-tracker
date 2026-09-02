---
type: Article
title: "第 5 章：Spring Platform × CQRS/ES（Axon）"
description: "Axon Framework によるイベントソーシングと CQRS で Cargo Tracker を構成する（draft-1）。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-27T08:25:38Z }
---

# 第 5 章：Spring Platform × CQRS/ES（Axon）

この章では、Cargo Tracker を CQRS/ES（Axon）として再構成する観点を整理します。  
先に明確にしておくと、**現行リポジトリには Axon 実装は含まれていません**。本章は、既存の Spring モジュラーモノリス／EDA 実装を起点に、CQRS/ES へ移行する際の設計差分を明文化する章です。

## イベントソーシング

現行実装は、イベントストアではなく状態ストア（RDB）中心です。たとえば `Cargo` の状態更新は `MyBatisCargoRepository` が `CargoRecord` へ反映します。

```java
public boolean update(Cargo cargo) {
    return mapper.updateStatus(toRecord(cargo)) == 1;
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/infrastructure/repositories/MyBatisCargoRepository.java`

CQRS/ES へ移行する場合、状態更新の正典はイベント列となり、`CargoRecord` は投影（read model）として再定義する必要があります。

## CQRS

現行コードはすでに CQRS の土台を持っています。

- 更新系: `application/internal/commandservices`
- 参照系: `application/internal/queryservices`

```java
public interface BookingQueryService {
    Page<BookingView> search(BookingSearchCriteria criteria, PageRequest page);
    Page<BookingView> findAwaitingRouting(PageRequest page);
    Optional<BookingView> findById(String bookingId);
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/queryservices/BookingQueryService.java`

つまり本プロジェクトは「CQRS 未着手」ではなく、**コマンド／クエリ分離済みの状態管理型アーキテクチャ**にあります。

## Axon Framework

### Axon コンポーネント

本リポジトリに Axon 依存（`org.axonframework`）や `@Aggregate` は存在しません。  
そのため、ここでは導入時に必要となる責務単位だけを定義します。

1. Aggregate（コマンド処理）
2. Command Handler / Command Bus
3. Event Store
4. Projection / Query Model
5. Saga（長期トランザクション）

### Axon Framework のドメインモデルコンポーネント

現行の `Cargo` 集約が担う業務規則は、Axon 導入後も集約境界そのものは維持できます。差分は「状態を直接保存するか」「イベント適用で再構成するか」です。

### Axon のインフラストラクチャコンポーネント：Axon Server

現行は PostgreSQL + MyBatis + Spring Event を使った構成であり、Axon Server は未導入です。導入時はイベント永続化・購読・再生の運用責務が増えるため、運用設計（監視、リテンション、再投影手順）を同時に定義する必要があります。

## CQRS/ES としての Cargo Tracker

### Axon を用いた境界づけられたコンテキスト

BC の切り方（`booking`, `routing`, `tracking`, `handling`, `billing`, `estimation`, `shipper`）は維持し、各 BC 内でコマンド処理と投影処理を分離するのが基本方針です。

### 境界づけられたコンテキスト：成果物作成

移行時に最初に分離すべき成果物は次の 3 種です。

1. コマンドモデル（Aggregate）
2. イベント定義
3. クエリモデル（Projection）

### 境界づけられたコンテキスト：パッケージ構造

現行の 4 層構造（interfaces / application / domain / infrastructure）は、CQRS/ES でも継続できます。違いは application 層の一部が「コマンドディスパッチ」「イベント購読」「投影更新」へ再編される点です。

### Axon を用いたドメインモデルの実装

現行のイベント定義はすでに `shared.domain.event` に集約されています。

```java
public record CargoStatusUpdatedEvent(
        UUID bookingId,
        String trackingNumber,
        String transportStatusLabel,
        Instant occurredAt,
        String locationUnlocode,
        String updatedBy) {
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/domain/event/CargoStatusUpdatedEvent.java`

このイベント語彙は、Axon 移行時のイベントスキーマ設計の起点として利用できます。

### 実装のまとめ

現行コードは「CQRS 準備済み・ES 未導入」です。  
Axon 導入の本質はフレームワーク置換ではなく、**状態更新の正典を DB 行からイベント列へ移すこと**です。

### Axon を用いたドメインモデルサービスの実装

`RouteSearchService` などの業務計算ロジックは、イベントソーシング導入後もドメインサービスとして維持できます。変更対象は主にハンドラと永続化境界です。

### 受信サービス

現行の Controller はユースケースを直接呼び出します。

```java
@Controller
@RequestMapping("/bookings")
public class BookingController { ... }
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/web/BookingController.java`

Axon 化では、ここが Command Gateway / Query Gateway 呼び出しへ置き換わる想定です。

### アプリケーションサービス

現行の `BookCargoCommandService` などは、コマンドハンドラへ責務を寄せる再編が必要です。一方で、ACL ポート境界や BC 依存方向は現行方針を維持できます。

```java
public interface ShipperExistenceChecker {
    boolean exists(ShipperId shipperId);
}
```

参照: `docs/article/source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/outboundservices/acl/ShipperExistenceChecker.java`

## まとめ

本章は、現行実装に存在する CQRS 要素と、未導入である ES/Axon 要素の境界を明確化しました。  
次章では、ここまでの 3 方式（モジュラーモノリス、EDA、CQRS/ES）を比較し、採用判断の基準を整理します。
