---
title: イテレーション 3 ふりかえり（KPT）
description: IT3（航海スケジュール US24/US25/US07/US08・Routing Context・Location 共有カーネル・外部経路 ACL）の KPT と次イテレーション引き継ぎ
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 3 ふりかえり（KPT）

- **対象**: IT3（航海登録 US24・更新 US25・検索 US07・経路候補算出 US08・Location 共有カーネル・外部経路 ACL・IT2 負債返済）
- **期間**: Week 5-6（〜2026-07-28 クローズ）
- **実績**: 14 SP 完了（達成率 100%）／ RSpec 205 examples 0 failures ／ Line 94.27%

## Keep（うまくいったこと）

- **中盤インサイドアウトが機能**。データ層（migration）→ リポジトリ → ドメイン（Voyage 集約・Schedule・CarrierMovement）→ アプリ → UI の順で貫通し、複雑な状態（時系列連結・貨物種別フィルタ）を貧血に陥らせず作れた。
- **Location 共有カーネルを UN/LOCODE 参照キー方式で導入**。VO 埋め込みによる privacy 違反・型結合を避け、公開 API（LocationDirectory）+ 文字列参照で BC 境界と両立できた。
- **外部経路 ACL を WebMock 契約テストで確立**。正常 3 候補・タイムアウト/5xx/不正 JSON→フォールバックを契約として固定し、フォールバック（自航海データ）で外部障害耐性を担保。
- **ADR-0004 で正典衝突を着手前に解決**。US08 の RouteCandidate が Estimation の要素という正典と、開発戦略の Routing 割当の衝突を「Routing 一時計算 / Estimation 永続化」で調停し、実装と同時に domain-model/architecture を更新（T12 実践）。
- **負債返済が仕組み化**。T2（ドメイン AR 禁止 RuboCop cop）・T11（playwright :js ドライバ）・T13（安全変換ヘルパ）・T14（荷主名選択 UX）を完了。特に cop と JS ドライバは「仕組みで守る」安全網。

## Problem（課題・負債の発生源）

- **「ローカル緑・CI 赤」が 2 回発生**。(1) RuboCop カスタム cop を Rails が eager load して LoadError、(2) `db:prepare` が新規 DB で seed を実行し荷主サンプルがテスト固定メールと衝突。いずれもローカルの緩い条件（lazy load・既存 DB）で見逃した。
- **共有カーネルを「作ったが使っていない」**。LocationDirectory を用意したが Booking の実在検証に配線しておらず、shared パックの privacy/dependency が実使用で未検証。
- **一時計算値の公開が射影されていない**。VoyageDirectory.calculate_route_candidates が内部ドメイン VO（RouteCandidate）を境界外へ素通しし、Packwerk privacy をすり抜けた。
- **「動くが業務で回らない」UX ギャップ**。経路割り当て画面がオーファン（導線なし・クローズ前修正）、候補に到着日/費用/運送会社なし、航海登録が UN/LOCODE・ISO 日時の手打ち・1 区間固定。
- **受入基準の一部が stub で「実装済みに見える」**。US08 寄港地接続評価はフォールバックが直行のみ、US25 差分確認は未実装。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T16 | CI 相当のローカル検証を標準化（`db:drop db:prepare` + eager load + `--order random`）をクローズ前チェックに | 「ローカル緑・CI 赤」の再発防止 | IT4 |
| T17 | Location 実在検証を Booking のアプリ層に配線（LocationDirectory#exists?）・booking→shared 依存宣言 | 共有カーネルの実消費・参照整合 | IT4 序盤 |
| T18 | 公開 API が内部 VO を素通しさせない射影規律を徹底（RouteCandidate 公開ビュー） | privacy のインスタンス漏れ防止 | IT4 |
| T19 | 経路候補 UI に到着予定日・費用・運送会社を表示、フォールバック費用/バッジの語義を改善 | 営業が荷主に日付・費用で回答できる | IT4 序盤 |
| T20 | 航海登録の入力補助（港名サジェスト・日時ピッカー・多区間対応）・航海検索の港名/部分一致 | 現場の打ち間違い削減 | IT4 |
| T21 | 楽観ロック（voyages lock_version）と RegisterVoyage/UpdateSchedule の DRY・reconstitute 分離 | 更新競合防止・責務明確化 | IT4 |
| T22 | US25 差分確認画面・US08 寄港地接続評価（フォールバック多区間）を実装 | 受入基準の完全充足 | IT4 |
| ~~T8~~（完了） | SonarQube を ruby/take-1 に導入（本 IT クローズ後に返済） | Quality Gate PASS・Bug 0・Vuln 0・重複 0%・カバレッジ 82.7% を達成 | 完了 |
| T23 | 命名統一（ExternalCargoRoutingService）を domain-model/architecture/test_strategy に反映・README を IT3 まで更新 | 用語ドリフト解消 | IT4 |

## SonarQube 品質ゲート（T8・クローズ後に返済）

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| Quality Gate | PASS | PASS | ✅ |
| Bug | 0 | 0 | ✅ |
| Vulnerability | 0 | 0 | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| カバレッジ | 82.7% | 全体 80% 以上 | ✅ |
| Code Smell | 9 | 可能な限り 0 | 下記方針で許容 |

**残 Code Smell 9 件の方針（許容）**:

- **空アクションの説明コメント（3 件）**: dashboard/voyages に加え、Rails 標準のプレースホルダ系。順次コメント付与済み・残りは薄いコントローラの慣習として許容。
- **form label の関連付け（3 件）**: `radio_button_tag`/`check_box_tag` を `label` で包む Rails 慣習に対する誤検知寄り。実利用ではアクセシブル。IT4 で明示的な `for`/`aria` 付与を検討。
- **11 引数（2 件・book_cargo/cargo）**: 貨物予約の豊富なドメイン属性をキーワード引数で受ける意図的設計。パラメータオブジェクト化は IT4 で検討。
- **`globalThis` 推奨（1 件）**: Stimulus/Turbo 由来の JS。影響軽微。

いずれも Quality Gate（新規コード基準）を阻害せず、Bug/Vulnerability/重複のハード基準は満たしている。

## 次イテレーション（IT4）への引き継ぎ

- **持ち越しなし**（IT3 スコープの US24/US25/US07/US08 は完了。US25 差分確認・US08 寄港地接続評価はスコープ調整として上記 Try で対応）。
- IT4 は Phase 2 後半（US09 経路選択・US10 再算出・US11 経路紐付け・US12 通知・US13 予約確定）。**IT3 で確立した Voyage/経路候補・Location 共有カーネル・状態機械（BookingStatus）を結合**する局面。
- IT4 序盤で T17（Location 配線）・T19（経路候補 UX）・T18（射影）を先着手し、業務で回る状態にする。
