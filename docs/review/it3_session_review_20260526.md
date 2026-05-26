# コードレビュー結果 — IT3 本セッション変更

## レビュー対象

- 範囲: `git diff a4297a5e..HEAD`（IT3 本セッションのソース変更、30 ファイル / 約 946 行追加）
- 主な変更:
  - routingms 経路設計依頼 参照 API（`RouteDesignRequestController` / `RouteDesignRequestQueryService` / `RouteDesignRequestResponse` + 単体テスト）
  - cross-service E2E（`cross-service.spec.ts`、環境変数ゲート）
  - 認証ヘッダの localStorage 統一（`src/shared/api/auth.ts`）
  - SonarQube Code Smell 25 → 0 リファクタ（FormEvent→SubmitEvent、AuthContext の useMemo/useCallback、BookingFormPage のバリデーション抽出ほか）
  - フロント API クライアントのテスト追加（カバレッジ 80% 達成）
- 実施日: 2026-05-26
- レビュアー: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative

## 総合評価

規律の効いた良質な変更で、マージに支障のある欠陥はない。特に「認証ヘッダが常に空」という実害バグを単一の真実（`shared/api/auth`）へ集約し回帰防止テストで固定した点、Code Smell 解消が振る舞いを変えない純粋リファクタに留まる点が高く評価された。一方で、(1) 新 DTO の一部フィールドがどの層でもアサートされていない偽陰性、(2) API カタログ（architecture_backend.md）のドリフト、(3) 経路設計者の業務導線（待ちリスト画面）と見積→予約化の情報引き継ぎという利用者視点のギャップが指摘された。(3) は IT3 スコープ外（IT4 ワークベンチ）の範囲だが、US06「経路設計者への通知」が UI 上未達である点は明示しておくべき。

## 改善提案（重要度順）

### 高（マージ前に対応すべき / IT4 で最優先）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | `RouteDesignRequestResponse.from` の全フィールド（特に `arrivalDeadline`/`status`/`requestedAt`）を 1 層で検証 | `RouteDesignRequestControllerTest.java:46-57`、`QueryServiceTest` の setUp | xp-tester | record 引数順の取り違えを全テストが緑のまま見逃す偽陰性。最小コストで安全網を追加できる |
| H2 | API カタログ（routingms / bookingms 表）を IT3 実装に同期。新 API `GET /api/v1/routes/design-requests[/{bookingId}]` と `GET /api/v1/voyages/search`・quotation 系・handoff/cancel を追記、未実装行に IT4 注記 | `docs/design/architecture_backend.md`（routingms 表 1102-1109 ほか） | xp-technical-writer | 本プロジェクト唯一の API カタログ。記載漏れで API の発見可能性がコード閲覧者に限定される |
| H3 | 経路設計待ちリストの画面とナビゲーション導線（経路設計者ロール）。API はあるが UI が無く、US06 受入「経路設計者に通知」が画面上未達 | `Navigation.tsx`、新規一覧画面 | xp-user-representative | 経路設計者が引き渡された依頼に気づけず業務が事実上止まる。IT4 ワークベンチ（S14）で最優先 |
| H4 | 見積詳細「予約化」で見積情報（荷主・出発地・目的地・期限・貨物種別・重量）を予約フォームにプリセット | `QuotationDetailPage.tsx`（`/bookings/new` 遷移） | xp-user-representative | 同一情報の二度入力・転記ミスの温床。US04「見積情報との整合性」にも関連 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `Number(values.weightKg)` / `Number(values.quantity)` の二重計算を解消（validate 側で検証済み数値を返す or handleSubmit で保持） | `BookingFormPage.tsx:138,142` | xp-programmer | 同じ変換を 2 回書く知識の重複（DRY） |
| M2 | 抽出した純粋関数 `validateBookingForm`/`validateRefrigerated` に境界値テーブルテスト（min>max・weight<=0・origin===destination） | `BookingFormPage.tsx:44-95` | xp-tester | UI 経由 E2E より純粋関数の直接テストが速く網羅的。テストピラミッド適正化 |
| M3 | cross-service E2E のアサーションに `status:'PENDING'`・`arrivalDeadline` を追加 | `cross-service.spec.ts:88-92` | xp-tester | 伝搬してデフォルト値が正しく入ったことまで担保 |
| M4 | `route_design_request.status` が IT3 時点で常に `PENDING` であること、状態遷移の責務（IT4）を ADR/Javadoc に明記 | `RouteDesignRequestEventHandler` / ADR-0009 / data-model | xp-architect | 公開 API が常に PENDING を返す前提が暗黙。利用者が status を解釈する際に迷う |
| M5 | 単一 `cargo-events` トピック構成のトピック分割方針（判断基準）を ADR に明文化 | `application-*.yml` / ADR | xp-architect | サービス増でイベントフローの追跡が困難化。今すぐ分割は不要だが判断基準は記録すべき |
| M6 | US13「ルート変更（差し戻し）」ボタンが UI に無い（バックエンドガードのみ）。進捗 100% 記載とのずれを明示 | `BookingDetailPage.tsx` | xp-user-representative | 荷主のルート変更要望は日常的。キャンセル→やり直しでは経路設計が無駄になる |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | `playwright.config.ts` の実行前提に bookingms（:8082）を追記 | `playwright.config.ts:4-7` | xp-technical-writer | 全 spec 共通の入口の前提が IT2 以降ドリフト |
| L2 | cross-service spec ヘッダに `npm run e2e:cross-service` を併記 | `cross-service.spec.ts` | xp-technical-writer | Windows ユーザーが `$env:` 構文を意識せず実行できる |
| L3 | 各 API クライアントの JSON 破損時フォールバック（既定メッセージ）テストを authApi 以外にも 1 ケース | `bookingApi.ts:85` ほか | xp-tester | 一貫性 |
| L4 | 経路設計待ちリストの既定ソートを `arrivalDeadline` 昇順（期限が近い順）に | 待ちリスト一覧（IT4） | xp-user-representative | 経路設計者が着手順を判断できる |
| L5 | `Pagination` の `<output>` はフォーム計算結果用でセマンティクスがやや弱い。ルール単位でなく意図単位で適用を確認 | `Pagination.tsx` | xp-architect | Code Smell 解消が意図に優先していないか |
| L6 | 冪等が first-write-wins（経路設計依頼は不変）である前提を Javadoc に一文 | `RouteDesignRequestEventHandler` | xp-architect | 誤って upsert 化されるのを防ぐ |

## 矛盾事項

複数エージェントの指摘が相反する事項はなし。利用者視点（H3/H4/M6）と「IT3 スコープは見積/引渡/検索/確定のバックエンド状態遷移まで」という計画上の判断は、**IT4 ワークベンチ（S14）での対応**として整合する（矛盾ではなく時間軸の違い）。

## 共通して挙がった懸念（横断）

- **cross-service E2E の偽陽性リスク**: 既定 `npm run e2e` では skip され、CI で `CROSS_SERVICE_E2E=1` を立てない限り常時スキップ（tester / architect / user-rep が指摘）。緩和: 同経路は `RouteDesignRequestKafkaIntegrationTest`（Testcontainers + Awaitility、gradle で自動実行）が多層防御としてカバー。nightly 等で `CROSS_SERVICE_E2E=1` を回す運用を推奨。
- **認可（ロール制御）の確認**: `/api/v1/routes/design-requests` のメソッドレベル認可が差分内で確認できない（programmer / user-rep）。既存 `VoyageController` と同方針なら整合だが、待ちリストの参照権限（経路設計者のみ）を整理すべき。

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 0 / 中: 1 / 低: 3）</summary>

評価: 規律の効いた良質な変更。認証バグの本質解決＋回帰テスト固定、Code Smell 解消が振る舞い不変のリファクタに留まる点が優秀。良い点: 認証一元化（DRY）、回帰防止テストの意図明示、バリデーション抽出の責務分割、useMemo/useCallback の依存配列の正しさ、cross-service E2E ゲート設計、API テストの粒度。改善: 【中】BookingFormPage の Number 二重計算（DRY）、【低】Controller テストのフィールド網羅、【低】Optional 化（ただし既存 VoyageController と一貫優先で現状維持妥当）、【低】parseDiscountRate の NaN 返却分岐の確認。懸念: voyageNumber! 削除は v7 useParams 型挙動に依存（isEdit ガードで安全）、認可確認。スコープ外: export type 重複、localStorage.clear 移行は整合済み。
</details>

<details>
<summary>xp-tester（高: 1 / 中: 2 / 低: 1）</summary>

評価: テストファースト・AAA・回帰防止の意図が明確。認証不整合バグを一元化＋回帰テストで封じた点が high value。良い点: 潜在バグの test 化、異常系網羅、メールの encodeURIComponent 境界、テスト名が仕様、E2E ゲートの自己記述性、quote strict mode 修正の正しさ。改善: 【高】RouteDesignRequestResponse.from の arrivalDeadline/status/requestedAt 未アサート（偽陰性）、【中】cross-service E2E に status/arrivalDeadline 追加、【中】validate 純粋関数の BVA テスト、【低】JSON 破損フォールバックの一貫テスト。懸念: cross-service E2E の偽陽性（緑だがスキップ）、E2E のレコードクリーンアップ。検証: tsc -b / vitest 46件 / routingms 単体すべて緑。
</details>

<details>
<summary>xp-architect（高: 0 / 中: 2 / 低: 2）</summary>

評価: ADR-0009 の意思決定が実装に忠実、processor 分離・冪等・gateway 集中認証も一貫し設計ドリフト小。良い点: cross-service イベント契約の置き場所（shared）、イベントの自己完結、processor 分離の粒度、冪等性、認証一元化の実バグ修正、テスト随伴。改善: 【中】status 常時 PENDING の文書化と IT4 状態遷移責務、【中】単一 cargo-events トピック分割方針の ADR 明文化、【低】冪等 first-write-wins の Javadoc、【低】output のセマンティクス。懸念: observation API の本番配置（findAll が固定 ORDER BY で VoyageController とページング非対称）、結果整合性の UI 波及、shared の god module 化防止制約。スコープ外: BookingSagaManager が空（IT4 で顕在化）、CI での cross-service 実行有無。
</details>

<details>
<summary>xp-technical-writer（高: 1 / 低: 2）</summary>

評価: 新 API・E2E・認証ヘルパーの JSDoc/コメントが US・ADR・read model に紐づき意図が追える水準。ただし唯一の API カタログ（architecture_backend.md）がドリフト。良い点: 新 API の JSDoc が設計文脈まで記述、cross-service E2E ヘッダの前提・実行手順、skip 理由が行動指示、認証ヘルパーの「なぜ」コメント、data-model との整合、報告書の透明性。改善: 【高】architecture_backend.md に新 API + IT3 分を同期（DoD に「新 API は表に追記」を加えるべきサイン）、【低】playwright.config に bookingms 追記、【低】spec に npm script 併記。懸念: API カタログ陳腐化の定着、E2E 知識の分散。スコープ外: 新 API の UC マッピング要検証、status 値一覧の未文書化。
</details>

<details>
<summary>xp-user-representative（高: 2 / 中: 2 / 低: 1）</summary>

評価: 見積〜引渡〜確定の中核フローは状態遷移ガードまで筋が通り技術土台は堅実。ただし経路設計者の業務入口（待ちリスト画面・導線）が無く、見積→予約化の手作業引き継ぎが使い勝手の課題。良い点: 予約状態タイムライン可視化、状態連動のボタン活性制御（誤操作防止）、handoff が相手リストに残る設計思想、認証キー統一。改善: 【高】経路設計待ちリストの画面・ナビ導線（US06 通知が UI 未達）、【高】見積→予約化の情報プリセット、【中】US13 差し戻しボタン欠如（進捗 100% とのずれ）、【中】操作後の手応え（引き渡し先への動線）、【低】待ちリストの期限昇順ソート。懸念: 待ちリストのロール別権限、概算料金の誤解防止、危険物絞り込みの見積/予約一貫性。スコープ外: cross-service E2E の既定 skip、voyageNumber リファクタ。
</details>

## 高指摘の対応方針

| # | 指摘 | 方針 | 備考 |
|---|------|------|------|
| H1 | DTO フィールドのアサーション漏れ | **修正する（即時）** | テスト改善のみ・低リスク・安全網 |
| H2 | API カタログのドリフト | **修正する（即時）** | architecture_backend.md に IT3 API を同期 |
| H3 | 経路設計待ちリストの画面・導線 | **保留（IT4）** | IT4 経路設計ワークベンチ（S14）の正式スコープ。retrospective-3 / IT4 計画に取り込む |
| H4 | 見積→予約化の情報引き継ぎ | **保留（IT4 / バックログ）** | UX 改善。US04 連携として IT4 計画で優先度協議 |

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-26 | 初版作成（5 エージェント並列レビュー統合） | k2works |
