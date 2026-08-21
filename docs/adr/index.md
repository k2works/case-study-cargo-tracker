# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](001-microservices-architecture.md) | バウンデッドコンテキスト単位のマイクロサービスアーキテクチャの採用 | 承認済み |
| [ADR-002](002-local-kubernetes-kustomize.md) | ローカル開発環境に kind + Kustomize を採用 | 承認済み |
| [ADR-004](004-gateway-jwt-verification.md) | JWT の署名検証は Gateway に一元化し、各サービスはロール認可のみを行う | 承認済み | 2026-08-19 |
| [ADR-005](005-token-storage-in-session-storage.md) | 認証トークンは sessionStorage に保持する | 承認済み | 2026-08-19 |
| [ADR-006](006-demo-login-prefill.md) | 開発環境のログイン画面に動作確認用の利用者を事前入力する | 承認済み | 2026-08-19 |
| [ADR-003](003-heroku-development-environment.md) | 開発環境（結合テスト）に Heroku Container Registry / Runtime を採用 | 承認済み |
| [ADR-007](007-authenticated-user-header-required.md) | 業務サービスは Gateway が付けた利用者ヘッダを必須とし、共有フィルタで検査する | 承認済み | 2026-08-20 |
| [ADR-008](008-no-user-shipper-link-in-it2.md) | IT2 では利用者と荷主を紐付けず、貨物予約は営業担当者のみが扱う | 承認済み | 2026-08-20 |
| [ADR-009](009-cargo-status-columns-from-the-start.md) | 貨物の状態列は最初から NOT NULL で作り、料金列は算出できるまで NULL を許す | 承認済み | 2026-08-20 |
| [ADR-010](010-location-master-shape.md) | 地点はサロゲート主キー + UN/LOCODE の一意制約で持ち、業務タイムゾーンを必須とする | 承認済み | 2026-08-20 |
| [ADR-011](011-booking-id-numbering.md) | 予約番号は DB のシーケンスで採番し、`BKG-YYYYNNNNNN` の形式とする | 承認済み | 2026-08-20 |
| [ADR-012](012-value-object-granularity.md) | 値オブジェクトは不変条件を持つ属性にだけ導入し、荷主は単一クラスのままとする | 承認済み | 2026-08-20 |
| [ADR-013](013-frontend-pages-and-features.md) | フロントエンドは `pages/` を画面の入口、`features/` を業務単位の実装に分ける | 承認済み | 2026-08-20 |
| [ADR-014](014-location-replica-sync.md) | 地点マスタの複製は同一の種データファイルを配り、内容の一致をテストで検査する | 承認済み | 2026-08-20 |
| [ADR-015](015-routing-requested-state.md) | 経路設計の依頼を `RoutingStatus` の状態として持ち、経路設計者に見せる範囲をそれで絞る | 承認済み | 2026-08-20 |
| [ADR-016](016-authorize-before-validate.md) | 入力の検査は認可のあとに、メソッド本体で行う | 承認済み | 2026-08-21 |
| [ADR-018](018-route-search-rules.md) | 経路候補の推奨順・費用の概算を決め、港湾制約は持たない | 承認済み | 2026-08-21 |

ADR の作成には `creating-adr` スキルを使用してください。
