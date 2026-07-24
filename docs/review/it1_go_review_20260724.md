---
title: IT1 マルチパースペクティブレビュー
description: イテレーション 1（US26 認証・US02/US03 荷主登録・US04 貨物予約登録・ウォーキングスケルトン）の開発成果物を XP 4 視点でレビューした結果と対応方針。
tags: review, iteration-1, go, ddd, hexagonal
---

# IT1 マルチパースペクティブレビュー（2026-07-24）

Go 版 cargo-tracker の IT1 成果物を、XP の 4 視点（プログラマー・テスター・アーキテクト・ユーザー代表）で並列レビューした。対象は US26 ログイン認証・US02/US03 荷主登録・US04 貨物予約登録・ウォーキングスケルトン。

前提: `make check`（build/test/golangci-lint/govulncheck/go-arch-lint）green、SonarQube Quality Gate PASS（Bug 0・脆弱性 0・Code Smell 0・重複 0%）、E2E green。

## 良い点（Keep・4 視点共通）

- ヘキサゴナルのレイヤー依存方向・DIP が徹底（domain は infra を知らない、ポートは application 層に定義）。
- 値オブジェクトの不変性・生成時バリデーション。認証ドメインが bcrypt を漏らさず `PasswordHasher` ポートに委譲。
- BC 独立性を業務識別子（shipper_code）+ ACL で担保。CargoBooked イベントの段階導入。
- テストピラミッドが正しい向き（ドメイン厚・E2E 薄）。IDGenerator/Clock のポート化で決定性確保、境界値・異常系の同値分割が網羅的。
- ロール別ナビ表示制御・RBAC の正/負両方を E2E で検証。PRG + セッション再生成（固定化攻撃対策）。

## 指摘と対応方針

### 高優先度

| # | 指摘（視点） | 対応 |
|---|---|---|
| H1 | Booking 登録エラーが理由を伝えない・入力も消える（programmer・user-rep） | **修正済み**: `ErrShipperNotFound` 等を出し分けフラッシュ表示 |
| H2 | 予約成功後に予約番号・仮受付が画面で見えず US04 受入④が E2E 未検証（user-rep・tester） | **修正済み**: 予約確認画面（`/bookings/confirm/{id}`）を追加し予約番号・状態を表示。E2E で `BKG-`・「仮受付」をアサート |
| H3 | 共有カーネル `ShipperId` の意味二重化（UUID vs shipper_code）（programmer・architect） | **方針明記（[ADR-0005](../adr/0005-bc-reference-and-shared-sqlcgen.md)）**: BC 間参照は業務識別子。IT2 で `ShipperCode` 型へ改称し domain-model/data-model 是正 |
| H4 | 共有 sqlcgen による隠れた BC 結合（architect） | **方針明記（ADR-0005）**: 当面は規律で越境禁止。IT2 で sqlc を BC 別パッケージへ分割 |
| H5 | 認証の enabled チェック順序でエラー種別が分岐（programmer） | **許容**: ハンドラが全エラーを汎用メッセージに正規化するため利用者への情報漏洩はなし。厳密化は IT2 で検討 |

### 中優先度（IT2 で対応 = Try）

- 予約フォームの入力項目が US04 受入より不足（品名・寸法・個数・希望引渡日）。仮受付スコープの割り切りだが、**品名は優先追加**（user-rep）。
- 割引率の異常系（31%/負値）が E2E 未検証。ドメイン=fraction / handler=percent のバリデーション分裂の結合部（tester）。
- `numericFromFloat`（shipper/booking）と コード生成（`GenerateShipperCode`/`generateBookingCode`）の重複 → shared へ抽出（programmer）。
- `applyOptional` が Address の 500 字超過エラーを黙殺（programmer）。
- cargo_repository の Save が round-trip 検証なし（列マッピング誤りを検出できない）（tester）。
- EventPublisher の payload が `any`/文字列イベント名で型安全性を欠く。Phase 2 の型付きイベントへ（architect）。
- domain-model.md / data-model.md 本体が shipper_code 参照・address 列に未追従（architect、iteration_plan 注記済み）。

### 低優先度

- BookingId が UUID 先頭 8 桁（16 進 8 桁の衝突リスク）。UK 違反時のハンドリング未定義。
- `strconv.ParseFloat` 失敗の握りつぶし（不正文字列で無音スキップ）。
- `sessionKeyRoles` の CSV 詰め替え（scs は `[]string` を直接 Put 可能）。
- `Location.IsZero()` が未使用（YAGNI）。
- ユーザー列挙のタイミング差（bcrypt を存在時のみ実行）。

## クローズ判定

高優先度 H1・H2 はクローズ前に**修正済み**。H3・H4 は構造的判断のため **ADR-0005 で方針を明文化**し IT2 の Try とした。H5 は情報漏洩がなく許容。中・低優先度は IT2 のふりかえり Try に引き継ぐ。IT1 はクローズ可能と判断する。

## メトリクス

- Quality Gate: PASS / Bug 0 / Vulnerability 0 / Code Smell 0 / 重複 0%
- カバレッジ: ドメイン層 90-100%、全体 68.5%（SonarQube）
- E2E: 15/15 passed（ナビゲーション 4・認証 6・登録 5）
