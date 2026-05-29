# イテレーション 6 ふりかえり（KPT）

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT6（追跡照会 + 例外処理、Phase 2 / 2） |
| **期間** | 2026-07-30 〜 2026-08-12（計画 2 週間）/ 2026-05-29（実績 1 日、Ralph Loop 7 iterations） |
| **実績** | 9/9 SP（コミット分 100%）、累計 60/76 SP（79%）、Phase 2 完了 |
| **対象 US** | US18（追跡情報照会）/ US19（遅延例外処理）/ US20（破損・紛失例外処理） |
| **コミット数** | 22 件（うち 13 件が本体実装、5 件が ADR / 計画書、4 件が品質改善・テスト修正）|
| **規模** | trackingms + frontend で 52 ファイル / 約 4,081 行追加（IT5 持ち越し基盤改善は除外）|

## サマリー

trackingms に **公開追跡照会（JWT 時限署名トークン）** と **例外管理（DELAY / DAMAGE / LOSS）** を追加し、Phase 2（Release 2.0）の最終インクリメントを完成させた。**Ralph Loop 7 iterations** で計画→実装→レビュー→品質ゲートまでをセッション内に閉じ、9 SP を 100% 達成。

US18 は HS256 JWT + `PublicTrackingTokenFilter`（OncePerRequestFilter で Spring Security 非依存）で公開エンドポイントを実装。Filter 単体 6 件 + 統合 5 件 + Vitest 6 件 + E2E 3 件で網羅。US19/US20 は `TrackingActivity` 集約に例外エンティティを内包し、`RegisterTrackingExceptionCommand` で「状態 → EXCEPTION + 例外登録 + (LOSS のみ) escalation」の 3 イベントを順次 apply（ADR-0012 集約発火型を実践）。LOSS の自動 escalation はドメインモデル M5 の不変条件としてエンティティに閉じた。

IT6 着手前に **ADR-0012 / 0013 / 0014** を起票し、IT5 ふりかえり Try T2/T3 + US18 設計判断を ADR で先行確定。設計セクションを iteration_plan-5.md パターン（PlantUML 7 種 + Salt 図 3 種 + API 表 + ディレクトリ構成）に拡充し、validating-iteration-plan 8 ステップ検証を 2 回実施して整合性を担保した。

SonarQube Quality Gate **PASS（OK）**：new_coverage 74.5% / new_duplicated 0.74% / new_violations 0 件。マルチパースペクティブレビューで指摘された Code Smell 4 件はその場で修正。レビュー全体（高 9 / 中 11 / 低 8 件）は IT7 序盤対応として整理した。

## Keep（継続すること）

- **ADR を IT 着手前に起票する規律**：ADR-0012 / 0013 / 0014 を 1 iteration 目で起票し、設計判断の根拠を実装より先にドキュメント化。新メンバーが ADR から仕様意図を逆引きできる構造を維持
- **設計書を iteration_plan-5.md パターンで充実化**：「主要設計方針 → ドメインモデル PlantUML → 集約の不変条件 → 状態遷移 → データモデル ER → ユーザーインターフェース Salt 図 → API 設計 → イベントフロー → ディレクトリ構成 → バリデーション / ロール」の 10 セクション構造を踏襲。validating-iteration-plan 2 回で整合性を担保
- **TDD インサイドアウトの徹底**：TrackingTokenService（10 件、Clock 注入）→ TrackingException エンティティ（14 件）→ TrackingActivity 集約（Axon Test Fixture 8 件追加）→ Controller（11 件 + 5 件統合）→ フロント（Vitest 12 件 + Playwright 7 件）の順で Red-Green-Refactor。テストファースト + identity check 対応（equals/hashCode）まで TDD 完結
- **集約発火型の方針一貫適用**：ADR-0012 で「集約発火型に統一」と決定し、US19/US20 で `RegisterTrackingExceptionCommand` 内で 3 イベントを順次 apply（status → EXCEPTION + registered + escalated）。CargoDeliveredEventPublisher の二段イベントは負債として IT7 持ち越しを review に明記
- **Spring Security 非依存の最小実装**：trackingms に Spring Security が未導入であることを発見し、`OncePerRequestFilter + FilterRegistrationBean` で公開エンドポイントを実現。「IT8 で SecurityFilterChain に統一移行」を FIXME(IT8) で全箇所明示し、スコープを膨らませない判断を選択
- **業務不変条件をエンティティに閉じる**：LOSS で `escalated = true` 自動設定、ResponseStatus 単方向遷移、RESOLVED で `resolution` 必須を `TrackingException` エンティティ内に閉じる。Controller / EventHandler は単純委譲のみで業務ロジック分散を防止
- **マルチパースペクティブレビュー → Quality Gate → 修正の即時化**：5 XP エージェントを並列起動して 21 コミット範囲をレビュー、SonarQube Quality Gate FAIL → Code Smell 4 件を 30 分以内に修正して PASS まで仕上げ。レビュー結果を `docs/review/IT6_review_20260529.md` に統合
- **Ralph Loop での max-iterations 設定**：promise/max なしのデッドロック回避として、各 Ralph Loop 起動時に AskUserQuestion で `max-iterations: 3` を確認。memory「ralph-loop-promise-none-deadlock」の教訓を実践し、各 iteration で 1 タスクずつ確実に区切る

## Problem（問題点）

- **タスク 0.1（Testcontainers Reusable + H6/H7）が未着手**：IT5 ふりかえり Try T1（最優先）として計画したが、build.gradle / JVM 起動オプション全体に影響する構造変更で「確認必須」事項と判断して保留。**`./gradlew :trackingms:test` フル実行で TrackingControllerIntegrationTest が event store 汚染で flaky 失敗する問題が再発**（memory `trackingms-spring-context-event-store-pollution` に記録）
- **タスク 0.5（handlingms フォールバック投影 DLQ 風 retro-update）も未実装**：ADR-0012 に方針記載のみで、`pending_handling_activity` 待避テーブル + retro-update の実装は IT7 に持ち越し。IT5 H2 の根本対処が依然未完
- **CargoDeliveredEventPublisher の二段イベントが残存**：ADR-0012 で「集約発火型に統一」と決めながら、既存 publisher の撤去・集約発火型移行は手付かず。**ADR と実装の整合性が割れた状態**で IT6 完了したのが最大の負債（review H1）
- **EXCEPTION_REGISTRABLE_STATES に EXCEPTION が含まれない**：programmer review H4 で指摘。複数例外の同時登録（並行 DELAY + DAMAGE 等）が業務的に拒否される設計だが、受入基準「複数例外登録可」が前提か PdM 確認が必要
- **architecture_backend.md API カタログに IT6 追加 7 endpoint 未記載**：technical-writer review H6。実装者が「未実装」と誤解するリスク。完了報告書クローズ前に対応が望ましかった
- **S15 公開照会 403 文言が一律**：user-rep review H7。「アクセスできません」だけだとトークン期限切れ vs URL 改変が判別不能で、運用に出すと営業窓口への問い合わせ増加
- **TrackingControllerIntegrationTest の flaky 再発**：IT5 H6 で `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` を入れたが、新設の `PublicTrackingControllerIntegrationTest` が同じ ApplicationContext を共有することで flaky が悪化。**IT5 H6 の対処が部分的だった**ことが IT6 で判明
- **`now == null` 死コード**：programmer review H2。`TrackingActivity.java:172-175` で `LocalDateTime.now(clock)` が null になり得ないのに防衛コードを書いた。「未使用変数の警告抑制」目的だが、設計意図が不明瞭
- **handleCompletionException + unwrap が Controller 間で完全コピー**：programmer review H3。`TrackingController` と `TrackingExceptionController` で同じ DRY 違反。今後 Controller 追加で増殖リスク
- **E2E spec のハードコード日時が現実時刻と乖離**：cross-service.spec.ts:338 で `occurredAt: '2027-01-09T10:00:00'` が「現在時刻以前」バリデーションに引っかかった。IT5 spec から持ち越された問題で、長期テストには動的計算が必要

## Try（次に試すこと）

### T1（IT7 序盤に対応・最優先 - 持ち越し）

**Testcontainers Reusable + 一意 topic prefix で Kafka container race を構造的解決**（IT5 Try 持ち越し）

- IT6 で着手できなかった IT5 T1 を IT7 で確実に実施
- 加えて H6 完全解決：`./gradlew :trackingms:test` フル実行で event store 汚染を解消（IT5 H6 の対処が部分的）
- `PublicTrackingControllerIntegrationTest` を `@WebMvcTest + MockBean` に分解（review M7）

### T2（IT7 で対応）

**CargoDeliveredEventPublisher 廃止 + 集約発火型へ移行**（review H1）

- ADR-0012「集約発火型に統一」の自己整合回復
- `TrackingActivity.handle(UpdateTransportStatusCommand)` 内で DELIVERED 遷移時に `CargoDeliveredEvent` を直接 apply
- `tracking_summary.delivered_published_at` フラグ列で冪等化（既存）と組合せて二度発行を防止

### T3（IT7 で対応）

**ADR-0016: @ProcessingGroup 一斉改名と token 移行手順**

- 既存 9 グループを `cross-` / `local-` / `outbound-` prefix に一斉改名
- `token_entry` テーブルの re-consume + event store リプレイ対策手順を ADR 化
- ArchUnit テストで「prefix 規約」を構造的にガード（review M1）

### T4（IT7 で対応・review H4 と統合）

**TrackingException 関連の設計改善**

- `EXCEPTION_REGISTRABLE_STATES` に EXCEPTION 自身を追加（複数例外同時登録の許可、PdM 確認後）
- `TrackingException.equals` を identity-only（`exceptionId` のみ）に戻し、Fixture 比較は `TrackingExceptionSnapshot` 値オブジェクトに分離（review M2）
- `replay()` の 8 引数を `TrackingExceptionSnapshot` 引数オブジェクトへ移行（@SuppressWarnings 解消、review L1）

### T5（IT7 で対応・review H7-H9）

**S15 / S19 UX 改善**

- S15 公開ページの 403 文言を「期限切れ / URL 改変 / 通信エラー」で差別化（review H7）
- S15 配送完了状態を緑バッジで強調（review H8）
- S19 RESOLVED 行の赤背景を解除、escalation 表示は「通知済」のみ残す（review H9）
- S19 行クリック戻り動線：URL クエリで filter state を保持（review M10）

### T6（IT7 で対応・technical-writer review H6）

**architecture_backend.md API カタログ更新**

- IT6 追加 7 endpoint を反映（POST /token、GET /public/tracking、例外関連 5 件）
- domain-model.md / data-model.md / ui_design.md の「反映必要」マーカー 4 件をクローズ

### T7（IT7-IT8 で対応）

**Controller 間の例外ハンドラ抽出**（review H3）

- `handleCompletionException + unwrap` を `@RestControllerAdvice` に抽出
- `TrackingCommandExceptionAdvice` として `trackingms.interfaces.rest` パッケージに配置

### T8（IT8 で対応）

**Spring Security 統一導入 + 公開トークン鍵管理本格化**

- trackingms 全体に Spring Security を導入し、`PublicTrackingTokenFilter` を `SecurityFilterChain` に統合
- ROLE_TRACKER / ROLE_ADMIN の認可を `@PreAuthorize` で各 Controller に追加
- ADR-0015 で AWS Secrets Manager + 四半期ローテーションへの切替を起票
- アプリ層 Bucket4j で 60 req/min のレート制限を実装（ui_design.md L739）

### T9（IT7 / IT8 - 既知の継続課題）

**Ralph Loop での max-iterations 明示の継続**

- memory「ralph-loop-promise-none-deadlock」の教訓を継続適用
- 各セッション開始時に AskUserQuestion で promise/max を確認するパターンを定着

## メトリクス

| カテゴリ | 計画 | 実績 | 達成率 |
|---------|------|------|--------|
| Story Points（コミット）| 9 | 9 | 100% |
| 累計（Release 2.0 = IT5 + IT6）| 19 | 19 | 100% |
| 全 IT 累計 | 76 | 60 | 79% |
| バックエンドテスト件数 | - | trackingms 60+ 件 PASS（IT6 追加分） | - |
| フロントエンドテスト件数 | - | 32 ファイル / 205 件 PASS | - |
| E2E spec 件数 | - | public-tracking 3 + exception-management 7 = 10 件追加 | - |
| SonarQube Quality Gate | PASS | **PASS（OK）** | ✅ |
| Code Smell（IT6 追加分修正後）| 0 | 0 | ✅ |
| new_coverage | 70%+ | 74.5% | ✅ |
| new_duplicated | <3% | 0.74% | ✅ |
| マルチパースペクティブレビュー | 5 視点 | 5 視点（高 9 / 中 11 / 低 8）| ✅ |

## 関連ドキュメント

- [IT6 計画](iteration_plan-6.md)
- [IT6 マルチパースペクティブレビュー](../review/IT6_review_20260529.md)
- [ADR-0012 cross-service 冪等性](../adr/0012-cross-service-idempotency-and-transactions.md)
- [ADR-0013 公開追跡照会トークン](../adr/0013-public-tracking-token.md)
- [ADR-0014 @ProcessingGroup 命名規約](../adr/0014-processing-group-naming.md)
- [IT5 ふりかえり](retrospective-5.md)
