# IT4 trackingms 実装コードレビュー結果

## レビュー対象

- **イテレーション**: IT4
- **対象マイクロサービス**: trackingms（新規構築）
- **対象コミット範囲**: `62f1f3c6..5c08c845`（US14/US15/US17 実装 + SonarQube 品質改善）
- **レビュー日**: 2026-05-09

## 総合評価

ヘキサゴナルアーキテクチャ（ポート & アダプタ）の層分離が正しく実装されており、bookingms との構造的一貫性も高い。ドメインモデルの不変条件保護（unmodifiableList、requireNonNull）やコマンドオブジェクトの活用など、DDD の実践が丁寧に行われている。一方で、**追跡番号生成ロジックに本番環境での衝突リスク**があり、**例外の文字列判定による制御フロー**、**イベント種別カバレッジの不足**など、IT5 以前に対処すべき高重要度の問題が確認された。

---

## 改善提案（重要度順）

### 高（IT5 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | `System.currentTimeMillis() % 1_000_000` による追跡番号生成を DB SEQUENCE またはアトミックカウンターに変更する | `TrackingNumberService.java:45` | programmer / tester | 同一ミリ秒内の呼び出しや周期的衝突で重複が発生する。UNIQUE 制約エラー時のリトライもなく API 障害に直結 |
| H2 | `TrackingNumber` のバリデーションに後半 6 桁が数字であることのチェックを追加する | `TrackingNumber.java:14` | programmer | `TRK-abcdef` が通過してしまう。正規表現 `TRK-\d{6}` を使うべき |
| H3 | `TrackingStatusController` の例外メッセージ文字列比較を専用例外クラスに変更する | `TrackingStatusController.java:66-72` | programmer / architect / technical-writer | メッセージ変更でサイレントに壊れる。`TrackingActivityNotFoundException` を導入して型で分岐 |
| H4 | `TrackingNumberController` のエラー時レスポンスに `ErrorResponse` ボディを付与する | `TrackingNumberController.java:44` | programmer / technical-writer | 空の 400 が返りエラー原因をクライアントが把握できない |
| H5 | 荷役記録成功後の追跡番号をフォームに保持する（作業種別・日時のみクリア） | `HandlingActivityPage.tsx` | user-representative | 同一貨物への連続作業記録（受領→積込）で追跡番号を入力しなおす手間が発生する |
| H6 | API エラーレスポンスを解析して具体的なエラーメッセージをフロントに表示する | `HandlingActivityPage.tsx:45-48` | user-representative | サーバーエラーか番号ミスかがユーザーに区別できない |
| H7 | 手動状態更新で同一状態・逆行遷移を UI 側で制限する | `TrackingStatusPage.tsx:127-131` | user-representative | 引渡済み貨物を未受領に戻せるなど、ありえない遷移がオペレーションミスの温床になる |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `TrackingActivityEventService` で `TrackingEventType.valueOf()` の `IllegalArgumentException` をサービス層で catch してエラーを適切に処理する | `TrackingActivityEventService.java:38` | programmer | Controller 側のエラーハンドリングと噛み合っていない |
| M2 | `CUSTOMS -> UNKNOWN` マッピングの意図をコメントまたは ADR で明示する | `TrackingActivity.java:69` | programmer / architect | 業務上の意図が不明確で、専用ステータス `IN_CUSTOMS` が必要か判断できない |
| M3 | `save()` の戻り値にイベントを含める（現在は空リストで返す） | `TrackingActivityRepositoryImpl.java:48` | architect | 呼び出し元が save 直後のイベントを参照すると不整合になる |
| M4 | 新規イベント判定を `id == null` 依存から改善する | `TrackingActivityRepositoryImpl.java:76-87` | architect | 二重挿入リスクがある暗黙の規約を排除する |
| M5 | `TrackingNumberTest` に境界値テストを追加する（5 桁、7 桁、非数字、空文字、小文字プレフィックス） | `TrackingNumberTest.java` | tester | 境界値が 1 ケースのみで不十分 |
| M6 | `TrackingActivityTest` に `CLAIM` / `CUSTOMS` イベントのテストを追加する | `TrackingActivityTest.java` | tester | 5 分岐の switch のうち 2 分岐がカバーされていない |
| M7 | `TrackingActivityEventServiceTest` に不正イベント種別（例: `"INVALID_TYPE"`）のテストを追加する | `TrackingActivityEventServiceTest.java` | tester | エラーハンドリングが検証されていない |
| M8 | 作業種別に応じて航路番号フィールドを必須/任意に動的に切り替える | `HandlingActivityPage.tsx:164-166` | user-representative | 積込・荷降しは航路番号が業務上必須 |
| M9 | 作業場所入力にオートコンプリートまたはドロップダウンを追加する | `HandlingActivityPage.tsx:136-146` | user-representative | UN/LOCODE の手入力ミスが追跡データ品質に直結 |
| M10 | 追跡番号発行成功後に発行された追跡番号を画面に表示する | `BookingDetailPage.tsx:56-65` | user-representative | 発行直後に番号が確認できない |
| M11 | `UpdateTrackingStatusRequest.newStatus` の許容値を Javadoc またはアノテーションで明示する | `UpdateTrackingStatusRequest.java:9` | technical-writer | API 利用者がどの値が有効か判断できない |
| M12 | `tracking_handling_event` テーブルの `tracking_id` にインデックスを追加する | `V1__init.sql` | architect | FK のみではイベント増加時のパフォーマンス劣化リスク |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | `@ControllerAdvice` で共通エラーハンドリングを横断的に実装する | 全 Controller | programmer | 3 Controller で try-catch パターンが重複している |
| L2 | `updateStatus(null)` の例外テストを追加する | `TrackingActivityTest.java` | tester | requireNonNull で守られているがテストで保証されていない |
| L3 | `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` の必要性を見直す | Controller テスト全体 | tester | `@Sql` でクリーンアップ済みなら不要かもしれず、テスト実行速度に影響 |
| L4 | イベント履歴テーブルの `key` を配列インデックスから一意 ID に変更する | `TrackingStatusPage.tsx:159-160` | technical-writer / user-representative | React の差分検出が正しく動かない可能性 |
| L5 | `TrackingActivity` に `equals`/`hashCode` を実装する | `TrackingActivity.java` | tester | コレクション操作やキャッシュで想定外の挙動が発生しうる |
| L6 | `BookingDetailPage` の「経路を割り当て」リンクを状態に応じて非表示にする | `BookingDetailPage.tsx` | user-representative | TRACKING_ISSUED 以降でも表示される |
| L7 | IT4 スコープ外の作業種別（CUSTOMS/CLAIM）を非表示にするか注意書きを追加する | `HandlingActivityPage.tsx:115-128` | user-representative | US15 の受入条件には含まれておらず混乱を招く |

---

## 矛盾事項

複数エージェント間で相反する指摘は確認されなかった。高重要度の指摘（H3: 専用例外クラス）は programmer / architect / technical-writer の 3 エージェントが同様の観点で指摘しており、一致している。

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 4 / 中: 3 / 低: 3）</summary>

### 評価サマリー
全体的にヘキサゴナルアーキテクチャの層分離が適切で、ドメインモデルの不変条件保護も丁寧に実装されています。ただし、追跡番号生成ロジックに致命的な一意性の問題があり、Controller 層のエラーハンドリングに設計上の課題があります。

### 良い点
- 値オブジェクトの設計が堅実: `TrackingNumber`, `TrackingBookingId` が record で実装され、コンパクトなコンストラクタでバリデーションを行っている
- 集約ルートの不変条件保護: `TrackingActivity.getEvents()` が `Collections.unmodifiableList` を返し、イベント追加は `addEvent()` 経由でのみ可能
- ドメインロジックの集約内配置: `deriveStatus()` が集約ルート内にあり、状態遷移ロジックがドメイン層に閉じている
- ポートとアダプタの分離: `TrackingActivityRepository` インターフェースがドメイン層に定義され、依存性逆転原則を遵守
- コマンドオブジェクトの活用: `RecordHandlingActivityCommand`, `UpdateTrackingStatusCommand` が record で簡潔に定義されている

### 改善提案
- 【重要度: 高】`TrackingNumberService.java:45` — `System.currentTimeMillis() % 1_000_000` による追跡番号生成は一意性を保証できない
- 【重要度: 高】`TrackingNumber.java:14` — 後半 6 桁が数字であることを検証していない
- 【重要度: 中】`TrackingStatusController.java:67` — 例外メッセージ文字列による 404/400 分岐が脆い
- 【重要度: 中】`TrackingActivityEventService.java:38` — `IllegalArgumentException` のハンドリングが Controller と噛み合っていない
- 【重要度: 中】`TrackingActivity.java:69` — `CUSTOMS -> UNKNOWN` がビジネス要件として正しいか不明
- 【重要度: 低】`TrackingNumberController.java:44` — エラー時にボディなしの 400
- 【重要度: 低】全 Controller — エラーハンドリングパターンの重複（`@ControllerAdvice` 推奨）
- 【重要度: 低】`TrackingActivityRepositoryImpl.java:48` — save 後に空イベントリストで返却

### 懸念事項
- 追跡番号の重複リスク（本番環境で確実に発生）
- `TrackingActivityEvent.voyageNumber` の null 許容の意図が不明確
- `event.getId() == null` による新規イベント判定でイベント二重挿入リスク

### スコープ外の発見
- `ONBOARD_CARRIER` と `AWAITING_CLAIM` ステータスへの遷移手段がない
</details>

<details>
<summary>xp-tester（高: 2 / 中: 5 / 低: 2）</summary>

### 評価サマリー
テストピラミッドのバランスは良好で、ユニットテスト (domain/application) と統合テスト (controller) が適切に分離されている。ただし、ドメインモデルのイベント種別カバレッジに漏れがあり、プロダクションコードの追跡番号生成ロジックにテスタビリティ上の重大な問題がある。

### 良い点
- AAA パターンが一貫して守られており、テストの可読性が高い
- `@DisplayName` で仕様として読めるテスト名が統一されている
- `TrackingNumber` で null、不正形式、等価性、toString を網羅的に検証している
- `getEvents()` の不変リスト保証テストは防御的プログラミングの検証として良い
- Mock の使い方が適切で、`verify(never())` で副作用の不在も確認している

### 改善提案
- 【重要度: 高】`TrackingNumberService.java:45` — 非決定的な番号生成でテスト制御が不可能。番号生成戦略を注入可能なインターフェースに切り出すべき
- 【重要度: 高】`TrackingActivityTest` — `CLAIM` と `CUSTOMS` イベントのテストが欠落（5 分岐中 3 分岐のみ）
- 【重要度: 中】`TrackingNumberTest` — 境界値テスト不足（5 桁、7 桁、非数字、空文字、小文字プレフィックス）
- 【重要度: 中】`TrackingActivityEventServiceTest` — UNLOAD/CLAIM/CUSTOMS イベントと不正種別のテスト欠落
- 【重要度: 中】`TrackingActivityTest` — `updateStatus(null)` の例外テストがない
- 【重要度: 低】JSON レスポンス抽出で正規表現より `JsonPath` を使うべき
- 【重要度: 低】`@DirtiesContext(BEFORE_EACH_TEST_METHOD)` の必要性を見直す

### 懸念事項
- 追跡番号の一意性が保証されていない（並行リクエストで衝突確率高）
- 状態遷移のビジネスルール（NOT_RECEIVED から直接 UNLOAD 等）が仕様として明確でない

### スコープ外の発見
- `TrackingActivity` に `equals`/`hashCode` が未実装
- `TrackingBookingId` のバリデーションテストが未確認
</details>

<details>
<summary>xp-architect（高: 0 / 中: 4 / 低: 3）</summary>

### 評価サマリー
trackingms はヘキサゴナルアーキテクチャの依存関係方向を正しく守っており、bookingms との構造的一貫性も高い。いくつかの設計上の改善点はあるが、全体として変更容易性の高い堅実な実装。

### 良い点
- 依存関係の方向が正しい（DIP 遵守）
- 集約ルートの設計が適切（不変条件保護、状態遷移ロジックの集約内配置）
- Value Object の活用（プリミティブ型の乱用を回避）
- bookingms との構造的一貫性
- マイクロサービス間の分離が明確

### 改善提案
- 【重要度: 中】`TrackingStatusController:66-70` — 例外メッセージによる分岐がフラジャイル（専用例外クラスを導入すべき）
- 【重要度: 中】`TrackingActivityRepositoryImpl:48` — save 後に空イベントリストで返却
- 【重要度: 中】`TrackingActivityRepositoryImpl:76-87` — `id == null` による新規イベント判定が脆い
- 【重要度: 低】`TrackingNumberController:42-44` — エラー時にレスポンスボディがない
- 【重要度: 低】`HandlingActivityController:38` — `ResponseEntity<Object>` の戻り型
- 【重要度: 低】`findAll()` 等のリスト取得がない（将来の管理画面で必要になる可能性）

### 懸念事項
- `update` メソッドの `@Transactional` がリポジトリ実装側になく呼び出し元依存
- `tracking_handling_event.tracking_id` にインデックスがない
- `CUSTOMS -> UNKNOWN` マッピングの設計意図が不明確

### スコープ外の発見
- bookingms コントローラーに `@Valid` バリデーションがない
- `TrackingActivityRecord` / `TrackingHandlingEventRecord` の可変性確認が必要
</details>

<details>
<summary>xp-technical-writer（高: 2 / 中: 2 / 低: 2）</summary>

### 評価サマリー
全体として API 設計は直感的で、フロントエンドのユーザー向けメッセージも日本語で丁寧に実装されている。ただし、エラーハンドリングの一貫性とレスポンス型の型安全性に改善の余地がある。

### 良い点
- エンドポイントの命名が RESTful で直感的
- `EVENT_TYPE_LABELS` / `STATUS_LABELS` で enum 値を日本語ラベルに変換している
- 必須項目に `*` マークと placeholder によるフォーマット例がある
- 送信中の disabled 状態と操作フィードバックが適切

### 改善提案
- 【重要度: 高】`TrackingNumberController.java:44` — ボディなし 400 を `ErrorResponse` に統一
- 【重要度: 高】`TrackingStatusController.java:66-72` — メッセージ文字列比較による分岐を専用例外クラスに変更
- 【重要度: 中】`ResponseEntity<Object>` の型問題（OpenAPI での正しいスキーマ出力ができない）
- 【重要度: 中】`UpdateTrackingStatusRequest.newStatus` の許容値が不明（Javadoc または `@Pattern`）
- 【重要度: 低】`RecordHandlingActivityRequest.eventType` の許容値が不明
- 【重要度: 低】`ErrorResponse` に `timestamp` / `status` コードのフィールドがない

### 懸念事項
- Bean Validation 失敗時のエラーレスポンス形式が `ErrorResponse` と不一致の可能性
- メッセージ文字列依存の制御フローが国際化対応やメッセージ変更でサイレントに壊れるリスク

### スコープ外の発見
- `TrackingStatusPage.tsx:160` — イベント履歴の `key` に配列インデックスを使用
</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 5 / 低: 2）</summary>

### 評価サマリー
US14/US15/US17 の基本的な業務フローは実装されており、荷役担当者と追跡管理者が最低限の業務を遂行できる状態。ただし、現場での実用性を考えると改善すべきポイントが複数ある。

### 良い点
- 予約詳細画面からの追跡番号発行が自然な導線になっている
- 荷役記録フォームの必須項目が明示されている
- 記録成功時のフィードバックが具体的
- 追跡イベント履歴テーブルで過去の荷役作業を時系列確認できる
- フォームのクリアボタンがある

### 改善提案
- 【重要度: 高】荷役記録成功後の追跡番号保持（連続記録のため）
- 【重要度: 高】API エラーレスポンスを解析した具体的なエラーメッセージ表示
- 【重要度: 高】手動状態更新の遷移制限（同一状態・逆行遷移を UI で防止）
- 【重要度: 中】作業場所入力のオートコンプリート/ドロップダウン対応
- 【重要度: 中】IT4 スコープ外の作業種別（CUSTOMS/CLAIM）の非表示または注意書き
- 【重要度: 中】追跡番号発行成功後に発行番号を画面に表示
- 【重要度: 中】手動更新時に最低限の経路情報を表示（現在は予約 ID のみ）
- 【重要度: 低】イベント履歴の `key` を一意 ID に変更
- 【重要度: 低】`BookingDetailPage` の「経路を割り当て」リンクを状態に応じて非表示

### 懸念事項
- US15-7「作業場所が予定ルートと異なる場合の警告」が未実装（物流事故につながるリスク）
- US17-2「位置・日時の入力による手動更新」が部分的にしか実装されていない
- 追跡番号発行後の画面遷移先が不明確

### スコープ外の発見
- `BookingDetailPage` で TRACKING_ISSUED 以降でも「経路を割り当て」リンクが表示される
</details>

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-09 | 初版作成（IT4 完了レビュー） | - |
