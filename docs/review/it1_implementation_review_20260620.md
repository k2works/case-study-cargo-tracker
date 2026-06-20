---
title: IT1 実装マルチパースペクティブレビュー
date: 2026-06-20
reviewers:
  - xp-programmer
  - xp-tester
  - xp-architect
  - xp-technical-writer
  - xp-user-representative
---

# IT1 実装マルチパースペクティブレビュー

## レビュー対象

- 対象コミット範囲: `110ee0ac..460f9072`（IT1 計画作成後から AuthFilter 全適用まで）
- 主要追加: 73 ファイル / 3,305 行
- スコープ: `apps/cargo-tracker/` 配下のプロダクション + テスト + Flyway V1-V4 + Twirl ビュー + IT1 ドキュメント

対象ストーリー: US26（認証）/ US02（個人荷主）/ US03（法人荷主）/ US01（見積）/ US04（予約）。

## 総合評価

IT1 は DDD + ヘキサゴナル + Scala 3 イディオム（opaque type / Either / final case class private）の骨格が正しく実現され、71 テスト全通過・Phase 1 半分完了という定量結果を達成しています。一方で **(1) 本番混入リスクのある開発用ハードコード（admin 資格情報）**、**(2) ArchUnit ルール未実装による設計を守る仕組みの欠如**、**(3) Web 入力と永続化復元の双方で `unsafeFrom` が乱用され opaque type の防御効果が薄れている**、**(4) DB に UNIQUE / version 制約が不足し競合状態に弱い**、の 4 点が IT2 着手前にクローズすべき共通指摘です。

## 改善提案（重要度順）

### 高（マージ前 / IT2 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | admin 初期パスワードのハードコードと UI プリフィルを `application.conf` + `Environment.isDev` ガードに移す | `AdminUserSeeder.scala:19`, `views/auth/login.scala.html:16-22` | programmer / writer | 本番デプロイ時に Seeder が走り得る重大セキュリティリスク |
| H2 | ArchUnit 4 ルールを実装（domain → infra/interfaces 禁止、shared → 他コンテキスト禁止、ACL 例外） | `test/cargotracker/architecture/` に新規 | architect | 計画書で前提とされているが grep で 0 件。依存逆流を CI で検知不能 |
| H3 | Web 入力に対する `ShipperId.unsafeFrom` を `ShipperId.apply` に変更しエラー分岐 | `BookingController.scala:130` | programmer / architect | 形式不正と荷主未存在が同じエラーに丸められ、ユーザーガイダンスが劣化 |
| H4 | `shipper.email` に UNIQUE 制約追加し DB レベルで重複登録を防ぐ | `V2__create_shipper.sql:8` | programmer | アプリ層チェックのみだとレース条件で二重登録可能。users.email と非整合 |
| H5 | `cargo` / `estimate` に `version INTEGER NOT NULL DEFAULT 0` を追加し US17 楽観ロックの土台を IT2 冒頭で確保 | `V4/V3` 追記 or V5 新設 | architect | 後付けはマイグレーション + 集約 + save の同時改修となり「楽な変更」を毀損 |
| H6 | `docs/index.md` と `mkdocs.yml` のナビゲーションを IT1 成果物に追従（ADR 0002-0004、iteration_report-1、retrospective-1） | `docs/index.md`, `mkdocs.yml:107` | writer | ナビ未登録で発見不可能。ADR 件数も「1 件」のまま |
| H7 | Testcontainers + Play Application を `GuiceOneAppPerSuite` + `BeforeAndAfterEach` での TRUNCATE に集約しテスト独立性を確保 | `e2e/*Spec.scala` 共通 | tester | 各テスト内 `buildApp`/`app.stop()` + DB クリーンアップ無しで Flaky 化リスク |
| H8 | カバレッジゲート 75% 暫定引き下げを 80% 復帰する時限（イテレーション・条件）を ADR か iteration_plan で時限管理 | `build.sbt`, ADR | tester / programmer | 暫定措置は技術的負債化しやすい。Module/Initializer 除外の妥当性も同所で記録 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| M1 | `AuthFilter.publicPathPrefixes` を完全一致 Set に倒し `/logout` を保護対象に | `AuthFilter.scala:25` | programmer |
| M2 | セッション cookie 改ざん耐性: `Instant.parse` を `Try` で包む | `AuthFilter.scala:41` | programmer |
| M3 | `nextIdentity` の `MAX(...)+1` 採番を `CREATE SEQUENCE` + `nextval` に置換 | `ScalikeJdbc{Cargo,Shipper}Repository` | programmer |
| M4 | `RouteSpecification` のプライマリコンストラクタを `private` 化し不変条件バイパスを防ぐ | `RouteSpecification.scala:8` | programmer |
| M5 | `Money` 他 case class の `copy` を private 化 or 通常クラス化（VO 契約の維持） | `Money.scala`, 他 VO | programmer |
| M6 | フォーム値オブジェクト変換ロジック（Location / Weight / CargoType）を `shared/interfaces/web/FormBindings` に抽出 | `BookingController`, `EstimateController` | programmer |
| M7 | リポジトリ復元の `unsafeFrom` を `private[infrastructure]` 化し、検証付き復元パスを設ける | `*.unsafeFrom` 全般 | architect |
| M8 | `User.reconstruct` の戻り値設計を見直し（Either 握り潰しで「見つからない」になる現状を改善） | `ScalikeJdbcUserRepository:43` | programmer |
| M9 | `PricingService` のエラーチャネル分離（`Money.Error` を保持しログに残す） | `InMemoryPricingService:29` | architect |
| M10 | テストピラミッドの是正（戦略 70/25/5 に対し IT1 実態 44/10/19）。アプリケーション層 0 件、Controller Form 検証不足 | `test/` 全体 | tester |
| M11 | UI ラベルから専門用語「UnLocode」を除去 or 補足（例示・placeholder・title 追加） | `views/{booking,estimate}/form.scala.html` | writer |
| M12 | コントローラのエラーメッセージを「何をすればよいか」型に修正 | `BookingController:106-114` 等 | writer |
| M13 | 法人/個人切替時に割引率・契約番号フィールドを htmx で表示制御 | `views/shipper/form.scala.html` | writer / user-rep |
| M14 | 各 Controller / Filter にクラスレベル ScalaDoc を追加（PRG・認証適用方針） | `*Controller.scala`, `AuthFilter.scala` | writer |
| M15 | 見積詳細 → 予約登録の業務導線を UI 上にリンクで提供 | `views/estimate/detail.scala.html` | user-rep |
| M16 | 予約登録時の荷主コード手入力を回避（荷主一覧から選択 or 検索） | `views/booking/form.scala.html` | user-rep |
| M17 | iteration_report-1 の Spec 名表記（`CargoBookingSpec` → `CargoSpec`）と期間注記の修正 | `iteration_report-1.md:125`, 期間表記 | writer |
| M18 | ADR 0002/0003/0004 本文のステータスを「承認」に揃える（index と本文の不一致解消） | `docs/adr/0002-,0003-,0004-*.md` | writer |
| M19 | `Module.scala` で `ScalikeJdbcInitializer` がプロセス全体の `ConnectionPool.singleton` を書き換えるリスクをドキュメント化 | `Module.scala` | architect |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L1 | `sealed trait Error` を Scala 3 の `enum` に統一 | `Cargo.scala:21` 他 | programmer |
| L2 | enum の DB マッピングに `toString` を使わず `name: String` 明示プロパティを使う | `ScalikeJdbcCargoRepository.save` | architect |
| L3 | テストコミットの粒度（Red→Green 別コミット）を意識的に分ける | git log 全般 | programmer |
| L4 | `InMemoryPricingService` の hashCode 距離係数を「IT3 で `pricing_tariff` に置換」と明示コメント化 | `InMemoryPricingService:29` | architect |
| L5 | `Cargo.book` のシグネチャから `ShipperExistenceChecker` を将来分離する案を検討 | `Cargo.scala` | architect |
| L6 | retrospective-1 の Try 担当者表記「IT2 着手者」を「IT2 Day 1 朝会で決定」に | `retrospective-1.md:62` | writer |
| L7 | `Currency` の opaque type 化（多通貨拡張準備） | `Money.scala` | programmer |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | programmer: `unsafeFrom` は永続化復元専用 | architect: 復元側も `unsafeFrom` は最終手段で検証付きに | Repository でも `unsafeFrom` を使うべきか | **architect 寄り**: Web 入力は `apply`、Repository 復元は `private[infrastructure]` の `unsafeFrom` + 失敗時ログ。両者整合 |

## 重要度「高」の対応方針

| # | 対応方針 |
|---|---------|
| H1 | **修正**: IT2 Day 1 で対応。`application.conf` 化 + Dev ガード + login テンプレ条件分岐 |
| H2 | **修正**: IT2 Day 1 で ArchUnit Spec 追加（4 ルール）。CI に組み込み |
| H3 | **修正**: IT2 Day 1 で `BookingController` の入力検証パス修正 |
| H4 | **修正**: V5 マイグレーションで `shipper.email` UNIQUE 追加 |
| H5 | **修正**: V5 マイグレーションで `version` 列追加。`Cargo` / `Estimate` 集約と `save` を同時改修 |
| H6 | **修正**: `docs/index.md` と `mkdocs.yml` 更新 |
| H7 | **修正**: 共通 Trait `IntegratedAppSpec`（GuiceOneAppPerSuite + TRUNCATE）を導入し e2e 共通化 |
| H8 | **修正**: ADR-IT2-1 として「カバレッジ 80% 復帰計画」を起票（IT3 末まで） |

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 3 / 中: 7 / 低: 3）</summary>

DDD レイヤ分割と Scala 3 イディオムを素直に適用。`opaque type + apply(Either) + unsafeFrom(復元用) + extension value` の VO パターンが統一されている点が高評価。一方、Web 入力で `unsafeFrom` を使用、admin ハードコード、`shipper.email` UNIQUE 欠落、`MAX(id)+1` 採番、`case class` の `copy` で VO 不変条件バイパス可能、フォーム変換ロジックの重複等が主要指摘。

</details>

<details>
<summary>xp-tester（高: 1 / 中: 2 / 低: 1）</summary>

ピラミッド形状の逸脱（戦略 70/25/5 vs 実態 44/10/19）。Testcontainers + Play Application の使い方が誤り（各テスト内 `buildApp`/`app.stop()` + DB クリーンアップ無し）で Flaky 化リスクが最大の懸念。CI ゲート 75% への暫定引き下げの根本原因（アプリケーション層 0 件、Controller Form 検証不足、戦略書 8.2 必須項目未着手）の解消が必要。

</details>

<details>
<summary>xp-architect（高: 3 / 中: 4 / 低: 2）</summary>

ヘキサゴナル + DDD の骨格は正しい。集約境界・共有カーネル・ACL ポートが自然な切れ目として機能。一方、**ArchUnit ルールが実装されていない**（grep で 0 件）、**永続化復元と Web 入力の両方で `unsafeFrom` が過剰使用**、**`version` 列欠落で US17 楽観ロックの準備不足**が IT2 以降に響く負債の芽。「設計意図は正しいが、設計を守る仕組みが弱い」。

</details>

<details>
<summary>xp-technical-writer（高: 2 / 中: 5 / 低: 1）</summary>

ADR 0002/0003/0004 は模範的、iteration_report と retrospective は定量分析が良好。一方 `docs/index.md` と `mkdocs.yml` が IT1 成果物に追従していない（ADR 件数 1 件のまま、retrospective-1 への入口無し）。UI 用語「UnLocode」がビジネスユーザーに不親切、コントローラに ScalaDoc 不足、iteration_report の Spec 名表記揺れ。

</details>

<details>
<summary>xp-user-representative（高: 0 / 中: 3 / 低: 0）</summary>

Internal Alpha デモとしては合格。最重要 3 点は **(1) 荷主コード手入力（UX 劣化）**、**(2) 見積 → 予約の業務導線が UI 上に無い**、**(3) 法人/個人切替の htmx 制御が未実装で割引率が個人にも表示される**。Release 1.0 までに必ず解消すべき。

</details>

## 関連ドキュメント

- [IT1 完了報告書](../development/iteration_report-1.md)
- [IT1 ふりかえり](../development/retrospective-1.md)
- [IT1 計画](../development/iteration_plan-1.md)
- [リリース計画](../development/release_plan.md)
- [ADR 0002 bcrypt とセッション](../adr/0002-bcrypt-and-session-management.md)
- [ADR 0003 PricingService 共通化](../adr/0003-shared-pricing-service.md)
- [ADR 0004 US26 横断ストーリー](../adr/0004-us26-cross-cutting-story.md)
