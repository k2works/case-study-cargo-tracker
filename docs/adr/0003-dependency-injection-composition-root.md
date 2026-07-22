# ADR 0003: interface 層の DIP を composition root への依存注入で回復する（IT2）

## ステータス

承認（IT2 時点）

## コンテキスト

IT1 の開発成果物レビュー（[it1_development_review_20260718.md](../review/it1_development_review_20260718.md)）で、**interface 層 → infra 実装への直接依存（DIP 逸脱）**が高優先度の技術的負債として指摘された。具体的には、`interface-web` の各ハンドラが `SqlxShipperRepository::new(state.pool.clone())` のように infra-persistence の sqlx 実装を直接生成しており、以下の問題があった。

- [バックエンドアーキテクチャ](../design/architecture_backend.md) のヘキサゴナル原則「アプリケーション層・インターフェース層はポート trait のみに依存し、DI は composition root（`main`）のみが行う」に反する
- ハンドラ単体でリポジトリ実装を差し替えられず、テスト時に実 DB（testcontainers）が必須になる
- トランザクション境界やリポジトリ実装の切り替え（キャッシュ層挿入等）を導入する余地が interface 層に漏れる

IT2 で Routing Context を追加した際も同じパターンを踏襲したため、負債が横展開された（[IT2 レビュー](../review/it2_development_review_20260722.md) 高 #5・ADR-0001 の依存規則にも抵触）。

## 決定

interface 層の依存を **composition root（`cargo-tracker-server`）でのみ生成し、`AppState` に出力ポート trait のトレイトオブジェクトとして注入**する。

- 各ドメインの出力ポート trait（`ShipperRepository` / `CargoRepository` / `ShipperExistenceChecker` / `VoyageRepository`）に対して、`Arc<dyn Trait>` のブランケット実装を追加し、`Arc` 越しに委譲できるようにする
- `AppState` は `pool: PgPool` に加えて `Arc<dyn 各ポート>` を保持する
- ハンドラは `AppState` からトレイトオブジェクトを `clone()` してアプリケーションサービスへ渡す（`VoyageQueryService::new(state.voyage_repo.clone())` 等）。`Sqlx*::new` は interface 層から消える
- composition root（`build_app`）が sqlx 実装（`SqlxVoyageRepository` 等）を生成して `AppState` に注入する
- テスト（web フローテスト）は `AppState` 構築ヘルパー経由で同じ sqlx 実装を注入する（テストは統合テストのため実装を差し替えないが、注入経路は本番と一致させる）

### 例外: 認証（User）

ログイン処理（`login_submit`）は `SqlxUserRepository::find_credentials`（argon2 検証）に依存し、これはドメインの出力ポートではなく infra 固有の認証機構である（[ADR-0002](0002-authentication-with-tower-sessions.md)）。認証は本 ADR の DIP 回復の対象外とし、`AppState.pool` から `SqlxUserRepository` を生成する現状を維持する。axum-login 採用時（ADR-0002 の後続）に再評価する。

## 影響

- interface 層からドメインリポジトリの infra 実装参照が消え、ヘキサゴナル境界がハンドラ単位で守られる
- アプリケーションサービスのジェネリクス（`VoyageCommandService<R>`）は `R = Arc<dyn VoyageRepository>` で単相化され、trait object 経由の動的ディスパッチになる（性能影響は業務系 Web では無視できる）
- `CurrentUser.roles` の `Vec<Role>` 型化（IT1 Try #1 後半）は本 ADR のスコープ外とし、セッションシリアライズ仕様への影響が大きいため別途対応する
- 将来リポジトリ実装を差し替える（キャッシュ・別 DB・モック）際に composition root の 1 箇所のみを変更すればよくなる
