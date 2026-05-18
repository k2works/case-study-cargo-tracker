---
title: イテレーション 6 ふりかえり
description: IT6（trackingms 新設・US18 公開追跡照会・S16 補完・TI06 US17 移管）の KPT ふりかえり。8 SP 完了、E2E 11/11 PASS、SonarQube PASS（new_coverage 83.5%）、マルチパースペクティブレビューで指摘 40 件を集約。
---

# イテレーション 6 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 6 |
| **期間** | Week 11-12（2026-07-23 〜 2026-08-05 計画 / 実績 2026-05-18 1 日集中実装） |
| **ゴール** | `trackingms` を新設し、JWT 時限トークンによる公開追跡照会（US18）と US17 の trackingms 移管（TI06）を実装することで Phase 2 追跡基盤を完成させる |
| **計画 SP** | 8 |
| **実績 SP** | 8 |
| **達成率** | 100% |

---

## 結果サマリー

### 完了ストーリー / タスク

| ID | ユーザーストーリー / タスク | SP | 結果 |
|----|------------------|----|----|
| TI05 | IT6 第 0 スプリント（ADR-0013・trackingms 骨格・ArchUnit 4 サービス共通化） | 2 | ✅ 完了 |
| US18 | 追跡情報を照会する（公開 URL・JWT 時限トークン） | 5 | ✅ 完了 |
| TI06 | US17 trackingms 移管（コア）+ handlingms Deprecation | 1 | ✅ 完了 |
| **合計** | | **8** | **100%** |

### 追加実施（SP 外）

| 項目 | 概要 |
|------|------|
| S16 追跡管理一覧 | IT5 漏れの補完実装（管理者用 `/tracking` 一覧 + 認証ルール調整） |
| HandlingActivityList スタイル統一 | 他の一覧画面に合わせて Card デザイン・色・パディング統一 |
| E2E + 品質確認 | Playwright `login-tracking.spec.ts` 追加 + SonarQube Quality Gate PASS |
| マルチパースペクティブレビュー | XP 5 エージェント並列で 40 件指摘集約（高 12 / 中 16 / 低 12） |
| レビュー高優先度 7 件即時対応 | JST 表記 / 誤配送バナー / 履歴降順 / 境界値テスト / Deprecation JavaDoc / 環境変数ドキュメント |

### 品質メトリクス

| メトリクス | 結果 |
|-----------|------|
| バックエンド ユニットテスト（trackingms） | 43 件 PASS（IT6 で新規追加） |
| バックエンド ユニットテスト（既存サービス全体） | 全 PASS |
| フロントエンド ユニットテスト | 142 件 PASS（IT6 で +17 件） |
| Playwright E2E テスト | 11/11 PASS |
| ArchUnit テスト | PASS（`@TargetEntityId` + `@CommandHandler` 強制を 4 サービス共通化） |
| SonarQube Quality Gate | PASS（new_coverage 83.5% / new_duplicated 2.87% / new_violations 0 / new_security_hotspots 100%） |
| コードレビュー指摘 | 40 件（高 12 / 中 16 / 低 12）→ 即時対応 7 件、残り IT7 持ち越し |

---

## KPT

### Keep（うまくいったこと）

#### K1: ADR-0013（JWT 時限トークン設計）を IT6 着手前に承認した

IT5 K1（ADR-0012 事前合意）の規律を継承。第 0 スプリント（TI05）で ADR-0013 を起票してから実装に入ったため、以下の設計判断が後で揺るがず安定した:

- JWT クレーム（`tn` / `exp` / `iss` / `sub`）と署名アルゴリズム（HMAC-SHA256）
- 失効ルール `min(exp, deliveredAt + grace)`
- `authms` と秘密鍵 `JWT_SECRET` を共有する運用方針
- HTTP エラーコードマトリクス（`TOKEN_INVALID` / `TOKEN_EXPIRED` / `TOKEN_TN_MISMATCH` / `TRACKING_NOT_FOUND`）

ドメイン層に `TrackingTokenService` interface、インフラ層に `JwtTrackingTokenService` 実装という DIP 構造を ADR で確定したことで、実装中の迷いがなくテストも容易になった。

#### K2: IT5 ふりかえり Try を IT6 第 0 スプリントで完全消化した

IT5 で挙げた 5 つの Try をすべて IT6 で消化:

- T1 (ArchUnit `@CommandHandler` 強制): 4 サービス共通化を `CommandHandlerArchitectureTest` で実装
- T2 (Axon static handler 引数規約): コーディングガイドに NG/OK 例つきで追記
- T3 (DTO 型→変数名規約): `*Record activity`・`*Record summary` 等を明文化
- T4 (Java major version 確認): tech_stack.md にチェックリスト追加（ArchUnit 1.4.x）
- T5 (Event 駆動 ACL): IT7 持ち越しを明示し申し送り事項に登録（規模ゆえ shared モジュール昇格と束ねる判断）

#### K3: ArchUnit ルール 4 サービス共通化が IT5 P1 を物理的に防ぐ仕組みになった

`CommandArchitectureTest`（`@TargetEntityId` 強制・IT4 H1 起因）と `CommandHandlerArchitectureTest`（`@CommandHandler` 強制・IT5 P1 起因）を bookingms / handlingms / routingms / trackingms 全 4 サービスにテンプレ展開。`allowEmptyShould(true)` でルールの「空集約に対するすり抜け」も塞いだ。今後追加されるマイクロサービスでも同形コピーで即適用可能。

#### K4: `Clock` 注入による時刻依存テストの完全制御

`JwtTrackingTokenService` で `Clock` を引数注入する設計にしたことで、`Clock.fixed(...)` を使って exp 経過 / `deliveredAt + 7d` 経過 / 境界値（±1 秒）を flaky なく完全に再現できた。レビュー指摘 H-10 の境界値テスト追加も `Clock` 注入のおかげで即座に書けた。

#### K5: マルチパースペクティブレビュー（XP 5 エージェント並列）で多角的指摘 40 件を集約

`developing-review` スキルで xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative の 5 視点を並列実行。各視点で異なる指摘が得られ、特に **xp-user-representative の業務適合性指摘 5 件（H-1〜H-5）は他の視点では拾えなかった重要課題**。レビュー結果は `IT6_implementation_review_20260518.md` として保存し IT7 計画に反映予定。

#### K6: SonarQube Quality Gate PASS（new_coverage 83.5%・new_violations 0）

IT6 で大量にコードを追加（102 ファイル・6,373 行）した直後の new_coverage は 69.6% で閾値未達だったが、`TrackingProjectionsEventHandlerTest` を 4 ケース追加して 83.5% へ押し上げ。new_violations も初回 12 件から 0 件まで丁寧に修正（unnamed pattern `_` / `@SuppressWarnings("java:S107")` / リテラル定数化 / lambda 単一 throw 化）。

---

### Problem（問題点）

#### P1: bookingms.TrackingNumber 値オブジェクト乖離が IT6 で表面化（IT4 由来の負債）

bookingms の `TrackingNumber` 値オブジェクトは `^TRK-[0-9A-Z]{10}$` を要求するのに、`Cargo#issueTrackingNumber` は `TRK-YYYYMMDD-XXXXXXXX`（21 文字）を `String` のまま発行している。bookingms 内では VO を経由せず string で保持しているためバリデーションが発火する経路がなく、IT6 で trackingms に値が渡って初めて顕在化した（`TrackingNumber は 'TRK-' + 大文字英数 10 桁である必要があります: TRK-20260518-3053F0B8`）。

直近コミット `eb4cc12f` は trackingms 側の VO を「`TRK-` 接頭辞 + 25 文字以内」に緩めて回避したが、これは **「サービス境界を越えるたびに検証を弱める」対症療法**で、Conway の法則に反する負債を増やしている（xp-architect 高 H-6 指摘）。

#### P2: 公開画面の業務適合性不足をレビューまで気づけなかった

xp-user-representative レビューで 5 件の高優先度指摘:

- H-1: 港名表示が UN/LOCODE のみ（荷主は読めない）
- H-2: 日時表示が UTC で 9 時間ずれる（toISOString 由来）
- H-3: 「誤配送 あり/なし」を公開画面に出している（社内用語の流出）
- H-4: 履歴の並び順が API レスポンス依存（最新が上でない）
- H-5: 期限切れ時の問合せ先連絡先が文言のみで導線なし

実装中は「動く・テストが通る」で進めたが、**荷主視点での業務シミュレーションが抜けていた**。バックエンド・アーキテクト視点だけだと業務適合性は見落とされる。

#### P3: TrackingActivity Aggregate 未初期化フォールバックの暫定設計（不変条件違反）

`bookingms.CargoTrackedEvent` の Event 駆動 ACL が IT7 持ち越しになったため、フロント側で `BookingDetailPage` の useEffect から `/api/v1/tracking/_internal/initialize` を自動呼び出しする暫定設計に。

さらに `TrackingActivity.updateStatus` で `this.trackingNumber == null` のときに Command 引数値をフォールバックに使う処理を入れた（NPE 回避）。これは **「未初期化 Aggregate への書き込みを許容する」不変条件違反**で、Event Sourcing の整合性を損なう設計臭（xp-architect 中 M-1 指摘）。

#### P4: `dummyValidUntil` で表示が実 JWT と無関係になっている

`TrackingController#getTracking` が `LocalDateTime.now().plusDays(30)` をクライアント表示用 `validUntil` として返しているため、配送完了済みの追跡番号でも「あと 30 日有効」と誤表示される（xp-programmer / xp-technical-writer 高 H-7 指摘）。`TrackingTokenService.verify` の戻り値が `TrackingNumber` のみで `validUntil` を取り出せない設計が原因。

#### P5: 運用ドキュメントへの新規環境変数反映漏れ

`TRACKING_TOKEN_EXPIRATION_DAYS` / `TRACKING_TOKEN_GRACE_DAYS` / `TRACKINGMS_URL` を `application.yml` と `ADR-0013` には記載したが、`docs/operation/開発環境セットアップ手順書.md` と `ADR-0006 Heroku デプロイ構成`の Config Vars 表に反映漏れ（xp-technical-writer 高 H-11 指摘）。レビューで気づくまで運用担当者の設定漏れリスクがあった。

#### P6: 初回 SonarQube new_coverage 69.6% で Quality Gate 未達

IT6 で大量にコードを追加した直後、new_coverage が 69.6% で閾値 80% を下回り Quality Gate FAIL。TrackingProjectionsEventHandler のテストが抜けていたのが主因。**「TDD で十分カバーしているつもり」と「SonarQube の new_coverage」は別物**。新規追加コードのカバレッジは継続的に監視する必要があった。

#### P7: TDD Red コミットの形跡が残っていない

xp-tester レビューで「全コミットで `feat(xx)` + テスト追加が同梱されており、TDD 後付けの可能性を排除できない」と指摘された。テストと実装を同時にコミットする運用は便利だが、Red→Green→Refactor の規律が外部から検証できない。

---

### Try（次に試すこと）

#### T1: shared モジュール昇格と Event 駆動 ACL の正式実装（P1 / P3 対策）

IT7 第 0 スプリントで以下を一括実施（IT6 持ち越し項目）:

- `apps/backend/shared` Gradle モジュールを有効化
- `bookingms.CargoBookedEvent` / `CargoRoutedEvent` / `CargoTrackedEvent` / `TrackingNumberIssuedEvent` を shared に昇格
- `bookingms.TrackingNumber` を `TRK-YYYYMMDD-XXXXXXXX` 形式に修正し `Cargo` 内部状態を VO 化
- `handlingms` / `trackingms` に Event 駆動 ACL を実装し `POST /cargo-snapshots`・フロント自動 initialize を廃止
- `TrackingActivity` の未初期化フォールバックを削除し `IllegalStateException` で拒否

責任者: IT7 第 0 スプリント / 期限: IT7 着手時 / 期待効果: サービス境界の検証一貫性回復 + 暫定設計の解消（推定 3 SP）。

#### T2: user-representative レビューを実装中盤に前倒し（P2 対策）

公開画面のように荷主向け UI を含むストーリーは、実装完了後ではなく **動くプロトタイプができた段階で xp-user-representative レビューを 1 回挟む**。これにより業務適合性の指摘を早期に拾い、後段のリリース直前手直しを避けられる。

責任者: 各イテレーション PM / 期限: IT7 から / 期待効果: 業務適合性指摘の手戻り低減。

#### T3: `TrackingTokenService.verify` 戻り値拡張で `dummyValidUntil` を解消（P4 対策）

```java
public interface TrackingTokenService {
  record VerifiedToken(TrackingNumber trackingNumber, LocalDateTime expiresAt) {}
  VerifiedToken verify(String token, LocalDateTime deliveredAt);
}
```

`Controller#getTracking` は `verified.expiresAt()` を `validUntil` としてそのままレスポンスに含める。JWT の `exp` が表示有効期限と一致する保証ができる。

責任者: IT7 第 0 スプリント / 期限: IT7 着手時 / 期待効果: 公開画面の有効期限表示の信頼性向上。

#### T4: 新規環境変数のドキュメント反映チェックリストを開発プロセスに組込み（P5 対策）

新規環境変数を追加する PR には以下のドキュメント更新を必須とする:

- `application.yml` のプレースホルダ
- `ADR-0006 Heroku デプロイ構成` の Config Vars 表
- `docs/operation/開発環境セットアップ手順書.md` の Config Vars 一覧
- 該当 ADR があれば「環境変数」セクション
- `docs/operation/Deprecation 一覧.md`（IT7 で新設候補）

責任者: PR 作成者 / 期限: IT7 から / 期待効果: 運用ドキュメント漏れの構造的防止。

#### T5: SonarQube new_coverage を CI のソフトゲートとして毎日確認（P6 対策）

Quality Gate FAIL を防ぐため、PR コミット毎ではなくとも 1 日 1 回 SonarQube スキャンを CI で実行し、`new_coverage` の傾向を可視化。閾値の 5 ポイント手前（75%）でアラートを出す。

責任者: SRE 役 / 期限: IT7 から / 期待効果: 終盤での慌てたテスト追加を防ぐ。

#### T6: TDD Red コミットを最低 1 つ残す規律（P7 対策）

ユーザーストーリー実装の最初のコミットは「テストだけ書いて失敗する」状態を残す（`test(US18): TrackingTokenServiceTest を追加（Red）` のように type / message に明示）。次のコミットで Green → Refactor。これにより TDD サイクルの外部検証が可能になる。

責任者: 全プログラマー / 期限: IT7 から / 期待効果: TDD 規律の可視化。

---

## ベロシティ実績

### IT1-IT6 ベロシティ推移

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 16 | 14 | 88% |
| IT2 | 14 | 14 | 100% |
| IT3 | 16 | 16 | 100% |
| IT4 | 25 | 25 | 100% |
| IT5 | 11 | 11 | 100% |
| IT6 | 8 | 8 | 100% |
| **合計** | **90** | **88** | **97.8%** |
| **平均（IT2-6）** | | **14.8** | **100%** |

**IT7 推奨ベロシティ**: 12〜16 SP。IT4 特例 25 を除く IT1-3-5-6 平均 12.6 + IT4 込み平均 14.8 のレンジ。IT6 は意図的な低スコープ（8 SP）だったため、IT7 で本来ペースに戻す。

### 累積進捗

| 指標 | 結果 |
|------|------|
| IT1-IT6 完了 SP | 88 / 106 SP（83%） |
| 残 SP | 18 SP（2 イテレーション分） |
| IT7-IT8 計画ベロシティ | 9〜10 SP / イテレーション |

---

## IT7 への申し送り事項

### IT5 / IT6 持ち越し（Event 駆動 ACL + shared モジュール）

1. **shared モジュール昇格**: bookingms の Event クラス（CargoBookedEvent / CargoRoutedEvent / CargoTrackedEvent / TrackingNumberIssuedEvent）を shared に移動
2. **handlingms Event 駆動 ACL**: `POST /api/v1/handling/cargo-snapshots` を廃止し `CargoBookedEvent` 購読 EventHandler に置換
3. **trackingms Event 駆動 ACL**: `CargoTrackedEvent` 購読で `TrackingActivity` を自動初期化、フロント `BookingDetailPage` の useEffect 自動 initialize を削除
4. **handlingms cargo_status_history → trackingms tracking_event データ移行**: Flyway 移行スクリプト

### IT4 由来の負債（IT6 で表面化）

5. **bookingms.TrackingNumber 値オブジェクト修正**: 正規表現を `^TRK-\d{8}-[0-9A-F]{8}$` に変更、`Cargo` 内部状態を VO 化
6. **data-model.md / domain-model.md 同期**: `tracking_summary.delivered_at` / `tracking_event.source` / `TrackingTokenService` / `JwtToken` / `EventSource` enum / `tracking_number: VARCHAR(25)` の反映

### IT6 レビュー高優先度残課題

7. **H-1**: 公開画面で港名表示（UN/LOCODE → 港名併記）。マスタ整備が必要
8. **H-5**: 期限切れ時の問合せ先連絡導線（要件確認後に対応）
9. **H-7**: `dummyValidUntil` 解消（T3 と同件・`TrackingTokenService.verify` 戻り値拡張）
10. **H-8**: JWT secret 本番 Fail-Fast（`@PostConstruct` で profile 判定）

### IT6 レビュー中優先度の代表項目

11. **M-1**: TrackingActivity 未初期化フォールバック削除（T1 と同件）
12. **M-2**: BookingId 値オブジェクトを使用箇所統一 or 削除（中途半端な VO 残置の解消）
13. **M-5**: `STATUS_LABEL` / `formatDateTime` の重複を `features/tracking/lib/` に集約
14. **M-6**: `sendAndWaitWithTimeout` を共通化（3 サービス重複）
15. **M-10**: ADR-0013 ステータスを「提案」→「承認済み」に更新

### IT4 中優先度（M1〜M6）の継続対応

16. IT3 繰越し UI 改善（US04-r1 / US05-r1 / US24-r1）: 低優先度のため IT7+ 任意対応
17. handlingms `Warning: 299` ヘッダー削除（RFC 9110 で deprecated）

---

## まとめ

IT6 は計画 8 SP を 100% 達成し、Phase 2 追跡基盤（trackingms + JWT 時限トークン + S15 公開照会 + S16 管理一覧 + TI06 US17 移管）を完成させた。IT5 で確立した規律（ADR 事前承認・第 0 スプリント・ArchUnit 共通化）が継承され、Quality Gate も PASS。

一方、`bookingms.TrackingNumber` 値オブジェクト乖離（IT4 負債）が IT6 で表面化し、サービス境界ごとに検証を弱める対症療法で凌いだ点は **IT7 で根本対処すべき技術的負債**。マルチパースペクティブレビューにより業務適合性指摘 5 件を拾えたのは大きな収穫で、その手法は IT7 以降も継続する。

Phase 2 完了に向けて IT7 では shared モジュール昇格を起点に Event 駆動 ACL を本実装し、IT6 で積み残した暫定処理（フロント自動 initialize / TrackingActivity フォールバック / dummyValidUntil）を一括解消する計画。

---

## 更新履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-05-18 | IT6 ふりかえり作成（KPT 6K/7P/6T・累計 88/106 SP 83% 完了・IT7 持ち越し 17 件整理） | AI Agent（XP PM） |
