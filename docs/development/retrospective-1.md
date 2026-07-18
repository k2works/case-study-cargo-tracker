---
title: イテレーション 1 ふりかえり（KPT）
description: IT1（予約基盤・US-AUTH-01/US02-05）の Keep・Problem・Try
published: true
date: 2026-07-18T00:00:00.000Z
---

# イテレーション 1 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 1（予約基盤） |
| **局面** | 序盤（アウトサイドイン） |
| **計画 SP / 実績 SP** | 16 / 16（達成率 100%） |
| **対象ストーリー** | US-AUTH-01・US02・US03・US04・US05 |
| **テスト** | 70 passed / 0 failed（単体 + testcontainers 統合 + HTTP フロー oneshot） |
| **実装コミット** | feat 12 / fix 3 / docs 多数 |
| **成果** | ログイン→荷主登録→予約登録→予約詳細の縦切りが実 PostgreSQL 上で成立・全ナビにプレースホルダ |

## Keep（継続すること）

### 技術的成功事項

- **DDD コアの先行実装（インサイドアウト）+ 序盤アウトサイドインの併用**: 認証・UI・サーバ結線に着手する前に、ドメイン→アプリ→永続化の中核を TDD で固めたことで、UI 層は「サービスを呼んで描画するだけ」に痩せ、手戻りが少なかった。
- **型による不変条件の表現**: `ShipperKind::Corporate(CorporateProfile)`・`Cargo::book` の必須検証・スマートコンストラクタが「不正状態を作れない」設計を実現し、テストすべき状態空間を縮小できた（レビュー 5 視点すべてが高評価）。
- **クレート分割による依存境界のコンパイラ強制**: `domain-*` が axum/sqlx に非依存であることを `cargo build` が保証。ArchUnit 相当の事後検証が不要。
- **testcontainers による実 PostgreSQL 統合テスト**: H2 等の代替を使わず本番同一エンジンで検証。方言差異の不具合を排除。
- **1 TDD サイクル 1 コミット + pre-commit（fmt/clippy）**: 常に green を維持し、壊れたコードをコミットしない規律を通せた。

### プロセス的成功事項

- **各層の緑を確認してから次層へ**: `cargo test -p <crate>` → clippy → fmt → commit の順で小さく前進。
- **設計ドキュメントとの往復**: 検証（validating-design / validating-iteration-plan）で設計ドキュメント間の不整合（cargo テーブル・割引率・クレート命名）を着手前に解消できた。
- **意図的逸脱の ADR 化**: axum-login → tower-sessions + 自前 RBAC の判断を ADR-0002 に記録。

## Problem（問題点）

### 未完了・持ち越し項目

- **1.7 sqlx オフラインビルド硬化（`query!` マクロ + `.sqlx`）**: 未実施。ランタイム `sqlx::query` を採用したためオフラインビルド自体は成立しているが、コンパイル時 SQL 検証は未導入。
- **3.6 予約登録イベント発行の骨格（infra-eventbus）**: ACL（ShipperExistenceChecker）は実装したが、tokio broadcast のイベントバス骨格は IT2 へ持ち越し。
- **E2E テスト（Playwright）**: ゼロ。HTTP レベル oneshot で代替検証したが、ピラミッド頂点が欠けた「台形」形状。
- **テストカバレッジ計測（cargo-llvm-cov）**: 未実施。目標（ドメイン 85% 等）に対する現在地が不明。

### 見積もり精度・プロセスの課題

- **IT1=16 SP が実効ベロシティ（10-12）を超過**: 認証・ナビ骨格の前倒し投資で必然的に重かった。初回のため実測が無く見積もりが粗かった。
- **設定系のランタイム不具合を自動テストで検知できなかった**: セッション Cookie の `Secure` 属性（http でログインループ）・デフォルト `DATABASE_URL` の認証情報不一致は、oneshot テスト（Cookie を手動転送）と CI では素通りし、実ブラウザ/実起動で初めて露見した。
- **認可ロジックの分散**: `has_role("ROLE_SALES")` 文字列比較を 4 ハンドラで手書き重複（レビューで 4 視点が独立指摘）。DRY・型安全性・セキュリティの負債。
- **interface 層 → infra 実装の直接依存（DIP 逸脱）**: ハンドラが `SqlxXxxRepository::new` を直接生成。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | 認可を axum extractor（`SalesUser` 等）+ `require_role` で宣言化し、`CurrentUser.roles` を `Vec<Role>` に型化 | 開発 | IT2 冒頭 | 認可書き忘れの構造的防止・型安全（レビュー高 #1） |
| 2 | interface 層のサービス生成を composition root（AppState ファクトリ）へ集約し DIP を回復（ADR 化） | 開発 | IT2 前半 | テスト差し替え容易・トランザクション境界導入余地（レビュー高 #2） |
| 3 | 法人荷主(US03)・危険物/冷凍(US05) の HTTP フローテストを追加 | 開発 | IT1 クローズ〜IT2 | 受入基準のハンドラ層実証（レビュー高 #3） |
| 4 | `cargo llvm-cov --workspace --lcov` を CI に組み込み、実測でカバレッジ目標をキャリブレーション | 開発 | IT2 | 品質の現在地可視化 |
| 5 | 起動・認証まわりの smoke テスト（実起動 or serve レベル）を追加し、Cookie/DB 設定不具合を検知 | 開発 | IT2 | 設定系ランタイム不具合の早期検知（Secure/DATABASE_URL 教訓） |
| 6 | ベロシティ実績（IT1=16）を IT2-3 の実測で補正し、想定 12→再調整 | 開発 | IT3 終了時 | 見積もり精度向上 |
| 7 | E2E（Playwright）着手イテレーションを計画に明記（US08/10/13 優先） | 開発 | 中盤 | ピラミッド頂点の補完 |
| 8 | 本番リリース前チェックリストに「`SECURE_COOKIES=1`・ログイン既定認証情報の除去・seed 無効化」を追加 | 開発 | リリース前 | セキュリティ事故防止（user-rep 指摘の一線） |

## 数値指標

| 指標 | 実績 |
|------|------|
| テスト数 | 70 passed / 0 failed |
| テストカバレッジ | 未計測（Try #4 で計測） |
| ビルド/lint | `cargo build` / `clippy -D warnings` / `fmt --check` 全 green |
| 予定達成率 | 100%（16/16 SP・機能スコープ） |
| 発見バグ（実起動） | 2 件（Cookie Secure・DATABASE_URL 不一致）— いずれも修正済み |
| コードレビュー指摘 | 高 3 / 中 7 / 低 6（developing-review） |

## 次イテレーションへの申し送り

- IT2（航海スケジュール・US24/25/07）着手前に、Try #1-#3（認可 extractor 化・DIP 回復・US03/05 フローテスト）をリファクタリング枠として `iteration_plan-2.md` に組み込む。
- IT2 で infra-eventbus のイベント発行骨格（CargoBookedEvent 等）を導入し、Tracking への連携基盤を用意する。
- ベロシティは IT1 実績 16 を踏まえ、IT2 は計画どおり 11 で様子見（3 イテレーション後に再調整）。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-07-18 | IT1 ふりかえり作成 | - |
