# ADR-0013: Tracking Number JWT 時限トークン設計

US18（追跡情報を照会する）で公開 URL `/tracking/{trackingNumber}?token=<JWT>` から荷主・荷受人がログイン不要で貨物追跡情報を照会できるようにするため、追跡番号と時限的にバインドされた JWT トークンの形式・署名方式・失効ルール・再発行フローを確定する。

日付: 2026-05-18

## ステータス

提案

## コンテキスト

IT6 で `trackingms` を新設し US18「追跡情報を照会する」を実装する。

`docs/requirements/user_story.md` US18 受入基準より:

- 受入 5: **荷主・荷受人へのメール通知に含まれる時限署名トークン付き URL（`/tracking/{tn}?token=<JWT>`）から照会できる**（システム内ログイン不要）
- 受入 6: **トークンは有効期限 30 日、配送完了後 7 日で自動失効する**
- 受入 7: **トークン検証失敗時は「リンクの有効期限が切れています」と表示し営業担当者連絡を促す**

加えて、IT4 リリース計画のマイルストーン M3「Tracking Number フォーマット決定 ADR」を本 ADR で吸収する（IT4 時点では JWT 化の判断材料が不足していたため IT6 へ持ち越し）。

### 検討すべき設計論点

1. **トークン形式**: JWT（ステートレス）か DB 永続化トークン（ステートフル）か
2. **署名アルゴリズム**: HMAC-SHA256（共通鍵）か RS256（公開鍵）か
3. **秘密鍵の管理**: `authms` と共通鍵を共有するか、`trackingms` 専用鍵を発行するか
4. **失効ルール**: `exp`（発行時 + 30 日）のみか、配送完了後 7 日も加味するか
5. **再発行フロー**: 期限切れ時に営業担当者が手動で再発行するか、自動再発行とするか
6. **クエリパラメタ vs Authorization ヘッダ**: 公開メール URL であるため `Authorization` ヘッダ付与は不可能。クエリパラメタが必須

### 既存実装との関係

- `authms` は `JWT_SECRET` 環境変数を Heroku Config Vars 経由で受け取り、HMAC-SHA256（jjwt ライブラリ）でログイン JWT を発行している（IT4 実装）
- `bookingms` の `Cargo.tracking_number` は IT4 で UUID v4 形式（既存）
- フロントエンド `/tracking/:trackingNumber` ルートは IT6 で新規作成

## 決定

### 1. トークン形式: JWT（ステートレス）

JWT を採用する。

**採用理由**:

- ステートレスにより `trackingms` の Read DB 負荷を最小化（DB 検索不要）
- 暗号学的に追跡番号と有効期限がバインドされており改ざん耐性がある
- `authms` で既に jjwt ライブラリの運用ノウハウがあり学習コストが低い

**未採用案**:

| 案 | 不採用理由 |
|----|-----------|
| DB 永続化トークン | JWT のステートレスメリットを失う。トークン検証ごとに DB アクセスが発生し追跡照会のレイテンシ悪化 |
| 永続トークン（再発行不可・期限なし） | 受入 6（30 日失効）と矛盾。漏洩時の対応コスト高 |
| クエリパラメタの追跡番号のみ（トークン無し） | 受入 5・受入 7 と矛盾。総当たりで他人の貨物を照会可能になりプライバシー違反 |

### 2. JWT クレーム

```json
{
  "tn": "{trackingNumber}",
  "exp": 1759622400,
  "iss": "case-study-cargo-tracker",
  "sub": "tracking-public-link",
  "iat": 1757030400
}
```

| クレーム | 型 | 説明 |
|---------|-----|------|
| `tn` | String | 追跡番号（UUID v4 文字列）。Custom claim |
| `exp` | NumericDate (epoch seconds) | 有効期限（発行時 + 30 日） |
| `iss` | String | 発行者識別子。固定値 `case-study-cargo-tracker` |
| `sub` | String | 用途識別子。固定値 `tracking-public-link`。`authms` の通常 JWT（`sub: user-login`）と区別する |
| `iat` | NumericDate | 発行時刻 |

### 3. 署名アルゴリズム: HMAC-SHA256

`authms` と同一秘密鍵 `JWT_SECRET` を `Heroku Config Vars` 経由で `trackingms` に注入する。

**採用理由**:

- `authms` で既に運用中のため鍵管理プロセスを再利用可能
- 共通鍵方式はマイクロサービス間で鍵を共有しやすい
- RS256（公開鍵方式）は鍵ローテーションが複雑化するため、本プロジェクト規模では HMAC-SHA256 で十分

**運用上の注意**:

- `authms` で発行された通常ログイン JWT と区別するため、`sub` クレームで識別する（`tracking-public-link` vs `user-login`）
- 秘密鍵漏洩時は両サービスで同時にローテーションが必要

### 4. 失効ルール

`TrackingTokenService.verify(jwt)` の検証ロジック:

```
1. JWT 署名検証（HMAC-SHA256, JWT_SECRET）
2. exp クレームの検証（現在時刻 < exp）
3. sub クレームの検証（sub == "tracking-public-link"）
4. tn クレームから追跡番号を抽出
5. tracking_summary テーブルから delivered_at を取得
6. delivered_at が非 NULL かつ (delivered_at + 7 日) < 現在時刻 ならば TOKEN_EXPIRED として失効
7. 上記すべてを通過した場合のみトークン有効
```

実効的有効期限は `min(exp, delivered_at + 7 days)` となる。

**採用理由**:

- 配送完了後の追跡情報照会は通常 1 週間以内に完結するため、それを超える長期間の URL 漏洩リスクを軽減
- 通常運用では `exp` が先に到来し、配送長期化時のみ `delivered_at + 7 days` が制約となる

### 5. 再発行フロー

営業担当者が S10（予約詳細）から手動で再発行する。

```
1. 営業担当者が S10 で「追跡トークン発行（メール送信）」ボタンを押下
2. フロントエンドが管理者 JWT 付きで POST /api/v1/tracking/_internal/issue-token を呼び出す
3. trackingms が新規 JWT を発行（exp = 現在時刻 + 30 日）
4. レスポンスとして { url: "/tracking/{tn}?token=<JWT>", validUntil: <ISO8601> } を返却
5. 営業担当者がメールクライアントから URL を送付（IT6 ではメール送信は手動。自動化は将来検討）
```

`POST /api/v1/tracking/_internal/issue-token` は管理者認証必須（Spring Security `@PreAuthorize`）。

### 6. エラーレスポンス

| シナリオ | HTTP ステータス | `errorCode` | フロント表示 |
|---------|---------------|-------------|------------|
| 署名検証失敗（改ざん・偽造） | 401 | `TOKEN_INVALID` | `alert-danger`「リンクが不正です」 |
| `exp` 経過 | 403 | `TOKEN_EXPIRED` | `alert-warning`「リンクの有効期限が切れています」 |
| `delivered_at + 7d` 経過 | 403 | `TOKEN_EXPIRED` | `alert-warning`「リンクの有効期限が切れています」 |
| `tn` クレーム不一致（URL パスと JWT 内 `tn` が異なる） | 400 | `TOKEN_TN_MISMATCH` | `alert-danger`「リンクが不正です」 |
| `tn` クレームに対応する追跡情報が存在しない | 404 | `TRACKING_NOT_FOUND` | `alert-danger`「追跡情報が見つかりません」 |

エラー時は受入 7 に従い「営業担当者にお問い合わせください」を促すリンクを表示する。

### 7. ドメイン配置

`TrackingTokenService` は `trackingms.domain.services` に配置する。

| 責務 | クラス | 配置 |
|------|--------|------|
| トークン発行・検証のドメインルール | `TrackingTokenService` (interface) | `trackingms/domain/services` |
| jjwt 実装 | `JwtTrackingTokenService` | `trackingms/infrastructure/security` |
| `JwtToken` 値オブジェクト | `JwtToken` (record) | `trackingms/domain/model/valueobjects` |

`JwtToken` は不変値オブジェクトとして文字列形式の JWT をラップする。

### 8. 環境変数

| 変数名 | 説明 | 既存/新規 |
|-------|------|---------|
| `JWT_SECRET` | HMAC-SHA256 秘密鍵（最低 256 ビット） | 既存（authms と共有） |
| `TRACKING_TOKEN_EXPIRATION_DAYS` | 有効期限日数（既定 30） | 新規 |
| `TRACKING_TOKEN_GRACE_DAYS` | 配送完了後の追加有効期間（既定 7） | 新規 |

`application.yml` で `cargo-tracker.tracking.token.*` 配下に読み込む。

## 影響

### 採用される構成

| 観点 | 設計 |
|------|------|
| トークン形式 | JWT (RFC 7519) |
| 署名アルゴリズム | HMAC-SHA256 (jjwt 0.12.x) |
| 秘密鍵 | `authms` と共通鍵 `JWT_SECRET` を Heroku Config Vars で共有 |
| 失効ルール | `min(exp, delivered_at + 7 days)` |
| 再発行 | 営業担当者が S10 から `POST /api/v1/tracking/_internal/issue-token` で手動再発行 |
| エラー識別 | `errorCode` で TOKEN_INVALID / TOKEN_EXPIRED / TOKEN_TN_MISMATCH / TRACKING_NOT_FOUND を区別 |

### 利点

1. **ステートレス**: `trackingms` Read DB へのトークン検証アクセスが不要（`delivered_at` 取得のみ）
2. **既存運用ノウハウ再利用**: `authms` で運用中の `JWT_SECRET` 管理プロセスを再利用
3. **暗号学的バインド**: 追跡番号と有効期限がトークンに含まれ改ざん不可
4. **再発行容易性**: 期限切れ時は営業担当者が S10 から即座に再発行可能

### トレードオフ

1. **秘密鍵漏洩時の影響範囲拡大**: `authms` と共通鍵のため両サービスで同時ローテーションが必要
2. **失効リスト無し**: 漏洩時にステートレス JWT を即時失効できない（`exp` 到来まで有効）。次イテレーションで失効リスト導入を検討
3. **メール送信自動化未対応**: IT6 ではフロントから URL をコピーする手動運用。メール送信機能は将来イテレーション

### 申し送り

- [ ] IT7 以降でトークン失効リスト（denylist）導入を ADR で再評価する
- [ ] メール送信自動化（SendGrid / AWS SES）導入時に本 ADR を更新する
- [ ] `JWT_SECRET` のローテーション手順を運用ドキュメント `docs/operation/` 配下に整備する
- [ ] 秘密鍵共有がスケールしなくなった時点で RS256（公開鍵方式）への移行を再評価する

## コンプライアンス

以下を CI / コードレビューで確認する。

1. **ArchUnit**: `JwtTrackingTokenService` が `trackingms/infrastructure/security` 配下に配置されているか
2. **ユニットテスト**: `TrackingTokenServiceTest` で以下を網羅
   - 正常系: 有効期間内の検証成功
   - 異常系: 署名改ざん検出
   - 異常系: `exp` 経過時の `TOKEN_EXPIRED`
   - 異常系: `delivered_at + 7d` 経過時の `TOKEN_EXPIRED`
   - 異常系: `tn` クレームと URL パス不一致
3. **E2E テスト**: Playwright で公開 URL からのアクセスフロー（発行 → 照会 → 期限切れ）を検証
4. **設定確認**: `Heroku Config Vars` に `JWT_SECRET`・`TRACKING_TOKEN_EXPIRATION_DAYS` が設定されているか

## 関連

- [ADR-0001 Axon Framework 採用](0001-axon-framework-adoption.md)
- [ADR-0004 マイクロサービス分割方針](0004-microservice-decomposition.md)
- [ADR-0012 handlingms と trackingms の責務分離](0012-handlingms-trackingms-responsibility-separation.md)
- [ユーザーストーリー US18](../requirements/user_story.md)
- [IT6 イテレーション計画](../development/iteration_plan-6.md)

## 備考

- 著者: AI Agent
- 関連イテレーション: IT6
- 関連マイルストーン: M3（Tracking Number フォーマット決定）を本 ADR で吸収
