---
type: ADR
title: "ADR-011: テスト戦略（形状・水準・DB・E2E）"
description: "U-02〜U-05 をピラミッド型、U-01 をダイヤモンド型とし、水準は Unit 別（Comprehensive／Standard）、DB 統合テストは PostgreSQL の実物、受入条件とテスト名を対応させる。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-04T03:59:38Z }
---

# ADR-011: テスト戦略（形状・水準・DB・E2E）

U-02〜U-05 をピラミッド型、U-01 をダイヤモンド型とし、水準は Unit 別（Comprehensive／Standard）、DB 統合テストは PostgreSQL の実物、受入条件とテスト名を対応させる。

日付: 2026-11-04

## ステータス

承認済み（2026-11-04、Bolt 1 の着手前ゲートとして）

> **承認の範囲：** Bolt 1 の着手に必要な範囲です。 以下は起草時の注記です。**元の注記：人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 TST-01・TST-02・TST-03・TST-05・TST-06・TST-07）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：Bolt 1 の前（変異テスト TST-04 は Bolt 5 の前、性能テスト TST-08 は非機能要件の合意後）

## コンテキスト

- 本番機能のテスト戦略は Standard 未満にしない（リリース計画）。U-01・U-03・U-05 は Comprehensive。
- 純粋なドメイン（`Either[Error, (State, Event)]`）に投資でき、Unit 間の連携は 17 本の往復テストが要る。
- 受入条件 120 件は人と AI の契約。判定できない受入条件が 8 件ある（解消まで実装・テストを始めない）。
- メモリ上の別 DB は PostgreSQL 固有の型・制約を再現できず、偽の緑になる。

## 決定

**U-02〜U-05 はピラミッド型、U-01 はダイヤモンド型**。水準は U-01・U-03・U-05 を **Comprehensive**（プロパティテスト + 守りを壊して赤を確認）、U-02・U-04 を **Standard**。**DB 統合テストは PostgreSQL の実物（Testcontainers）**で行い、メモリ上の別 DB は使わない。E2E は業務シナリオ単位で少数に絞り、**一覧から対象を選んで始める**（Playwright）。**テスト名に `US13-AC02` のようにストーリーと受入条件の番号を含め**、AC 一覧との突き合わせを自動で検査する。カバレッジはドメイン 90%・全体 80% を出発点とし、閾値は上げる一方にする（計測ツールは Scala 3 の対応を確認してから）。

### 変更箇所

- テストの道具：ScalaTest・ScalaCheck・pekko の testkit・Testcontainers・ArchUnit、フロントは Vitest・Testing Library・MSW・Playwright・axe-core（外部ツールの導入は承認が要る）。
- `sbt test` はモジュールを絞らず全体で通す。

### 代替案

- **全体を統合重視（ダイヤモンド型）**：ドメインが純粋で速く検査できる利点を捨てる
- **全 Unit を Comprehensive**：コストが過剰
- **メモリ上の DB を使う**：方言差で偽の緑になる
- **受入条件ごとに E2E**：遅く壊れやすい

## 影響

### ポジティブ

- 速いフィードバックと、受入条件へのトレーサビリティ。

### ネガティブ

- Testcontainers のため CI で Docker が要る。
- Comprehensive の Unit は人の確認（壊して赤）の手間が増える。

## コンプライアンス

- AC 番号とテストの突き合わせスクリプトを Bolt 1 の計画に含める。
- A1〜A9・F1〜F9・I1〜I8 を CI の段階に割り当てる（テスト戦略 12）。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [テスト戦略](../design/test_strategy.md)
- 関連コミット: なし（未コミット）
