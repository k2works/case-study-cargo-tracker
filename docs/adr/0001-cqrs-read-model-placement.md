# ADR 0001: CQRS Read Model の sqlx 実装を infra-persistence に配置する

## ステータス

承認

## コンテキスト

本システムは DDD・ヘキサゴナルアーキテクチャ・CQRS を採用し、cargo workspace のクレート分割によりレイヤー間の依存制約をコンパイラで強制している。CQRS の読み取り側（Read Model）は、JOIN を含む生 SQL（`query_as!` マクロ）で画面表示用のフラットな DTO を直接返す方式を採る。

当初の設計案では、クエリ実装（`query_services/`）を `app-booking` 等の app 層クレートに置き、`query_as!` で DTO を直接返すとしていた。しかしこの案では app 層クレートが sqlx に依存することになり、以下の問題が生じる。

- 「Application 層は Domain のみに依存する」というレイヤー責務・依存方向の規約と矛盾する
- `domain-*` / `app-*` を axum / sqlx 非依存に保つことで得ているテスト容易性（mock による単体テスト）が読み取り側で失われる
- クレートの依存グラフによる構造検証（`cargo build` がアーキテクチャテストを兼ねる）に例外が生まれる

## 決定

CQRS Read Model のクエリ実装は `infra-persistence` クレートに配置する。

- **app 層**: クエリポート trait（例: `BookingQueryPort`）のみを持つ。戻り値の Read Model DTO は app 層で定義し、sqlx に依存しない（`FromRow` 導出を行わない）
- **infra-persistence**: クエリポート trait の実装（例: `SqlxBookingQueryAdapter`）を置き、`query_as!` マクロ・`FromRow` 導出などの sqlx 依存部分をすべてここに閉じ込める
- **interface 層**: app 層のクエリサービス（クエリポート経由）を呼び出す。sqlx 実装を直接参照しない

これにより読み取り側もヘキサゴナルアーキテクチャの「ポートとアダプター」構造に統一される。ドメインモデルを経由しないという CQRS の利点（読み取り最適化・フラット DTO・コンパイル時 SQL 検証）は維持する。

## 影響

- app 層クレート（`app-booking` 等）の Cargo.toml から sqlx 依存を排除でき、依存制約の機械的検証が読み取り側にも及ぶ
- クエリサービスの単体テストがクエリポート trait のモックで可能になる
- Read Model DTO の定義（app 層）と `FromRow` マッピング（infra 層）が分離されるため、DTO 変換の記述量がわずかに増える（許容するトレードオフ）
- `docs/design/architecture_backend.md` のヘキサゴナル図・CQRS 設計・クレート構成を本決定に合わせて更新した
