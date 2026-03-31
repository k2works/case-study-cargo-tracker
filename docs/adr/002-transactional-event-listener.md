---
title: "ADR-002: ドメインイベントに @TransactionalEventListener(AFTER_COMMIT) を使用する"
description: Spring ApplicationEventPublisher によるドメインイベント発行に @TransactionalEventListener(phase = AFTER_COMMIT) を必須とし、コミット前リスナー実行によるデータ不整合を防止する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: adr, spring, events, transaction, ddd
---

# ADR-002: ドメインイベントに @TransactionalEventListener(AFTER_COMMIT) を使用する

Spring `ApplicationEventPublisher` によるドメインイベント発行では、リスナーに `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` を必須とし、トランザクションコミット前のリスナー実行によるデータ不整合を防止する。

日付: 2026-03-31

## ステータス

承認済み

## コンテキスト

本システムは DDD + ヘキサゴナルアーキテクチャを採用し、コンテキスト間通信に Spring の `ApplicationEventPublisher` を使用する。ドメインイベント（`CargoBookedEvent`、`CargoRoutedEvent`、`HandlingEventRegisteredEvent` 等）は集約内で発生し、アプリケーション層で発行される。

**問題の背景**:

Spring の `@EventListener`（デフォルト）はイベント発行時点（トランザクション実行中）に同期実行される。以下の問題が発生しうる。

```
1. アプリケーション層でトランザクション開始
2. 集約（Cargo）を変更・保存
3. ApplicationEventPublisher.publishEvent(CargoBookedEvent) を呼び出し
4. @EventListener がトランザクション内で同期実行
   - Notification コンテキストが DB に通知レコードを書き込む
5. ★ ここでトランザクションがロールバック
6. 結果: 貨物の DB 変更は消えたが、通知レコードだけ残る（不整合）
```

この問題は特に以下のシナリオで顕在化する。

- 通知コンテキストによるメール送信トリガー
- 追跡コンテキストへの TrackingId 発行通知
- 請求コンテキストへの配送完了通知

**検討した代替アノテーション**:

| アノテーション | 実行タイミング | 問題 |
|---|---|---|
| `@EventListener` | イベント発行時（トランザクション中） | ロールバック時にサイドエフェクトが残る |
| `@TransactionalEventListener(BEFORE_COMMIT)` | コミット直前 | ロールバック時のリスクは残る |
| `@TransactionalEventListener(AFTER_COMMIT)` | コミット確定後 | サイドエフェクトはコミット後にのみ発生 |
| `@TransactionalEventListener(AFTER_ROLLBACK)` | ロールバック後 | 補償処理用途に限定 |

## 決定

**すべてのドメインイベントリスナーに `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` を使用する。**

実装パターン：

```java
// ✅ 正しい実装
@Component
public class CargoBookedEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CargoBookedEvent event) {
        // コミット確定後に実行されるため、貨物データが確実に永続化済み
        notificationService.sendBookingConfirmation(event.getBookingId());
    }
}

// ❌ 使用禁止（ロールバック時にサイドエフェクトが残るリスク）
@Component
public class CargoBookedEventHandler {

    @EventListener  // ← 使用禁止
    public void handle(CargoBookedEvent event) {
        notificationService.sendBookingConfirmation(event.getBookingId());
    }
}
```

### 変更箇所

| ファイル・クラス | 変更内容 |
|---|---|
| `*EventHandler.java` | すべてのリスナーを `@TransactionalEventListener(AFTER_COMMIT)` に統一 |
| ArchUnit ルール | `@EventListener` の使用を禁止するアーキテクチャテストを追加 |
| コーディング規約 | `@EventListener` 使用禁止を明文化 |

ArchUnit によるコンプライアンスチェック：

```java
@Test
void domainEventListeners_mustUseTransactionalEventListener() {
    noClasses()
        .that().resideInAPackage("..application..")
        .should().beAnnotatedWith(EventListener.class)
        .because("ドメインイベントリスナーは @TransactionalEventListener(AFTER_COMMIT) を使用すること")
        .check(importedClasses);
}
```

### 代替案

| 代替案 | 却下理由 |
|---|---|
| **アウトボックスパターン（Transactional Outbox）** | ドメインイベントを別テーブルに永続化し、バッチで処理する。信頼性は高いがインフラ複雑度が大幅に増加する。本ケーススタディのスコープでは過剰設計 |
| **Spring ApplicationEvent のトランザクション外発行** | ドメインロジックとトランザクション境界を分離する必要があり、ヘキサゴナルアーキテクチャの構造と相性が悪い |
| **メッセージキュー（SQS / RabbitMQ）** | ケーススタディとしての複雑度が過剰。将来的な拡張として検討 |

## 影響

### ポジティブ

- トランザクションロールバック時にサイドエフェクトが残らないため、データ整合性が保証される
- ドメインイベントの発行タイミングが直感的に理解しやすくなる
- ArchUnit によるアーキテクチャテストで誤った `@EventListener` 使用を自動検出できる

### ネガティブ

- `@TransactionalEventListener(AFTER_COMMIT)` のリスナーはトランザクション外で実行される。リスナー内で新たなトランザクションが必要な場合は `@Transactional(propagation = REQUIRES_NEW)` を明示的に付与する必要がある
- リスナー実行失敗時のリトライ機構がない（デフォルト）。重要なサイドエフェクト（メール送信等）は try-catch でエラーハンドリングを実装すること
- イベント発行とリスナー実行の間に僅かなタイムラグが生じる（テスト時に注意: `@TestTransaction` との組み合わせでリスナーが呼ばれない場合がある）

## コンプライアンス

- ArchUnit テスト `domainEventListeners_mustUseTransactionalEventListener` が CI で全通過すること
- コードレビューチェックリストに「`@EventListener` 使用禁止」を追加すること
- 統合テストで「コマンド実行 → ロールバック → イベントリスナーが呼ばれていないこと」を検証するテストケースを含めること

## 備考

- 著者: Project Team
- 関連ドキュメント: `docs/design/architecture_backend.md`（ドメインイベント発行パターン）
- 関連 ADR: ADR-001（Spring Boot 4.0 採用）
