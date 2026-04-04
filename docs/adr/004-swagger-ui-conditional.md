# ADR-004: Swagger UI を環境変数で条件付き有効化する

springdoc-openapi 3.0.2 を導入し、`app.openapi.enabled` プロパティで環境別に Swagger UI を制御する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

REST API の開発・テスト効率を向上させるため、API ドキュメント自動生成ツールが必要である。

- 開発環境では Swagger UI で API を直接テストしたい
- 本番環境では Swagger UI を無効化してセキュリティリスクを排除したい
- Spring Security との統合が必要（認証なしでアクセス可能にする）

## 決定

**springdoc-openapi 3.0.2 を導入し、`app.openapi.enabled` プロパティで環境別に有効/無効を切り替える。**

### 変更箇所

- `build.gradle` に `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2` を追加
- `OpenApiConfig.java` に `@ConditionalOnProperty(prefix = "app.openapi", name = "enabled", havingValue = "true")` を設定
- `SecurityConfig.java` で `openApiEnabled` フラグに基づき Swagger UI パスを permitAll
- `application.yml` に `app.openapi.enabled: true`（開発）
- `application-product.yml` に `app.openapi.enabled: false`（本番）

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Spring プロファイルで制御 | プロパティベースの方が柔軟（環境変数で上書き可能） |
| Swagger UI を常時有効 | 本番環境でのセキュリティリスク |
| API ドキュメントなし | 開発効率の低下 |

## 影響

### ポジティブ

- 開発時に `http://localhost:8080/swagger-ui/index.html` で API を確認・テストできる
- 本番では自動的に無効化されるためセキュリティリスクがない

### ネガティブ

- springdoc-openapi の依存関係が追加される

## コンプライアンス

- 開発環境: `/swagger-ui/index.html` が 200 OK を返すこと
- product プロファイル: `/swagger-ui/index.html` がアクセス不可であること

## 備考

- 関連コミット: `cab0cb8`
- 関連 ADR: ADR-001
