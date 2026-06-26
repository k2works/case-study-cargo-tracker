# 0002 アーキテクチャ規約検査ツール `arch-check` の自作実装

国際貨物輸送管理システム (Haskell 版) のアーキテクチャ規約を CI で自動検証するツールの実装方針

日付: 2026-06-26

## ステータス

2026-06-26 承認されました

## コンテキスト

ADR 0001 で Haskell + Servant スタックを採用した結果、Scala 版で使用していた ArchUnit (JVM バイトコード検証) 相当のアーキテクチャ規約検査ツールを Haskell エコシステム内で確保する必要が生じた。

[テスト戦略](../design/test_strategy.md) §3.3 で定めたアーキテクチャ規約 4 ルールを CI で機械的に検証しないと、開発期間中に依存方向の崩れが進行し、ヘキサゴナルアーキテクチャの利点 (テスト容易性・変更容易性) が失われる。Scala 版 IT8 で「ADR 起票時に ArchUnit ルール 3 と整合せず fullTest で初検出」(commit 6fe0b22c) の教訓を Haskell 版でも継承する必要がある。

Haskell のエコシステムでアーキテクチャ規約検査として利用可能な候補:

| 候補 | 評価 |
| :--- | :--- |
| **A. 自作 arch-check (haskell-src-exts ベース)** | 必要なルールに焦点を絞れる。HSE は Haskell 拡張を網羅 |
| B. 自作 arch-check (ghc-lib-parser ベース) | GHC 公式パーサ。HSE より厳密だが API が重い |
| C. 自作 arch-check (grep / モジュール命名規約のみ) | 軽量だが偽陽性・偽陰性のリスク高い |
| D. `stan` (静的解析ツール) | 既存ルールセットが豊富。カスタムルールの追加性に制約あり |
| E. HLint カスタムルール (`hlint.yaml`) | モジュール import 制約 (`- modules:`) の機能あり。設計判断の表現力に限界 |
| F. 検査なし | テスト戦略の前提が崩壊。不採用 |

## 決定

**案 A と案 E のハイブリッド構成を採用する。**

- **案 E (HLint カスタムルール) を 1 次防衛線として導入**: `hlint.yaml` の `- modules:` ルールで「`Cargotracker.*.Domain.*` 内では `Servant.*` / `Database.PostgreSQL.Simple.*` / `Data.Aeson.*` を import 禁止」等の単純な依存方向ルールを宣言的に表現する。実装コストはほぼゼロ。
- **案 A (自作 arch-check) を 2 次防衛線として実装**: HLint で表現できない複雑なルール (例: ドメインエラー時のトランザクション境界規約 T-01〜T-03、CQRS のコマンド/クエリ責務分離) を専用バイナリで検査する。

採用理由:

1. **HLint の `- modules:` ルール**は本プロジェクトのアーキテクチャ規約 4 件のうち 3 件 (依存方向ルール 1-3) を網羅できる
2. **自作 arch-check** は context 間の直接参照禁止 (ルール 4) と、トランザクション境界規約 (T-01〜T-03) を表現できる
3. ハイブリッドにより、規約の表現力と保守コストのバランスを取る

### arch-check の実装方針

#### 入力

- ソースルート: `src/Cargotracker/`
- 設定ファイル: `arch-check.yaml` (ルール定義)

#### 出力

- 違反箇所のリスト (`<file>:<line> 違反内容`)
- 終了コード: 違反 0 件で 0、1 件以上で 1

#### 内部実装

- 言語: Haskell (本プロジェクトと同一)
- パーサ: `haskell-src-exts` (Cabal の安定版を使用)
- AST 解析: モジュール宣言と import リストを抽出し、ルール定義と突き合わせる
- 配置: 本プロジェクトの test スイート内 (`test/arch-check/`) または独立サブパッケージ
- 実行: `stack test --test-arguments="--match ArchCheck"` または `stack exec arch-check`

#### サポートするルール

```yaml
# arch-check.yaml の例
rules:
  # ルール 1: ドメイン層がインフラ層に依存しない
  - name: domain-no-infrastructure
    forbid_import:
      - in_module: "Cargotracker.*.Domain.*"
      - imports: "Cargotracker.*.Infrastructure.*"

  # ルール 2: ドメイン層がフレームワークに依存しない
  - name: domain-no-framework
    forbid_import:
      - in_module: "Cargotracker.*.Domain.*"
      - imports:
          - "Servant.*"
          - "Database.PostgreSQL.Simple.*"
          - "Data.Aeson.*"
          - "Network.HTTP.*"

  # ルール 3: アプリケーション層はインフラ層をポート経由のみ
  - name: application-via-port
    forbid_import:
      - in_module: "Cargotracker.*.Application.*"
      - imports: "Cargotracker.*.Infrastructure.*"

  # ルール 4: Bounded Context 間の直接参照禁止
  - name: cross-context-via-acl-only
    forbid_import:
      - in_module: "Cargotracker.<Ctx>.*"
      - imports: "Cargotracker.<OtherCtx>.*"
      - except:
          - "Cargotracker.Shared.*"
          - "Cargotracker.*.Application.Acl.*"
          - "Cargotracker.*.Domain.Model.Events.*"

  # トランザクション境界規約 T-01: 検証関数を withTransaction 内で呼び出さない
  - name: tx-validation-outside
    forbid_pattern:
      - in_module: "Cargotracker.*.Application.CommandService"
      - pattern: "withTransaction.*(Aggregate.create|smartConstructor)"
      - message: "T-01 違反: ドメイン検証は withTransaction の外で行うこと"

  # トランザクション境界規約 T-03: イベント発行を withTransaction 内で行わない
  - name: tx-event-publish-outside
    forbid_pattern:
      - in_module: "Cargotracker.*.Application.CommandService"
      - pattern: "withTransaction.*publish"
      - message: "T-03 違反: イベント発行はコミット後 (withTransaction の外) で行うこと"
```

#### 段階的導入

| 段階 | 内容 | 完了時期 |
| :--- | :--- | :--- |
| Phase 1 | HLint `hlint.yaml` で `- modules:` 規約を導入 (ルール 1-3) | IT1 (Sprint 0 完了直後) |
| Phase 2 | `arch-check` バイナリ実装 + ルール 4 + テスト | IT1 末 |
| Phase 3 | トランザクション境界規約 T-01〜T-03 の AST パターンマッチ追加 | IT2 |
| Phase 4 | CQRS 責務分離・楽観ロック未適用検出など追加ルール | IT3 以降 |

## 影響

- CI 時間が `arch-check` の実行分 (想定 10-30 秒) 増加する
- `haskell-src-exts` への依存が追加される (LGPL-2.1。本ツール限定で利用)
- 規約違反が CI でブロックされるため、開発時のフィードバックループに組み込まれる
- 新規 ADR 起票時 (ADR 0001 「ADR ↔ ArchUnit 整合チェックリスト」の Haskell 版) は arch-check の影響を確認する規律が必要
- ADR 0001 で言及した「自作 import 規約チェッカ」の具体化が完了する

## コンプライアンス

- 本 ADR で定めるルール定義は `arch-check.yaml` (リポジトリルート) に格納する
- `arch-check` の実行は `.github/workflows/ci.yml` の lint ジョブで実施する
- 規約変更は ADR 起票で記録 (ADR 0001 の「ADR ↔ ArchUnit 整合チェックリスト」を Haskell 版に翻訳)
- 例外的にルール違反を許容する場合 (legacy code 移行中等) は `arch-check.yaml` 内で `allow:` リストに該当モジュールを明示する

## 備考

- 著者: 開発チーム
- 関連:
  - [ADR 0001 Haskell + Servant スタック採用](0001-haskell-servant-stack.md)
  - [テスト戦略](../design/test_strategy.md) §3.3 アーキテクチャテスト
  - [バックエンドアーキテクチャ](../design/architecture_backend.md) トランザクション境界規約 T-01〜T-03
  - Scala 版教訓: ArchUnit ルール 3 と ADR 0017 の整合チェック (commit 6fe0b22c)
