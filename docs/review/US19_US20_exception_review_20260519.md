# コードレビュー結果 — US19/US20 追跡例外処理

**日付**: 2026-05-19
**対象コミット範囲**: 1c5edf54..39ca6319（US19/US20 実装全体）
**レビュー対象ファイル数**: 26 ファイル、1,295 行追加

---

## 総合評価

US19/US20 の機能要件はすべて満たされており、Axon Framework の CQRS 流儀に沿った素直な実装です。一方、①レイヤー境界の混在（`TrackingExceptionRecord` を API レスポンスに直返し）、②escalation ルールのドメイン外漏れ（`"LOSS"` のハードコードが複数箇所に散在）、③`resolveException` での `LocalDateTime.now()` 直呼びが Event Sourcing 再生の一貫性を損なう可能性、④ドキュメントの誤記（`TrackingExceptionRegisteredEvent` の JavaDoc）の 4 点は現イテレーション内で対処を推奨します。

---

## 改善提案（重要度順）

### 高（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H-1 | `TrackingExceptionRecord` を API レスポンスに直返しせず `TrackingExceptionResponse` DTO を新設する | `TrackingQueryService.java:87`, `TrackingController.java:200` | programmer / architect | 永続化スキーマ変更が即 API 破壊変更になる。S16 の `TrackingListItemResponse` で既に分離できているのに本機能だけ抜けている |
| H-2 | `"LOSS".equals(event.exceptionType())` を Aggregate / enum で一元化する | `TrackingProjectionsEventHandler.java:96`, `TrackingExceptionForm.tsx:62` | programmer / architect | escalation ルールがドメイン外（Projection・UI）に散在。`ExceptionType` enum を新設し `isEscalation()` で意図を表現する |
| H-3 | `resolveException` の `LocalDateTime.now()` を Command に `resolvedAt` として持たせるかたちに修正 | `TrackingActivity.java:176` | programmer / architect | Aggregate 内でのシステム時刻取得はイベント再生のたびに `resolvedAt` が変わる。`registerException` が `command.occurredAt()` を使えているのに `resolveException` だけ不整合 |
| H-4 | `TrackingExceptionRegisteredEvent.java` の JavaDoc 誤記を修正（`TransportStatusUpdatedEvent(EXCEPTION)` を発行するという記述は誤り） | `TrackingExceptionRegisteredEvent.java:9-10` | architect / technical-writer | 実装は Projection が `updateCurrentStatus` を直接呼んでおり `TransportStatusUpdatedEvent` は発行しない。保守者を誤誘導する |
| H-5 | `registerException` の invalidateQueries に `['tracking', tn, 'exceptions']` も追加 | `useTracking.ts:195-197` | technical-writer | 登録直後に例外タブを開くと古い一覧のままになる。`useResolveTrackingException` 側（266行）と不整合 |
| H-6 | `on(TrackingExceptionResolvedEvent)` が空なのは意図的であることをテストで縛る | `TrackingActivity.java:182-184` | programmer / tester | 「解決後も EXCEPTION のまま」という非直感的仕様がコメントだけで保護されている。`assertThat(activity.getCurrentStatus()).isEqualTo(EXCEPTION)` を追加 |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M-1 | `java.time.LocalDateTime.now()` / `java.util.UUID.randomUUID()` の完全修飾 import を整理 | `TrackingActivity.java:176`, `TrackingController.java:221` | programmer | ファイル先頭に import が既にあり不統一 |
| M-2 | `ResponseEntity<?>` のワイルドカード戻り型を `ResponseEntity<List<TrackingExceptionResponse>>` に修正 | `TrackingController.java:200` | programmer / technical-writer | OpenAPI スキーマ生成が壊れる。他メソッドで型明示しているのに不揃い |
| M-3 | `(request.operatorId() == null \|\| request.operatorId().isBlank()) ? "system" : ...` の三項演算子を `operatorOrDefault(String)` ヘルパーに集約 | `TrackingController.java:229, 257` | programmer | 同パターンが 3 箇所重複 |
| M-4 | `exceptionType` を `'DELAY' \| 'DAMAGE' \| 'LOSS'` のユニオン型で型付けし、マジック文字列を排除 | `useTracking.ts`, `TrackingExceptionForm.tsx` | programmer / technical-writer | 型レベルで許容値を表現するとドキュメント不要になり DRY が成立 |
| M-5 | `TrackingExceptionList.tsx` の `isPending` が全行共有されているため、複数行ある場合に全行のボタンが disabled になる挙動を修正 | `TrackingExceptionList.tsx:30` | programmer | 行単位で mutation を分離するか `mutationKey` を使って独立させる |
| M-6 | `occurredAt` を生文字列で表示せず `formatDate` ユーティリティに揃える | `TrackingExceptionList.tsx:28` | programmer | S15 等の既存コンポーネントの慣習と不一致 |
| M-7 | `resolveException` の JavaDoc に「解決後も EXCEPTION 状態を維持」の意図を明記 | `TrackingActivity.java:159-161` | technical-writer | コマンド側 JavaDoc に書かれていないと API 利用側が気づけない |
| M-8 | `TrackingController.java` のエンドポイント JavaDoc に認証要件・レスポンスステータス・DTO への参照を追加 | `TrackingController.java:194-264` | technical-writer | `updateStatus` 等と粒度が揃っていない |
| M-9 | LOSS 登録に二段階確認（チェックボックス＋確認モーダル）を導入 | `TrackingExceptionForm.tsx` | user-representative | 緊急通知が誤送信されるリスク。保険・損害賠償の起点になる重大判断 |
| M-10 | 例外対応タブに未解決件数バッジを表示 | `CargoStatusUpdatePage.tsx` | user-representative | 複数貨物管理時に取りこぼし防止 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L-1 | `useTracking.ts` の fetch + adminToken + エラー処理パターンを `apiFetch<T>()` ヘルパーに集約 | `useTracking.ts` 全体 | programmer | 同パターンが 6 箇所で繰り返されている。120 行削減可能 |
| L-2 | `TrackingController` を `TrackingAdminController` と `TrackingPublicController` に分割 | `TrackingController.java`（328 行、9 エンドポイント） | programmer | SRP 違反傾向。認証ポリシーの違いとも一致する分割ライン |
| L-3 | `TrackingExceptionRecord` の 12 引数コンストラクタ呼び出しを Builder 化またはファクトリ化 | `TrackingProjectionsEventHandler.java:86-98` | programmer | 位置引数 12 個の多くが String で、型チェックでは順序誤りを検出できない |
| L-4 | 例外一覧の種別を英コードでなく日本語で表示する | `TrackingExceptionList.tsx` | user-representative | S18 では日本語併記しているのに一覧では英語のみ |
| L-5 | 解決内容入力欄を `input` → `textarea` かつ必須化する | `TrackingExceptionList.tsx` | user-representative | 実業務では複数行の記録が必要 |
| L-6 | S18 フォームに発生日時入力欄を追加（デフォルト=現在時刻、過去日時修正可） | `TrackingExceptionForm.tsx` | user-representative | 現状フォームに発生日時入力欄がなく後から気づいて遡及登録するケースに対応できない |

---

## 矛盾事項

なし（全エージェントの指摘は概ね一致）

---

## 懸念事項まとめ

| 懸念 | 重要度 | 説明 |
|------|--------|------|
| 状態モデルの不整合 | 高 | resolve しても Aggregate の `currentStatus` は EXCEPTION のまま。Read Model と Write Model のステータスが乖離する。`updateStatus` を別途打つことが暗黙の運用前提になっている |
| 例外 ID の冪等性 | 中 | 同じ `exceptionId` で複数回 Command が来ると Projection 側で PK 違反。フロントにリトライ機構がなく、ネットワーク再送で破綻する可能性 |
| LOSS 誤登録による緊急通知の誤送信 | 高 | 現状は 1 クリックで確定。取り消し手段が UI 上から見えない |
| `exceptionType` バリデーション不在 | 中 | `DELAY/DAMAGE/LOSS` 以外を渡しても DB レイヤまで素通りする |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 3 / 中: 7 / 低: 5）</summary>

### 評価サマリー
CQRS の役割分担が明確で Axon の流儀に沿っているが、レイヤー越境リーク・escalation ルールの散在・インライン import など、「変更を楽に安全にできる」観点から整理が必要な箇所が複数ある。

### 良い点
- Aggregate の不変条件（`trackingNumber == null` ガード）が一貫して揃っている
- `sendAndWaitWithTimeout` ヘルパーで CompletionException 畳み込みが DRY
- フロントの Hook は `useMutation + invalidateQueries` パターンが統一されている
- `TrackingExceptionForm` の `validate()` が純粋関数として切り出されており、テストが書きやすい

### 主要改善提案
- 【高】`TrackingExceptionRecord` を API に直返しせず DTO に詰め替える
- 【高】`"LOSS"` のマジック文字列を `ExceptionType` enum + `isEscalation()` に集約
- 【高】Aggregate 内 `LocalDateTime.now()` を Command に `resolvedAt` として持たせる
- 【中】インライン完全修飾 import の整理
- 【中】`operatorOrDefault()` ヘルパーで三項演算子の DRY 化
- 【低】`useTracking.ts` の fetch パターンを `apiFetch<T>()` に集約（120 行削減可能）

</details>

<details>
<summary>xp-tester（テスト品質レビュー）</summary>

### 評価サマリー
バックエンドのユニットテストは TDD サイクルを遵守し、Aggregate・Projection・ArchUnit の 3 層で正確に検証されている。フロントエンドはコンポーネントレベルのテストが揃っており品質は高い。一方、コントローラー統合テスト・E2E テストでの例外エンドポイントカバレッジが薄く、またいくつかのエッジケース（空の解決内容、exceptionId が存在しない場合の resolve など）がテストされていない。

### 良い点
- `TrackingActivityTest` に RegisterException・ResolveException の両コマンドがカバーされている
- LOSS 種別で `escalated=true` になることを専用テストで縛っている
- ArchUnit（`CommandArchitectureTest`）が `@TargetEntityId` の付け忘れを自動検出している
- E2E が DELAY と LOSS の 2 シナリオをカバーしている

### 主要改善提案
- 【高】`TrackingControllerTest` に `POST /exceptions` と `PATCH /exceptions/{id}/resolve` のテストを追加（現在未カバー）
- 【高】`TrackingExceptionList` で解決内容が空のまま「解決」ボタンを押した際のバリデーションテストが不足
- 【中】`on(TrackingExceptionResolvedEvent)` 後も `currentStatus == EXCEPTION` であることを assert するテストを追加
- 【中】`registerException` 重複 ID テスト（同じ exceptionId で 2 回 Command を送った場合）
- 【低】E2E の `test.skip()` が慢性化するとカバレッジ低下を見落とす。CI で skip 件数を監視する

</details>

<details>
<summary>xp-architect（高: 3 / 中: 3 / 低: 2）</summary>

### 評価サマリー
CQRS の書き込み側（Aggregate + Event）と読み取り側（Projection + Query Service）の分離は正しく維持されているが、`TrackingExceptionRecord` のレイヤー越境・`exceptionType` が String のままドメインルールが漏出・Aggregate 内の `LocalDateTime.now()` の 3 点が Event Sourcing アーキテクチャの整合性を脅かしている。

### 良い点
- 既存の `InitializeTrackingCommand` / `UpdateTransportStatusCommand` と同じパターンで Command/Event を追加しており、アーキテクチャの一貫性が高い
- `TrackingQueryService` への集約が維持されている（Controller が Mapper を直接呼ばない）
- Flyway マイグレーションが既存 V002 に `tracking_exception` テーブルを含めており、DB スキーマ管理が一元化されている

### 主要改善提案
- 【高】`TrackingQueryService` → `TrackingController` のレイヤー境界でインフラ型を露出しない
- 【高】escalation ルールを Aggregate / ドメインサービスに移動し、`TrackingExceptionRegisteredEvent` に `escalated: boolean` を含める
- 【高】Aggregate 内 `LocalDateTime.now()` は `Clock` 注入または Command への `resolvedAt` 移動で対処
- 【中】`PATCH /{tn}/exceptions/{id}/resolve` の `/resolve` アクション URL は RPC 的。REST の慣習では `PATCH /{tn}/exceptions/{id}` に `responseStatus: "RESOLVED"` を渡す方が自然

</details>

<details>
<summary>xp-technical-writer（高: 2 / 中: 4 / 低: 2）</summary>

### 評価サマリー
エンドポイント一覧・`@param` の許容値明記など基礎的なドキュメントは整っているが、`TrackingExceptionRegisteredEvent` の JavaDoc に実装と矛盾する記述があり即修正が必要。また OpenAPI 生成を壊す `ResponseEntity<?>` の混在も要対処。

### 良い点
- `TrackingController` クラスレベル JavaDoc がエンドポイント一覧と関連 ADR を併記
- `RegisterTrackingExceptionRequest` の `@param` で許容値とデフォルト挙動が明示
- `login-tracking-exception.spec.ts` のシナリオコメントが実行前提まで明記
- `useInitializeTracking` の JSDoc で「暫定・TI06+ で削除予定」と寿命を明記

### 主要改善提案
- 【高】`TrackingExceptionRegisteredEvent.java:9-10` の「`TransportStatusUpdatedEvent(EXCEPTION)` を発行」という誤記を修正
- 【高】`ResponseEntity<?>` → `ResponseEntity<List<TrackingExceptionResponse>>` で OpenAPI スキーマ生成を修正
- 【中】`exceptionType` を `'DELAY' | 'DAMAGE' | 'LOSS'` のユニオン型に変更（ドキュメント不要の設計へ）
- 【中】`useRegisterTrackingException` の `onSuccess` に `['tracking', tn, 'exceptions']` の invalidate を追加（クエリキー不整合のバグ）

</details>

<details>
<summary>xp-user-representative（高: 4 / 中: 5 / 低: 3）</summary>

### 評価サマリー
基本機能は揃っているが、例外処理は通常業務の数倍重い業務判断であり、現状の UI は誤操作防止・緊急通知の誤送信防止・対応漏れ防止が不足している。特に LOSS の 1 クリック確定は業務的に受け入れがたい。

### 良い点
- 3 種別を日本語併記で表示
- 状態更新・例外対応のタブ分離
- LOSS 時の警告バナー表示
- PENDING のみ解決ボタンが出る設計

### 主要改善提案
- 【高】LOSS 登録に二段階確認（チェックボックス＋確認モーダル）
- 【高】例外対応タブに未解決件数バッジ
- 【高】発生からの経過時間表示と経過時間ソート
- 【高】S18 フォームに発生日時入力欄（デフォルト=現在時刻）
- 【中】S19 一覧の種別を日本語表示
- 【中】解決内容入力欄を `textarea` かつ必須化

</details>

---

## 今イテレーションで対処すべき項目

| 優先度 | 項目 | 対応方針 |
|--------|------|----------|
| 対処する | H-4 JavaDoc 誤記修正（`TrackingExceptionRegisteredEvent`） | 即修正 |
| 対処する | H-5 `useRegisterTrackingException` の invalidateQueries 追加 | 即修正 |
| 対処する | H-6 `on(TrackingExceptionResolvedEvent)` のテスト追加 | TDD で追加 |
| 対処する | M-1 完全修飾 import の整理 | Checkstyle 通過のため |
| 次イテレーションへ | H-1 DTO 分離（`TrackingExceptionResponse`） | M-15 フィーチャバッファ候補 |
| 次イテレーションへ | H-2 `ExceptionType` enum 化 | 設計変更を伴うため |
| 次イテレーションへ | H-3 `resolvedAt` の Command 移動 | TI07/TI08 の Clock 統合と合わせて |
| 許容する | M-9 LOSS 二段階確認 | IT8 以降の UX 改善サイクルで |
