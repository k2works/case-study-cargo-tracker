---
title: イテレーション 2 ふりかえり
description: IT2（貨物予約・航海スケジュール登録・Axon 5.1 Event Sourcing 本格導入）の KPT ふりかえり
published: true
date: 2026-05-15T00:00:00.000Z
---

# イテレーション 2 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 / 8 |
| **期間** | 2026-05-28 〜 2026-06-10（計画）／ 2026-05-13 〜 2026-05-15 で前倒し完了 |
| **ゴール** | bookingms に Cargo Aggregate（Axon Event Sourcing）を導入し貨物予約を実装。routingms を新規起動し航海スケジュール新規登録を実装。あわせて IT1 持越し（アカウントロック・ログアウト・E2E）を完了 |
| **計画 SP / 実績 SP** | 14 / 14（達成率 100%） |

---

## Keep（続けること）

### K1: ADR ベースのアーキテクチャ意思決定

ADR-0007（Axon 5.1 Event Sourcing API 採用方針）と ADR-0008（Spring Boot 4 統合の具体パターン）を IT2 中に作成・更新した。ADR-0008 では `@EventSourcedEntity` 単独 / 明示 Bean / `@EventSourced` の 3 試行を表で比較し、却下理由まで残した。将来の見直し時に同じ検証を繰り返さなくて済む形に到達した。

### K2: 計画より E2E 駆動で死角を発見

`@MockitoBean CommandGateway` で隠れていた Aggregate 未登録問題が Playwright E2E で発覚し、ADR-0008 で本質対応した。「テスト戦略のレイヤごとの責務分離（ユニット = Mockito、統合 = MockitoBean、E2E = 実環境）」が機能し、死角を構造的に塞ぐ仕組みを確立できた。再発防止として `BootSmokeTest` を追加し、SpringBootTest と異なる Bean 解決順での起動確認を継続的にチェックする仕組みも整った。

### K3: 計画外バッファの活用

routingms スケルトン構築（+12h 相当）と `local-docker` プロファイルの全サービス起動対応を計画外バッファで吸収しつつ、計画ストーリー 14 SP は完全に達成した。IT1 のふりかえり T1（計画外タスクのバッファ明示）を実践できた。

### K4: Clean as You Code は採用せず正面突破

SonarQube Quality Gate が IT2 完了時点で違反 252 件あったが、ADR-0009 の Clean as You Code 方針（暫定対応案）を一度書きかけて却下し、**全違反 + カバレッジ向上で正面突破**した。結果として Backend new_coverage 88.6%、Frontend 83.8%、両プロジェクト Gate PASS を達成。技術的負債を持ち越さない判断は将来の保守性に寄与する。

### K5: CI / Heroku デプロイの安定稼働

GitHub Actions（Backend / Frontend / E2E）が IT2 完了時点で全 success。Heroku Container Registry での全 4 マイクロサービス（authms / bookingms / routingms / gatewayms）デプロイも動作確認済み。ローカル → CI → 本番（Heroku）の 3 環境すべてで POST → Projection → GET の貫通動作を確認できている。

### K6: スコープ判断の透明性

「フロントエンド UI を IT2 内で完成させるか IT3 へ持ち越すか」「SonarQube Gate を緩めるか厳格対応するか」など、複数の重要判断ポイントでユーザーに A/B/C の選択肢を提示し合意を得てから着手した。IT1 の T3（完了定義の厳格化）を実践できた。

---

## Problem（問題点）

### P1: 計画と実機の乖離（PooledStreamingEventProcessor 問題）

bootRun / bootJar / Heroku で `PooledStreamingEventProcessor` が `TOKENENTRY` テーブル不在で起動失敗するという、計画書に予測されていなかった問題が 3 段階（local-h2 / local-docker / heroku）で順次発覚した。ADR-0007 のリスク欄には「`@EventSourced(idType=)` の併用要否が不明」と記載があったが、Event Processor の運用面までは予測できていなかった。

### P2: 試行錯誤のコミット数が多い

CI/Heroku 起動失敗を経験したことで、`fix(ci): npm ci 不一致` 2 連発、`fix(deploy): heroku の TOKENENTRY 問題` など、本来 1 回で済ませたい修正コミットが複数生じた。ローカル macOS と CI Linux / Heroku Linux の差を初回 push 前に検証する仕組みが弱かった。

### P3: PIT 75% 主指標が未達

`test_strategy.md` の主指標である PIT 75%（ドメイン層）が IT2 でも導入できず、IT3 へ持越し。Jacoco 行カバレッジ 88.6% / 83.8% は副指標で、ミューテーション耐性の定量化はできていない。Clean as You Code 採用を見送ったタイミングで PIT 導入もスケジュール再評価すべきだった。

### P4: data-model.md の更新漏れ

`users.lock_until` / `users.failed_attempts` カラム追加を IT2 完了時に `docs/design/data-model.md` へ反映する計画だったが、未実施で IT3 持越し。実装ドキュメントと設計ドキュメントの同期メカニズムが弱い。

### P5: 業務利用に耐える入力検証の欠如（レビュー指摘）

XP 5 エージェント並列レビューで xp-user-representative が指摘した「荷主 ID 数値直入力」「IMO クラス・UN 番号フリーテキスト」「温度条件 0/0 で登録可」「出発日 < 到着日 / 寄港地連続性の検証なし」など、業務に投入できない UI が IT2 終了時点で残った。受入条件は「形式的に動く」までしか定義されておらず、業務的妥当性の閾値が暗黙だった。

### P6: ドキュメント陳腐化（レビュー指摘）

xp-technical-writer が指摘した通り、`apps/frontend/e2e/README.md` と運用手順書 §7 に「IT3 で予定」「Phase 0」記述が残置していた。IT2 完了処理の一部として今すぐ修正可能だが、本来は実装の都度ドキュメントを更新する規律が必要。

---

## Try（次に試すこと）

### T1: クロスプラットフォーム検証の前倒し

ローカル開発（macOS）と CI（Linux）/ 本番（Linux）の差で起動失敗するパターンを IT2 で複数経験した。IT3 では「最初の push 前に Docker (Linux) で `npm ci` と `bootJar` 起動を確認する」プリチェックを CI 風スクリプトとして用意する。

### T2: ADR-0007 のリスク欄に基づく事前検証

ADR-0007 のリスク欄には「`@EventSourced(idType=)` の併用要否が不明」など、将来の問題を予測する記述がある。IT3 着手時に各 ADR のリスク欄を「未検証なら検証タスクとして IT3 計画に明示する」運用を試す。

### T3: PIT 導入を IT3 タスク 1 として優先実行

IT3 計画の最初のタスクとして PIT プラグイン導入 + CI 統合を実施する（4h 想定）。Jacoco との並走運用とし、ドメイン層の `Cargo` / `Voyage` Aggregate に最初に適用する。

### T4: 業務的受入条件のチェックリスト化

US04/US05/US24 のような「業務担当者が使う」ストーリーには、`xp-user-representative` 視点の受入チェックリスト（マスタ検索可、入力規則あり、エラー文言が業務理解可能）を IT3 から計画段階で含める。レビュー指摘 H10-H13 を IT3 改善ストーリーとして起票する。

### T5: ドキュメント連動コミットの規律強化

実装変更時に関連ドキュメント（手順書、README、ADR）の更新を同コミットに含める規律を pre-commit またはコードレビュー時の必須項目に格上げする。IT3 着手前に `apps/frontend/e2e/README.md` と運用手順書 §7 を IT2 実態に整合。

### T6: 計画外タスクの分類記録

IT2 では計画外タスク（gradlew 修正、ci-e2e routingms 追加、SonarQube 違反対応、Heroku トラブル対応 等）が IT1 同様に多発した。IT3 では計画外タスクを「インフラ整備」「実装上の発見」「ドキュメント追補」等にカテゴリ分けし、ベロシティ算定の際に分類別に時間を可視化する。

---

## IT3 への申し送り事項

### 持越しタスク

| タスク | 元 ID | SP 影響 |
|--------|--------|---------|
| US25: 既存航海スケジュールを更新する | release_plan IT2 から IT3 へ繰越し済み | 3 SP |
| PIT 75% 主指標導入（タスク 7.2） | IT2 タスク 7.2 | バッファ枠（4h） |
| `data-model.md` に `users.lock_until` / `failed_attempts` 反映 | IT2 DoD 未達 | バッファ枠（1h） |
| ドキュメント陳腐化解消（e2e/README、手順書 §7、ADR-0007 のクロスリンク） | レビュー指摘 H8/H9 | バッファ枠（1h） |
| 業務的入力検証の改善ストーリー化 | レビュー指摘 H10-H13 | 新規ストーリー、SP 見積必要 |

### IT3 で注意すべきリスク

1. **Aggregate 登録の死角**: ADR-0008 で `@EventSourced` + `@Profile("!springboot-integration-test")` 採用、`subscribing` モード暫定運用と決定したが、IT3 で trackingms / handlingms 追加時に同じパターンを踏襲する。Subscribing → Pooled への切り替え条件（Axon Server 連携 or TokenEntry の Flyway 整備）は未確定で、IT3 で確定タスクとして扱う。
2. **ArchUnit 自動検証**: xp-architect 指摘の通り、ADR-0004 のパッケージ間依存ルールを ArchUnit で機械検証する仕組みが未実装。IT5 で trackingms / handlingms / billingms が加わる前に強制機構を整備する必要がある。
3. **Controller の翻訳ロジック肥大化**: xp-programmer 指摘の DTO → VO 変換が Controller に集約され、テスト独立性が損なわれている。IT3 で Assembler パターンへリファクタリング可能か検討。
4. **フォームステート管理の重複**: `useReducer` or `react-hook-form` への置換を IT3 のフロントエンド改善ストーリーで計画化。
5. **「Reference Branch = main」案を再評価**: 一度検討した Clean as You Code 方針（ADR-0009 草案）は IT2 内で却下したが、IT3 以降で違反が累積した場合の代替案として保留中。

### 申し送りメモ

- IT2 ふりかえり結果は K6 件・P6 件・T6 件で、IT1 と比べて Keep の数が増え、Problem も同数。改善サイクルが機能している。
- xp-user-representative の指摘（H10-H13）は IT3 で取り組むべき最優先事項。「動くソフトウェア」から「業務に投入できるソフトウェア」への質的転換点。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-15 | 初版作成（IT2 完了処理の一部として） | AI Agent（XP PM） |
