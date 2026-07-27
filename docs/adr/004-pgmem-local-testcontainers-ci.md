# ADR-004: ローカル開発は pg-mem、テストの正は Testcontainers PostgreSQL とする

ローカル開発のデフォルト DB を pg-mem（インメモリ PostgreSQL 互換）とし、統合テスト・CI の合否判定は Testcontainers による実 PostgreSQL 16 を正とする。

日付: 2026-07-27

## ステータス

承認済み

## コンテキスト

参照元の Java 版設計はローカル開発・テストに H2（PostgreSQL 互換モード）と Docker Compose を使っていた。TypeScript 版では「Docker 不要で `npm run dev` だけで即起動できる開発体験」を優先したい一方、インメモリ DB と実 PostgreSQL の SQL 互換性乖離（H2 で繰り返し問題になった教訓）を合否判定に持ち込みたくない。

## 決定

- ローカル開発: **pg-mem** をデフォルトとする。起動時に node-pg-migrate のマイグレーションを適用しシードデータを投入、データはプロセス終了で破棄
- 統合テスト・CI: **@testcontainers/postgresql**（実 PostgreSQL 16）を正とし、pg-mem はテストの合否判定に使用しない
- 実 PostgreSQL でのローカル検証（マイグレーション検証・SQL 互換性確認・永続データ）が必要な場合は Docker Compose 構成（postgres:16-alpine + adminer）をオプションとして併用する

### 代替案

- **ローカルも Docker Compose PostgreSQL のみ**: 本番との差異は最小だが、Docker 起動が開発の前提となり、起動速度・環境構築の負担が大きい。却下（オプションとして残置）
- **SQLite インメモリ**: 方言差が大きすぎ、PostgreSQL 前提の DDL・型が動かない。却下
- **pg-mem をテストにも使用**: window 関数・型キャスト・照合順序などで実 PostgreSQL と挙動が乖離し、偽陽性・偽陰性の温床になる。却下

## 影響

### ポジティブ

- Docker なしで即起動でき、オンボーディングと開発ループが高速
- 合否判定は常に実 PostgreSQL で行われ、SQL 互換性の乖離が CI で検出される

### ネガティブ

- 「ローカル緑・CI 赤」が起こりうる（pg-mem 非対応の SQL 機能）。test_strategy.md に乖離しやすい機能の注意書きを置き、SQL の正当性判断は Testcontainers に委ねる
- pg-mem 用の起動時マイグレーション・シード投入コードの保守が必要

## コンプライアンス

- 統合テストが @testcontainers/postgresql を使用していること（pg-mem をアサートに使っていないこと）を CI 設定とコードレビューで確認する
- ローカル起動（pg-mem）で全マイグレーションが適用可能であることを開発時に確認する

## 備考

- 著者: k2works
- 関連コミット: f3a984ce
- 関連 ADR: ADR-002
