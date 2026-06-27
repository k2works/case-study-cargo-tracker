# IT2 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| --- | --- |
| イテレーション | IT2 |
| 期間 | 2026-06-27 (Ralph Loop 21 反復) |
| 計画 SP | 10 (+Try 8) |
| 実績 SP | 18 (達成率 180%) — arch-check Phase 2 は IT3 繰越 |
| テスト | 207 examples / 0 failures / 10 pending |
| コミット数 | 24 (IT1 末以降) |
| 新規 BC | 1 (Estimation) |

---

## Keep (継続すべき良かったこと)

### 技術面

- **sum type による不変条件の型レベル強制**: `CargoType (General | Hazardous decl | Refrigerated req)` で「種別 = 危険物だが宣言なし」が型エラーになる。IT1 retrospective P-9 (`Shipper.name = email` placeholder) と同型の罠を予防できた
- **Cross-BC ACL ライト版 (Text 化)**: Estimation BC では `shipperIdText` / `cargoTypeText` / `voyageNumbers : [Text]` と全て Text 化し、arch-check Rule 4 を新規違反させずに済んだ
- **楽観ロック付き UPDATE パターンの一貫性**: US06 (cargo `WHERE booking_id=? AND version=?`) / US25 (voyage `SELECT ... FOR UPDATE` + DELETE + INSERT + UPDATE) で同じ規約に従い、`ConcurrentModification` 伝播の経路を統一できた
- **PRG + matchLocationPrefix の組み合わせ**: T-07 で ID をサーバ採番化した際、exact match を接頭辞一致マッチャに書き換えるだけで通った
- **dbmate `YYYYMMDDHHMMSS_*.sql` と論理番号 `008_*` の併記**: 計画書では論理順序、実ファイルは date prefix、両者の対応を計画書に明記する規約を確立 (Sprint 0 で発見した命名規約のズレを反映)

### プロセス面

- **Ralph Loop による圧縮実行の再現**: IT1 (20 SP) → IT2 (18 SP) いずれも 1 日完結。ストーリー数増 (4 → 4) + Try 数増 (0 → 10) でも崩れず
- **「Try 必達 → 本体ストーリー → arch-check Phase 2」の順序**: 負債返済を先に消化したことで、本体実装時にすでに PRG/htmx テスト・hedgehog プロパティが揃っており、リグレッション検知が早かった
- **TDD サイクルの厳守**: 各反復で Red テスト → Green 実装 → コミット → 次の反復 という流れを 21 反復維持。途中で型不整合や Postgres tuple 型問題などのつまずきはあったが、全反復で 0 failures を保てた
- **マルチパースペクティブレビュー (IT1 のもの) の Try 反映率 100%**: T-01〜T-10 すべてを 1 イテレーションで消化し、レビュー指摘の死蔵を防げた

---

## Problem (問題・改善すべき点)

### スコープ

- **arch-check Phase 2 の見積もり過小**: AST バイナリ化 (6h) + Rule 6 (2h) を IT2 内で完遂できなかった。Rule 6 (Interfaces → Domain) は import-grep だと既存 30+ 件の VO import を即時違反扱いし、Application 層との往復リファクタが先に必要だと判明 (IT3 繰越)
- **E2E (Playwright) 未拡張**: 本体ストーリー 4 件すべてで unit/hspec-wai は揃ったが Playwright spec は IT1 の 4 件のままで、US01/US06/US25/危険物予約の E2E はまだ走っていない
- **UI 部分の積み残し**: US01 の `/estimates/new` フォーム、US05 の CargoType 動的フィールド、US25 の voyageEditPage プリフィルが軒並み IT3 繰越となった (Domain/App/Repository は完了)

### コード品質

- **HPC カバレッジ全体 62%**: IT2 目標は「ゲート 60% / IT3 目標 70%」だったため通過したが、IT2 計画上の理想 (Domain 95% / 全体 70%) には届かなかった。Domain 別計測も未実施
- **PostgresBookingRepository.findCargo の 14 列 SELECT**: cargo_type/特殊フィールドの読み出しを `Maybe` フィールドの巨大タプル + `textToCargoType` で実現したが、コード量が膨張した。`postgresql-simple-named` / レコード復元ヘルパで圧縮できる
- **既存 Cross-BC Domain 違反 6 件は ALLOWLIST 化のまま**: Booking → Shipper.Domain.Value.ShipperId 直接 import は IT3 で Booking 側 `ShipperRef` VO へリファクタすべき (T-06 で検知器のみ追加した状態)

### テスト品質

- **CreateEstimateCommand に正常系 happy-path 1 件しかない**: バリデーション 4 件 + ConcurrentModification 1 件で網羅性は出たが、Postgres 統合テスト (実 DB に対する INSERT/SELECT) は皆無 (DATABASE_URL CI 未設定)
- **arch-check Rule 4 の ALLOWLIST は機械的整合性チェックがない**: ファイルパス文字列マッチで判定するため、ファイル名がリネームされると ALLOWLIST が壊れる

### 計画と実態の齟齬

- **計画 SP 18 vs 実績 18 だが達成率は誤解を招く**: IT2 計画では本体 10 + Try 8 + arch-check 2 = 20 SP を予定していたが arch-check Phase 2 は 2 SP 分のみ IT3 繰越。表面上 180% に見えるのは IT1 ふりかえり指摘の「Ralph Loop 圧縮実行は通常運用ベロシティの基準にしない」の通り
- **iteration_plan-2.md §デモ項目「アクセス制御の実証」が未消化**: §7.3b M-10 ロール別アクセス制御 (US04/US24/US25) は計画タスクに含めたが、IT2 でロール check を実 HTTP に組み込むところまでは行かなかった (handler 単位の認可は IT3)

### ドキュメント

- **`domain-model.md` / `data-model.md` が IT2 実装と未同期**: Estimation BC 追加 / CargoType sum type / Shipper.name フィールド / BookingStatus 状態遷移はソースには反映済だが、設計ドキュメントには未反映
- **iteration_report-1.md の HPC カバレッジ行は IT2 で実測値 (62%) に更新したが、Domain 別の数字は依然未取得**

---

## Try (次のアクション)

### IT3 必達 (本体機能完成 + 負債返済)

| # | アクション | 担当 | 期限 | 期待効果 |
| --- | --- | --- | --- | --- |
| U-01 | US01 HTTP/UI: `/estimates/new` フォーム + 候補表示 + 「この見積で予約する」リンク | IT3 担当 | IT3 Week 1 | US01 価値完成 |
| U-02 | US05 UI: BookingFormView に CargoType select + 動的危険物/冷凍フィールド (htmx) | IT3 担当 | IT3 Week 1 | US05 価値完成 |
| U-03 | US25 UI プリフィル: voyageEditPage に既存 movements を input value で埋める | IT3 担当 | IT3 Week 1 | US25 体験改善 |
| U-04 | arch-check Phase 2: haskell-src-exts AST バイナリ + Rule 6 (Interfaces → Domain) | IT3 担当 | IT3 Week 1 | 構造規約の自動検証 |
| U-05 | Booking → Shipper.Domain ACL リファクタ: Booking 側に `ShipperRef` VO を導入し ALLOWLIST 6 件を解消 | IT3 担当 | IT3 Week 1 | Rule 4 ALLOWLIST 解消 |
| U-06 | HPC カバレッジ Domain 別計測 + ゲート 70% 引き上げ | IT3 担当 | IT3 Week 2 | 品質メトリクス確立 |
| U-07 | M-10 ロール別アクセス制御の実装 (US04=営業 / US24+US25=マスタ管理者 / US06=営業) | IT3 担当 | IT3 Week 1 | 業務ロール準拠 |
| U-08 | E2E (Playwright) 拡張: US01 / US06 / US25 ハッピーパスを実機検証 | IT3 担当 | IT3 Week 2 | デモ確認の自動化 |
| U-09 | domain-model.md / data-model.md を IT2 実装結果で同期 (Estimation BC / CargoType / Shipper.name / BookingStatus) | IT3 着手前 | IT3 Day 1 | 設計と実装の整合 |
| U-10 | `v0.1.0-alpha` タグ + GitHub Release ノート (本ふりかえり完成後) | 即時対応 | IT3 着手前 | Release 0.1 Internal Alpha 完了宣言 |

### IT3 推奨 (中優先度)

| # | アクション | 期限 |
| --- | --- | --- |
| U-11 | PostgresBookingRepository の 14 列 SELECT を `postgresql-simple-named` 等で圧縮 | IT3 内 |
| U-12 | CreateEstimateCommand の Postgres 統合テストを追加 (DATABASE_URL CI 設定 + dbmate up 自動化) | IT3 内 |
| U-13 | hedgehog プロパティ拡張: Estimate / RouteCandidate / TemperatureRequirement 等の境界値検証 | IT3 内 |
| U-14 | arch-check Rule 4 ALLOWLIST のファイル名検証 (ファイル不在ならエラー) | IT3 内 |

### プロセス改善

- **「Try 必達 → 本体ストーリー → アーキ整備」順序を IT3 でも踏襲**: IT2 で機能した順序を継続
- **`developing-review` を IT2 マルチパースペクティブレビューとして実行**: XP 5 エージェント並列で IT2 成果を再評価し、IT3 着手前に Try を補強
- **設計ドキュメント同期を「コミット直後」から「ふりかえり直後」に正式化**: IT2 では domain-model.md / data-model.md 未同期が発生したため、各 IT の retrospective 完成と同時に設計ドキュメント反映を必須化
- **Ralph Loop の停止判定**: AI 単独完結可能なタスク消化後、ふりかえりまで生成して終端する運用が IT1/IT2 で再現性あり (memory `feedback_ralph-loop-end-of-life.md` の追記)

---

## ベロシティ実績

| イテレーション | 計画 SP | 実績 SP | 達成率 | 備考 |
| --- | --- | --- | --- | --- |
| IT1 | 13 | 20 | 154% | Ralph Loop 圧縮実行 |
| IT2 | 18 | 18 | 100% | 本体 4/4 + Try 10/10、arch-check Phase 2 は IT3 繰越 |

**学習コスト係数 1.20 の妥当性検証 (中間)**: IT1 (154%) → IT2 (100%) と落ち着いた。IT2 では計画 SP 自体を Try 込みで 18 に拡張しているため、実質ベロシティは IT1 より安定方向。3 IT 平均で再評価する規律は維持。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
| --- | --- | --- |
| 2026-06-27 | 初版作成 (本体 4/4 + Try 10/10 + arch-check Phase 2 繰越決定後) | Claude |
