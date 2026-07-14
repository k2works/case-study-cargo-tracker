# ADR-0006: Ambient Transaction によるトランザクション伝播

リポジトリの永続化ポートから `System.Data.IDbTransaction` 引数を除去し、DI スコープ内で共有する `AmbientTransaction`（現在トランザクション保持子）を経由してトランザクションを伝播する。ADR-0002 の `IUnitOfWork.Transaction` 公開方式を置き換える。

日付: 2026-07-09

## ステータス

2026-07-09 承認されました（ADR-0002 のトランザクション公開方式を Supersede）

## コンテキスト

IT2 の開発成果物レビュー（`docs/review/開発成果物_IT2_review_20260709.md`）で、以下が高優先指摘（H1・H2）として挙げられた。

- **H1**: ドメイン/アプリケーションの永続化ポート（`IShipperRepository`・`IEstimateRepository`・`ICargoRepository`）のメソッドシグネチャが `IDbTransaction`（ADO.NET＝インフラ技術）を引数に取り、技術的関心事がドメイン層へ漏れていた。ADR-0002 は `IUnitOfWork.Transaction` プロパティで `IDbTransaction` を公開し、コマンドサービスがそれをリポジトリへ渡す設計だった。
- これは DIP に反し、永続化機構を差し替えるとポート署名が動く（変更を楽に安全にできない）。

IT2 の M1 対応でこれを是正し、`AmbientTransaction` 方式を導入した。本 ADR はその設計判断と前提条件・トレードオフを記録する。

## 決定

**DI スコープ（＝ HTTP リクエスト）内で共有する `AmbientTransaction` を経由してトランザクションを伝播する。**

1. **ポートから `IDbTransaction` を除去**: リポジトリポートは `SaveAsync(entity, ct)` のように業務的シグネチャのみを持ち、`System.Data` に依存しない。`IUnitOfWork` からも `Transaction` プロパティを削除する。
2. **`AmbientTransaction`（scoped 登録）**: `Shared/Infrastructure/Persistence/AmbientTransaction.cs`。現在の `IDbTransaction?` を保持し、`Require()` で取得（未設定なら例外）。リポジトリ実装（Infrastructure）はこれを注入して現在トランザクションに参加する。
3. **`UnitOfWork` が設定・解除**: `UnitOfWorkFactory.Begin()` が生成する `UnitOfWork` は、トランザクション開始時に `AmbientTransaction.Begin(tx)` で設定し、`DisposeAsync` で `Clear()` する。
4. **前提条件（重要）**: 本方式は「**DI スコープ内では単一・非ネスト・非並行のトランザクション**」を前提とする。1 リクエスト = 1 ユースケース = 1 トランザクションを原則とする（ADR-0002 の方針を踏襲）。
5. **前提違反の明示的検出（H2）**: 既に `Current` がある状態での `Begin()`（スコープ内のネスト・並行実行）は前提違反として `InvalidOperationException` を送出し、外側トランザクションの静かな破壊を防ぐ。単体テストで固定する。

### 代替案

- **ADR-0002 の `IUnitOfWork.Transaction` 公開を継続**: ポートが `IDbTransaction` を漏らし続ける（却下。H1 の是正が目的）。
- **`TransactionScope`（環境トランザクション）**: .NET 標準の環境トランザクションだが、SQLite/PostgreSQL の二方言・Dapper 手書き SQL の現構成では過剰で、分散トランザクションの複雑さを招く（却下）。
- **リポジトリに `IUnitOfWork` を渡す**: ドメインポートがアプリケーション層の `IUnitOfWork` に依存する逆方向依存になる（却下）。

## 影響

### 良い影響

- ドメイン/アプリケーションポートが `System.Data` 非依存になり、DIP に忠実になる（ArchUnit ルール 1 の趣旨を強化）。
- 永続化機構の差し替えでポート署名が動かない。

### 悪い影響・制約（負債利子）

- **暗黙依存化**: 「このリポジトリメソッドは UoW スコープ内で呼ばれねばならない」という制約が型に現れず、実行時 `Require()` 例外で担保する。
- **scoped・非ネスト・非並行が前提**: 単一リクエスト内で `Task.WhenAll` 等により複数 UoW を並行実行すると `Current` が競合するため、`Begin()` のネストガードで検出する。将来、イベントハンドラが別 UoW を開く設計（ADR-0002）と組み合わせる場合は、この前提を再確認する。
- 読み取り系（`FindByBookingIdAsync` 等）は `Current` があればトランザクション参加、なければ独立接続という二経路を持つ（read-your-writes のため。実装で明示）。
- **【追記 2026-07-14・ADR-0009】** イベントハンドラが別 UoW を開く連鎖（IT5 の荷役同期・IT6 の例外通知）では、各ハンドラの Ambient Transaction が独立するため部分適用が起こりうる。整合性は単一トランザクションではなく結果整合性で担保し、冪等実装・失敗ログ・手動修復の方針は [ADR-0009](0009-post-commitイベント連鎖の結果整合性方針.md) に従う。46 行で述べた「別 UoW 設計と組み合わせる場合の前提再確認」の結論が ADR-0009 である。

## 関連

- [ADR-0001 集約永続化戦略](0001-集約永続化戦略.md)
- [ADR-0002 UnitOfWork と post-commit イベントディスパッチ](0002-UnitOfWorkとpost-commitイベントディスパッチ.md)（トランザクション公開方式を本 ADR が Supersede）
- [ADR-0009 post-commit イベント連鎖の結果整合性方針](0009-post-commitイベント連鎖の結果整合性方針.md)（別 UoW 連鎖の結果整合性）
- IT2 開発成果物レビュー（H1・H2）
