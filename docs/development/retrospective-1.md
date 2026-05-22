# イテレーション 1 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 1 |
| **期間** | 2026-05-21 〜 2026-06-03 |
| **実施日** | 2026-05-22 |
| **参加者** | k2works |

---

## 実績サマリー

| 項目 | 計画 | 実績 | 達成率 |
|------|------|------|--------|
| SP | 10 | 10 | 100% |
| テストカバレッジ（全体） | 80% | 62.3% | 78% |
| テストカバレッジ（新規コード） | 80% | 81.8% | 102% |
| 重複率 | 3% 未満 | 0.0% | ✅ |
| Code Smell | 0 | 0 | ✅ |
| Bug | 0 | 0 | ✅ |
| コミット数 | - | 36 | - |

### 完了ストーリー

| ID | ユーザーストーリー | 計画 SP | 実績 SP |
|----|-------------------|---------|---------|
| US00 | 認証（ログイン・ログアウト・アカウントロック） | 3 | 3 |
| US24 | 航海スケジュールを新規登録する | 3 | 3 |
| US25 | 既存航海スケジュールを更新する | 2 | 2 |
| - | 基盤構築（マルチモジュール・Kafka 接続・Heroku デプロイ） | 2 | 2 |
| **合計** | | **10** | **10** |

---

## KPT

### Keep（よかったこと・継続すること）

#### 技術的成功事項

1. **TDD サイクルの遵守**: VoyageAggregate・User ドメインモデルを Red→Green→Refactor で実装し、テスト品質を担保した
2. **Java record の活用**: Command / Event / DTO クラスを record に変換し、重複率を 8.5% → 0.0% に改善。ボイラープレート除去が効果的だった
3. **Heroku Container Registry デプロイ**: authms・routingms・gatewayms・frontend を Docker イメージとして Heroku に配信する構成を確立できた
4. **Aiven Managed Kafka 接続**: SSL 証明書（ca.pem / service.cert / service.key）を 1 行化して Heroku Config Vars に設定する手順を自動化できた
5. **プロファイル分離**: local-h2 / local-docker / heroku の 3 プロファイルで環境差分を適切に管理できた
6. **SonarQube 品質管理**: JaCoCo + sonar-scanner で継続的品質計測の基盤を整備し、Quality Gate PASS を達成した

#### プロセス的成功事項

1. **コミット規律**: Conventional Commits 形式（feat/fix/refactor/test/chore）を 36 コミット全てで遵守
2. **pre-commit フック**: Gradle check を自動実行することで壊れたコードのコミットを防止
3. **ADR 記録**: Heroku デプロイ構成・設定変数をドキュメント化し、意思決定の経緯を残せた

---

### Problem（問題・改善が必要なこと）

1. **全体カバレッジ 62.3%（目標 80% 未達）**: authms の JwtTokenProvider・JwtAuthenticationFilter・SecurityConfig・AuthController・UserRepositoryImpl が未テスト。セキュリティ層のテストは統合テスト環境が必要で工数見積もりが甘かった
2. **Spring Boot / Spring Cloud バージョン互換問題**: Spring Boot 3.5.3 + Spring Cloud 2025.0.0 の組み合わせで起動失敗。3.4.7 + 2024.0.1 への切り戻しに時間を要した
3. **SonarQube Gradle プラグイン + Gradle 9 非互換**: Gradle 9 の Convention API 削除により SonarQube Gradle プラグインが動作せず、sonar-scanner CLI 方式への切り替えが必要だった
4. **JaCoCo Java 25 非対応（0.8.12）**: toolVersion を 0.8.13 に更新するまでカバレッジレポートが生成されなかった
5. **Docker Compose ヘルスチェック設定の不安定**: ZooKeeper / Kafka のヘルスチェックが環境依存で失敗し、`restart: on-failure` に変更する対応が必要だった
6. **Heroku H2 初期ユーザーデータの BCrypt ハッシュ誤り**: ローカルと Heroku で使用するハッシュ値が異なり、ログイン不可問題が発生した

---

### Try（次イテレーションで試すこと）

| # | アクション | 期待効果 | 期限 |
|---|-----------|---------|------|
| T1 | authms セキュリティ層の統合テストを IT2 初日に追加（JwtTokenProvider・AuthController） | 全体カバレッジを 80% に到達させる | IT2 Day 1 |
| T2 | Spring Boot / Spring Cloud バージョン組み合わせを `libs.versions.toml` にコメントで記録し、更新前に互換性マトリクスを確認する | バージョン衝突による手戻り防止 | IT2 開始時 |
| T3 | Gradle / Java バージョンアップ時に JaCoCo・SonarQube プラグインの対応状況を事前確認する手順を CLAUDE.md に追記する | ツールチェーン非互換問題の早期検知 | IT2 開始時 |
| T4 | Docker Compose の依存サービスヘルスチェックを `condition: service_healthy` + `healthcheck` の組み合わせで再設計し、CI での安定性を確保する | ローカル開発環境の起動安定化 | IT2 中 |

---

## ベロシティ分析

### IT1 実績

| 項目 | 値 |
|------|-----|
| **計画 SP** | 10 |
| **完了 SP** | 10 |
| **ベロシティ** | 10 SP |
| **1 SP あたりの実績時間** | 約 4.9h（計画 49h/10SP） |
| **主な時間外コスト** | バージョン互換問題対応・SonarQube ツールチェーン設定 |

### 次イテレーションへの反映

- IT2 目標 SP: **10**（IT1 ベロシティを維持）
- バッファ: 技術的スパイクが発生しやすい序盤のため、US02〜US05 のうち 1 US をバッファに設定する

---

## 品質振り返り

### SonarQube メトリクス（IT1 完了時点）

| メトリクス | 値 | 評価 |
|-----------|-----|------|
| カバレッジ（全体） | 62.3% | △ 目標未達 |
| カバレッジ（新規コード） | 81.8% | ✅ 目標達成 |
| 重複率 | 0.0% | ✅ 目標達成 |
| Bug | 0 | ✅ |
| Vulnerability | 0 | ✅ |
| Code Smell | 0 | ✅ |
| **Quality Gate** | **PASS** | ✅ |

### 技術的負債

- authms セキュリティ層（JwtTokenProvider / JwtAuthenticationFilter / SecurityConfig / AuthController）のテストが未整備
- IT2 の最初のタスクとして対応を予定

---

## IT2 への引き継ぎ事項

### 持ち越しタスク

- [ ] authms セキュリティ層の単体テスト追加（カバレッジ 80% 達成のため）

### IT2 スコープ（予定）

| ID | ユーザーストーリー | SP |
|----|-------------------|-----|
| US02 | 荷主を登録する | 3 |
| US03 | 法人荷主を登録する | 3 |
| US04 | 貨物予約を登録する | 3 |
| US05 | 危険物・冷凍貨物の予約を登録する | 2 |
| **合計** | | **11（バッファ考慮で 10）** |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-22 | 初版作成 | k2works |
