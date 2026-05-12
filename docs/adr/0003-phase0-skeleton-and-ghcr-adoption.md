# ADR-0003 Phase 0 雛形生成とコンテナレジストリとして GHCR を採用する

開発フェーズ（IT1）開始前に整備する Phase 0 の作業範囲と、コンテナレジストリとして **GitHub Container Registry（GHCR）+ `GITHUB_TOKEN`** を採用する決定を記録する。AWS ECR + OIDC は当面採用しない。

日付: 2026-05-12

## ステータス

2026-05-12 受け入れ済み

## コンテキスト

[運用成果物レビュー（2026-05-12）](../review/運用成果物_review_20260512.md) で次の根本課題が指摘された。

1. **手順書と実装の乖離**: `apps/.gitkeep` のみ、`gulpfile.js` に dev タスク未登録、ルート `docker-compose.yml` は MkDocs 用 → 新規メンバーは即詰まる
2. **セキュリティの基本欠陥**: `POSTGRES_PASSWORD: cargo` 平文、Axon Server `DEVMODE_ENABLED=true` の警告欠如、`Dockerfile` の `safe.directory '*'`、`ssh.js` の `accept-new`
3. **チーム規模との不均衡**: バックエンド 2 名で 7 サービス + Axon + CQRS/ES/Saga 同時習得は持続可能性脅威
4. **コンテナレジストリの方針齟齬**: 既存 `.github/workflows/docker-publish.yml` は GHCR + `GITHUB_TOKEN`、設計（`tech_stack.md`、`architecture_infrastructure.md`、`operation.md`）は Amazon ECR + OIDC

これらの解消を IT1 キックオフ前の **Phase 0** として整備する。本 ADR は Phase 0 全体方針と、矛盾事項 4 の **「コンテナレジストリ選定」** の確定を扱う。

## 決定

### 1. Phase 0 整備計画（IT1 キックオフ前必須）

| # | 作業 | 関連レビュー指摘 | 対応 |
|---|------|-----------------|------|
| 1 | **本 ADR-0003 を発行** | OH1, OH10 | 着手 |
| 2 | `apps/backend/<service>/` スケルトン作成（Gradle マルチプロジェクト、bookingms 最小 BootApplication） | OH1 | 着手 |
| 3 | ルート `docker-compose.yml` を `docker-compose.docs.yml` にリネーム、`apps/docker-compose.yml` 雛形を新規配置 | OH10 | 着手 |
| 4 | `gulpfile.js` に `dev:bookingms` / `tdd:bookingms` / `up:all` / `down:all` / `smoke` タスク追加 | OH11 | 着手 |
| 5 | `.env.example` 作成 + `.env` の `.gitignore` 登録 + `vault:encrypt` 運用ルール明示 | OH3, OH8 | 着手 |
| 6 | **コンテナレジストリの確定**: GHCR + `GITHUB_TOKEN` を採用（本 ADR の主決定、後述） | OH7（部分採用） | 着手 |
| 7 | `Dockerfile` の `safe.directory '*'` を `/srv` 限定に修正 | OH5 | 着手 |
| 8 | `ssh.js` の `StrictHostKeyChecking=accept-new` を `=yes` + `known_hosts` 事前配布に変更 | OH6 | 着手 |
| 9 | `sonar_local.js` の DB パスワードデフォルト値を `.env` 必須化 | OH15 | 着手 |
| 10 | 手順書に「実装ステータス」マーカー、マシンスペック要件、DEVMODE 警告、local-docker 二段階構成を追加 | OH2, OH4, OH12, OH14 | 着手 |
| 11 | 設計ドキュメント（`architecture_infrastructure.md` / `tech_stack.md` / `operation.md`）の Amazon ECR / OIDC 言及を **GHCR + `GITHUB_TOKEN`** に整合 | OH7 の派生 | 着手 |

未対応事項（Phase 1 以降）:

- OH9 層別テスト実行コマンド（test_strategy.md 充実化）
- OH13 オンボーディングパス（別ドキュメント）
- 中・低優先度の各項目

### 2. コンテナレジストリの採用判断

**GitHub Container Registry（GHCR）+ `GITHUB_TOKEN` を採用する。** Amazon ECR + OIDC は当面採用しない。

#### 採用バージョン

| 項目 | 内容 |
| :--- | :--- |
| レジストリ | `ghcr.io/k2works/case-study-cargo-tracker/<service>` |
| 認証（CI） | `GITHUB_TOKEN`（`permissions: packages: write`）|
| イメージタグ運用 | `latest`（main マージ時）+ `v<semver>`（リリースタグ push 時）+ `sha-<short>` |
| 公開範囲 | プライベートパッケージ（必要に応じて public 化を別途判断） |
| マルチサービス | サービス別パッケージ名で 7 サービス + Frontend をそれぞれ公開 |

#### 代替案

| 候補 | 評価 |
| :--- | :--- |
| **GHCR + `GITHUB_TOKEN`（採用）** | 長所: 既存ワークフローと整合・追加認証基盤不要・無料枠で十分・GitHub Actions との連携が最短。短所: AWS ECS 側で GHCR 認証情報をパラメータストアに格納する手間が発生 |
| Amazon ECR + OIDC | 長所: AWS との認証が一貫・キーレス・運用ノウハウ豊富。短所: 既存ワークフロー全面書換・追加 ADR・別 IAM ロール構築・別レジストリ運用負荷 |
| Docker Hub | 長所: シンプル。短所: 無料プランで pull 制限がきつい・プライベートリポジトリは有料 |

#### 採用理由

- **既存資産の活用**: 現状 `.github/workflows/docker-publish.yml` は GHCR + `GITHUB_TOKEN` で動作している。これを正として設計を整合させる方が変更コストが小さい
- **チーム規模**: バックエンド 2 名のチーム規模で AWS ECR + OIDC への移行を Phase 0 で同時実施するのは過大負荷（OH13 と矛盾）
- **段階的進化**: GHCR で安定運用した後、将来必要であれば ECR 移行を別 ADR として再評価可能
- **コスト**: GHCR は GitHub のプライベートリポジトリと同じ枠で利用可能、追加コストなし

#### AWS ECS から GHCR への pull 構成

ECS タスクが GHCR の **プライベートパッケージ** を pull できるよう、次のいずれかを採用する。

| 方式 | 内容 |
| :--- | :--- |
| **A. Secrets Manager 経由（採用）** | GHCR の Personal Access Token（PAT、`read:packages`）を AWS Secrets Manager に格納し、ECS タスク定義の `repositoryCredentials` で参照 |
| B. Docker Hub 経由のミラー | 不採用（複雑性増） |
| C. ECR ミラー（pull-through cache） | 不採用（GHCR には公式対応がなく、Lambda での自前同期が必要） |

PAT のローテーション運用は四半期 1 回。Secrets Manager の自動ローテーションは GitHub API による PAT 再発行スクリプトと組み合わせる。

### 3. 影響範囲（修正対象ファイル）

| ファイル | 変更内容 |
| :--- | :--- |
| `docs/adr/0003-phase0-skeleton-and-ghcr-adoption.md` | 本 ADR 新規 |
| `docs/adr/index.md` | ADR-0003 追加 |
| `docs/design/architecture_infrastructure.md` | Amazon ECR → GHCR（`ghcr.io/k2works/case-study-cargo-tracker/<service>`）、OIDC → GitHub Actions `GITHUB_TOKEN` |
| `docs/design/tech_stack.md` | コンテナレジストリの記述を ECR → GHCR、OIDC → GITHUB_TOKEN |
| `docs/design/operation.md` | デプロイ手順の ECR push を GHCR push に |
| `docs/operation/アプリケーション開発環境セットアップ手順書.md` | 4 章・14 章の ECR / OIDC 記述を GHCR + GITHUB_TOKEN に |
| `Dockerfile` | `safe.directory '*'` → `safe.directory /srv` |
| `ops/scripts/ssh.js` | `StrictHostKeyChecking=accept-new` → `=yes` + `known_hosts` 事前配布手順 |
| `ops/scripts/sonar_local.js` | デフォルトパスワードを除去し `.env` 必須化 |
| `docker-compose.yml`（ルート） | `docker-compose.docs.yml` にリネーム |
| `apps/docker-compose.yml`（新規） | Axon Server + PostgreSQL + 7 サービス + Frontend の雛形、`.env` 経由のシークレット読込 |
| `apps/backend/settings.gradle`（新規） | Gradle マルチプロジェクト設定（7 サービス + shared） |
| `apps/backend/build.gradle`（新規） | 共通設定（Java 25、Spring Boot 4、Axon 5、MyBatis 等） |
| `apps/backend/gradle/libs.versions.toml`（新規） | Version Catalog |
| `apps/backend/bookingms/`（新規） | 最小 BootApplication スケルトン |
| `apps/backend/{authms,routingms,trackingms,handlingms,billingms,gatewayms,shared}/`（新規） | 各サービスは `.gitkeep` のみ |
| `gulpfile.js` | `dev:bookingms` / `tdd:bookingms` / `up:all` / `down:all` / `smoke` タスク追加 |
| `.env.example`（新規） | 環境変数のテンプレート |
| `.gitignore` | `.env` を追加（既存に追記） |

## 影響

### ポジティブ

- 手順書と実装の乖離が解消し、新規メンバーがオンボーディング可能となる
- 既存 CI ワークフローを活かせるため Phase 0 のコスト最小化
- セキュリティ基本欠陥（平文パスワード・`safe.directory '*'`・`accept-new`）を排除
- マイクロサービス本格実装は段階的（bookingms から）に進められる

### ネガティブ

- AWS ECS から GHCR への pull には Secrets Manager + PAT 運用が必要（追加運用負荷）
- 将来 ECR + OIDC に移行する判断が出た場合、別 ADR + 移行作業が発生
- 設計ドキュメント（インフラ・技術スタック・運用）の修正が広範囲に及ぶ

### 中立

- ECR + OIDC を採用する案は否定しておらず、将来の選択肢として残る
- GHCR の rate limit / 可用性は GitHub 全体の SLA に依存（GitHub の SLA は契約による）

## コプライアンス

次の項目を Phase 0 完了時に検証する。

- `apps/backend/settings.gradle` に 7 サービス + `shared` が登録されていること
- `apps/docker-compose.yml` が存在し、ルートの `docker-compose.yml` は `docker-compose.docs.yml` にリネーム済みであること
- `gulpfile.js` に `dev:bookingms`、`tdd:bookingms`、`up:all`、`down:all`、`smoke` タスクが登録されていること
- `.env.example` が存在し、`.env` が `.gitignore` に登録されていること
- `Dockerfile` に `safe.directory '*'` が含まれず `safe.directory /srv` のみ存在すること
- `ops/scripts/ssh.js` に `accept-new` 文字列が含まれないこと
- `ops/scripts/sonar_local.js` にハードコードされたパスワードデフォルト値が含まれないこと
- `.github/workflows/docker-publish.yml` が GHCR + `GITHUB_TOKEN` で動作すること
- 設計ドキュメント（infra・tech_stack・operation）に "Amazon ECR" / "AWS ECR" / "OIDC（コンテナレジストリ用）" の記述が残っていないこと
- 手順書冒頭に「実装ステータス: Phase 0 整備済み / IT1 で実装着手」のマーカーが存在すること

## 備考

- 著者: アーキテクト
- 関連 ADR:
  - [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](0001-axon-framework-adoption.md)
  - [ADR-0002 データアクセスとして MyBatis を採用する](0002-mybatis-adoption.md)
- 関連レビュー:
  - [運用成果物レビュー（2026-05-12）](../review/運用成果物_review_20260512.md)
  - [分析成果物レビュー（2026-05-12）](../review/分析成果物_review_20260512.md)
- 将来の見直しトリガー（GHCR → ECR 移行検討）:
  - GHCR の pull 制限が業務に支障を来たす場合
  - AWS との認証統合（IAM Identity Center 拡張）が必須となる業務要件発生時
  - マルチクラウド戦略への変更
