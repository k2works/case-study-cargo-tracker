---
title: Phase 0 Walking Skeleton コードレビュー
description: bookingms Phase 0 スケルトン（BookingApplication / PingController / build.gradle.kts）の 5 エージェント並列レビュー結果
published: true
date: 2026-05-13T00:00:00.000Z
---

# コードレビュー結果

## レビュー対象

- `apps/backend/bookingms/src/main/java/.../BookingApplication.java`
- `apps/backend/bookingms/src/main/java/.../interfaces/rest/PingController.java`
- `apps/backend/bookingms/src/test/java/.../BookingApplicationTests.java`
- `apps/backend/bookingms/build.gradle.kts`
- `apps/backend/gradle/libs.versions.toml`
- `apps/backend/settings.gradle.kts`

## 総合評価

Phase 0 Walking Skeleton として YAGNI に忠実な最小実装であり、全体的な完成度は高い。OpenAPI・Version Catalog・プロファイル分離など、後続フェーズの基盤が適切に整備されている。最大の課題は **PingController のテストが存在しない点**（TDD サイクル未遵守）と、**7 サービス分割が早すぎる点**（アーキテクチャリスク）の 2 点。これらは Phase 1 着手前に対処を推奨する。

---

## 改善提案（重要度順）

### 高（Phase 1 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | PingController の統合テストを追加する | `PingControllerIntegrationTest.java` 新規 | xp-programmer / xp-tester | TDD 規律違反。Walking Skeleton が「全レイヤーを通じて動く」ことを証明するテストがない |
| 2 | 事前マイクロサービス分割（7 サービス `.gitkeep`）の再検討 | `settings.gradle.kts` | xp-architect | 現時点で独立デプロイの根拠がなく、モジュラーモノリスから始めて自然な切れ目で分割すべき |
| 3 | `shared` モジュールの役割を ADR で明確化 | `apps/backend/shared/` | xp-architect | shared の境界が不明確なまま実装が進むと、サービス間の結合度が上がる |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 4 | ArchUnit によるアーキテクチャテストを Phase 0 で導入 | 新規テストファイル | xp-tester | DDD + CQRS のレイヤー境界は早期に定義・自動検証しないと後から崩れる |
| 5 | `axon-spring-boot-starter`（extensions）と `axon-test`（core）のバージョンを独立管理 | `libs.versions.toml` | xp-architect | extensions と core は常にバージョンが一致するとは限らない |
| 6 | `OffsetDateTime.now()` を `Clock` 注入に変更 | `PingController.java:28` | xp-programmer / xp-tester | テストで時刻を固定できず timestamp の値検証ができない。ドメインイベントへの影響拡大を防ぐ |
| 7 | PingController の削除条件を具体化（`@Deprecated` または Javadoc で基準明記） | `PingController.java` | xp-technical-writer | 「Phase 1 で削除する」が曖昧で削除漏れリスクがある |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 8 | `Map<String, Object>` の戻り値型を record に変更 | `PingController.java:26` | xp-programmer | Phase 1 以降では型安全な `PingResponse` record を推奨 |
| 9 | `contextLoads` テスト名を意図が伝わる名前に変更 | `BookingApplicationTests.java:17` | xp-tester | 「何を検証しているか」が名前から読めない |
| 10 | OpenAPI `contact` に URL を追加 | `BookingApplication.java` | xp-technical-writer | name のみではフィードバック先が不明 |
| 11 | Phase 1 移行時に BookingApplication Javadoc の技術列挙を削除 | `BookingApplication.java` | xp-technical-writer | 技術スタックは変更頻度が高く、Javadoc 更新漏れのリスクがある |

---

## 矛盾事項

なし。5 エージェントの指摘に相反する内容はなかった。

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 1 / 中: 2 / 低: 1）</summary>

### 評価サマリー
Phase 0 Walking Skeleton として必要最小限の構成が揃っており、YAGNI に忠実な良い出発点です。ただし、PingController のテストが欠落しており、TDD の規律が守られていない点が最も重要な改善ポイントです。

### 良い点
- Walking Skeleton として余計なコードがなく、「要素が最小」を満たしている
- OpenAPI アノテーションが初期段階から入っており、API ドキュメントが自動生成される設計
- `@ActiveProfiles("local-h2")` でテスト環境を分離している
- `Map.of()` による不変マップの返却は適切
- Version Catalog による依存管理は保守性が高い

### 改善提案
- 【重要度: 高】PingController のテストがない。`contextLoads()` はコンテキスト起動確認であり、PingController の振る舞いを検証していない。TDD の原則に従えば、テストを先に書いてから実装すべきだった
- 【重要度: 中】`OffsetDateTime.now()` の直接呼び出し。`Clock` を注入する設計にするとテスタビリティが向上する
- 【重要度: 低】`Map<String, Object>` の戻り値型。Phase 1 以降で専用 record に置き換えを推奨

### 懸念事項
- `mybatis-spring-boot = "3.0.5"` + Spring Boot 4.x の互換性を要確認
- Axon 5.1.0 + Spring Boot 4.x の互換性マトリクスを確認すること

</details>

<details>
<summary>xp-tester（高: 1 / 中: 2 / 低: 1）</summary>

### 評価サマリー
Walking Skeleton としては最小限の構成で妥当です。ただし、Phase 0 の段階で「テストの型」を確立しておくことが、後続フェーズのテスト品質を決定づけます。現状はコンテキスト起動テストのみで、PingController のテストが存在しません。

### 良い点
- `@ActiveProfiles("local-h2")` でプロファイルを分離し、テスト環境の独立性が確保されている
- テスト依存関係が充実しており（ArchUnit・Testcontainers・Axon Test）、後続フェーズに向けた基盤が準備されている
- PingController がステートレスで副作用がなく、テスタビリティが高い

### 改善提案
- 【重要度: 高】PingController の統合テストを追加する（HTTP レイヤーを通す `@SpringBootTest(webEnvironment = RANDOM_PORT)` で）
- 【重要度: 中】`Clock` 注入で timestamp の値を固定可能にする
- 【重要度: 中】ArchUnit によるアーキテクチャテストを Phase 0 で導入する

### 懸念事項
- Testcontainers が依存に含まれているが、テストプロファイルは H2。PostgreSQL 統合テストプロファイルが未整備
- Axon Test が依存に含まれているが Aggregate テストが存在しない。Phase 1 で最初の Aggregate 実装時にサンプルを用意すべき

</details>

<details>
<summary>xp-architect（高: 2 / 中: 1 / 低: 1）</summary>

### 評価サマリー
Phase 0 として bookingms のみを実装し段階的アプローチを取っている点は健全です。ただし、7 つのマイクロサービスを事前に分割している点に構造的な懸念があります。

### 良い点
- Version Catalog による依存バージョンの一元管理は適切
- `FAIL_ON_PROJECT_REPOS` で依存解決の一貫性を強制している
- bookingms 以外をコメントアウトし、動くものから始めている姿勢は XP のシンプルな設計に合致
- Spring Boot 4.0.6 + Axon 5.1.0 という最新スタックの選定

### 改善提案
- 【重要度: 高】事前のマイクロサービス分割（7 サービス）を再考する。まず bookingms 内にモジュラーモノリスとして bounded context を配置し、自然な切れ目が観察できてから分割すべき
- 【重要度: 高】`shared` モジュールの役割を ADR で明確化する。内容が不明確なままだとサービス間の結合度が上がる
- 【重要度: 中】`axon-spring-boot-starter`（extensions）と `axon-test`（core）のバージョンを独立管理する
- 【重要度: 低】`bookingms` の内部に `domain/`, `application/`, `infrastructure/` のパッケージ構造を ADR で定義する

### 懸念事項
- 7 サービスの運用には分散トレーシング・Saga 障害補償・eventual consistency 管理が必要で、準備なく分離すると開発速度が著しく低下する
- Axon Server が単一障害点になるため、開発環境・CI での可用性と起動速度に注意が必要

</details>

<details>
<summary>xp-technical-writer（高: 0 / 中: 1 / 低: 2）</summary>

### 評価サマリー
全体として質が高い。Phase 0 Walking Skeleton という一時的な成果物であることが各所で明示されており、読み手が「なぜこれが存在するのか」「いつ消えるのか」を迷わず理解できる。

### 良い点
- 全ドキュメントで「Phase 0 で存在し、Phase 1 で置換/削除される」と記載されている
- Javadoc の構造（1 行目が要約、`<p>` 以降が補足）が標準的
- OpenAPI の `version` と `description` に Phase 0 であることが含まれている
- libs.versions.toml に「マイルストーン・RC は使用しない」というポリシーが記載されている

### 改善提案
- 【重要度: 中】PingController の削除条件をより具体的に。`@Deprecated(since = "Phase 1", forRemoval = true)` を併用するか、削除判断の基準を明記する
- 【重要度: 低】OpenAPI の `contact` に URL またはメールを追加する
- 【重要度: 低】Phase 1 移行時に BookingApplication Javadoc の技術列挙を `docs/design/tech_stack.md` 参照に切り替える

</details>

<details>
<summary>xp-user-representative（高: 0 / 中: 1 / 低: 1）</summary>

### 評価サマリー
Phase 0 Walking Skeleton として、業務機能が動く「土台」を先に整えるアプローチは正しい。この段階でユーザーが直接触れる機能はないが、IT1 で業務 API を載せるための基盤として必要十分です。

### 良い点
- `/api/ping` があることで IT1 開発中に「そもそもサービスが動いているのか」を即座に確認できる
- Swagger UI で業務 API 実装後すぐにフィードバックできる体制が最初から整っている
- 中途半端な業務機能を入れず骨格だけにした判断が適切

### 改善提案
- 【重要度: 中】IT1 で US00（ログイン）実装時、ログイン画面の表示が 2 秒以内であることを非機能要件として合意しておきたい
- 【重要度: 低】`/api/ping` の `phase` フィールドは開発者向け情報。ユーザー向け画面には内部情報（phase 番号等）を表示しないこと

### 懸念事項
- **IT1 のストーリー順序**: US02（荷主登録）は US00（ログイン）が完了していないと受け入れテストができない。「ログインせずに荷主登録画面にアクセスできる」状態でのデモは業務評価が難しい
- **US02 と US03 の依存関係**: 個人荷主・法人荷主で入力項目や審査プロセスが異なる。IT1 計画時に優先順位と依存関係を整理する必要がある

### スコープ外の発見
- 荷主が自分で予約状況・書類を確認できる「荷主ポータル」が将来的に必要か確認を推奨。アーキテクチャとして拡張可能かを早期に合意しておきたい

</details>

---

## 対応方針（推奨）

| # | 優先度 | 対応方針 | タイミング |
|---|--------|---------|-----------|
| 1 | 高 | PingController の統合テストを追加する | **IT1 着手前** |
| 2 | 高 | マイクロサービス分割方針を ADR に記録し再確認 | **IT1 着手前** |
| 3 | 高 | `shared` モジュールの役割を ADR に明記 | **IT1 着手前** |
| 4 | 中 | ArchUnit アーキテクチャテストの導入 | IT1 内 |
| 5 | 中 | Axon バージョン独立管理 | IT1 内 |
| 6 | 中 | IT1 でログイン（US00）を荷主登録（US02）より先に完成させる | IT1 計画時に確認 |
| 7 | 低 | その他 low 指摘 | Phase 1 移行時に順次対応 |
