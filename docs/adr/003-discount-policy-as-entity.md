---
title: "ADR-003: DiscountPolicy をエンティティとして設計し、ドメインサービスへの昇格を Phase 2 以降に保留する"
description: 請求コンテキストの DiscountPolicy は初期実装でエンティティとして設計し、割引ルールの複雑化に応じてドメインサービスへの昇格を Phase 2 以降に判断する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: adr, ddd, domain-model, billing
---

# ADR-003: DiscountPolicy をエンティティとして設計し、ドメインサービスへの昇格を Phase 2 以降に保留する

請求コンテキストの `DiscountPolicy` は Phase 1 ではエンティティとして設計し、割引ルールの複雑化（複数ポリシーの組み合わせ・外部割引テーブル参照等）が発生した場合に限り、Phase 2 以降でドメインサービスへの昇格を判断する。

日付: 2026-03-31

## ステータス

承認済み

## コンテキスト

請求コンテキスト（Billing Context）には、運賃への割引適用ロジックが存在する。現在の要件（US-17: 請求書発行）では以下の割引ルールが想定される。

- 貨物種別（GENERAL / PERISHABLE / DANGEROUS）による基本割引率
- 取引量（過去 6 ヵ月の予約件数）に基づくボリューム割引
- 契約顧客向け固定割引率

**設計の選択肢**:

| 設計 | 説明 | DDD パターン |
|---|---|---|
| **値オブジェクト** | `DiscountRate` のような不変の割引率のみ保持 | Value Object |
| **エンティティ** | `id` を持ち、割引ルールの CRUD 管理・DB 永続化が可能 | Entity |
| **ドメインサービス** | 複数集約・外部システム参照・複雑なルール組み合わせを処理 | Domain Service |

**現時点の要件分析**:

Phase 1 の要件では、割引ルールは「貨物種別 × 顧客カテゴリ × ボリューム」の組み合わせであり、以下の特性を持つ。

- 割引ポリシーは DB テーブルに保存・管理者が変更できる（→ 値オブジェクトより DB 参照可能なエンティティが適切）
- 単一の `Invoice` 集約内の `calculateDiscount(cargo, customer)` メソッドで完結する（→ 複数集約を横断しない）
- 外部システムへの参照は現時点で不要（→ ドメインサービスの必要性は低い）

**ドメインサービスへの昇格が必要になる条件**:

- 割引計算に複数の集約（Customer + ContractHistory + CargoBatch 等）の参照が必要になった場合
- 割引ルールエンジン（外部 API や BRMS）との統合が必要になった場合
- 複数の割引ポリシーを組み合わせる「割引チェーン」が実装される場合

## 決定

**Phase 1: `DiscountPolicy` をエンティティとして実装する。**

```java
// Phase 1 の実装（エンティティ）
public class DiscountPolicy {
    private final DiscountPolicyId id;
    private final String policyName;
    private final CargoType applicableCargoType;   // GENERAL / PERISHABLE / DANGEROUS
    private final CustomerCategory customerCategory; // STANDARD / CONTRACT / VOLUME
    private final DiscountRate discountRate;         // 0〜100% の値オブジェクト
    private final LocalDate validFrom;
    private final LocalDate validTo;

    public Money applyDiscount(Money baseAmount) {
        return baseAmount.multiply(BigDecimal.ONE.subtract(discountRate.value()));
    }
}
```

`Invoice` 集約内での使用：

```java
public class Invoice {
    public Money calculateDiscountedAmount(DiscountPolicy policy) {
        return policy.applyDiscount(this.subtotal);
    }
}
```

### Phase 2 以降の昇格判断基準

以下の条件が 1 つ以上発生した場合、`DiscountPolicyService`（ドメインサービス）への昇格を検討する。

| 条件 | 昇格の根拠 |
|---|---|
| 割引計算に `Customer` 集約の Contract 情報が必要 | 複数集約の参照 → ドメインサービス |
| ボリューム割引のために過去 N ヵ月の請求履歴参照が必要 | リポジトリ参照 → ドメインサービス |
| 複数割引ポリシーの優先順位付き適用（チェーン） | 複雑なルール組み合わせ → ドメインサービス |

### 変更箇所

| ファイル | 内容 |
|---|---|
| `billing/domain/DiscountPolicy.java` | エンティティとして実装 |
| `billing/domain/Invoice.java` | `calculateDiscountedAmount(DiscountPolicy)` メソッド |
| `billing/infrastructure/DiscountPolicyRepository.java` | MyBatis マッパー（CRUD） |
| `discount_policies` テーブル | データモデルの `discount_policies` テーブルを参照 |

### 代替案

| 代替案 | 却下理由 |
|---|---|
| **値オブジェクト** | 割引ポリシーは管理者が変更可能な永続化データであるため、`id` を持つエンティティが適切 |
| **Phase 1 からドメインサービス** | 現時点の要件では単一集約内で完結するため、ドメインサービスは過剰設計。YAGNI 原則に基づき保留 |
| **アプリケーションサービスに割引ロジックを実装** | 割引ルールはビジネスロジックであり、ドメイン層に属すべき。アプリケーション層への漏れは DDD 違反 |

## 影響

### ポジティブ

- Phase 1 の要件に対してシンプルで適切な設計を維持できる
- `DiscountPolicy` のエンティティ管理により、管理者 UI から割引ルールを変更できる
- 将来のドメインサービス昇格に備え、`Invoice.calculateDiscountedAmount()` のシグネチャを変えない設計にすることで、移行コストを最小化できる

### ネガティブ

- 複雑な割引計算ニーズが発生した場合、エンティティからドメインサービスへのリファクタリングが必要になる（ただし、テストが整備されていれば安全にリファクタリング可能）
- 「エンティティが計算ロジックを持つ」ことへの設計上の違和感が生じる場合がある（Fat Entity になりやすい）

## コンプライアンス

- `DiscountPolicy` クラスが `billing.domain` パッケージに配置されていること
- `DiscountPolicy` は Spring アノテーション（`@Service` 等）を持たないこと（ArchUnit で検証）
- `Invoice` の割引計算テストで `DiscountPolicy` のモックを使用できること

## 備考

- 著者: Project Team
- 関連ドキュメント: `docs/design/architecture_backend.md`（請求コンテキスト）、`docs/design/domain-model.md`
- 関連 ADR: ADR-001
