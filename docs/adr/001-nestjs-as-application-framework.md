# ADR-001: アプリケーションフレームワークとして NestJS を採用する

バックエンドのアプリケーションフレームワークに NestJS 11（Node.js 24.18 LTS / TypeScript 5.x）を採用する。

日付: 2026-07-27

## ステータス

承認済み

## コンテキスト

本プロジェクトは、Java / Spring Boot 版として設計された国際貨物輸送管理システム（Cargo Tracker）を TypeScript で実装するケーススタディである。参照元設計は DDD + ヘキサゴナルアーキテクチャ + CQRS（7 コンテキスト + Shared Kernel）を Spring のエコシステム（DI、Spring MVC、Spring Events、Spring Security）で実現しており、TypeScript 版でも同じアーキテクチャ思想を自然に表現できるフレームワークが必要である。

- DI コンテナとモジュールシステムによる疎結合な構成が必須（ヘキサゴナルのポート注入）
- コンテキスト間連携のための同一プロセス内イベント機構が必要
- セッションベース認証 + RBAC（6 ロール）の実装が必要
- Spring 版の設計ドキュメント・移行マッピングとの対応関係を明確に保ちたい

## 決定

NestJS 11 を採用する。Spring の主要概念と 1 対 1 に近い対応（DI = Provider/Module、Spring MVC = Controller、Spring Events = @nestjs/event-emitter、Spring Security = Guard + Passport、Bean Validation = class-validator）があり、Spring → NestJS 移行マッピング（architecture_backend.md）として体系的に記録できる。

### 変更箇所

- `apps/cargo-tracker/src/contexts/<context>/{domain,application,infrastructure,presentation}` のディレクトリ規約で実装する
- domain 層には NestJS デコレータを持ち込まない（dependency-cruiser で強制）

### 代替案

- **Express / Hono + 手組み DI**: 軽量だが DI・モジュール境界・Guard に相当する仕組みを自作することになり、7 コンテキストの規模では構造の一貫性を保ちにくい。却下
- **Next.js（フルスタック）**: React SSR には適するが、DDD のレイヤ分離・ポート注入・イベント機構を表現する骨格がなく、バックエンド中心の本システムには不適合。却下

## 影響

### ポジティブ

- Spring 版設計との概念対応が明確で、設計ドキュメントの移植・保守が容易
- DI により Port（インターフェース）差し替えが自然に書け、テスト容易性が高い
- Guard / Interceptor / ValidationPipe など横断的関心事の定型化

### ネガティブ

- デコレータ・メタデータ依存が強く、domain 層への混入を規律（dependency-cruiser）で防ぐ必要がある
- Express ベースの素朴な構成よりも学習コスト・抽象レイヤが増える

## コンプライアンス

- dependency-cruiser ルール「domain パッケージが `@nestjs/*` を import しない」を CI で検証する
- アーキテクチャ構成が architecture_backend.md のディレクトリ規約に従っていることをコードレビューで確認する

## 備考

- 著者: k2works
- 関連コミット: f3a984ce
- 関連 ADR: ADR-002、ADR-003、ADR-005
