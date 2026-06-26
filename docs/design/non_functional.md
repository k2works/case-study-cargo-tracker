---
title: 非機能要件定義 - 国際貨物輸送管理システム
description: ISO/IEC 25010 品質モデルに準拠した非機能要件の測定可能な定義 (Haskell 版)。
published: true
date: 2026-06-26T00:00:00.000Z
tags: design, non-functional, performance, security, availability, haskell
---

# 非機能要件定義 - 国際貨物輸送管理システム (Haskell 版)

## 1. 概要

### 1.1 目的と対象範囲

本ドキュメントは国際貨物輸送管理システム (Haskell 版) の非機能要件を測定可能な形で定義する。
性能・可用性・セキュリティ・保守性・拡張性・ユーザビリティの各観点で SLA / SLO を設定し、設計・運用の判断基準とする。

### 1.2 ISO/IEC 25010 品質モデルとの対応

| 品質特性 | 本ドキュメントの対応セクション |
| :--- | :--- |
| 性能効率性 (Performance Efficiency) | §2 性能要件 |
| 信頼性 (Reliability) | §3 可用性要件 |
| セキュリティ (Security) | §4 セキュリティ要件 |
| 保守性 (Maintainability) | §5 保守性要件 |
| 互換性 / 移植性 (Portability) | §6 拡張性要件 |
| 使用性 (Usability) | §7 ユーザビリティ要件 |
| 機能適合性 | 機能要件文書 (要件定義・ユースケース) を参照 |

---

## 2. 性能要件 (Performance)

### 2.1 レスポンスタイム目標

| 操作 | P50 | P95 | P99 | 計測対象 |
| :--- | :---: | :---: | :---: | :--- |
| 公開貨物追跡 (`/public/tracking/:number`) | 200 ms | 500 ms | 1.0 s | ALB → ECS → DB |
| 認証済みダッシュボード | 300 ms | 800 ms | 1.5 s | 同上 |
| 貨物予約登録 (POST) | 500 ms | 1.0 s | 2.0 s | トランザクション + イベント発行 |
| 経路候補算出 (外部 API 含む) | 1.0 s | 3.0 s | 5.0 s | 外部 API レスポンス込み |
| 一覧画面 (50 件) | 300 ms | 700 ms | 1.5 s | JOIN クエリ |
| htmx 部分更新 (`/tracking/:n/status`) | 100 ms | 250 ms | 500 ms | 単一 SELECT |

### 2.2 スループット目標

| 指標 | 目標値 |
| :--- | :--- |
| ピーク時 RPS | 200 req/s |
| 想定同時接続ユーザー数 | 500 人 |
| 同時バックグラウンド処理 | 10 イベント/s (ドメインイベント) |

> **Haskell の利点**: GHC のグリーンスレッドにより、Warp は数千の同時接続を低いメモリ消費で処理できる。
> JVM 比でメモリ使用量が小さく、ECS Fargate 256 CPU / 512 MB から開始可能。

### 2.3 データ量・ストレージ見積もり

| データ | 年間増加量 (見積) | 5 年累積 |
| :--- | :--- | :--- |
| `cargo` | 100,000 件 | 500,000 件 |
| `tracking_handling_event` | 1,000,000 件 (1 件あたり 10 イベント想定) | 5,000,000 件 |
| `handling_activity` | 1,000,000 件 | 5,000,000 件 |
| `invoice` | 100,000 件 | 500,000 件 |
| `notification_log` | 500,000 件 | 2,500,000 件 |
| **DB ストレージ** | **約 5 GB / 年** | **約 25 GB** |

RDS インスタンスは `db.t3.medium` (50 GB SSD) で 5 年は十分。スケールアップは `db.t3.large` への変更で対応。

### 2.4 性能劣化基準 (SLO 違反時の対応)

| SLO 違反 | 1 次対応 | 恒久対応 |
| :--- | :--- | :--- |
| P95 レスポンス > 目標値 × 1.5 (10 分継続) | Slack 通知 + Auto Scaling 発火 | 遅いクエリの特定・インデックス追加 |
| P99 レスポンス > 目標値 × 2.0 (5 分継続) | PagerDuty 緊急通知 | 緊急パッチデプロイ |
| 5xx エラー率 > 1% (5 分継続) | Slack 通知 | リバート + 障害分析 |

---

## 3. 可用性要件 (Availability)

### 3.1 稼働率 SLA / SLO

| 指標 | 目標値 | 月間許容停止時間 |
| :--- | :---: | :---: |
| **SLA (対外公約)** | 99.5% | 約 3.6 時間 |
| **SLO (内部目標)** | 99.9% | 約 43 分 |

> 計画停止 (メンテナンスウィンドウ) は SLA に含めない。

### 3.2 RTO / RPO

| 指標 | 目標値 | 実現手段 |
| :--- | :--- | :--- |
| RTO (Recovery Time Objective) | 1 時間以内 | RDS Multi-AZ 自動フェイルオーバー (60 秒) + ECS 再デプロイ |
| RPO (Recovery Point Objective) | 5 分以内 | RDS 自動バックアップ + ポイントインタイム復旧 (PITR) |

### 3.3 メンテナンスウィンドウ

| 種別 | 時間帯 | 頻度 | 事前告知 |
| :--- | :--- | :--- | :--- |
| 定期メンテナンス | 日曜 03:00-05:00 JST | 月 1 回程度 | 1 週間前 |
| 緊急メンテナンス | 随時 | 必要時 | 即時通知 |

### 3.4 ヘルスチェック設計

| エンドポイント | 用途 | 内容 | 応答時間 |
| :--- | :--- | :--- | :--- |
| `GET /health` | ALB ターゲットグループ | アプリ生存確認 + DB 疎通 (`SELECT 1`) | < 100 ms |
| `GET /health/liveness` | (将来) k8s liveness probe | アプリ生存のみ | < 50 ms |
| `GET /health/readiness` | (将来) k8s readiness probe | DB + 外部依存確認 | < 200 ms |

ヘルスチェック失敗時の挙動:

- ALB は 30 秒間隔で 2 連続失敗 → タスクを Unhealthy 判定 → ECS が再起動
- 起動時はマイグレーション (`dbmate up`) 適用後にヘルスチェックを有効化

---

## 4. セキュリティ要件 (Security)

### 4.1 認証・認可

| 要件 | 内容 |
| :--- | :--- |
| 認証方式 | フォーム認証 (bcrypt パスワードハッシュ) + JWT または HMAC 署名付き Cookie |
| パスワード強度 | 最低 12 文字、英大小数字記号の組み合わせ |
| パスワード履歴 | 過去 5 世代の再利用禁止 (`password_history` テーブル) |
| パスワード有効期限 | 90 日 (期限後はログイン時に強制変更) |
| アカウントロック | 連続 5 回失敗で 30 分ロック (`users.failed_login_attempts` / `locked_until`) |
| セッションタイムアウト | 30 分 (無操作時)、絶対上限 8 時間 |
| 同時セッション数 | 1 (新規ログイン時に既存セッション無効化、`session_generation` インクリメント) |
| 多要素認証 (MFA) | 初期リリース対象外。将来 TOTP 導入を検討 |
| ロールベース認可 (RBAC) | Servant `AuthProtect` + `requireRole`。7 ロール定義 |

### 4.2 通信セキュリティ

| 要件 | 内容 |
| :--- | :--- |
| 通信暗号化 | 全通信 HTTPS (TLS 1.2 以上)。HTTP は ALB で 443 へリダイレクト |
| TLS 証明書 | AWS ACM で発行・自動更新 |
| HSTS | `Strict-Transport-Security: max-age=31536000; includeSubDomains` |
| CSRF 対策 | Double Submit Cookie パターン (meta + Cookie) |
| CSP | `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'` (htmx 用) |
| Cookie 属性 | `Secure; HttpOnly; SameSite=Lax` |
| CORS | 同一オリジン限定 (初期)。将来 API 公開時に許可リスト設定 |

### 4.3 データ保護

| 要件 | 内容 |
| :--- | :--- |
| 保管時暗号化 | RDS 暗号化 (AWS KMS)、S3 暗号化 (SSE-KMS) |
| 転送時暗号化 | 全 HTTPS、DB 接続も TLS |
| シークレット管理 | AWS Secrets Manager (DB 接続情報、JWT 鍵)。コード・リポジトリにハードコードしない |
| PII (個人情報) | 荷主・荷受人の氏名・連絡先。ログ・エラーメッセージに出力しない |
| マスキング | UI 表示時、メールアドレスの中央部 (`a***@example.com`) はマスキング (将来) |

### 4.4 監査ログ

以下の重要操作は `notification_log` または専用監査テーブルに永続記録する。

| 操作 | 記録項目 |
| :--- | :--- |
| ログイン (成功・失敗) | `users.id`, IP アドレス, User-Agent, 結果 |
| 予約確定・キャンセル | `bookingId`, 操作者, 旧→新ステータス |
| 経路選択・確定 | `bookingId`, 選択された `voyageNumbers` |
| 荷役登録 | `bookingId`, `eventType`, `location`, 操作者 |
| 例外発生・解決 | `trackingNumber`, `exceptionType`, 操作者 |
| 請求書発行・入金確認 | `invoiceId`, 操作者, 金額 |
| 割引適用 | `invoiceId`, `discountRate`, 適用根拠 |
| ユーザー作成・ロール変更 | 変更前後、操作者 |

ログ保持期間: **7 年** (国際輸送の規制対応)。

### 4.5 脆弱性対策

| 要件 | 内容 |
| :--- | :--- |
| 依存脆弱性スキャン | `cabal-audit` または `stack-audit` を CI で実行 |
| コンテナイメージスキャン | AWS ECR の脆弱性スキャン (Push 時) |
| SQL インジェクション対策 | postgresql-simple の `?` パラメータバインディング (生 SQL 文字列結合禁止) |
| XSS 対策 | Lucid の `toHtml` 自動エスケープ。`toHtmlRaw` は原則使用禁止 |
| CSRF 対策 | §4.2 参照 |
| WAF | (将来) AWS WAF ルールセットで一般的攻撃をブロック |
| 侵入テスト | 年 1 回外部委託 (将来) |

---

## 5. 保守性要件 (Maintainability)

### 5.1 ログ設計

| ログレベル | 用途 |
| :--- | :--- |
| `DEBUG` | 開発時の詳細トレース (本番では出力しない) |
| `INFO` | 業務イベント (予約作成、状態遷移、イベント発行) |
| `WARN` | 想定範囲内の異常 (バリデーション失敗、リトライ) |
| `ERROR` | 想定外のエラー (例外発生、外部 API 失敗) |
| `FATAL` | アプリ停止が必要なレベル (ほぼ使用しない) |

ログ形式: katip による JSON 構造化ログ。例:

```json
{
  "timestamp": "2026-06-26T10:00:00.000Z",
  "level": "INFO",
  "traceId": "abc123",
  "userId": "user-001",
  "context": "booking",
  "message": "貨物予約を登録しました",
  "bookingId": "BK-A1B2C3"
}
```

### 5.2 監視・メトリクス

| カテゴリ | メトリクス | 監視ツール |
| :--- | :--- | :--- |
| アプリケーション | HTTP 5xx 率、レスポンスタイム (P50/P95/P99)、エンドポイント別 RPS | CloudWatch Logs (メトリクスフィルタ) + Dashboard |
| ランタイム | GC 時間、メモリ使用量、ライブスレッド数 | (将来) GHC RTS メトリクス + Prometheus exporter |
| インフラ | ECS タスク数、CPU/メモリ使用率、ALB ターゲット健全数 | CloudWatch Metrics |
| データベース | 接続数、レプリケーション遅延、低速クエリ、デッドロック | CloudWatch + RDS Performance Insights |
| ビジネス | 予約数 / 日、例外発生数、配送完了率 | CloudWatch カスタムメトリクス |

アラート閾値は [運用要件](operation.md) を参照。

### 5.3 コード品質目標

| 指標 | 目標値 | 計測ツール |
| :--- | :---: | :--- |
| 単体テストカバレッジ (Domain) | 95% 以上 | hpc |
| 単体テストカバレッジ (全体) | 85% 以上 | hpc |
| HLint 警告数 | 0 | hlint |
| フォーマット違反 | 0 | fourmolu --mode check |
| デッドコード | 0 | weeder |
| 関数の循環的複雑度 (Cyclomatic) | 10 以下 (推奨) | (手動レビュー) |
| 1 関数の行数 | 50 行以下 (推奨) | (手動レビュー) |
| アーキテクチャ規約違反 | 0 | 自作 arch-check |

---

## 6. 拡張性要件 (Scalability)

### 6.1 水平スケーリング

| 対象 | スケーリング方針 |
| :--- | :--- |
| Web/API (ECS Fargate) | Auto Scaling: CPU 80% で +1 タスク、20% で -1 タスク。最小 2 / 最大 10 |
| バックグラウンド処理 | 初期はメインプロセス内同期実行。将来 Kafka + 専用ワーカーへ分離 |

> Haskell の Warp + ReaderT は完全にステートレス (Session は Cookie に閉じる) のため、水平スケールでスティッキーセッション不要。

### 6.2 DB スケーリング

| 対象 | スケーリング方針 |
| :--- | :--- |
| RDS PostgreSQL | 縦スケール (`db.t3.medium` → `db.t3.large`) を 1 次対応 |
| 読み取り | RDS リードレプリカ (将来)。CQRS のクエリ側で活用 |
| 書き込み | パーティショニング (将来)。`tracking_handling_event` を月次パーティションに分割 |

接続プール設定:

```haskell
-- src/Cargotracker/Shared/Infrastructure/Db/Pool.hs
createDbPool :: AppConfig -> IO (Pool Connection)
createDbPool cfg = createPool
  (connectPostgreSQL (dbUrl cfg))
  close
  10           -- subpools
  60           -- 接続再利用時間 (秒)
  (cfgMaxConn cfg)  -- 1 subpool あたり最大接続数 (デフォルト 5 → ECS タスク 2 で計 100 接続)
```

RDS の `max_connections` (デフォルト 100) と整合させる。タスク数 × pool サイズ ≤ RDS max_connections × 0.8。

### 6.3 将来の拡張シナリオ

| シナリオ | 対応方針 |
| :--- | :--- |
| ユーザー数が 10 倍 (5,000 同時) | ECS 最大タスク数を 20 に拡張、RDS をリードレプリカ + 縦スケール |
| 外部 API 公開 | 既存 Servant API を `/api/v2/` で公開、レート制限 + API キー認証追加 |
| イベント駆動の永続化 | Transactional Outbox + Kafka 導入、Tracking Context をマイクロサービス化 |
| マルチリージョン | RDS Aurora Global Database、CloudFront でグローバル配信 |

---

## 7. ユーザビリティ要件 (Usability)

### 7.1 レスポンシブ対応

| ブレークポイント | 主な利用シーン |
| :--- | :--- |
| デスクトップ (≥ 1200px) | 営業担当者、経理担当者の主作業 |
| タブレット (768-1199px) | 経路設計者、追跡管理者 |
| モバイル (< 768px) | 荷役作業員 (現場での荷役登録)、荷主の追跡確認 |

Bootstrap 5 のグリッドで対応。荷役作業登録画面は特にモバイル最適化 (大きなタッチターゲット、バーコードスキャン連携)。

### 7.2 アクセシビリティ

| 要件 | 内容 |
| :--- | :--- |
| WCAG 2.1 AA 準拠 | コントラスト 4.5:1 以上、キーボード操作可能 |
| スクリーンリーダー対応 | ARIA ラベル、`aria-live` で動的更新通知 |
| フォーカス可視化 | `:focus-visible` で明確なフォーカスリング |

### 7.3 エラーメッセージ設計

| 種別 | メッセージ例 | UI 表示 |
| :--- | :--- | :--- |
| バリデーションエラー | 「重量は 0 より大きい数値を入力してください」 | フィールド下に赤字 |
| ドメインエラー | 「指定された予約 ID は見つかりません (BK-XXXXXX)」 | 画面上部の alert-danger |
| システムエラー | 「一時的なエラーが発生しました。しばらくしてから再度お試しください」 (詳細はログのみ) | フラッシュメッセージ |
| 認可エラー | 「この操作を実行する権限がありません」 | 専用エラーページ (403) |

### 7.4 ローディング・フィードバック

| 操作 | フィードバック |
| :--- | :--- |
| 通常画面遷移 | ブラウザのデフォルトローディング |
| htmx リクエスト | `hx-indicator` でスピナー表示 (500 ms 以上の場合) |
| フォーム送信 | 送信ボタンを `disabled` 化、スピナー表示 |
| 成功時 | 緑色 Flash メッセージ「予約を登録しました」 |
| 失敗時 | 赤色 Flash メッセージ + フィールドエラー |

### 7.5 セッションタイムアウト警告

セッション期限の 5 分前に htmx で警告モーダルを表示し、「セッション延長」ボタンを提供。
未操作で期限切れの場合は `/login` にリダイレクト + 元の URL を `?returnTo=` で保持。

---

## 8. 法令・コンプライアンス要件

| 要件 | 内容 |
| :--- | :--- |
| 個人情報保護法 | 荷主・荷受人の PII を §4.3 に従い保護 |
| 電子帳簿保存法 | 請求書・精算記録を 7 年間保持。改竄検知のため監査ログでハッシュ記録 (将来) |
| 国際海上輸送規則 | 危険物 (IMDG コード)、冷凍貨物の温度管理条件を `cargo` テーブルに記録 |
| GDPR (将来 EU 展開時) | 個人データの削除権・データポータビリティ対応 |

---

## 付録: 非機能要件確認チェックリスト

リリース判定時に以下を確認する。

### 性能

- [ ] 公開貨物追跡の P95 < 500 ms (本番相当の負荷で計測)
- [ ] 一覧画面の N+1 クエリなし (postgresql-simple ログで確認)
- [ ] 5 年分のデータ量で性能劣化なし (パフォーマンステスト)

### 可用性

- [ ] RDS Multi-AZ 設定済み
- [ ] ECS 最小 2 タスク、Auto Scaling 設定済み
- [ ] `/health` がアプリ + DB の生死を返す
- [ ] バックアップ復旧手順を年 1 回テスト

### セキュリティ

- [ ] 全通信 HTTPS
- [ ] パスワードは bcrypt
- [ ] CSRF / XSS / SQL インジェクション対策
- [ ] 監査ログ 7 年保持
- [ ] シークレットを AWS Secrets Manager で管理
- [ ] 依存ライブラリの脆弱性スキャン (cabal-audit)

### 保守性

- [ ] 単体テストカバレッジ (Domain ≥ 95%, 全体 ≥ 85%)
- [ ] HLint 警告 0
- [ ] アーキテクチャ規約違反 0
- [ ] JSON 構造化ログを CloudWatch に出力
- [ ] 主要メトリクス・アラート設定済み

### 拡張性

- [ ] ECS Auto Scaling 動作確認
- [ ] DB 接続プールサイズが RDS max_connections と整合

### ユーザビリティ

- [ ] レスポンシブ対応 (375px / 768px / 1200px で確認)
- [ ] アクセシビリティ自動チェック (axe-core 等) で重大違反 0
- [ ] エラーメッセージの日本語が自然

### コンプライアンス

- [ ] PII がログ・エラーメッセージに含まれていない
- [ ] 監査ログが定義通り出力されている

---

## 参照

- [バックエンドアーキテクチャ](architecture_backend.md)
- [インフラアーキテクチャ](architecture_infrastructure.md)
- [テスト戦略](test_strategy.md)
- [運用要件](operation.md)
- Scala 版参考: `tmp/case-study-cargo-tracker/docs/design/non_functional.md`
