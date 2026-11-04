---
type: ADR
title: "ADR-009: フロントエンドの構成（React + TypeScript + Vite の SPA）"
description: "React + TypeScript + Vite の SPA とし、サーバー状態は TanStack Query + graphql-request + graphql-codegen、認証方式は AuthProvider の内側に閉じ込める。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-04T03:59:37Z }
---

# ADR-009: フロントエンドの構成（React + TypeScript + Vite の SPA）

React + TypeScript + Vite の SPA とし、サーバー状態は TanStack Query + graphql-request + graphql-codegen、認証方式は AuthProvider の内側に閉じ込める。

日付: 2026-11-04

## ステータス

承認済み（2026-11-04、Bolt 1 の着手前ゲートとして）

> **承認の範囲：** Bolt 1 の着手に必要な範囲です。 以下は起草時の注記です。**元の注記：人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 FE-01・FE-02・FE-03・TS-08・TS-11）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：Bolt 1 の前

## コンテキスト

- 業務画面（内部ツール・ダッシュボード）が中心で、SEO の要件は無い。公開の追跡照会も SPA でよい。
- 荷役作業員はモバイル端末（ブラウザのレスポンシブ。モバイルアプリはスコープ外）。
- コマンドは冪等性キー付き、書き込みは結果整合で遅れて反映される。認証方式は未決（BE-09）。
- チームのスキルセットが不明（Scala.js も代替）。

## 決定

**React + TypeScript + Vite の SPA**（`apps/web/`）を採る。サーバー状態は **TanStack Query + graphql-request + graphql-codegen** で扱い、クライアント状態は Context + hooks に留める。`features/` を Unit と 1 対 1 に対応させ、**feature 同士は import しない**。認証方式は `AuthProvider` に閉じ込め、画面は `useCurrentUser()` だけを使う。Node.js は 24 LTS。UI の部品は自前の薄い部品群を基本とし、デザインシステムは UI 設計で決める（FE-06）。

### 変更箇所

- TanStack Query の版（v5／v6）、Vite、React のマイナー、Tailwind は着手時に確認して固定する。
- 正規化キャッシュ（Apollo）は結果整合の取りこぼしを隠すため採らない。

### 代替案

- **Scala.js（Laminar など）**：言語を揃えられるが、GraphQL のコード生成や UI 部品のエコシステムが小さい
- **Next.js（SSR）**：検索エンジンへの掲載が要件に無く、SSR の運用負荷に見合わない
- **Apollo Client／urql**：Apollo の正規化キャッシュは更新の取りこぼしを隠す。urql は軽量だが本件では TanStack を選ぶ

## 影響

### ポジティブ

- エコシステムが大きく、コード生成・テスト・アクセシビリティの道具が揃う。
- Unit と feature が対応し、変更が Unit の中に収まる。

### ネガティブ

- バックエンド（Scala）と別の言語になる。
- 外部ライブラリが多く、導入に承認が要る（R-01）。

## コンプライアンス

- F1・F2：feature 同士の import 禁止、画面から GraphQL を直接呼ばない（ESLint）。F3：スキーマ差分でビルド失敗。F5・F6：ロール × 画面の到達性と 403。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [フロントエンド 2〜5](../design/architecture_frontend.md)、[技術スタック 3](../design/tech_stack.md)
- 関連コミット: なし（未コミット）
