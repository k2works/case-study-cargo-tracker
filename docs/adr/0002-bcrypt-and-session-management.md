# 0002 認証のパスワードハッシュに bcrypt を採用しセッションを Play Session で管理する

US26（ログイン・ログアウト）の実装方針として、パスワードハッシュアルゴリズムとセッション管理方式を確定する

日付: 2026-06-20

## ステータス

2026-06-20 提案されました

## コンテキスト

US26 で導入する認証基盤は、以下のすべての後続ストーリーで前提となる：

- Shipper Context（US02・US03）: 個人情報・契約情報を保護
- Booking Context（US04 以降）: 業務操作の監査ログを操作者単位で記録
- 全コントローラ: 役割（営業担当者・経路設計者・追跡担当者・精算担当者・マスタ管理者）による認可

決定すべき技術判断は次の 3 点：

1. パスワードハッシュアルゴリズム
2. セッション保持方式
3. セッションタイムアウト動作

選択肢：

| 観点 | 候補 | 評価 |
|------|------|------|
| ハッシュ | bcrypt（jbcrypt 0.4） | OWASP 推奨、build.sbt に組込済、Java 版 take-2 と同等 |
| ハッシュ | Argon2id | より新しいが Scala/Play 標準依存に追加が必要 |
| ハッシュ | PBKDF2 | JDK 標準で実現可能だが反復回数調整が手作業 |
| セッション | Play Session（Cookie 署名・クライアント側保持） | Play 標準、サーバ側ステート不要、署名で改竄防止 |
| セッション | DB セッションテーブル | サーバ側で失効可能だが I/O コストとスキーマ管理が増える |
| セッション | Redis | 追加インフラが必要 |
| タイムアウト | アクセス毎に更新（スライディング） | UX 良好、無操作 30 分で失効 |
| タイムアウト | 固定有効期限 | シンプルだが業務中に切れる可能性 |

## 決定

**bcrypt + Play Session（Cookie 署名）+ スライディングタイムアウト 30 分**を採用する。

詳細：

- **ハッシュ**: `org.mindrot.jbcrypt.BCrypt` を使用。`BCrypt.gensalt(12)` を初期コストとする
- **セッション**: Play 標準の Session を使用。Cookie は HttpOnly + Secure（本番）+ SameSite=Lax で発行
- **セッション内容**: `userId`（業務キー）、`role`、`lastAccessedAt`（ISO8601）の 3 要素
- **タイムアウト**: `SessionTimeoutFilter` で `lastAccessedAt` を毎リクエスト更新。30 分超過時はセッション破棄 → ログイン画面リダイレクト
- **公開 URL の除外**: `/public/tracking/:trackingNumber` と `/login` は認証フィルタの対象外
- **CSRF**: Play 標準の CSRF Filter を `application.conf` で有効化

## 影響

**ポジティブ**:

- jbcrypt が既に build.sbt に取り込まれており追加依存ゼロ
- Play Session でサーバ側ステートレスを維持できる（水平スケール可能）
- スライディングタイムアウト 30 分は荷主担当者の業務継続性と不正アクセス防止のバランスが良い
- Java 版 take-2 と同じ jbcrypt を使うことで、ユーザー DB の移行性が高い

**ネガティブ・トレードオフ**:

- Cookie サイズの上限（4KB）に縛られるが、3 要素のセッションでは十分余裕
- サーバ側強制ログアウト（管理者によるセッション失効）はできない。要件が出たら DB セッション層を追加する
- bcrypt のコスト 12 は CPU 負荷が高めだが、ログイン頻度を考えれば許容

## コンプライアンス

- `BCryptPasswordHasher` の単体テストでハッシュ・検証・互換性を確認
- `SessionTimeoutFilter` の統合テストで 30 分タイムアウトと公開 URL 除外を確認
- E2E テストで `/login` → 保護リソース → `/logout` の貫通を確認
- application.conf の CSRF 有効化を Pull Request のレビューで確認

## 備考

- 著者: AI Agent
- 関連: US26、ADR 0001（Play Framework 採用）
- 参考: Java 版 take-2 の認証実装、OWASP Authentication Cheat Sheet
