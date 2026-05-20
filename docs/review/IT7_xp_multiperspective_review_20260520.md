# IT7 XP マルチパースペクティブ コードレビュー

**レビュー日**: 2026-05-20
**対象イテレーション**: IT7（US19 追跡例外登録 / US20 例外解決）
**レビュアー**: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative

---

## レビュー対象

| ファイル | 種別 |
|---------|------|
| `trackingms/.../TrackingController.java` | 新規（330行・10エンドポイント） |
| `trackingms/.../commands/RegisterTrackingExceptionCommand.java` | 新規 |
| `trackingms/.../commands/ResolveTrackingExceptionCommand.java` | 新規 |
| `trackingms/.../events/TrackingExceptionRegisteredEvent.java` | 新規 |
| `trackingms/.../events/TrackingExceptionResolvedEvent.java` | 新規 |
| `trackingms/.../persistence/TrackingExceptionMapper.java` | 新規 |
| `trackingms/.../persistence/TrackingExceptionRecord.java` | 新規 |
| `trackingms/.../dto/RegisterTrackingExceptionRequest.java` | 新規 |
| `trackingms/.../dto/ResolveTrackingExceptionRequest.java` | 新規 |
| `handlingms/.../HandlingController.java` | 修正（未使用フィールド削除） |
| `handlingms/.../BookingEventAclHandler.java` | 修正（restricted identifier対応） |
| `TrackingControllerIntegrationTest.java` | テスト追加（+9件） |
| `HandlingControllerIntegrationTest.java` | テスト追加（+2件） |
| `frontend/TrackingExceptionForm.tsx` | 新規（例外登録フォーム） |
| `frontend/TrackingExceptionList.tsx` | 新規（例外一覧） |

---

## 総合評価

CQRS / Event Sourcing / Hexagonal Architecture の基本構造に沿った実装であり、Aggregate での Command/Event パターン・Projection による Read Model 分離・TDD による正常系/異常系カバレッジは良好に保たれている。SonarQube Quality Gate も PASS（new_coverage: 84.1%）を達成した。

一方で、**インフラ層 Record の REST 直露出**・**`exceptionType` の String 流通**・**`TrackingController` の責務肥大化（330行・10エンドポイント）**・**コマンドへの Bean Validation 不足**・**テストのアサーション不足（CommandGateway 送信内容を未検証）** という複合的な設計負債が蓄積している。加えてユーザー視点では、LOSS 時の緊急通知が実際には未送信・レスポンスに `escalated` が含まれない・例外一覧に登録者/対応者が含まれない等、業務投入前に解消すべき問題が複数ある。

---

## 改善提案（重要度順）

### 高（次イテレーション以降で対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | `TrackingExceptionRecord`（インフラ層）をレスポンス型から除去し `TrackingExceptionResponse` DTO を新設 | TrackingController.java:203、TrackingQueryService.java | programmer / architect / user-representative | DB スキーマ変更が API 契約に直撃。`escalated`・`createdAt` 等の内部フィールドが漏洩 |
| H2 | `exceptionType` を `ExceptionType enum` に型化（`isEscalated()` メソッド付き） | RegisterTrackingExceptionCommand.java、TrackingProjectionsEventHandler.java | programmer / architect | 不正値が Aggregate まで到達。Projection の `"LOSS".equals(...)` ハードコードを排除 |
| H3 | テストに `ArgumentCaptor` でコマンドの中身を検証（trackingNumber / exceptionType / operatorId など） | TrackingControllerIntegrationTest.java | tester | コントローラがコマンドを送らない実装でもテストが緑になる。仕様化テストとして機能していない |
| H4 | `registerException_operatorIdNullでsystemが使われる()` に `ArgumentCaptor` で `cmd.operatorId()=="system"` を検証 | TrackingControllerIntegrationTest.java | tester | 現状はレスポンスに operatorId が出ないため論理分岐が全く検証されていない |
| H5 | LOSS 登録時の実通知実装（メール or 管理者ダッシュボード未読バッジ） | TrackingProjectionsEventHandler / 通知チャネル | user-representative | フロントに「緊急通知が送信されます」と表示しているが、実際の通知は未実装で虚偽表示になる |
| H6 | POST レスポンスに `escalated` フィールドを追加 | TrackingController.java（registerExceptionレスポンス） | user-representative | LOSS 登録者が緊急扱いになったか即座に確認できない |
| H7 | 例外一覧レスポンスに `operatorId`（登録者）・`resolvedBy`（解決者）を追加 | TrackingExceptionRecord / Response DTO | user-representative | 複数担当者体制での引き継ぎ・二重対応防止に必須 |
| H8 | `TrackingController クラス JavaDoc` のエンドポイント一覧に US19/US20 の 3 エンドポイントを追加 | TrackingController.java:50-62 | technical-writer | クラス JavaDoc が陳腐化し新規参加者への発見可能性が低下 |
| H9 | `HandlingController.updateStatus` に Java `@Deprecated(since="IT6", forRemoval=true)` アノテーション付与 | HandlingController.java:179 | technical-writer | IDE の取り消し線・SonarQube 検出が機能していない |
| H10 | フロントの例外登録フォームに「発生日時」入力欄を追加（デフォルト=現在時刻、編集可） | TrackingExceptionForm.tsx | user-representative | バックエンドは `occurredAt` を受け取れるのにフロントが常に現在時刻を送る |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `TrackingExceptionController` として例外 API を分離（`/api/v1/tracking/{tn}/exceptions`） | TrackingController.java | programmer | 330 行・10 エンドポイントの責務肥大化。SRP 違反 |
| M2 | `operatorIdOrSystem(String raw)` プライベートメソッド抽出（3 箇所重複） | TrackingController.java:215-239 | programmer | DRY 違反 |
| M3 | `listExceptions_例外一覧取得()` に `jsonPath("$").isArray()` と件数検証を追加 | TrackingControllerIntegrationTest.java | tester | 現状は status().isOk() のみで中身を未検証 |
| M4 | `resolveException` 不変条件追加（exceptionId 存在確認・集合管理） | TrackingActivity.java | architect | 未登録 exceptionId への resolve が 200 を返す恐れ |
| M5 | Aggregate 内の `LocalDateTime.now()` を除去し `resolvedAt` をコマンドから受け取る | TrackingActivity.java:176、ResolveTrackingExceptionCommand.java | architect | 非決定性・Event Sourcing の再生再現性の破壊 |
| M6 | `TrackingExceptionMapper` を Read Port インターフェース経由にする | TrackingQueryService.java | architect | Hexagonal: Application がインフラに直接依存している |
| M7 | `occurredUnlocode` が null の場合に `tracking_summary.current_unlocode` を NULL 上書きしないよう Mapper に NULL ガードを追加 | TrackingSummaryMapper.xml | architect | 発生場所省略可の仕様なのに既存 location が消える |
| M8 | `listExceptions` の 404 レスポンスにボディ（`{errorCode, message}`）を付与し他エンドポイントと統一 | TrackingController.java:202-208 | tester / user-representative | 他 API は日本語メッセージ入りボディを返す。業務担当者が原因を判断できない |
| M9 | `RegisterTrackingExceptionRequest` / `ResolveTrackingExceptionRequest` に `@NotBlank` 等 Bean Validation を追加し Controller で `@Valid` を有効化 | 両 DTO ファイル | programmer / technical-writer | 空ボディ POST で 201 が返る。`HandlingController` は `@Valid` を使っているのに非対称 |
| M10 | `exceptionType` のコントローラ先行検証を追加（`PUT .../status` と同パターン） | TrackingController.java | programmer / technical-writer | 不正値が Aggregate まで素通り |
| M11 | `TrackingController.registerException` のメソッド JavaDoc に LOSS の副作用（escalated=true 自動付与・緊急通知発火）を明示 | TrackingController.java:210-213 | technical-writer | レスポンスに出ない副作用が API 利用者に伝わらない |
| M12 | `HandlingController` クラス JavaDoc のエンドポイント一覧に `status-history` / `snapshot` を追加し Deprecated 記述のネスト `<b>` を解消 | HandlingController.java:46-49 | technical-writer | クラス JavaDoc 陳腐化・HTML ネスト不正による描画不安定 |
| M13 | `PATCH /resolve` に `resolvedAt`（任意）フィールドを追加 | ResolveTrackingExceptionRequest.java | user-representative | 昨日解決したが今日登録するケースで報告書の日付が食い違う |
| M14 | CommandGateway タイムアウト時の 500 レスポンステストを追加 | TrackingControllerIntegrationTest.java | tester | `sendAndWaitWithTimeout` の例外分岐が完全未カバー |
| M15 | Aggregate ユニットテスト（`AggregateTestFixture`）で LOSS → escalated 判定・resolveException 不変条件を追加 | TrackingActivityTest.java | tester | 統合テスト過多・逆アイスクリームコーン。ロジックの仕様化がユニット層で欠落 |
| M16 | フロントの例外種別表示を日本語化（一覧で「DELAY」→「遅延（DELAY）」） | TrackingExceptionList.tsx | user-representative | フォームは日本語ラベルなのに一覧で英語に戻り UX 不整合 |
| M17 | 「緊急」バッジに赤背景・太字の強調スタイルを適用 | TrackingExceptionList.tsx | user-representative | LOSS は最重要案件なのに視覚的に目立たない |
| M18 | 解決ボタン押下時に確認モーダルを追加 | TrackingExceptionList.tsx | user-representative | 誤クリックで荷主報告に直結する操作が即発火する |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | `HandlerId.system()` ファクトリメソッドを導入し `SYSTEM_OPERATOR_ID = "system"` 定数を VO に閉じ込める | TrackingController.java:71 | programmer | ドメイン用語のシステム操作者が Controller 定数になっている |
| L2 | UUID 生成をドメイン/採番ポリシーとして ADR に明示 | TrackingController.java:224 | programmer | Controller で UUID を生成する採番責務の所在を明確化 |
| L3 | テスト内 magic value を定数化（`TestData.VALID_TRACKING_NUMBER` 等） | TrackingControllerIntegrationTest.java | tester | `"TRK-20260810-N1E2W3T4"` が各テストにベタ書きで意図が読みにくい |
| L4 | PATCH 系の `import static` 追加（MockMvcRequestBuilders.patch） | TrackingControllerIntegrationTest.java | tester | フルパスで呼んでおり可読性が低い |
| L5 | `occurredUnlocode` の UN/LOCODE フォーマットバリデーションを追加 | RegisterTrackingExceptionRequest.java | user-representative | 小文字 / フリーテキスト等の混入でレポート集計が破綻 |
| L6 | UN/LOCODE の港名サジェスト UI を追加 | TrackingExceptionForm.tsx | user-representative | 現場担当者は UN/LOCODE を暗記していない |
| L7 | 例外一覧に `resolvedAt` を表示追加 | TrackingExceptionList.tsx | user-representative | 「いつ解決したか」が一覧に出ない |
| L8 | `operatorId` 省略時を本番では 400 エラーにし、テスト用デフォルト "system" と分離 | TrackingController.java | user-representative | 責任所在追跡の業務要件と矛盾 |
| L9 | `TrackingExceptionRecord.escalated` をプリミティブ `boolean` に変更 | TrackingExceptionRecord.java:18 | technical-writer | Boolean（ボックス型）は null 許容に見えるが DB は NOT NULL DEFAULT FALSE が自然 |
| L10 | `responseStatus` の取り得る値（PENDING/RESOLVED/ESCALATED）を用語集に正規登録 | 用語集 / terminology.md | technical-writer | OpenAPI 化時の表記ゆれ温床 |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | **architect**: operatorId デフォルト化は Controller で妥当（外部入力の欠損補正） | **user-representative**: 本番では operatorId 省略を 400 エラーにすべき | テスト利便性 vs 本番責任追跡 | 環境別設定（本番は必須 / 統合テストプロファイルはデフォルト）で両立を推奨 |
| C2 | **programmer**: `TrackingExceptionController` への分離を推奨 | **technical-writer**: クラス JavaDoc の更新で当面対応可能 | 分離コスト vs 可読性改善 | 分離を優先（JavaDoc は腐りやすく構造的解決が長期コスト低） |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 5 / 中: 7 / 低: 3）</summary>

### 評価サマリー
US19/US20 の例外登録・解決 API は Axon Framework の CQRS/Event Sourcing アーキテクチャに沿って素直に実装されており、テストも先行して書かれている TDD 規律が守られた良質な変更。一方で TrackingController の責務肥大化と、ドメイン層に対する Bean Validation 不足、Layer 越境（infrastructure 型を Controller が直接公開）といった設計上の改善余地がある。

### 良い点
- TDD サイクルの遵守が明確（Aggregate ユニットテスト + Controller 統合テストの両層）
- コマンドの設計が良好（record で不変、@TargetEntityId、責務が単一）
- マジックストリングの定数化が一貫（SYSTEM_OPERATOR_ID 等）
- HandlingController の死コード除去（未使用フィールド削除）

### 懸念事項
- 複数例外の存在しない exceptionId への resolve 呼び出しで 200 が返る
- Authorization 未確認（管理者操作のはずが @PreAuthorize なし）
- exceptionType の非正規値が tracking_exception テーブルに到達できる
</details>

<details>
<summary>xp-tester（高: 4 / 中: 6 / 低: 2）</summary>

### 評価サマリー
レスポンス契約と CommandGateway への送信パスは妥当にカバーされているが、CommandGateway 送信コマンドの中身を ArgumentCaptor で検証していないため、「テストがついた安心感だけ与えて回帰を検知しない」状態。設計起因のテスト困難点（時刻外部依存・LOSS escalated 判定の Projection 集中等）も未解消。

### 良い点
- @MockitoBean で Axon Server 接続を回避した高速・独立な統合テスト
- DisplayName が日本語で仕様として読める
- seedTrackingSummary ヘルパで Arrange を共通化
- 404/400 のような失敗パステストが API ごとに対称に並ぶ

### 懸念事項
- SonarQube 84.1% の数値は「行が踏まれた」だけで「assert が意味を持つ」を保証しない
- ミューテーションテスト（PIT 等）で killed mutant 率が低く出る懸念
</details>

<details>
<summary>xp-architect（高: 3 / 中: 4 / 低: 1）</summary>

### 評価サマリー
Aggregate コマンドハンドラー・イベント発行・Projection の Write/Read 分離は CQRS / Hexagonal の規範に則っているが、インフラ層 Record の REST 直露出と exceptionType の String 流通が境界を貫通している。今が型強化と DTO 層整備のコスト最小タイミング。

### 良い点
- TrackingActivity が @CommandHandler で EventAppender を呼ぶ Write 側パスが正しい
- 未初期化集約への登録/解決を IllegalStateException で拒否
- Projection 側で Read Model 更新を集約（同一ハンドラ内で完結）
- operatorId デフォルト化の位置（Controller）は妥当

### 懸念事項
- 同一 exceptionId を 2 度 POST すると Event Store に重複・Read Model は PK 制約違反
- escalated=true がLOSS のみという仕様根拠が ADR に無い
- 解決後も currentStatus が EXCEPTION 固定
</details>

<details>
<summary>xp-technical-writer（高: 3 / 中: 5 / 低: 3）</summary>

### 評価サマリー
エンドポイント単位の JavaDoc とイベントの副作用記述は丁寧な一方、TrackingController のクラス JavaDoc が IT7 追加分（例外系 3 エンドポイント・US19/US20・関連 ADR）に追従できていない。HandlingController も status-history / snapshot 未掲載で同じパターンが再現している。

### 良い点
- TrackingExceptionRegisteredEvent の JavaDoc が非自明な副作用（TransportStatusUpdatedEvent 不発行）を明示
- HandlingController の Deprecated 記述が RFC 9745 / RFC 8594 への準拠を明示
- エンドポイント単位 JavaDoc に US 番号 + HTTP メソッド + パスが揃っている
- initialize JavaDoc が「IT6 暫定・TI06 で廃止」と寿命を明示

### 懸念事項
- @Deprecated アノテーション未付与により IDE の取り消し線・SonarQube S1133/S1874 が機能しない
- LOSS の緊急通知挙動が API 仕様書にもエラーレスポンスにも現れない
</details>

<details>
<summary>xp-user-representative（高: 4 / 中: 8 / 低: 5）</summary>

### 評価サマリー
US19/US20 の基本的な業務フロー（例外登録→一覧確認→解決）は API として動くが、追跡担当者が実際に安心して使うには重要要素が複数欠落している。特に LOSS の escalated=true が通知に繋がっていない点・例外一覧に登録者/対応者が出ない点・フロントの入力項目が業務実態に追いついていない点は本番投入前に対処すべき。

### 良い点
- 例外種別が DELAY/DAMAGE/LOSS の 3 つに整理され業務分類と一致
- 発生日時省略で現在時刻が入る設計が現場業務に合っている
- PENDING の例外だけ解決ボタンが出る構造（誤解決の防止）
- LOSS 選択時にフロントで「緊急通知が送信されます」バナーを表示

### 懸念事項
- 荷主向け公開追跡画面に例外情報がどう反映されるか不明（EXCEPTION を生の英語で見せるのは NG）
- LOSS 登録後の貨物状態遷移が一方通行（紛失発見→通常輸送復帰フローがない）
- responseStatus の "ESCALATED" 値が型定義にあるのに未使用
</details>

---

## 対応方針サマリー

| 対応区分 | 件数 | 推奨タイミング |
|---------|------|--------------|
| 高（H1〜H10） | 10件 | IT8 前半または専用タスク（TI09）として計画 |
| 中（M1〜M18） | 18件 | IT8〜IT9 で段階的に対応 |
| 低（L1〜L10） | 10件 | 余裕時またはバックログ |

### 最優先 3 件（ビジネスリスク）

1. **H5**: LOSS 緊急通知の実実装（フロントに「通知が送信されます」と書いてあるが未実装）
2. **H1+H2**: `TrackingExceptionRecord` 直露出 + `exceptionType` String 流通の解消（型システムで境界保護）
3. **H3+H4**: テストのコマンド内容検証追加（現状は仕様化テストとして機能していない）
