---
title: イテレーション 1 ふりかえり
description: IT1 の Keep / Problem / Try を整理し、IT2 へ反映するためのふりかえり記録。
published: true
date: 2026-04-01T00:00:00.000Z
tags: retrospective, it1
---

# イテレーション 1 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT1 |
| 計画期間 | 2026-03-31 〜 2026-04-13 |
| 実績期間 | 2026-03-31 〜 2026-04-01 |
| 対象ストーリー | US02 / US03 / US04 |
| 計画 SP | 10 |
| 実績 SP | 10 |

## Keep

### 技術面

- ドメインモデル、Repository、Web UI を TDD ベースで実装し、`RegisterShipper` / `RegisterBooking` の主要フローを早期に安定化できました。
- Playwright E2E を追加したことで、荷主一覧・予約一覧の未実装不備を早い段階で検知し、一覧画面の完成度を高められました。
- Web / REST 分離、Swagger UI、default seed data、Git Hooks、SonarQube の導入まで一気通貫で整備でき、IT2 以降の開発基盤が強化されました。

### プロセス面

- backend テスト、E2E、SonarQube Quality Gate を変更ごとに回す流れが定着し、レビュー指摘の反映まで品質を維持できました。
- UI / UX レビュー、アーキテクチャレビュー、テストレビューの結果を実装に戻し、単なる CRUD 完了で終わらせず次イテレーションの土台を整えられました。
- ドキュメント更新、ADR 記録、Git Hooks 整備まで同じイテレーションで完了し、運用品質の先送りを抑制できました。

## Problem

### スコープ管理

- IT1 の 10 SP 自体は達成したものの、完了後に E2E 強化、Web / REST 分離、Swagger、seed、レビュー反映まで実施し、事実上の改善スコープが計画を超えて拡張しました。
- iteration plan 上の受入条件と、改善後に到達した実装状態の間に差分が生まれ、進捗ドキュメントの更新が後追いになりました。

### 品質・設計

- E2E を追加するまで、荷主一覧・予約一覧が空のままでも backend テストだけでは見逃していました。
- Swagger の公開範囲、API 認証 UX、default profile seed の結合度など、レビューで初めて可視化された設計課題がありました。
- Windows + Docker Desktop + Testcontainers、Playwright 用アプリ起動ポート、H2 Console 有効化など、環境差分の吸収に想定以上の時間を使いました。

## Try

| Try | 担当 | 期限 | 期待効果 |
|-----|------|------|----------|
| IT2 開始時に、計画 SP 外の改善タスクを別枠で明示し、ストーリーコミットメントと改善コミットメントを分離する | Copilot | IT2 計画作成時 | 計画達成率と実作業量のズレを可視化できる |
| 新しい API / profile / security 変更では、controller test に加えて `SecurityConfigTest` と profile 切り替えテストを DoD に含める | Copilot | IT2 開始時 | 本番とテストの挙動差を早期に防げる |
| E2E シナリオをストーリー受入条件にひも付けて管理し、一覧表示・詳細表示・導線変更を必ず回帰対象に含める | Copilot | IT2 実装開始時 | UI 改善時の回帰漏れを減らせる |

## 次イテレーションへの引き継ぎ

- IT2 では US01（輸送見積）と US06（最適ルート検索）を中心に、IT1 で整備した Web / REST 分離、seed、Swagger、品質ゲートの基盤を活用します。
- 品質ベースラインは backend 113 テスト Green、E2E 9 シナリオ Green、SonarQube Quality Gate PASS を維持条件とします。
- UI / UX レビューで残った 404、アクセシビリティ、共通ナビゲーション改善は、IT2 の余力または別改善枠で扱います。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-01 | IT1 ふりかえりを作成 | Copilot |
