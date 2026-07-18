---
title: IT1 開発成果物レビュー（マルチパースペクティブ）
description: IT1（予約基盤・US-AUTH-01/US02-05）の実装を XP 5 エージェントで並列レビューした統合結果
published: true
date: 2026-07-18T00:00:00.000Z
---

# IT1 開発成果物レビュー結果

## レビュー対象

- イテレーション: IT1（予約基盤）
- 範囲: `apps/cargo-tracker/crates/` 配下 9 クレートの IT1 実装（約 4,400 行）
- 対象コミット: `feat(shared-kernel)` 〜 `fix(server)`（US-AUTH-01・US02-05・`/api/shippers`・seed/migrate・livereload）
- レビュー実施: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative の 5 視点並列

## 総合評価

ヘキサゴナル + DDD の境界（domain→ports→app→infra→interface の依存方向）が cargo workspace のクレート分割でコンパイラ強制され、型による不変条件表現（`ShipperKind::Corporate` が契約情報を内包・`Cargo::book` の必須検証）・ACL 分離・CQRS 読み書き分離・ADR-0001 準拠と、基盤の規律は 5 視点すべてで高評価。「業務が一周する骨格」として IT1 は合格。

一方で、**認可ロジックの分散（文字列ロール比較 + 4 ハンドラでの重複）**、**interface 層 → infra 実装の直接依存（DIP 逸脱）**、**境界値・HTTP フローの網羅穴（法人荷主・危険物/冷凍・入力バリデーション）**、**カバレッジ未計測・E2E 未実装**が中盤に向けた技術的負債として共通指摘された。とりわけ認可は複数視点（programmer/architect/tester/user-rep）が独立に指摘しており、次イテレーション早期の対応を強く推奨する。

## 改善提案（重要度順）

### 高（次イテレーション早期に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 認可を axum extractor（`SalesUser` 等）+ `require_role` で宣言化し、`CurrentUser.roles` を `Vec<String>`→`Vec<Role>` に型化 | interface-web/src/lib.rs（shipper/booking の 4 ハンドラ） | programmer, architect, tester, user-rep | 認可はセキュリティ境界。文字列比較の手書き重複はロール追加・改名時にコンパイラ検知不可で、新規ハンドラでの認可書き忘れが欠陥に直結 |
| 2 | interface 層 → `infra-persistence` 直接依存を解消し、サービス生成を composition root（AppState ファクトリ）へ集約（DIP 回復・ADR 化） | interface-web/src/lib.rs, cargo-tracker-server | architect, programmer | ハンドラが `SqlxXxxRepository::new` を直接 new。テスト差し替え・将来のトランザクション境界導入が困難。依存方向の唯一の逸脱点 |
| 3 | 法人荷主（US03）と危険物/冷凍（US05）の HTTP フローテストを追加 | interface-web/tests/{shipper,booking}_flow_test.rs | tester | US03「割引率 0〜30%」・US05「申告/温度 必須」がハンドラ層で未実証。フォーム→検証→エラー表示の経路に穴 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 4 | `row.try_get(...).map_err(backend)?` の大量重複を型付きヘルパー（`get::<T>(&row,col)`）に集約 | infra-persistence/src/{cargo,shipper}_repository.rs | programmer | マッピングのノイズが本質（組み立て意図）を覆う。可読性 |
| 5 | `backend` 変換の 3 ファイル重複を共有化、または各エラー型に `From<sqlx::Error>` 実装で `?` 化 | infra-persistence/src/*.rs | programmer | 変換規則の不統一を防ぐ |
| 6 | `build_command`（純粋関数）・ロール分岐の単体テストを interface 層に追加 | interface-web/src/lib.rs | programmer, tester | パース/認可分岐こそバグ温床。結合テスト頼みだと回帰検知が鈍い |
| 7 | 割引率・メール形式の境界値/同値クラスを網羅（proptest 推奨） | domain-shipper/src/value_objects.rs | tester | 金額直結の割引率・メール検証の分岐カバレッジ穴。片側境界のみ |
| 8 | コンテキスト間物理 FK（`cargo.shipper_id`→`shipper.id`）の意図的採否を ADR 化 | migrations/20260721000001_it1_init.sql | architect | ACL 疎結合方針と DB 物理 FK の緊張を明示的に記録 |
| 9 | 出発地=目的地・入力バリデーション（不正 UN/LOCODE・重量欠落）の 400 系 HTTP テスト | interface-web/tests | tester | ドメインで押さえた不変条件がハンドラで正しく表示変換されるか未検証 |
| 10 | `user_roles` の未知ロール黙殺（skip）の挙動をテストで固定（フェイルオープン懸念） | infra-persistence/src/user_repository.rs:140 | tester | 不正ロール文字列を含むユーザーがロールなしで認証通過し得る |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 11 | `from_str_or_general`/`_preliminary` の暗黙フォールバックを `TryFrom`/ログ+エラーへ（ADR 検討） | domain-booking/src/value_objects.rs | programmer, tester | 未知の永続化値をサイレントに既定値化＝データ破損の検知遅延 |
| 12 | バリデーション/ログイン失敗の HTTP 200 を 401/422 に | interface-web/src/lib.rs | programmer | REST セマンティクス・可観測性。SSR 都合は理解できるが認識を残す |
| 13 | `Quantity` の i32↔u32 変換の `unwrap_or(MAX/0)` を `RepositoryError` へ | infra-persistence/src/cargo_repository.rs | programmer | 桁あふれの黙殺・矛盾する既定値（0 は InvalidQuantity 誘発） |
| 14 | `find_by_booking_id` の `SELECT *` を明示列指定に（IT1.7 の query! 硬化時） | infra-persistence/src/cargo_repository.rs:94 | programmer | カラム追加時の暗黙挙動変化リスク |
| 15 | infra 層の一部公開 API（`find_credentials`・`save`）に rustdoc `# Errors` 節を補う | infra-persistence/src/{user,cargo}_repository.rs | technical-writer | ドメイン/app 層は 100%。水準を揃える |
| 16 | seed 認証情報・ログイン既定値・既知制約（静的アセット未配信・E2E 未）を README/手順書に記載 | README.md, docs/operation | technical-writer, user-rep | 発見容易性。手順書と実装の乖離 |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | tester: 統合テスト過多（頂点欠けの台形） | programmer/architect: ドメイン層の TDD は良好 | IT1 のテスト形状 | IT1 は認証+登録基盤ゆえ統合寄りは妥当。E2E 着手 IT を計画明記し、境界値をユニットで補強してピラミッドに寄せる |

なし（重大な相反指摘はなし。各視点の指摘は補完的）。

## エージェント別フィードバック要約

### xp-programmer（高: 2 / 中: 4 / 低: 4）
依存方向・型による不変条件・CQRS 分離・ドメイン TDD を高評価。最大の投資対効果は「認可の extractor 化 + `CurrentUser` の `Vec<Role>` 化」。`try_get().map_err(backend)` と `backend` 変換の重複、ハンドラの SRP 逸脱、interface 層の単体テスト欠如を指摘。

### xp-tester（高: 4 相当 / 中: 3 / 低: 3）
テストレベル分離・日本語テスト名・AAA・独立性を高評価。カバレッジ未計測、割引率境界の片側のみ、法人荷主/危険物冷凍/入力バリデーションの HTTP フロー穴、BookingStatus 状態機械テスト不在（IT2 以降と明記推奨）、E2E ゼロによる「頂点欠けの台形」を指摘。

### xp-architect（高: 1 / 中: 2）
依存方向のコンパイラ強制・ADR-0001 準拠・ACL 設計を模範的と評価。(1) interface→infra 直接依存の DIP 回復（composition root 集約・ADR）、(2) 認可の宣言化→中盤 axum-login ミドルウェア化、(3) コンテキスト間物理 FK の ADR 記録 を返済対象に。

### xp-technical-writer（中心は低）
doc コメントはプロジェクト全体で高水準（クレートルート `//!` 全完備、`# Errors` はドメイン/app 層 100%）。infra 層一部に `# Errors` 欠け。最大の改善対象は手順書/README と実装の乖離（seed・ログイン既定値・静的アセット未配信・swagger-ui 未実装の未記載＝発見容易性）。

### xp-user-representative
「業務が一周する骨格」として合格。最優先の業務痛点は **荷主 ID の手入力（誤配送リスク直結）** と **荷主登録後の ID 引き継ぎ**（ui_design のインクリメンタル検索モーダル化）。危険物/冷凍の条件付き入力欄が JS 無しで常時表示される点、**ログイン既定認証情報の本番混入**はリリース前チェックリストで必ず潰すこと。

## 高優先度指摘への対応方針

| # | 指摘 | 対応方針 |
|---|------|----------|
| 1 | 認可の宣言化・型化 | **IT2 早期に対応**（次イテレーション計画のリファクタリングタスクに追加）。セキュリティ境界のため最優先 |
| 2 | interface→infra DIP 回復 | **IT2 で対応** + ADR 起票。composition root への集約 |
| 3 | 法人/危険物/冷凍の HTTP フローテスト | **IT1 クローズ前に追加可能**。小さく安全なテスト追加のため、ふりかえり後に着手推奨 |

## 次ステップ

- 高優先度 3 件の対応方針を IT2 計画（`planning-releases --iteration 2`）のリファクタリング枠に反映
- カバレッジ計測（`cargo llvm-cov`）を IT1 クローズ前に一度実行し目標をキャリブレーション
- ふりかえり（`planning-releases --retrospective`）の Try に本レビューの高/中指摘を投入
- ログイン既定認証情報の本番混入防止をリリース前チェックリスト化
