---
title: イテレーション 5 ふりかえり
description: IT5（handlingms 新設・荷役作業記録）の KPT ふりかえり。11 SP 完了、E2E 10/10 PASS、SonarQube PASS（new_coverage 82.7%）。
---

# イテレーション 5 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 5 |
| **期間** | Week 9-10（2026-07-09 〜 2026-07-22 計画 / 実績 2026-05-18 1 日集中実装） |
| **ゴール** | `handlingms` を新設し、荷役作業記録（US15/US16）と貨物状態手動更新（US17）を実装することで Phase 2 追跡基盤を確立する |
| **計画 SP** | 11 |
| **実績 SP** | 11 |
| **達成率** | 100% |

---

## 結果サマリー

### 完了ストーリー（全 4 件）

| ID | ユーザーストーリー | SP | 結果 |
|----|------------------|----|----|
| TI04 | IT5 第 0 スプリント（handlingms 骨格・ADR-0012・IT4 レビュー H1-H3 対応） | 2 | ✅ 完了 |
| US15 | 荷役作業を記録する | 5 | ✅ 完了 |
| US16 | 引取作業を記録する | 2 | ✅ 完了 |
| US17 | 貨物状態を手動更新する | 2 | ✅ 完了 |
| **合計** | | **11** | **100%** |

### 品質メトリクス

| メトリクス | 結果 |
|-----------|------|
| バックエンド ユニットテスト（handlingms） | 25 件 PASS |
| バックエンド ユニットテスト（bookingms 既存） | 89 件 PASS |
| フロントエンド ユニットテスト | 121 件 PASS |
| Playwright E2E テスト | 10/10 PASS（17.0s） |
| ArchUnit テスト | PASS（`@TargetEntityId` 強制） |
| SonarQube Quality Gate | PASS（new_coverage 82.7%・new_violations 0・重複 2.84%） |

---

## KPT

### Keep（うまくいったこと）

#### K1: ADR-0012 による責務分離方針の事前合意が IT5 実装を安定化させた

IT4 ふりかえりの「IT5 計画前に Bounded Context の ADR 記録」を実施。ADR-0012「handlingms と trackingms の責務分離・Saga 適用方針」を IT5 着手前に承認したことで:

- US17 を handlingms に**暫定実装**する判断が明文化された（IT6 で trackingms に移管）
- Saga 採用は IT5 では見送り、bookingms ↔ handlingms ↔ trackingms 連携時に再評価という基準を確立
- `CargoSnapshot` ACL を介した Booking → Handling の依存隔離を設計レベルで決定

これにより、IT5 着手後の設計判断の迷いが発生せず、手戻りゼロで完了できた。

#### K2: IT4 コードレビュー指摘を第 0 スプリントで先に解消した

IT4 レビュー H1（ArchUnit テスト）・H2（sendAndWait タイムアウト）・H3（統合テスト整合）を、US15 実装より**先に**完了させた。

- TI04 で 6 時間程度のコストで H1-H3 を解消
- ArchUnit テストが新規 handlingms にも自動適用される設計（package 指定）
- bookingms の既存 89 件テストを破壊せず移行完了

「技術的負債は早期返済」の原則を実証。

#### K3: 値オブジェクト 8 種類に対する 17 件のテストが Quality Gate PASS の決め手

SonarQube 初回スキャンで new_coverage 67.9%（閾値 80%）未達。値オブジェクト（`UnLocode` / `Location` / `HandlingType` / `HandlerId` / `VoyageNumber` / `TrackingNumber` / `CargoSnapshot` / `ClaimVerification`）のテストを 17 件追加して **82.7%** へ到達。

- ドメイン層 PIT 75% 主指標も同時に達成
- テストの「強さ」と「網羅率」を両立する手段として有効

#### K4: 段階的 TDD サイクル（ドメイン → インフラ → REST → UI）が機能した

US15 実装で以下の順序を厳守:

1. 値オブジェクト・ドメインイベント・コマンド（型定義 + バリデーション）
2. Aggregate（Red-Green-Refactor サイクル）
3. Projection EventHandler（unit test）
4. MyBatis Mapper / XML（resultMap 検証）
5. REST Controller（統合テスト）
6. フロントエンド S20/S21（コンポーネントテスト）

各レイヤーで Green を確認してから次へ進んだため、最終 E2E までの統合バグは `@CommandHandler` 欠落 1 件のみだった（P1 として後述）。

#### K5: ADR-0012 の暫定実装が IT6 移管コストを設計時に明示した

US17 の `UpdateCargoStatusCommand` を handlingms 内に実装する際、ADR-0012 で「IT6 で trackingms へ移管」と事前に明記。`cargo_status_history` テーブルも IT6 で `tracking_event` に置き換える前提で設計。

移管コスト（推定 2-3 SP）を IT5 計画時点で IT6 申し送り事項に登録済み。「暫定実装＝技術的負債」を可視化することで、将来の意思決定の根拠が残った。

---

### Problem（うまくいかなかったこと）

#### P1: `@CommandHandler` 注釈が完全に欠落していた（IT4 H1 と類似パターン）

`HandlingActivity.register` / `updateStatus` static メソッドに `@CommandHandler` 注釈を付与し忘れていた。バックエンドのユニットテスト・統合テストでは `CommandGateway` をモックしていたため**検出できず**、E2E（実 Axon Server）で初めて発覚:

```
No handler was subscribed for command [RegisterHandlingActivityCommand]
```

IT4 H1（`@TargetEntityId` 欠落）と**同じカテゴリの「Axon 規約違反」**であり、ArchUnit ルールでカバーすべき。

#### P2: Aggregate static メソッドへの Spring Bean 注入で詰まった

最初は `HandlingActivity.register(command, CargoSnapshot snapshot, EventAppender appender)` という signature で `snapshot` を Controller から直接渡そうとしたが、Axon の Command Handler では「Command を第 1 引数、それ以降は Spring Bean 注入」が原則。`CargoSnapshot` は Bean ではないためエラー。

解決として `CargoSnapshot` を `RegisterHandlingActivityCommand` のフィールドに含めて Command 経由で渡す設計に変更。**Axon 5.1 の static Command Handler の制約を事前に理解していなかった**。

#### P3: SonarQube `record` 変数名で 7 件の警告（restricted identifier）

Java 14+ で `record` は予約識別子。変数名として使用すると SonarQube が警告を出す。`HandlingActivityRecord activity = new HandlingActivityRecord()` のような変数を 7 箇所で `var record =` と書いてしまった。

#### P4: `record` 変数名警告の修正で意味的な命名がブレた

`record` → `activity` / `snapshot` / `verification` / `history` のように個別に改名したが、命名が DTO ファイル名（`HandlingActivityRecord`）と一致しない箇所が残った。**「DTO 型を変数名で短縮表現する」一貫したルールが事前に決まっていなかった**。

#### P5: ArchUnit 1.3.0 が Java 25 をサポートせず Major version 69 エラー

`@TargetEntityId` ArchUnit テスト追加時に、ArchUnit 1.3.0 が Java 25 のクラスファイル（major version 69）をパースできずビルド失敗。1.4.1 へアップグレードして解決。

依存ライブラリの Java バージョン対応を事前に検証していなかった。

---

### Try（次に試すこと）

#### T1: ArchUnit ルールに `@CommandHandler` 強制を追加（P1 対策）

`HandlingActivity` の static メソッドで `*Command` を引数に取るものに `@CommandHandler` 注釈を必須化する ArchUnit ルールを bookingms / handlingms / 将来の trackingms / billingms に共通適用する。

```java
@ArchTest
static final ArchRule aggregateStaticMethodsAcceptingCommandMustHaveCommandHandler =
    methods()
        .that().areStatic()
        .and().arePublic()
        .and().areDeclaredInClassesThat().areAnnotatedWith(EventSourced.class)
        .and().haveRawParameterTypes(/* *Command で終わるクラス */, ...)
        .should().beAnnotatedWith(CommandHandler.class);
```

責任者: IT6 第 0 スプリント / 期限: IT6 着手時 / 期待効果: Axon Command Handler 欠落の CI 自動検出。

#### T2: Axon 5.1 Static Command Handler の引数制約を ADR に記録（P2 対策）

Axon 5.1 の Aggregate static Command Handler の引数規約をコーディングガイドに明文化:

- 第 1 引数: Command（必須）
- 第 2 引数以降: `EventAppender` または Spring Bean のみ
- 値オブジェクトを渡したい場合は **Command のフィールドに含める**

ADR または `docs/reference/コーディングとテストガイド.md` に追記。新しい Aggregate 追加時のチェックリストに含める。

#### T3: 「DTO 型 → 変数名」のネーミング規約を確立（P3/P4 対策）

`record` を変数名に使わない、加えて `*Record` クラスは以下のように変数名を統一:

- `HandlingActivityRecord activity` （Record サフィックスを除いた小文字キャメル）
- `CargoSnapshotRecord snapshot`
- `ClaimVerificationRecord verification`
- `CargoStatusHistoryRecord history`

`docs/reference/コーディングとテストガイド.md` のセクション「テストデータ管理」に追記する。

#### T4: 依存ライブラリの Java バージョン対応を `tech_stack.md` 確認リストに追加（P5 対策）

`tech_stack.md` の「実装着手前の確認チェックリスト（バージョン実在性）」に「Java major version 対応」項目を追加。ArchUnit のように JDK 25 のクラスファイルパースが必要なツールは、最新版での動作を IT0 で検証する。

#### T5: handlingms ↔ bookingms の Event 駆動 ACL を IT6 で正式実装（IT5 暫定の解消）

IT5 では `POST /api/v1/handling/cargo-snapshots` を暫定 REST ACL として実装。IT6 では Axon Event Bus 経由で `CargoBookedEvent` / `CargoRoutedEvent` を購読して `cargo_snapshot` を自動維持する。

責任者: IT6 担当 / 期限: IT6 着手時 / 期待効果: 暫定 REST エンドポイントの削除、責務分離の徹底（ADR-0012 準拠）。

---

## ベロシティ実績

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|--------------|---------|---------|--------|
| IT1 | 16 | 14 | 88% |
| IT2 | 14 | 14 | 100% |
| IT3 | 16 | 16 | 100% |
| IT4 | 25 | 25 | 100% |
| IT5 | 11 | 11 | 100% |
| **平均（IT2-5）** | | **16.5** | **100%** |

**IT6 推奨ベロシティ**: 12〜16 SP（IT4 特例 25 を除いた IT1-3-5 平均 13.7 + IT4 込み平均 16.5 のレンジ）。IT5 は意図的に低スコープ（持続可能ペース重視）だったため、IT6 は標準ペースに戻す。

---

## IT6 への申し送り事項

### 持越しタスク

| タスク | 元 ID | 優先度 |
|--------|--------|--------|
| US04-r1 荷主 ID マスタ検索 | IT3 繰越し | 低（IT6+ 任意） |
| US05-r1 IMO クラス・UN 番号ドロップダウン化 | IT3 繰越し | 低（IT6+ 任意） |
| US24-r1 出発日 < 到着日チェック強化 | IT3 繰越し | 低（IT6+ 任意） |
| US17 を trackingms へ移管 | IT5 暫定 | 高（推定 2-3 SP） |
| `cargo_status_history` → `tracking_event` データ移行 | IT5 暫定 | 高（Flyway スクリプト） |
| `/api/v1/handling/activities/{trk}/status` 旧 API の Deprecation | IT5 暫定 | 中（IT6-IT7） |
| `POST /api/v1/handling/cargo-snapshots` を Axon Event 購読に置換 | IT5 暫定 | 高（IT6 設計時） |

### IT4 レビュー中優先度指摘（M1〜M6）の IT6 取り込み

| ID | 内容 | IT6 取り込み判断 |
|----|------|----------------|
| M1 | `data-testid` 属性を UI 要素に付与 | IT6 着手 |
| M2 | gatewayms predicates を YAML リスト形式に変更 | IT6 着手 |
| M3 | Tracking Number フォーマット仕様を ADR に記録 | IT6 着手（trackingms 設計の前提） |
| M4 | `sendAndWait` 変更理由を Javadoc に追記 | IT6 着手 |
| M5 | `NotifyRouteCommand` に IT5+ メール送信予定を記載 | IT6 着手 |
| M6 | `sendAndWait` 遅延時の処理中インジケータ追加 | IT6 着手 |

### IT6 で注意すべきリスク

1. **`@CommandHandler` 欠落の再発（P1）**: ArchUnit ルール（T1）を IT6 第 0 スプリントで実装。trackingms / billingms の Aggregate にも適用する。
2. **trackingms 新設のインフラコスト**: handlingms と同様に Spring Boot + Axon + Flyway + gateway 登録を一括で実施。IT5 で確立したパターン（11 ファイルセット）を再利用する。
3. **CargoSnapshot ACL の真の実装**: `POST /api/v1/handling/cargo-snapshots` を `@EventHandler(CargoBookedEvent)` に置換する。bookingms の Event を handlingms / trackingms 双方で購読する設計を ADR で確定する。
4. **US17 の移管に伴う API 互換性**: 旧エンドポイント `/api/v1/handling/activities/{trk}/status` の Deprecation Warning を 1 イテレーション期間維持し、フロントエンドの追従を待つ。

### 申し送りメモ

- IT5 は ADR 駆動・第 0 スプリント先行・段階的 TDD の効果で実装期間が大幅短縮（計画 2 週間 → 実績 1 日集中）。
- `@CommandHandler` 欠落は IT4 H1（`@TargetEntityId` 欠落）と類似パターン。**Axon 規約の自動検証を ArchUnit に集約**することが Phase 2 全体の品質維持に重要。
- ADR-0012 の暫定実装パターン（US17 を handlingms → IT6 で trackingms 移管）は他の責務分離にも適用可能な「先延ばし可能な技術的負債の可視化」テンプレートになる。
- ベロシティが IT5 で 11 SP に下がったのは意図的（持続可能ペース）。IT6 は標準 13-16 SP に戻す。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-18 | 初版作成（IT5 完了後・KPT 5K/5P/5T・@CommandHandler 欠落の教訓を反映） | AI Agent（XP PM） |
