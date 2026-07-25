---
title: IT3 マルチパースペクティブレビュー
description: IT3（US24/US25/US07 航海スケジュール・US01 見積・US06 引き渡し・基盤整備）の XP 5 視点レビュー統合レポート。
tags: review, iteration-3, developing-review, go
---

# IT3 マルチパースペクティブレビュー（2026-07-25）

対象: IT3 実装差分 `8e1e7b42..HEAD`（67 ファイル・約 3,400 行）。Routing/Estimation の 2 新規 BC 立ち上げ、US24/US25/US07/US01/US06、CargoType 共有カーネル昇格、sqlc BC 別分割。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）を並列起動し統合。

## エグゼクティブサマリー

2 新規 BC の立ち上げは DDD/ヘキサゴナル/CQRS/TDD の規律が高水準で保たれ、BC 独立性・レイヤー依存に違反なし（Architect: 構造健全）。高優先度指摘は **ドメイン正しさ・設計ドキュメント整合・フォーム UX** に集約され、クローズ前に対応した。UI の深掘り（動的区間・候補精緻化）は US08（IT4）依存として繰越。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高優先度指摘 | 対応 |
|---|---|---|---|
| Programmer | 要対応 1 | H-1: Schedule が時刻連続性を検証していない（宣言と実装の乖離） | ✅ ErrOutOfOrderSchedule を追加し不変条件化・テスト追加。L-2（decodeCargoTypes 検証）も対応 |
| Tester | 要追加 8（高 4） | Schedule 3区間連結・US25/US07/US01 の境界・E2E 日付ハードコード | ✅ Schedule 検証と E2E 相対日付化を対応。US25/07/01 の境界テストは IT4 Try に計上 |
| Architect | 構造健全（高 0） | 中: sqlc models の全型複製が ADR 記述と乖離 | ✅ ADR-0005 に仕様上の制約として注記 |
| Technical Writer | 要対応 4（高） | 設計ドキュメント本体が IT3 の「注」是正を未反映（DoD 未達） | ✅ data-model/domain-model/ui_design に voyage 拡張・CargoType 昇格・航路権限を反映 |
| User Representative | 要対応 3（高） | H1: フォーム必須検証 / H2: 区間固定・edit データ損失 / H3: 候補の経由港・期限不成立通知 | 一部対応・一部 IT4 繰越（下記） |

## クローズ前に対応した高優先度

- **Schedule の時刻連続性検証**（Prog H-1 / Tester）: 次区間の出発が前区間の到着以降であることを不変条件に追加。時間逆行スケジュールを拒否。
- **設計ドキュメント本体の是正**（TW 高 4・DoD 必達）: voyage の vessel_name/carrier/supported_cargo_types、Voyage 集約フィールド、CargoType の共有カーネル昇格、航路の書き込み権限を data-model/domain-model/ui_design に反映。
- **フォーム必須検証**（User-rep H1）: 航海登録フォームの区間1（港・日付）を required 化、区間3-4 を追加し複数寄港に対応。
- **E2E 日付の相対化**（Tester）: ハードコード日付を daysFromNow で相対化し、将来の CI 赤化（時限爆弾）を防止。
- **カバレッジ補強**: routing/estimation の domain ゲッター・Restore・Ja、両 handler の unit テストを追加。
- **decodeCargoTypes の検証**（Prog L-2）・**ADR-0005 の注記**（Architect 中）。

## 次イテレーション（IT4）への Try（保留・繰越）

| 由来 | 内容 | 優先 |
|---|---|---|
| User-rep H2 | 運送区間の動的行追加、edit の複数区間対応（現状 edit は 1 区間で上書きするデータ損失リスク） | 高 |
| User-rep H3 | ルート候補に経由港列を追加、候補ゼロ時の期限不成立通知（候補精緻化は US08 と同時） | 中 |
| Tester | US25（区間数変更）round-trip・US07（複合 AND・出発期間境界）・US01（期限ちょうど）の境界テスト | 中 |
| Prog M-3 | stubCandidates の time.Now を Clock ポートで注入（テスト決定性） | 中 |
| Prog M-1 | Estimate の arrivalDeadline 非ゼロ・未来日検証を集約へ | 中 |
| Prog L-1/L-3 | uuidToString を標準ライブラリ化、SearchVoyageService エイリアス削除 | 低 |
| Arch 低 | CargoType 新種別追加時に booking 再エクスポートも更新する DoD 化 | 低 |

## 品質確認

- 全単体テスト green・integration（testcontainers）green・E2E green（voyage/estimate/navigation 含む）
- ドメイン層カバレッジは domain 単体テスト追加で向上（SonarQube ゲートで最終確認）
- `make check`（build + test + lint + govulncheck + arch）green

## 関連

- [IT3 計画](../development/iteration_plan-3.md)
- [ADR-0005](../adr/0005-bc-reference-and-shared-sqlcgen.md) / [ADR-0006](../adr/0006-shared-cargo-type-and-voyage-model.md)
