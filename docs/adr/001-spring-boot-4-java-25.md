# ADR-001: Spring Boot 4.0.5 + Java 25 を採用する

Spring Boot 4.0.5 と Java 25 をアプリケーションフレームワーク・言語バージョンとして採用する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

国際貨物輸送管理システムの開発にあたり、アプリケーションフレームワークと Java バージョンを選定する必要がある。

- Spring Boot 4.0.5 は Spring Framework 7.x をベースとし、Jakarta EE 11 に対応している
- Java 25 は最新の GA リリースであり、LTS 候補として位置付けられている
- DDD + ヘキサゴナルアーキテクチャの実装に Spring エコシステムが適合する

## 決定

**Spring Boot 4.0.5 + Java 25** を採用する。

### 変更箇所

- `apps/cargo-tracker/build.gradle` で `org.springframework.boot` version `4.0.5`、Java toolchain `25` を設定

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Spring Boot 3.4.x + Java 21 LTS | 安定性は高いが、Spring Boot 4.0 の新機能（spring-boot-h2console 等）が使えない |
| Quarkus / Micronaut | Spring エコシステムとの統合やチームの習熟度を考慮し見送り |

## 影響

### ポジティブ

- Spring Boot 4.0 の最新機能を活用できる（H2 Console モジュール、Jakarta EE 11 ��応等）
- Java 25 の言語機能を利用できる

### ネガティブ

- SpotBugs が Java 25 のクラスファイル（major version 69）を未サポート → `ignoreFailures = true` で対応
- Byte Buddy が Java 25 の ClassFileVersion を解決できない → `-Dnet.bytebuddy.experimental=true` で対応
- 一部のライブラリで互換性問題が発生する可能性���ある

## コンプライアンス

- `./gradlew build` が BUILD SUCCESSFUL で完了すること
- `./gradlew bootRun` でアプリケーションが起動し、`/actuator/health` が `{"status":"UP"}` を返すこと

## 備考

- 関連コミット: `0eec9a8`, `cab0cb8`
