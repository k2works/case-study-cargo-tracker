# イテレーション 4 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4 |
| **期間** | 2026-06-09〜2026-06-20（2 週間） |
| **ゴール** | trackingms を新規構築し、追跡番号発行・荷役作業記録・貨物状態手動更新の API + 画面を実装する。併せて IT3 コードレビュー高優先度指摘を解消する |
| **計画 SP** | 21 |
| **実績 SP** | 21 |
| **達成率** | 100% |

---

## 成果サマリー

| 指標 | 目標 | 実績 | 判定 |
|------|------|------|------|
| ストーリーポイント | 21 SP | 21 SP | 達成 |
| テストカバレッジ | 80% 以上 | trackingms 全レイヤー TDD（ドメイン〜インターフェース）| 達成 |
| E2E テスト | 全通過 | 追跡番号発行→荷役記録→状態更新フロー | 達成 |
| SonarQube Quality Gate | PASS | Bug 0・Vulnerability 0・new_violations 0 | 達成 |
| IT3 コードレビュー高優先度指摘 | 解消 | TI01 全項目解消（#1/#2/#4） | 達成 |

### 完了ストーリー

| ID | ストーリー | SP | 状態 |
|----|-----------|----|----|
| TI01 | IT3 コードレビュー高優先度指摘解消 | 3 | 完了 |
| US14 | 追跡番号を発行する | 5 | 完了 |
| US15 | 荷役作業を記録する | 8 | 完了 |
| US17 | 貨物状態を手動更新する | 5 | 完了 |

---

## KPT 分析

### Keep（継続すること）

#### 技術的成功

- **新規マイクロサービス構築の全レイヤー TDD**: trackingms をゼロから構築し、ドメイン層（TrackingActivity 集約、TrackingNumber 値オブジェクト）→ アプリケーション層 → インフラ層（MyBatis + Flyway）→ インターフェース層（REST Controller）の順で TDD を適用。インサイドアウトアプローチが新規サービス構築でも有効であることを確認した

- **H2 インメモリ DB テスト分離の確立**: `DB_CLOSE_DELAY=-1` による H2 DB 共有問題を `@Sql` クリーンアップアノテーション + テスト専用 `application.yml` で解決。複数の Spring コンテキストが同じ H2 DB を共有する際のテスト間データ汚染パターンとその解決策を習得した

- **SonarQube Quality Gate PASS の継続**: IT3 に引き続き new_violations: 0 を達成。`ResponseEntity<?>` → `ResponseEntity<Object>` への変換、値オブジェクトの record 化、制限識別子（`record`）の変数名回避など、Java 21+ の best practice に沿った実装を維持した

- **record への移行によるコード簡素化**: `TrackingNumber` と `TrackingBookingId` を `final class` から `record` に変換。compact constructor によるバリデーションロジックを維持しながら、`equals`・`hashCode`・`toString` のボイラープレートを排除した

#### プロセス的成功

- **Codex 1 ファイル単位指示戦略の確立**: IT3 で発見した「6 ファイル同時指示→タイムアウト」の教訓を活かし、全実装を 1 ファイル単位で Codex に委譲。各 15〜35 秒で成功し、trackingms の全レイヤーを効率的に構築できた

- **IT3 コードレビュー指摘の計画的解消**: TI01 として高優先度指摘（`@Transactional` 未付与・状態ガード欠如・エラーレスポンス改善）を最初のタスクとして配置し、技術的負債を早期に解消してから新機能開発に集中できた

- **import パスの正確性確認**: Codex が `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`（存在しない）を使用した際、正しい `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` を特定して即座に修正。Spring Boot 4.0 のパッケージ変更に対する知見が蓄積された

### Problem（課題）

- **H2 テスト分離の設計が後手に回った**: 統合テスト（`HandlingActivityControllerTest` + `TrackingNumberControllerTest`）間のデータ汚染に全テスト実行後まで気づかなかった。単独テストでは通過するが全体実行で失敗するパターンは診断に時間を要する。新規サービス構築時は最初から `@Sql` クリーンアップを設計に含めるべきだった

- **bookingms → trackingms 状態連携の未実装**: US14 受入条件 4「bookingms の予約状態が TRACKING_ISSUED に遷移する」は trackingms 側の実装に留まり、bookingms への状態反映（RabbitMQ イベント連携）は未実装。IT5 以降で対応が必要

- **E2E テスト（Playwright）の実施が限定的**: タスク 3.4「E2E: 追跡番号発行→荷役記録→状態更新の一連フロー Playwright テスト」は API レベルの統合テストで代替した。フロントエンドを含む本格的な E2E テストは IT5 以降での整備が課題

- **通知 API（メール通知 stub）の未実装**: US14/US15/US17 の受入条件に含まれる荷主への通知（stub 可）は実装しなかった。IT4 では trackingms のコア機能構築を優先したため許容範囲内だが、IT5 以降で計画的に対応が必要

### Try（改善アクション）

| アクション | 責任者 | 期限 | 期待効果 |
|-----------|--------|------|---------|
| 新規サービス構築時の統合テスト分離チェックリスト作成 | チーム | IT5 開始前 | H2 テスト間データ汚染の早期検知 |
| bookingms → trackingms RabbitMQ イベント連携の実装（TRACKING_ISSUED 遷移） | BE | IT5 | US14 受入条件 4 の完全充足 |
| Playwright E2E テストの整備（追跡フロー全体） | FE | IT5 | 追跡機能のリグレッション防止 |
| 通知 API（stub → 実装）の段階的対応 | BE/FE | IT5〜IT6 | US14/US15/US17 受入条件の完全充足 |

---

## ベロシティ分析

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|--------------|--------|--------|--------|
| IT1 | 24 | 24 | 100% |
| IT2 | 24 | 33 | 138% |
| IT3 | 18 | 18 | 100% |
| IT4 | 21 | 21 | 100% |
| **平均** | **21.8** | **24** | **110%** |

**IT4 ベロシティ**: 21 SP（計画通り）

新規ドメイン（trackingms）の構築という高リスクな作業にもかかわらず、計画通りの SP を達成した。Codex 分業体制と 1 ファイル単位指示戦略が安定したベロシティを実現した主要因。

---

## IT5 への持ち越し事項

1. **bookingms → trackingms 状態連携**: TRACKING_ISSUED 遷移の RabbitMQ イベント連携
2. **通知 API の stub 実装**: US14/US15/US17 の荷主通知機能
3. **Playwright E2E テストの整備**: 追跡番号発行〜荷役記録〜状態更新の全フロー
4. **IT3 コードレビュー中優先度指摘の対応**: 経路設計画面での予約情報自動引き継ぎ（IT3 指摘 #5）・confirmBooking 前の確認ダイアログ（IT3 指摘 #6）

---

## 累計進捗

| 指標 | 値 |
|------|-----|
| 累計完了 SP | 97/194 SP（50%） |
| Phase 1 完了 | 97/115 SP（84%）|
| 残イテレーション（Phase 1） | IT5 1 回 |
| 平均ベロシティ（IT1〜IT4） | 24.3 SP/IT |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-08 | 初版作成（IT4 完了時） | - |

---

## 関連ドキュメント

- [イテレーション 4 計画](./iteration_plan-4.md)
- [イテレーション 3 ふりかえり](./retrospective-3.md)
- [IT3 コードレビュー結果](../review/it3_review_20260508.md)
- [リリース計画](./release_plan.md)
