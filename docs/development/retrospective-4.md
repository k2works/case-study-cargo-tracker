# イテレーション 4 ふりかえり（KPT）

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT4（経路設計） |
| **期間** | 2026-07-02 〜 2026-07-15 |
| **実績** | 11/11 SP（コミット分 100%）、Phase 1 完了 |

## サマリー

US08（経路候補算出）から US12（荷主通知）までを TDD で実装し、Phase 1（Release 1.0 MVP）を達成した。IT3 で確立した cross-service 基盤（ADR-0009）を逆方向（routingms→bookingms）へ適用し、`RouteConfirmedEvent` による経路確定 → 予約状態更新の連携を実装した。ベロシティは 3 イテレーション連続の 10 SP から 11 SP へ計画どおり。

## Keep（継続すること）

- **インサイドアウト TDD の徹底**：ドメインサービス（OptimalRouteService）→ アプリ層 → REST → cross-service ハンドラ → プロジェクションの順で Red-Green-Refactor。新規クラスのカバレッジは 83〜100%。
- **設計ドキュメント準拠**：domain-model / data-model / ui_design / ADR-0009 に沿って実装し、逸脱（route_design_request の状態遷移 = M4）は設計書へ反映。
- **1 コミット 1 目的**：US08 → US09 → US11（routingms 側 / bookingms 側）→ US12 → フロント → E2E → docs と論理単位で分割しコミット。pre-commit の全サービス check で回帰を都度検出。
- **cross-service の冪等設計**：RouteConfirmedEventHandler は CommandExecutionException を握りつぶして再処理・重複配信に耐える。cargo_leg は booking_id 単位で delete → insert。

## Problem（問題点）

- **経路候補が非永続のため select / confirm で再算出する**：route_design_request に旅程列を持たない制約上、選択・確定のたびに OptimalRouteService を再実行する。航海データ変更時に候補集合が変わるリスク（MVP では許容）。
- **ライブ検証が未完**：bookingms が停止中かつ新コード未反映のため、cross-service E2E と SonarQube Quality Gate のライブ実行が未実施。単体・統合テストとカバレッジで代替。
- **OptimalRouteService は直行 + 1 経由まで**：多段経由を未対応とした（リスク対策どおり段階拡張）。US10（条件調整）も未着手。

## Try（次に試すこと）

- **T1（IT5 着手前）**：全スタック（Kafka + 4 サービス）の再ビルド・起動を運用スクリプト化し、cross-service E2E と SonarQube スキャンをライブで通す手順を確立する。
- **T2**：経路候補のサーバー側スナップショット（短期キャッシュ or 一時テーブル）を検討し、select/confirm の再算出依存を解消する（IT8 品質改善で判断）。
- **T3**：OptimalRouteService の多段経由探索（Dijkstra/A*）と US10 条件調整を IT8 バッファで実装する。
- **T4**：IT3 から継続の Try（認証ヘッダ統一・JaCoCo フル再生成運用・QG 条件・HMR 沈静化チェックリスト）をレビュー観点テンプレートへ正式反映する（IT4 では未消化）。

## 数値指標

| メトリクス | 実績 |
|-----------|------|
| ベロシティ | 11 SP/IT（計画 11） |
| カバレッジ（routingms LINE） | 91.4% |
| カバレッジ（bookingms LINE） | 84.4% |
| フロントテスト | 134 件 PASS |
| Code Smell（新規） | TDD により最小限（ライブ SonarQube 確認は次アクション） |

## IT5 への引き継ぎ事項

- US14（追跡番号発行）：CargoTrackedEvent と trackingms の InitializeTrackingCommand 連携。BookingSagaManager の追跡発行フェーズを追加。
- 環境ライブ検証（T1）と US10/多段経由（T3）の消化。
