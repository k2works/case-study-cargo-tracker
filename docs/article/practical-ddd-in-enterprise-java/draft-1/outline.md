---
type: Article
title: "エンタープライズ Java における実践的ドメイン駆動設計 — アウトライン"
description: "エンタープライズ Java における実践的 DDD（draft-1）の執筆計画。章構成・共通フォーマット・執筆順序。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-27T08:25:38Z }
---

# エンタープライズ Java における実践的ドメイン駆動設計 — アウトライン

```plantuml
@startmindmap

* 目次
left side
** ドメイン駆動設計
*** DDD の概念
**** 問題空間／ビジネスドメイン
**** サブドメイン／境界づけられたコンテキスト
*** ドメインモデル
**** 集約／エンティティオブジェクト／値オブジェクト
**** ドメインルール
**** コマンド／クエリ
**** イベント
**** サガ
*** まとめ
** Cargo Tracker
*** コアドメイン
*** Cargo Tracker: サブドメイン／境界づけられたコンテキスト
*** Cargo Tracker: ドメインモデル
**** 集約
**** 集約識別子
**** エンティティ
**** 値オブジェクト
*** Cargo Tracker: ドメインモデルの操作
*** サガ
*** ドメインモデルサービス
*** ドメインモデルサービス設計
*** Cargo Tracker: DDD 実装
*** まとめ
right side
** Cargo Tracker: Spring プラットフォーム
*** Spring プラットフォーム
**** Spring Boot: 機能
**** Spring Claud
**** Spring Framework のまとめ
*** モジュラーモノリスとしての Cargo Tracker
**** 境界づけられたコンテキスト
**** ドメインモデルの実装
**** ドメインモデルサービスの実装
**** 受信サービス
**** RESTful API
**** ネイティブ Web API
**** アプリケーションサービス
**** アプリケーションサービス：イベント
**** 送信サービス
**** 実装のまとめ
*** まとめ
** Cargo Tracker: Spring プラットフォーム
*** Spring プラットフォーム
**** Spring Boot: 機能
**** Spring Claud
**** Spring Framework のまとめ
*** EDA としての Cargo Tracker
**** 境界づけられたコンテキスト
***** 境界づけられたコンテキスト：パッケージング
***** 境界づけられたコンテキスト：パッケージ構造
***** interfaces
***** application
***** domain
***** infrastructure
***** Cargo Tracker の実装
***** ドメインモデル：実装
****** コアドメインモデル：実装
****** ドメインモデルの操作
****** コマンド
****** クエリ
****** ドメインイベント
***** ドメインモデルサービス
***** 送信サービス
***** 実装のまとめ
**** まとめ
** Cargo Tracker Spring プラットフォーム
*** イベントソーシング
*** CQRS
*** Axon Framework
**** Axon コンポーネント
**** Axon Framework のドメインモデルコンポーネント
**** Axon のインフラストラクチャコンポーネント：Axon Server
*** CQRS/ES としての Cargo Tracker
**** Axon を用いた境界づけられたコンテキスト
**** 境界づけられたコンテキスト：成果物作成
**** 境界づけられたコンテキスト：パッケージ構造
**** Axon を用いたドメインモデルの実装
**** 実装のまとめ
**** Axon を用いたドメインモデルサービスの実装
**** 受信サービス
**** アプリケーションサービス
*** まとめ

@endmindmap
```

## 1. 記事の位置づけ

| 項目 | 内容 |
| :--- | :--- |
| タイトル | エンタープライズ Java における実践的ドメイン駆動設計 |
| 題材 | Cargo Tracker（国際貨物輸送管理） |
| 主眼 | DDD の概念を Cargo Tracker 実装へ落とし、Spring Platform で段階的に実装戦略を比較する |
| 想定読者 | DDD の基礎を知っており、Java/Spring で実装判断を具体化したい開発者 |

## 2. 章構成（章別計画）

| 章 | 仮ファイル名 | 主題 | この章で示すこと |
| :--- | :--- | :--- | :--- |
| 第 1 章 | `01-ddd-fundamentals.md` | ドメイン駆動設計 | 問題空間、サブドメイン、境界づけられたコンテキスト、集約、イベント、サガの最小整理 |
| 第 2 章 | `02-cargo-domain-model.md` | Cargo Tracker のドメインモデル | コアドメイン、集約識別子、エンティティ、値オブジェクト、ドメインサービス設計 |
| 第 3 章 | `03-spring-modular-monolith.md` | Spring Platform × モジュラーモノリス | BC 単位の実装、受信/送信サービス、RESTful API、アプリケーションサービス |
| 第 4 章 | `04-spring-eda.md` | Spring Platform × EDA | パッケージ構造（interfaces/application/domain/infrastructure）とドメインイベント中心の実装 |
| 第 5 章 | `05-spring-cqrs-es-axon.md` | Spring Platform × CQRS/ES（Axon） | Event Sourcing、CQRS、Axon コンポーネントによるモデル/サービス実装 |
| 第 6 章 | `06-conclusion.md` | まとめ | 3 つの実装アプローチ（モノリス/EDA/CQRS-ES）の使い分け指針 |

## 3. 各章の共通フォーマット

1. 章のゴール
2. 設計（DDD 観点）
3. 実装（コード/パッケージ/責務分離）
4. トレードオフ（採用理由と非採用理由）
5. 章末まとめ

## 4. 執筆順序

1. 第 1 章（DDD の前提）
2. 第 2 章（Cargo Tracker 固有モデル）
3. 第 3〜5 章（実装アプローチ比較）
4. 第 6 章（比較結果と選択指針）

## 5. 各章の詳細展開

### 第 1 章：ドメイン駆動設計

- DDD の概念
  - 問題空間／ビジネスドメイン
  - サブドメイン／境界づけられたコンテキスト
- ドメインモデル
  - 集約／エンティティオブジェクト／値オブジェクト
  - ドメインルール
  - コマンド／クエリ
  - イベント
  - サガ
- まとめ

### 第 2 章：Cargo Tracker のドメインモデル

- コアドメイン
- Cargo Tracker: サブドメイン／境界づけられたコンテキスト
- Cargo Tracker: ドメインモデル
  - 集約
  - 集約識別子
  - エンティティ
  - 値オブジェクト
- Cargo Tracker: ドメインモデルの操作
- サガ
- ドメインモデルサービス
- ドメインモデルサービス設計
- Cargo Tracker: DDD 実装
- まとめ

### 第 3 章：Spring Platform × モジュラーモノリス

- Spring プラットフォーム
  - Spring Boot: 機能
  - Spring Framework のまとめ
- モジュラーモノリスとしての Cargo Tracker
  - 境界づけられたコンテキスト
  - ドメインモデルの実装
  - ドメインモデルサービスの実装
  - 受信サービス
  - RESTful API
  - ネイティブ Web API
  - アプリケーションサービス
  - アプリケーションサービス：イベント
  - 送信サービス
  - 実装のまとめ
- まとめ

### 第 4 章：Spring Platform × EDA

- Spring プラットフォーム
  - Spring Boot: 機能
  - Spring Claude
  - Spring Framework のまとめ
- EDA としての Cargo Tracker
  - 境界づけられたコンテキスト
    - 境界づけられたコンテキスト：パッケージング
    - 境界づけられたコンテキスト：パッケージ構造
      - interfaces
      - application
      - domain
      - infrastructure
  - Cargo Tracker の実装
  - ドメインモデル：実装
    - コアドメインモデル：実装
    - ドメインモデルの操作
      - コマンド
      - クエリ
      - ドメインイベント
  - ドメインモデルサービス
  - 送信サービス
  - 実装のまとめ
- まとめ

### 第 5 章：Spring Platform × CQRS/ES（Axon）

- イベントソーシング
- CQRS
- Axon Framework
  - Axon コンポーネント
  - Axon Framework のドメインモデルコンポーネント
  - Axon のインフラストラクチャコンポーネント：Axon Server
- CQRS/ES としての Cargo Tracker
  - Axon を用いた境界づけられたコンテキスト
  - 境界づけられたコンテキスト：成果物作成
  - 境界づけられたコンテキスト：パッケージ構造
  - Axon を用いたドメインモデルの実装
  - 実装のまとめ
  - Axon を用いたドメインモデルサービスの実装
  - 受信サービス
  - アプリケーションサービス
- まとめ

### 第 6 章：まとめ

- 3 つの実装アプローチの比較
  - モジュラーモノリス
  - EDA
  - CQRS/ES（Axon）
- 適用判断の観点
  - チーム規模と運用コスト
  - 境界づけられたコンテキストの独立性
  - 同期／非同期の整合性要件
- 次の実装ステップ
