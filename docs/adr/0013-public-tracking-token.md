# ADR-0013: 公開追跡照会の時限署名トークン（JWT）採用

US18（追跡情報の公開照会）では、荷主・荷受人がメール内 URL からログイン不要で追跡情報を確認できる必要がある。一方で、追跡番号のみを認証要素にすると総当たり攻撃で他者の貨物情報が露見するリスクがある。本 ADR で **「JWT (HS256) の時限署名トークンを URL クエリパラメータで渡し、Spring Security の filter で署名・期限・追跡番号一致を検証する」** 方針を採用する。

日付: 2026-05-29

## ステータス

提案中（IT6 着手前）

## コンテキスト

US18 の受入基準は user_story.md L390 で以下が定義されている。

- 追跡番号を入力して貨物情報を照会できる
- 現在の状態・位置（港湾名）・推定到着日が表示される
- 追跡イベント履歴（日時・場所・作業種別）が時系列で表示される
- 追跡番号が存在しない場合、「追跡番号が見つかりません」と表示される
- **ログインなしでも追跡番号があれば照会できる**

ui_design.md L727-739 でアクセス方式が規定されている。

- トークン形式: JWT (HS256)、Claims = `tn`（追跡番号）/ `sub`（荷主 ID or 荷受人 ID）/ `exp`（有効期限）/ `iat`（発行時刻）/ `role`（SHIPPER or CONSIGNEE）
- 署名鍵: AWS Secrets Manager 管理、四半期ローテーション（本 ADR では IT8 切替方針として継承）
- 有効期限: 30 日（配送完了後 7 日で自動失効）
- レート制限: 同一 IP から 60 req/min、超過 429（本 ADR では IT8 切替方針として継承）
- 検証失敗時: 403 ページ

domain-model.md L711-725 で `TrackingTokenService` ドメインサービス / `JwtToken` / `VerifiedToken` の型定義は既存。

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **JWT (HS256) 時限署名トークン（採用）** | ステートレス・検証高速・標準準拠・ライブラリ豊富（jjwt） | 鍵管理が必要・JWT 取り消しが期限切れまで効かない |
| トークンなし（追跡番号のみ）| 実装最小 | 推測攻撃に脆弱（36^10 = 3.6×10^15 だが鍵長に劣る）・他者貨物の露見リスク |
| Basic Auth | 標準サポート | 荷主の認証情報管理負担・パスワード管理運用が重い・ログイン要件と矛盾 |
| OAuth (Authorization Code) | 標準フロー | オーバースペック・荷主がアカウント登録する必要がある |
| 短期 URL（TinyURL 型）| URL がシンプル | サーバー側にトークンストアが必要・スケール時の負荷 |

## 決定

**JWT (HS256) を時限署名トークンとして採用し、URL クエリパラメータ（`?token=<JWT>`）で公開エンドポイントに渡す。Spring Security `permitAll` + 専用 `PublicTrackingTokenFilter` で署名・期限・`tn` claim 一致を検証する。**

具体的には以下のとおりとする。

### 1. トークン仕様

| 項目 | 値 |
|------|-----|
| アルゴリズム | HS256（HMAC-SHA256） |
| 鍵長 | 最低 32 バイト |
| Claims | `iss = "trackingms"`、`aud = "tracking.public"`、`sub = subjectId（荷主 ID or 荷受人 ID）`、`tn = trackingNumber`、`role = SHIPPER \| CONSIGNEE`、`exp`、`iat` |
| 有効期限 | 発行時点から **30 日**。配送完了済みの場合は `deliveredAt + 7 日` で頭打ち |
| ライブラリ | jjwt 0.12+ |

### 2. 発行エンドポイント

| メソッド | パス | 認証 |
|---------|------|------|
| POST | `/api/v1/tracking/{trackingNumber}/token?role={SHIPPER\|CONSIGNEE}&subjectId={id}` | ROLE_TRACKER + ROLE_ADMIN |

レスポンスは `{ token, validUntil }` を返す。実運用ではこのトークンを NotificationAcl が荷主・荷受人向けメールに URL として埋め込む。

### 3. 検証エンドポイント

| メソッド | パス | 認証 |
|---------|------|------|
| GET | `/api/v1/public/tracking/{trackingNumber}?token=<JWT>` | **permitAll**（PublicTrackingTokenFilter で検証）|

Spring Security では `/api/v1/public/**` を permitAll に設定し、`PublicTrackingTokenFilter`（OncePerRequestFilter）で以下を順次検証する。

1. クエリパラメータ `token` が存在する
2. JWT 署名が正しい
3. `exp` が未来である
4. `aud` が `tracking.public` である
5. `tn` claim がパス変数 `{trackingNumber}` と完全一致する

検証失敗時はすべて **HTTP 403 Forbidden**（ui_design.md L738 準拠）を返す（リソース存在の秘匿のため 401 ではなく 403）。

### 4. 鍵管理（IT6 暫定 / IT8 本格）

| 段階 | 方式 | 切替時期 |
|------|------|----------|
| IT6 暫定 | Heroku Config Vars / 環境変数 `tracking.public-token.secret`（32 バイト以上）| IT6 |
| IT8 本格 | AWS Secrets Manager + 四半期ローテーション（ui_design.md L734 準拠）| IT8（ADR-0015 として別途起票予定）|

authms の `JwtTokenProvider` とは **別鍵**で運用し、認証 JWT と公開照会 JWT を完全分離する（鍵漏洩時の影響範囲限定）。

### 5. レート制限（IT6 暫定 / IT8 本格）

| 段階 | 方式 | 切替時期 |
|------|------|----------|
| IT6 暫定 | Heroku 標準制限のみ | IT6 |
| IT8 本格 | アプリ層 Bucket4j または リバースプロキシ層で 60 req/min（ui_design.md L739 準拠）| IT8（ADR-0015 と統合）|

## 影響

### 適用対象

- **trackingms**: `TrackingTokenService` 実装（domain/services）、`PublicTrackingTokenFilter` 追加（infrastructure/security）、`PublicTrackingController` 追加（interfaces/rest）、`SecurityConfig` で `/api/v1/public/**` を permitAll
- **shared**: 不要（公開エンドポイントは trackingms 内で完結）
- **frontend**: `/tracking/:trackingNumber?token=<JWT>` パブリックルート追加（PrivateRoute 除外）
- **domain-model.md**: `TrackingTokenService.verify(token, deliveredAt)` を `verify(token, expectedTrackingNumber)` に更新（IT6 完了時）

### 受け入れテスト

- TrackingTokenService 単体テストで issue() / verify() の正常系・期限切れ・tn 不一致・署名不正の 4 ケースを検証
- PublicTrackingController 統合テストで 200 / 403 を Spring Security 経由で確認
- E2E（Playwright）でブラウザがメール URL を開いて追跡情報表示 + トークン期限切れで 403 アラート表示

### セキュリティ

- 鍵長 32 バイト未満は起動時に拒否（fail fast）
- Bouncy Castle 不使用（HS256 は JCE 標準で十分）
- HTTPS 強制（Spring Security `requiresChannel().anyRequest().requiresSecure()` を Heroku 設定で有効化）

## コプライアンス

- 新規公開エンドポイント追加時、本 ADR の 5 規約をレビューチェックリストに含める
- IT8 で AWS Secrets Manager 移行する際は新規 ADR を起票して上書きする

## 備考

- 著者: k2works (IT6 計画時)
- 関連 Issue: take-5 US18
- 参照: ui_design.md L727-739、domain-model.md L711-725、iteration_plan-6.md タスク 0.3
