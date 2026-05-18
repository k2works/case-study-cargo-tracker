# IT6 実装成果物 マルチパースペクティブレビュー（2026-05-18）

## レビュー対象

IT6 全成果物（コミット範囲 `317a114b^...HEAD`・15 コミット・102 ファイル・約 6,373 行追加）

- `trackingms` 新設（Aggregate / Event / Command / Service / Controller / Mapper / Flyway / ArchUnit）
- US18 公開追跡照会（S15）+ S16 追跡管理一覧（IT5 漏れ補完）
- TI06 US17 trackingms 移管（PUT `/api/v1/tracking/{tn}/status` + handlingms 旧エンドポイント Deprecation）
- ArchUnit ルール 4 サービス共通化（`@CommandHandler` / `@TargetEntityId`）
- ADR-0013 起票（JWT 時限トークン設計）
- フロントエンド S10/S17 連携（追跡トークン発行 UI）
- 荷役作業履歴画面のスタイル統一

レビュー実施者: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative（並列）

## 総合評価

**「変更を楽に安全にできる」基盤が誠実に整っており、IT4/IT5 で確立された品質水準を維持できている**。
ADR-0012/0013 の設計実装一致度は高く、ArchUnit 4 サービス共通化により IT5 P1 の再発を物理的に防ぐ仕組みが整った。
new_coverage 83.5% / new_violations 0 で SonarQube Quality Gate も PASS している。

一方、以下が IT7 で優先対処すべき課題として浮上した:

1. **荷主向け表示（公開画面 S15）の業務適合性不足** — 港名表示が UN/LOCODE のみ・タイムゾーン UTC・履歴並び順・「誤配送」公開・期限切れ時導線
2. **IT4 由来の `bookingms.TrackingNumber` 値オブジェクト乖離が IT6 で表面化** — サービス境界ごとに検証を弱める対症療法的対応で済んでいる
3. **暫定処理の積み残し** — `TrackingActivity.updateStatus` の未初期化フォールバック、`dummyValidUntil`、フロント自動 initialize
4. **運用ドキュメントへの新規環境変数反映漏れ**（`TRACKING_TOKEN_EXPIRATION_DAYS` 等）

## 改善提案（重要度順）

### 高（IT7 で対応すべき / リリース前に対処したい）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H-1 | 公開画面の現在位置・履歴で港名表示を実装 | `TrackingPublicView.tsx` L64-72/L92/L111 | user-rep | US18 受入 3「港名で表示する」未充足。荷主は UN/LOCODE を読めない |
| H-2 | 公開画面の日時整形が UTC のまま 9 時間ずれる | `TrackingPublicView.tsx` L183-185 (`toISOString()`) | user-rep | 推定到着が 9 時間ずれて表示され契約トラブルの可能性 |
| H-3 | 公開画面に「誤配送 あり/なし」を出している | `TrackingPublicView.tsx` L77 | user-rep | 社内オペレーション情報の流出。荷主の不安・クレーム誘発 |
| H-4 | 履歴並び順を「最新が上」に固定 | `TrackingPublicView.tsx` L107 | user-rep | API レスポンス依存で順序不定。荷主の自然な読み方と逆 |
| H-5 | 期限切れエラー時の問合せ先連絡導線 | `TrackingPublicView.tsx` L150-154 | user-rep | 「営業担当者まで」だけで連絡先が無く、運用パニックの原因 |
| H-6 | `bookingms.TrackingNumber` 値オブジェクトと実生成フォーマット乖離 | `bookingms/.../valueobjects/TrackingNumber.java` + `Cargo#issueTrackingNumber` | architect | サービス境界ごとに検証を弱める対症療法。IT4 由来の負債を IT7 で根本解消すべき |
| H-7 | `TrackingController#getTracking` の `dummyValidUntil` 表示が誤情報 | `TrackingController.java` L118-120 | programmer / writer | `now()+30d` を表示有効期限として返す。実 JWT と無関係でユーザー誤認 |
| H-8 | JWT secret デフォルト値の本番 Fail-Fast | `JwtTrackingTokenService.java` + `application.yml` | programmer | 本番で `dev-secret-...` のまま起動できる潜在リスク。`@PostConstruct` 等で profile 判定 |
| H-9 | TOKEN_TN_MISMATCH のフロント分岐テスト追加 | `TrackingPublicView.test.tsx` | tester | バックエンド 400 とフロント表示の対応が壊れたら検知不可 |
| H-10 | 境界値テスト（exp 経過直前 / `deliveredAt + 7d` 直前）追加 | `TrackingTokenServiceTest.java` L62-85 | tester | ±1 日でしか検証しておらず ADR-0013 の秒精度を保証していない |
| H-11 | 新規環境変数 `TRACKING_TOKEN_*` を運用ドキュメント / ADR-0006 に反映 | `docs/operation/開発環境セットアップ手順書.md` / `docs/adr/0006-heroku-deployment-setup.md` | writer | 運用担当者の設定漏れ |
| H-12 | handlingms 旧 PUT エンドポイントをクラス JavaDoc に追記 | `HandlingController.java` L44-52 | writer | Sunset 期限を見落とし IT7 削除事故の可能性 |

### 中（IT7 第 0 スプリント or 任意）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M-1 | `TrackingActivity.updateStatus` の未初期化フォールバックを削除 | `TrackingActivity.java` L88-99 | architect / programmer | 不変条件違反の暫定処理。`IllegalStateException` で明示拒否すべき |
| M-2 | `BookingId` 値オブジェクトを実際に使う or 削除 | `trackingms/.../BookingId.java` | architect | 定義済みだが全て `String` で扱われる中途半端な VO |
| M-3 | `TrackingTokenService#verify` の戻り値拡張で `dummyValidUntil` 解消 | `TrackingTokenService.java` | programmer / architect | `record VerifiedToken(TrackingNumber, expiresAt, validUntil)` |
| M-4 | フロント `BookingDetailPage` 自動 initialize を Event 駆動 ACL に置換 | `BookingDetailPage.tsx` L29-61 | architect / user-rep | ビジネス境界をフロントが代行している暫定設計 |
| M-5 | `STATUS_LABEL` / `formatDateTime` の重複解消 | `TrackingPublicView.tsx` + `TrackingList.tsx` | programmer | 同じ Record / 関数が 2 ファイルに複製 |
| M-6 | `sendAndWaitWithTimeout` を `shared-test` モジュールへ抽出 | 3 サービス Controller | programmer | bookingms / handlingms / trackingms で重複実装 |
| M-7 | Aggregate 状態遷移テストの拡充（パラメータ化） | `TrackingActivityTest.java` | tester | 9 状態 × 遷移の決定表型テスト不在 |
| M-8 | Read Model 投影の冪等性テスト追加 | `TrackingProjectionsEventHandlerTest.java` | tester | Replay / DLQ 再投入時の保証不在 |
| M-9 | `domain-model.md` / `data-model.md` の IT6 反映 | `docs/design/` | writer / architect | 計画 L377-383 / L534-537 で約束した同期が未実施 |
| M-10 | ADR-0013 ステータスを「承認済み」へ変更 | `docs/adr/0013-*.md` L7 | writer | 採用済みなのに「提案」のまま |
| M-11 | S16 一覧の絞り込み（状態 / 誤配送のみ）を追加 | `TrackingListPage.tsx` | user-rep | 件数増加時の運用性 |
| M-12 | TrackingTokenIssuer の `validUntil` を `formatDateTime` で整形 | `TrackingTokenIssuer.tsx` L116 | user-rep | UTC ISO 文字列のまま表示 |
| M-13 | 履歴の `handlingType` 日本語化（`HANDLING_TYPE_LABELS` 共有） | `TrackingPublicView.tsx` L110 | user-rep | 公開画面で英語コードが見える |
| M-14 | TrackingProjectionsEventHandler L60-69 の `occurred_at` を `LocalDateTime.now()` へ | `TrackingProjectionsEventHandler.java` | architect | 初期化 event の occurred_at に estimatedArrival を入れている |
| M-15 | `TrackingController` を Public / Internal に物理分離 | `TrackingController.java` | architect | 計画 L834-848 で `TrackingInternalController` 分離想定 |
| M-16 | IT7 申し送り事項を単一表に集約 | `iteration_plan-6.md` L153-167 / L904-914 / 4.4 | writer | 3 箇所に分散しており全貌把握困難 |

### 低（任意 / 改善余地）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L-1 | URL 生成のテンプレート化 `${cargo-tracker.tracking.public-url-template}` | `TrackingController.java` L144 | programmer |
| L-2 | `BookingDetailPage` の useEffect を専用フックへ抽出（ESLint 抑制を削除） | `BookingDetailPage.tsx` | programmer |
| L-3 | `LocalDateTime.now()` を `Clock` 注入に統一 | `TrackingController.java` L120 | programmer |
| L-4 | E2E `test.skip()` を環境変数で `fail` 切替 | `login-tracking.spec.ts` L54-58 | tester |
| L-5 | `alg: none` 攻撃などの署名改ざんパターン拡充 | `TrackingTokenServiceTest.java` | tester |
| L-6 | S16 ソート順検証テスト追加 | `TrackingList.test.tsx` / `TrackingControllerIntegrationTest.java` | tester |
| L-7 | `TransportStatus` 9 値全部のラベル変換テスト（`it.each`） | `TrackingPublicView.test.tsx` | tester |
| L-8 | Flyway 番号順を実装に合わせ計画書 L857-860 を修正 | `iteration_plan-6.md` | architect |
| L-9 | 用語集（`docs/design/glossary.md`）の追加 | プロジェクト全体 | writer |
| L-10 | 行クリックで S17 遷移（テーブル UX） | `TrackingList.tsx` | user-rep |
| L-11 | ⚠ / ✓ 絵文字を SVG/文字に置換 | `TrackingTokenIssuer.tsx` / `HandlingActivityList.tsx` | user-rep |
| L-12 | `HandlingController` の `Warning: 299` ヘッダーを除去（RFC 9110 で削除済み） | `HandlingController.java` | architect |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | architect: `TrackingActivity` 未初期化フォールバックを `IllegalStateException` で拒否すべき | programmer / 現実装: 後方互換のため `Command.trackingNumber()` を fallback に使用 | フロント自動 initialize に依存していると遅延中の挙動が変わる | フロント側を確実に initialize する設計（H-7 / M-3 / M-4）と合わせて、IT7 で削除 |
| 2 | writer: ADR-0013 ステータスを「承認済み」へ | 現状: 「提案」のまま | 採用されているが ADR が確定状態でない | IT7 第 0 スプリントで「承認済み」に昇格 |

## TDD 規律の観察

- **テストファーストの形跡**: コミット履歴上で Red コミットが残っていないため判別不能（tester 指摘）
- **テストと実装の同時投入**: 全コミットで `feat(xx)` + テスト追加が同梱されており、TDD 後付けの可能性を排除できない
- **次イテレーション以降の規律改善**: Red コミット（テストだけ失敗する状態）を 1 つ残すワークフロー導入を推奨

## 重要度「高」への対応方針提案

| # | 対応方針 |
|---|---------|
| H-1, H-13 | バックエンドで `portName` を必ず付与する経路を IT7 で整備（マスタの shared 化と合わせる） |
| H-2 | `formatDateTime` を `toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })` に修正（即時対応可能） |
| H-3 | 公開画面で `misrouted` を非表示にし、`MISROUTED` 状態時のみバナー表示（即時対応可能） |
| H-4 | フロント側で `events.slice().reverse()` で降順固定（即時対応可能） |
| H-5 | 「お問合せ: ◯◯」のテキスト固定 or 環境変数化（PM と要相談） |
| H-6 | IT7 第 0 スプリントで `bookingms.TrackingNumber` 修正 + `Cargo` 内部 VO 化 |
| H-7 | M-3 と一緒に対応 |
| H-8 | `JwtTrackingTokenService` で `@PostConstruct` + profile チェック追加 |
| H-9, H-10 | 即時対応可能（テスト追加のみ） |
| H-11, H-12 | ドキュメント更新（即時対応可能） |

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 2 / 中: 5 / 低: 3）</summary>

評価サマリー: IT5 規約遵守・TDD 規律・jjwt 依存閉込・ArchUnit 共通化など IT4/IT5 同等以上。一方 `dummyValidUntil`・JWT secret デフォルト・`*Record` POJO 化・Aggregate 暫定処理など意図的な妥協が複数残り IT7 負債回収計画が必要。

主要指摘: 上記表参照。`Clock` 注入を Controller まで一貫させる提案、`STATUS_LABEL` 重複、`sendAndWaitWithTimeout` 重複が中優先度。

</details>

<details>
<summary>xp-tester（高: 3 / 中: 5 / 低: 4）</summary>

評価サマリー: バックエンド 40 件 / フロント 137 件 / new_coverage 83.5% / new_violations 0 で品質基準良好。`TrackingTokenServiceTest` の `Clock.fixed` 設計は秀逸。一方、境界値の厳密化（exp 直前 / delivered_at + 7d 直前）・TOKEN_TN_MISMATCH のフロント検証・冪等性テストが抜けている。

主要指摘: 境界値 ±1 秒 / フロント `TOKEN_TN_MISMATCH` 表示 / 状態遷移パラメータ化 / Read Model 冪等性 / `alg: none` 攻撃検証。

</details>

<details>
<summary>xp-architect（高: 2 / 中: 7 / 低: 2）</summary>

評価サマリー: ADR-0012/0013 の設計実装一致度高。ArchUnit 共通化と Event 駆動 ACL 持ち越し判断は誠実。`bookingms.TrackingNumber` 乖離が IT6 で表面化したまま暫定対処で済んでおり、サービス境界ごとに検証を弱める運用に流れている。

主要指摘: bookingms VO 修正（IT7）・`BookingId` VO 使用統一・Aggregate 未初期化フォールバック削除・JWT secret デフォルト統一・`TrackingController` 分離・gatewayms 認証ルール堅牢性確認。

</details>

<details>
<summary>xp-technical-writer（高: 2 / 中: 6 / 低: 2）</summary>

評価サマリー: ADR-0013 と iteration_plan-6.md は IT4 品質水準。JavaDoc も ADR 参照込みで一貫。一方、新規環境変数の運用ドキュメント未反映・handlingms Deprecation のクラス JavaDoc 未記載・持ち越し事項の散在が課題。

主要指摘: `TRACKING_TOKEN_*` の運用ドキュメント反映・handlingms 旧エンドポイント説明追加・`dummyValidUntil` コメント補強・ADR-0013「提案」→「承認済み」・IT7 申し送り単一化。

</details>

<details>
<summary>xp-user-representative（高: 5 / 中: 8 / 低: 4）</summary>

評価サマリー: 受入基準は最低限満たすが、荷主向け表示の業務適合性が不足。港名表示・タイムゾーン・誤配送公開・履歴並び順・期限切れ導線は本リリース前に対応必須。社内管理側（S16/S10/S17/荷役）は実用に耐える。

主要指摘: 公開画面の港名表示 / UTC ずれ / 「誤配送」表示 / 履歴最新が上 / 期限切れ問合せ先 / handlingType 日本語化 / S16 絞り込み / TrackingTokenIssuer の有効期限整形。

</details>

## レビュー完了条件

- [x] 全エージェント（5 名）のフィードバック収集
- [x] 統合レポート作成
- [x] 重要度「高」12 件の対応方針を提案
- [x] レビュー結果ドキュメント化（本ファイル）
- [ ] 重要度「高」への対応決定（ユーザー判断待ち）

## 申し送り

本レビューで指摘された高優先度項目を **IT6 ふりかえり**・**IT6 完了報告書**・**IT7 計画** に反映すること:

1. **即時対応可能（IT6 内）**: H-2 タイムゾーン / H-3 誤配送非表示 / H-4 履歴順序 / H-9/H-10 テスト追加 / H-11/H-12 ドキュメント
2. **IT7 第 0 スプリント候補**: H-6 bookingms VO 修正 / M-9 設計書同期 / M-10 ADR 承認 / M-1 Aggregate フォールバック削除
3. **IT7 US 候補**: H-1 港名マスタ shared 化 / H-5 問合せ先導線 / M-11 S16 絞り込み
4. **IT7 持ち越し事項として既登録**: Event 駆動 ACL（3.4/3.5）・shared モジュール昇格・JWT denylist
