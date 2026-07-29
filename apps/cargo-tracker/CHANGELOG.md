# Changelog

本プロジェクト（Cargo Tracker Rails 版 / ruby/take-1）の変更履歴。
[Semantic Versioning](https://semver.org/) と [Conventional Commits](https://www.conventionalcommits.org/) に従う。

## [1.2.0] - 2026-07-29

MVP 後のハードニング・イテレーション（IT9）。料金調整（US21-6）を経理業務として実運用できる品質に強化し、設計規律を制度化。

### Features

- 補償費用の増減方向を減算（当社負担）に確定 T45（符号正規化を InvoiceLineItem に一本化・ADR-0005） (11a3dc72)
- 料金調整の取消・監査証跡・例外→請求導線 US21-6 T47（remove_adjustment/CancelAdjustment・担当者/理由・invoice_number_for_booking） (ef9b7edb)
- 料金調整の監査日時 adjusted_at（担当者・理由・日時の 3 点で監査証跡を充足・レビュー高対応）

### Bug Fixes

- IT9 レビュー高優先6件対応（remove_adjustment の seq_number 負値インデックスによる末尾誤削除・偽陽性の担当者テスト・監査日時欠落・negate の public 露出を MoneyAmount#subtract へ・ビューの状態再判定を cancellable フラグへ） (6bccd1fe)

### Refactor

- シードの過去日発行を公開 API 経由に置換 T46（calculate_freight の issued_at・生 SQL の invoices 直接更新を除去・BC privacy バイパス解消） (f556a3a8)
- 請求書未検出メッセージの定数化（SonarQube 新規違反解消） (c2a2ce1a)

### Documentation

- 設計 DoD（状態機械のガードは現場ケース起点・ドメイン不変条件は VO/集約に閉じる）を明文化 T43/T44 (52a69853)
- ADR-0005（料金調整の符号規約・一律減算）・domain-model/data-model に InvoiceLineItem/adjusted_at 反映
- IT9 計画・レビュー・ふりかえり・完了報告書

### 品質指標

- RSpec 429 examples, 0 failures / カバレッジ Line 95.95% / 新規 91.7%
- RuboCop・Brakeman・Packwerk（privacy）すべてクリーン
- SonarQube Quality Gate PASS（Bug 0・Vulnerability 0・重複 0.0%）・Backend CI success
- マルチパースペクティブレビュー（5 視点）高優先 6 件是正・2 件許容

## [1.1.0] - 2026-07-29

予備（バッファ）イテレーション（IT8）。IT7 で正直に未達と記録した受入基準の残を充足し、**MVP の全受入基準（US01-US27）を完全充足**。

### Features

- 支払期限超過の未払い通知 US23-5（MarkOverdueInvoices バッチ・OVERDUE 遷移・invoice_overdue・billing:mark_overdue Rake） (720a102a)
- 例外発生時の料金調整 US21-6（InvoiceLineItem 減額/補償・add_adjustment・total 再計算・下限ガード） (9a372a37)
- 遅延対応の新到着予定日を構造化し公開追跡に反映 T37（revised_arrival_date・推定到着日に優先） (99c77eaf)

### Bug Fixes

- IT8 レビュー高優先4件対応（OVERDUE 請求書の塩漬け解消＝期限超過後の遅延入金/調整を可能に・符号正規化のドメイン化・境界値テスト・下限ガード） (65dba47f)

### Chores

- 業務全体を実行できるシードデータ（航海・見積・フルライフサイクル・遅延例外・未払い請求）とプレースホルダ整合性 (e24514d9, 24a342ac)

### Documentation

- 未払い通知バッチ運用手順（cron・環境変数・冪等）・IT8 設計反映（adjustment_type・revised_arrival_date・invoice_overdue）
- IT8 計画・レビュー・ふりかえり・完了報告書

### 品質指標

- RSpec 416 examples, 0 failures / カバレッジ Line 95.94% / 新規 91.5%
- RuboCop・Brakeman・bundler-audit・Packwerk（privacy）すべてクリーン
- SonarQube Quality Gate PASS（違反 0・重複 0.0%）・Backend CI success
- マルチパースペクティブレビュー（5 視点）高優先 4 件全対応

### 残（IT9 引き継ぎ）

- 「補償費用」の増減方向の業務確認・料金調整の取消/監査/導線・未払いバッチのジョブ化

## [1.0.0] - 2026-07-29

Phase 4（見積・料金計算・精算）完了。**MVP 完成（全 4 Phase・US01-US27 完了）**。見積作成から料金算出・法人割引・精算までの業務を確立し、Estimation/Billing の 2 BC を新設。

### Features

- 料金計算ドメインサービス US21/US22（距離係数×重量×貨物種別係数→割引→燃油→消費税10%・MoneyAmount/DiscountRate/Surcharge） (7ad9583f)
- 請求書集約と支払状態 US21/US23（Invoice・PaymentStatus・支払期限30日・二重精算防止） (ce74ea9e)
- 見積集約 US01（Estimate・RouteCandidate・EstimateStatus） (9335eaf4)
- 見積の永続化と 5 テーブル migration（estimates/route_candidates/invoices/invoice_line_items/payments） (f54e2a76)
- 輸送見積作成 US01（Routing 候補 ACL + Billing 概算 ACL・見積画面・危険物動的表示） (aa059cb3, 54eb0701)
- 請求書の永続化 US21/US23（二重請求防止・楽観ロック・base_amount/shipper_id/surcharge 永続化） (f2737078)
- 輸送料金算出・法人割引 US21/US22（DELIVERED 実績 ACL・Shipper 割引 ACL・請求書発行） (488351b9)
- 精算処理 US23（入金確認→CONFIRMED→予約 SETTLED 同期・PaymentGatewayPort） (565bafd5)
- 請求・精算画面と精算通知 US21/US22/US23（料金明細・割引根拠・入金確認・invoice_created/settled） (5abd7bcb, 20f13fd1)
- 荷役冪等キーに DB unique index T35（並行 POST の TOCTOU 最終防衛） (4a8f3e87)

### Bug Fixes

- IT7 レビュー高優先6件対応（請求明細サーチャージ・精算整合・shipper_id 永続化・割引率クランプ） (6262f872)

### Tests

- 料金算出→割引→精算の業務シナリオ E2E (9167e038)

### Documentation

- IT7 実装を設計ドキュメントに反映（Money→MoneyAmount 統一・invoice イベント・ADR-0004 決定4） (bff742b1)
- IT7 計画・整合性検証・ふりかえり・完了報告書・レビュー

### 品質指標

- RSpec 397 examples, 0 failures / カバレッジ Line 96.01% / 新規 94.4%
- RuboCop・Brakeman・bundler-audit・Packwerk（privacy）すべてクリーン
- SonarQube Quality Gate PASS（違反 0・重複 0.0%）・Backend CI success
- マルチパースペクティブレビュー（5 視点）高優先 6 件全対応

### 残（IT8 引き継ぎ）

- US23 支払期限超過の未払い通知バッチ・US21 例外時の料金調整（減額・補償）

## [0.3.0] - 2026-07-29

Phase 3（追跡・荷役・例外処理）完了リリース。追跡番号発行・荷役記録から追跡照会・例外処理までの一連の追跡業務を確立。IT6 で追跡照会（公開ページ・30 秒ポーリング）と遅延・破損・紛失の例外処理を追加。

### Features

- 追跡例外処理のドメイン層 US19/US20（ExceptionType・TrackingExceptionEvent・TrackingStatus.EXCEPTION） (3b032d0b)
- 遅延・破損・紛失例外の登録と対応報告 US19/US20（tracking_exception_detected/resolved・紛失時の管理職エスカレーション） (f5a6ac9f)
- 例外管理 UI US19/US20（一覧/登録/対応報告・種別動的表示・ロール別到達導線） (d3d49df8, 6969d968)
- 公開貨物追跡ページ US18（認証不要・個人情報非表示・推定到着日・イベント履歴） (92e93eab, 89fc64f0)
- 追跡詳細の 30 秒 Turbo Frame 差分ポーリング US18 (b28c88dc)
- 荷役の二重登録防止と楽観ロック競合回帰テスト T28/T29 (c03680cc)
- MISROUTED を cargos.routing_status に反映 T32 (cf917287)

### Bug Fixes

- IT6 レビュー高優先の対応（発生前状態の永続化・通知テスト・公開履歴） (15e6c5b0)

### Documentation

- IT6 実装を設計ドキュメントに反映（設計反映 7 点） (8ff2e9c5)
- IT6 実装マルチパースペクティブレビュー結果 (0ffe5d41)
- IT6 計画・整合性検証・ふりかえり・完了報告書 (2c3f6d41, 0fb7fecd)

### 品質指標

- RSpec 337 examples, 0 failures / カバレッジ Line 95.38% / 新規 92.7%
- RuboCop・Brakeman・bundler-audit・Packwerk（privacy）すべてクリーン
- SonarQube Quality Gate PASS（違反 0・重複 0.0%）
- マルチパースペクティブレビュー（5 視点）高優先 5 件全対応

## [0.2.0] - 2026-07-28

Phase 2（航路・経路・確定・通知）リリース。航海スケジュールから経路候補提示・経路確定・予約確定・通知基盤までを確立。

### Features

- 経路（旅程）を予約に紐付ける US09/US11（CargoItinerary/Leg・ROUTE_PROPOSED） (3cdbf77e)
- 予約確定・差戻し・キャンセルと通知駆動 US12/US13（ADR-0002 ドメインイベント） (96e936b8)
- 経路条件調整の再算出 US10・cargos 拡張（consignee/routing_status） (ad9f2a86)
- 通知基盤（DomainEvents・NotificationRecorder・notifications） (6eed077e)
- 経路候補算出 US08（外部 ACL・フォールバック・ADR-0004） (8ef5ffe6)
- 航海検索 US07 と航路 UI（一覧/登録/詳細/更新） (c19987c7)
- 航海スケジュール登録・更新 US24/US25（Routing Context） (380c5c9b)
- Location 共有カーネル（packs/shared）を導入 (13b8c1e9)
- 荷主 ID 入力を荷主名で選択可能に T14 (b2f38660)

### Bug Fixes

- 経路割り当て導線の維持とドメインイベント購読のテスト分離 (1dd05b07)
- IT3 レビュー高優先の対応（時系列検証・型正規化・ACL 堅牢化・導線） (48303c55)
- 業務サンプルシードを development 限定にし CI のテスト重複を解消 (3fe8cb85)
- RuboCop カスタム cop を Rails autoload 対象から除外 (75683687)
- CSS を全ビュー対応のデザインシステムに刷新 (145eea4c)

### Refactoring

- IT3 負債返済 T17（Location 配線）/T18（射影）/T19（経路候補 UX） (72dd318e)
- 予約未検出メッセージを定数化（SonarQube 指摘対応） (7fde5622)

### Documentation

- IT4 実装を設計ドキュメントに反映（7 点） (dc71eb69)
- ADR-0004 US08 経路候補の BC 帰属 (4402e817)

### Chores

- SonarQube 品質ゲートを導入（T8）・Bug 0 に是正 (60f27a29)
- 負債返済 T11（JS ドライバ）・T2（ドメイン AR 禁止 cop） (92c080fb)

## [0.1.0] - 2026-07-28

Phase 1（予約基盤）リリース。認証・荷主登録・貨物予約の基盤を確立。

### Features

- 貨物予約 US04/US05/US06（Booking Context・Shipper への ACL 境界） (5c1f4890)
- 画面遷移図に沿った全ルートのプレースホルダ画面（ウォーキングスケルトン） (53e92bb0)
- シード利用者とログイン画面のデフォルト入力 (44d4b842)
- 荷主登録 US02/US03（ヘキサゴナル PORO） (47e8a696)
- ウォーキングスケルトンと認証 US26/US27（5 ロール RBAC・アカウントロック） (6a31a545)

### Bug Fixes

- IT2 レビュー高優先の対応（アトミック遷移・例外露出・設計同期） (0ff4e816)
- IT1 レビュー高優先の修正（重複競合・割引率必須・監査ログ検証） (748e3157)
- スタイルシートの読み込み修正とアプリ全体の CSS 追加 (179a0934)
- data-model を正典に整合（割引率 30%・5 ロール・住所カラム） (145c2677)

### Refactoring

- DIP 回復・公開 API・Packwerk privacy で BC 境界を確立（T1/T3/T4） (d8edb8cf)

### Chores / CI

- CI 修正（packwerk-extensions）と負債返済（T5/T9） (8c1d9831)
- workflow_dispatch を追加し take ブランチで手動実行可能に (6e9495e1)
- loofah/rails-html-sanitizer を脆弱性修正版へ更新 (7ed666f8)
- Backend CI ワークフローを構築 (a5868519)
- pre-commit フック（Husky + lint-staged）を追加 (6c992834)
- Rails 8 アプリケーション開発環境を構築 (f072764b)

### 品質指標

- RSpec 151 examples, 0 failures / カバレッジ Line 96.07% / Branch 82.99%
- RuboCop・Brakeman・bundler-audit・Packwerk（privacy）すべてクリーン
- Backend CI success

[1.1.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv1.1.0
[1.0.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv1.0.0
[0.3.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv0.3.0
[0.2.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv0.2.0
[0.1.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv0.1.0
