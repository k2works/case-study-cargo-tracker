---
title: "ADR-001: Java 25 / Spring Boot 4.0 採用と移行ロードマップ"
description: Java 25 LTS と Spring Boot 4.0 を採用し、エコシステム未成熟時の退路として Java 21 LTS + Spring Boot 3.4.x スタートを定義する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: adr, java, spring-boot, versioning
---

# ADR-001: Java 25 / Spring Boot 4.0 採用と移行ロードマップ

Java 25 LTS と Spring Boot 4.0 を正式採用し、エコシステムが未成熟な場合は Java 21 LTS + Spring Boot 3.4.x でスタートして Spring Boot 4.0 GA 後に移行する段階的ロードマップを定義する。

日付: 2026-03-31

## ステータス

承認済み

## コンテキスト

本プロジェクトは Jakarta EE 実装（DDD ケーススタディ）を Spring Boot で再実装するケーススタディである。以下の状況が意思決定の背景となった。

- **Java のリリースモデル**: Java は 6 ヵ月サイクルでリリースされ、LTS は 2 年ごと（Java 21: 2023-09、Java 25: 2025-09）。
- **Spring Boot 4.0**: 2025 年後半に GA リリース。Jakarta EE 11 対応・JSpecify null safety 強化・Spring Framework 7.x を前提とする。Spring Boot 4.0 は Java 25 を minimum required JVM とする。
- **エコシステムの成熟度リスク**: リリース直後のメジャーバージョンは、サードパーティライブラリ（MyBatis、Flyway、Testcontainers 等）の対応状況にラグが生じることがある。
- **開発効率の要件**: ケーススタディとして DDD + ヘキサゴナル + CQRS を Spring Boot で実装するため、最新の自動構成機能（`@ServiceConnection`、Virtual Threads 等）を活用したい。

**検討した Java バージョンの選択肢**:

| バージョン | LTS | Spring Boot 4 対応 | エコシステム成熟度 |
|---|---|---|---|
| Java 21 | ○（2028 年まで） | Spring Boot 3.x まで | 成熟 |
| Java 25 | ○（2031 年まで） | Spring Boot 4.0 対応 | 成長中 |
| Java 24 | ✗（短期） | 非推奨 | 非推奨 |

## 決定

**Java 25 LTS + Spring Boot 4.0.x を正式採用する。**

採用の根拠：

- Java 25 は LTS であり、2031 年まで Oracle Premier Support が提供される
- Spring Boot 4.0 は Virtual Threads（Project Loom）の正式統合により IO バウンドな Web アプリケーションのスループットが向上する
- Jakarta EE 11 / Spring Framework 7.x による JSpecify null safety 強化は、ドメインモデルの型安全性向上に寄与する
- Testcontainers 2.x、MyBatis Spring Boot Starter 4.0.1 の Spring Boot 4 対応が確認済み

### 段階的移行ロードマップ

エコシステムの未成熟リスクへの対応として、以下の退路（フォールバック）を定義する。

```
Phase 1（フォールバック時）: Java 21 LTS + Spring Boot 3.4.x でスタート
  ↓ Spring Boot 4.0 GA 安定確認後（GA 後 3 ヵ月目安）
Phase 2: Java 25 LTS に JVM 切り替え
  ↓ 依存ライブラリの Spring Boot 4 対応確認後
Phase 3: Spring Boot 4.0.x へ移行（依存変更・自動構成見直し）
  ↓ テスト全通過確認後
Phase 4: Virtual Threads 対応・JSpecify null safety 段階適用
```

### 変更箇所

| ファイル | 変更内容 |
|---|---|
| `build.gradle` | `java.toolchain.languageVersion = JavaLanguageVersion.of(25)` |
| `build.gradle` | `id 'org.springframework.boot' version '4.0.x'` |
| `docker-compose.yml` | `eclipse-temurin:25-jre-alpine` |
| `Dockerfile` | `FROM gradle:9-jdk25-alpine` / `FROM eclipse-temurin:25-jre-alpine` |
| CI ワークフロー | `java-version: '25'` |

### 代替案

| 代替案 | 却下理由 |
|---|---|
| **Java 21 LTS + Spring Boot 3.4.x のみ** | Virtual Threads 正式統合・JSpecify null safety の恩恵を受けられない。ケーススタディとして最新 Spring Boot の実装パターン習得が目的の一つであるため |
| **Java 24（非 LTS）+ Spring Boot 4.0** | 非 LTS バージョンはサポート期間が 6 ヵ月であり、本番利用に不適切 |
| **Jakarta EE 11 + Payara/WildFly** | Jakarta EE 参考実装の移植先として Spring Boot を選択した（元の決定）。本 ADR の範囲外 |

## 影響

### ポジティブ

- Java 25 LTS により 2031 年まで長期安定したプラットフォームを確保できる
- Virtual Threads による同時接続性能の向上（Tomcat スレッドプールが不要になりスループット改善）
- Spring Boot 4 の `@ServiceConnection` による Testcontainers 統合がシンプルになる
- JSpecify `@NonNull` / `@Nullable` による null 安全性の向上でドメイン層のバグが減少する

### ネガティブ

- Spring Boot 4.0 は 2026 年時点でリリース直後であり、コミュニティ Q&A（Stack Overflow 等）が Spring Boot 3.x より少ない
- Jakarta EE 11 の `javax.*` → `jakarta.*` パッケージ移行が完了していない社内共有ライブラリがある場合、依存関係の調整が必要
- フォールバック（Phase 1）を選択した場合、後日の移行コストが発生する（依存ライブラリの変更・自動構成の見直し等）

## コンプライアンス

以下を確認することで決定が正しく実装されていることを確認する。

- `./gradlew -version` の出力で JVM バージョンが 25.x であること
- `./gradlew bootRun` 起動ログに `Spring Boot 4.0.x` が表示されること
- CI の Java バージョンマトリクスに Java 25 が含まれること
- 全単体テスト・統合テストが Java 25 環境で通過すること

## 備考

- 著者: Project Team
- 関連ドキュメント: `docs/design/tech_stack.md`（バージョン採用方針）
- 関連 ADR: なし（本 ADR が初番）
