---
type: Article
title: "アウトライン — エンタープライズアーキテクチャの 4 観点"
description: "エンタープライズアーキテクチャの 4 観点シリーズの執筆計画。対象・執筆方針・章別計画・ファイル構成。"
tags: [article, enterprise-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-14T09:07:33Z }
---

# アウトライン — エンタープライズアーキテクチャの 4 観点

## 対象

| 項目 | 内容 |
| :--- | :--- |
| 参照元 | [`source/java-2`](../source/java-2)（Java 25 / Spring Boot 4.1 / MyBatis / Thymeleaf + htmx / PostgreSQL 16） |
| 軸 | ビジネス／アプリケーション／データ／テクノロジーの 4 アーキテクチャ観点 |
| 章数 | 11（導入 1 ＋ 各観点 2〜3 ＋ 総括 1） |
| 一次資料 | `docs/strategy/`・`docs/requirements/`・`docs/design/`・`docs/adr/`（ADR-001〜025） |

既存 4 シリーズが **時間軸**（イテレーション）や **プラクティス軸**で同じ実装を追うのに対し、本シリーズは **構造軸**で切る。「いつ何が起きたか」を追わず、「完成した構造がどの層でどう接続されているか」を扱う。

## 執筆方針

- **4 観点は独立に読めるが、縦に貫通していることを示す。** 各観点の章末に、上位観点からの受け取りと下位観点への引き渡しを明示する
- 引用はすべて `source/java-2` の実ファイルから転記する。ビルド成果物（`build/`）は引用しない
- **設計ドキュメントと実装が食い違う箇所は、食い違ったまま書く。** 一次資料側が誤っていた事例（`route_candidate` の所有 BC）は総括で扱う
- CI は java-2 配下に無い。テクノロジー観点では **Gradle のタスクとして実在する検査だけ**を扱い、GitHub Actions は設計上の記述として区別する

## 前提整備

| 項目 | 状態 |
| :--- | :--- |
| 参照元ソース | 収録済み（`docs/article/source/java-2`） |
| サンプル実装 | **新規に書かない。** 本シリーズは既存実装の構造解説であり、追加のコードは伴わない |
| 記事ディレクトリ | `docs/article/enterprise-architecture/` |
| サイトナビゲーション | `mkdocs.yml` の「記事」セクションに追加 |
| CI | **追加しない**（サンプル実装を持たないため検証対象が無い） |

## 章別計画

| 章 | 観点 | 焦点 |
| :--- | :--- | :--- |
| 1 | 導入 | 4 観点の定義と、この題材での貫通の地図 |
| 2 | ビジネス | BMC・バリューストリーム・ケイパビリティマップ（成熟度とヒートマップ） |
| 3 | ビジネス | ケイパビリティ → 要件 → ユーザーストーリー → BC への落とし込み |
| 4 | アプリケーション | 7 BC ＋共有カーネル＋支援サブドメインのコンテキストマップ |
| 5 | アプリケーション | ヘキサゴナルの 4 層とポート／アダプタの配置規則 |
| 6 | アプリケーション | BC 間連携 — ACL 27 ポートとドメインイベント 9 種、結果整合 |
| 7 | データ | 概念／論理データモデルと、テーブル 25 件の所有 BC |
| 8 | データ | MyBatis による CQRS の読み書き分離と Flyway 46 本の進化 |
| 9 | テクノロジー | ランタイムスタック・コンテナ・環境別 DB 戦略 |
| 10 | テクノロジー | アーキテクチャを検査に落とす — ArchUnit・JIG・品質ゲート |
| 11 | 総括 | 4 観点の縦のトレーサビリティと、それが破れた箇所 |

## ファイル構成

```text
docs/article/enterprise-architecture/
├── index.md
├── outline.md
├── 01-four-viewpoints.md
├── 02-business-model.md
├── 03-capability-to-story.md
├── 04-context-map.md
├── 05-hexagonal-layers.md
├── 06-context-integration.md
├── 07-data-model.md
├── 08-persistence-and-cqrs.md
├── 09-runtime-stack.md
├── 10-architecture-governance.md
└── 11-traceability.md
```
