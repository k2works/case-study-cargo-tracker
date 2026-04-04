# ADR-008: ローカル SonarQube で品質ゲートを管理する

ローカル環境に SonarQube Community Build を Docker で構築し、コード品質の継続的計測と Quality Gate を運用する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

コード品質（Bug・Vulnerability・Code Smell・カバレッジ・重複率）を継続的に計測し、品質基準を自動判定する仕組みが必要である。

- Checkstyle / SpotBugs は個別のルール検証に留まり、総合的な品質判定ができない
- SonarCloud は外部サービスのため、オフライン環境や無料プランの制約がある
- ローカルで品質をリアルタイムに確認しながら開発したい

## 決定

**SonarQube Community Build を Docker Compose でローカルに構築し、Gradle sonarqube プラグインでスキャンを実行する。**

### 変更箇所

- `build.gradle` に `org.sonarqube` プラグイン 6.3.1 を追加
- `build.gradle` に `sonar` プロパティ（projectKey、JaCoCo レポートパス等）を設定
- `sonarqube.config.json` で cargo-tracker プロジェクトのスキャン設定を定義
- `ops/docker/sonarqube-local/docker-compose.yml` で SonarQube + PostgreSQL コンテナを管理

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| SonarCloud | オフライン環境で使えない、無料プランの制約 |
| Checkstyle + SpotBugs のみ | 総合的な品質判定・カバレッジ計測ができない |
| CodeClimate | SonarQube の方が Java エコシステムとの統合が深い |

## 影響

### ポジティブ

- Quality Gate で Bug 0 / Vulnerability 0 / カバレッジ 80% 等の基準を自動判定できる
- Gulp タスク（`npx gulp sonar-local:scan`、`sonar-local:gate`、`sonar-local:issues`）で簡単に操作できる
- JaCoCo のカバレッジデータを SonarQube に統合できる

### ネガティブ

- Docker Desktop が必要（RAM 4GB 以上推奨）
- 初回起動に数分かかる

## コンプライアンス

- `npx gulp sonar-local:gate` で Quality Gate が PASS であること
- `npx gulp sonar-local:issues` で未解決イシューが 0 件であること

## 備考

- 関連コミット: `831eac0`
