---
title: イテレーション 3 ふりかえり
description: IT3 の Keep / Problem / Try を整理し、IT4 へ反映するためのふりかえり記録。
published: true
date: 2026-04-02T00:00:00.000Z
tags: retrospective, it3
---

# イテレーション 3 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT3 |
| 計画期間 | 2026-04-28 〜 2026-05-11 |
| 実績期間 | 2026-04-02 |
| 対象ストーリー | US05 / US07 / US08 / US09 |
| 計画 SP | 12 |
| 実績 SP | 12 |
| テスト件数 | 278 件（全 Green） |
| カバレッジ | 92.5%（JaCoCo LINE） |
| SonarQube | Quality Gate 修正済み ✅ |

## Keep

### 技術面

- **TDD サイクルの徹底**: Red → Green → Refactor のサイクルを 16 タスクで一貫して実施し、278 テスト全通過を維持しながら 4 ストーリーを追加できました。
- **ACL（TrackingLookupPort）パターン**: Booking BC から Tracking BC への依存を `TrackingLookupPort` インターフェース + `TrackingLookupPortAdapter` で分離しました。IT1 で確立した `ShipperExistencePort` と同一パターンで、テストの差し替えが容易でした。
- **`@TransactionalEventListener` による BC 間非同期処理**: `BookingConfirmedEvent` を Tracking BC が受信して追跡番号を発行する設計が、IT1 で確立した `@Commit` テストパターンと組み合わせて安定動作しました。
- **コードレビュー + UI/UX レビューの二重チェック**: 高優先（コードレビュー 8 件・UI/UX レビュー 6 件）を分けて実施することで、設計品質とユーザビリティの両面を網羅できました。
- **E2E Page Object の拡充**: `RouteConfirmPage` を追加し、`booking-special.spec.ts`・`route-confirm.spec.ts` として受入条件に対応したシナリオを体系的に整備できました。

### プロセス面

- IT2 の Try 事項「SonarQube イシューを参照してから設計するルール」を適用し、コントローラー実装時に既存違反パターンを事前確認することで new_violations を最小化できました。
- コミットを機能単位・レイヤー単位に細分化し、US05 → US07 → US08 → US09 の順序で依存関係に沿った開発を進められました。
- レビュー指摘（高優先 14 件）をすべて同イテレーション内に対応・コミットし、技術的負債を持ち越さずに完了できました。

## Problem

### 設計・実装

- **ドメインイベント多重発行**: テストで `Booking.register()` を使ってフィクスチャを作成すると `BookingRegisteredEvent` が `domainEvents` に残留し、後続操作（`assignRoute()` / `confirm()`）のイベントと合算されて `TooManyActualInvocations` が発生しました。`reconstitute()` に変更することで解消しましたが、フィクスチャポリシーの規約を早期に確立すべきでした。
- **E2E アサーションと UI 表示の乖離**: `BookingStatus` の `displayName` 対応で E2E アサーションの一括更新が必要になりました。UI 表示テキストは定数として E2E テストと共有する仕組みを検討する必要があります。
- **コミット前テスト確認の不徹底**: `times(1)` への変更時に import が不足したままコミットしてしまいました。ローカルビルド確認を必須化する習慣が必要です。

### 品質管理

- IT3 で追跡番号（US09）は REST API のみ対応しており、予約詳細の追跡番号表示には「（追跡機能は準備中です）」のテキストが残っています。IT4 の US13（追跡情報照会）で Web UI と連携して解消する必要があります。

## Try

| Try | 担当 | 期限 | 期待効果 |
|-----|------|------|----------|
| テストフィクスチャポリシーの確立: 「永続化後の再構成オブジェクトは `reconstitute()`、新規作成は `register()`」を iteration_plan-4.md に明記する | Copilot | IT4 計画時 | ドメインイベント多重発行によるテスト失敗を事前防止できる |
| IT4 ベロシティ注意（13 SP、平均 10.7 超）: US10/11/12/13 のタスク分解を細かく行い、Day 3 時点でのリスク検知サイクルを設ける | Copilot | IT4 開始時 | 計画超過リスクを早期に特定し、スコープ調整の時間を確保できる |
| 追跡機能の拡充: US13 で発行済み追跡番号を使った Web UI 照会画面を実装し、`detail.html` の「追跡機能準備中」テキストを解消する | Copilot | IT4 実装中 | エンドツーエンドの追跡フローが完結し、Phase 1 コア機能のシナリオが繋がる |

## 次イテレーションへの引き継ぎ

- IT4 では US10（荷役作業）/ US11（引取作業）/ US12（手動更新）/ US13（追跡情報照会）を 13 SP で実装します。
- 品質ベースラインは backend 278 テスト Green、E2E 26 シナリオ Green、SonarQube Quality Gate PASS を維持条件とします。
- IT3 で確立した `TrackingLookupPort` ACL パターンと `reconstitute()` フィクスチャポリシーを IT4 全タスクに適用します。
- IT3 で発行した追跡番号（US09）を US13（追跡情報照会）と連携させ、`detail.html` の「追跡機能準備中」を解消します。
- IT3 終了時点での実績ベロシティ平均は 10.7 SP（IT1: 10 / IT2: 10 / IT3: 12）。IT4 の 13 SP はやや高めのため、スコープ調整の準備をしておきます。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-02 | IT3 ふりかえりを作成 | Copilot |
