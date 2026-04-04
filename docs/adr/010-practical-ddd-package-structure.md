# ADR-010: Practical DDD in Enterprise Java のパッケージ構成を採用する

各境界付けられたコンテキストのパッケージ構成を Practical DDD in Enterprise Java (Chapter 3) に準拠させる。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

DDD + ヘキサゴナルアーキテクチャの実装にあたり、パッケージ構成の標準を定める必要がある。

- 複数の DDD 実装パターンが存在し、チーム内で一貫性を保つための基準が必要
- 参考書籍「Practical DDD in Enterprise Java」(Chapter 3) の cargo-tracker 実装がパッケージ構成の実績ある参照モデルとして利用可能
- ドメイン層の内部構造（集約・値オブジェクト・コマンド・エンティティ）を明確に分離したい
- インターフェース層（REST Controller）とインフラ層（リポジトリ）を分離し、DTO 変換を明示的に管理したい

## 決定

**各コンテキストのパッケージ構成を以下のとおり定める。**

```
{context}/
├── domain/model/
│   ├── aggregates/          集約ルート
│   ├── commands/            コマンドオブジェクト
│   ├── entities/            エンティティ
│   └── valueobjects/        値オブジェクト
├── application/internal/
│   ├── commandservices/     コマンドサービス（書き込みユースケース）
│   ├── queryservices/       クエリサービス（読み取りユースケース）
│   └── outboundservices/
│       └── acl/             ACL（腐敗防止層）
├── infrastructure/
│   ├── repositories/        リポジトリ実装（MyBatis）
│   └── services/            外部サービスクライアント
└── interfaces/
    ├── rest/                REST Controller
    │   ├── dto/             リクエスト / レスポンス DTO
    │   └── transform/       DTO ⇔ コマンド変換（Assembler）
    ├── web/                 画面 Controller（Thymeleaf）
    └── events/              イベントハンドラ
```

共有カーネルは `shareddomain/` パッケージに配置する。

```
shareddomain/
├── events/                  ドメインイベント（CargoBookedEvent 等）
└── model/                   共有値オブジェクト（ShipperId 等）
```

### 変更箇所

- `docs/design/architecture_backend.md` のレイヤー責務一覧・パッケージ構成例・全体アーキテクチャ図を更新
- `docs/operation/dev_app_instrunction.md` のディレクトリ構造を更新
- `docs/development/iteration_plan-1.md` のディレクトリ構成を更新

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Spring Boot 標準（`controller/`・`service/`・`repository/`） | DDD の境界付けられたコンテキストを表現できない |
| architecture_backend.md 独自構成（`domain/model/`・`application/command/`・`infrastructure/persistence/`） | 参照モデルがなく、チーム間で解釈が分かれる可能性 |
| Clean Architecture 形式（`usecase/`・`gateway/`・`presenter/`） | Spring Boot エコシステムとの命名慣習が異なる |

## 影響

### ポジティブ

- 書籍の参照モデルがあるため、チームメンバーが構造を理解しやすい
- DTO 変換が `transform/` に局所化され、Controller が薄く保たれる
- `commands/` パッケージにより CQRS のコマンド側が明確になる
- `outboundservices/acl/` により ACL パターンが構造的に表現される

### ネガティブ

- パッケージ階層が深い（最大 5 階層）
- 小さなコンテキストでもフルのパッケージ構造を作る必要がある

## コンプライアンス

- ArchUnit テストで以下のルールを検証する（ADR-011 参照）
  - `domain.model` パッケージが `infrastructure` パッケージに依存しないこと
  - `domain.model` パッケージに Spring アノテーション（`@Component`・`@Service`・`@Repository`）が含まれないこと
  - `application` パッケージが `interfaces` パッケージに依存しないこと
- 全コンテキストが上記パッケージ構成に従っていること

## 備考

- 関連コミット: `32c9098`, `5b646fe`, `704e7f9`
- 参考文献: Practical DDD in Enterprise Java, Chapter 3 (Vijay Nair)
