# IT6 開発成果物 マルチパースペクティブレビュー（2026-07-23）

対象: IT6（US01 輸送見積・US18 追跡情報公開照会・US19 遅延例外処理）
方式: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）並列レビュー
対象コミット: b426e317, fc66d7ce, a95f6ba0, b103920c, 198ba1f2, ed19ccd3, c30364bf, 3c4db3b8, 4674aa62

## エグゼクティブサマリー

BC 独立・ヘキサゴナル境界・冪等性（ADR-0006）・純粋関数導出の中核設計は 5 視点いずれからも高評価。受入基準は E2E 25 件・HTTP フロー 5 件・単体多数で green。一方、**通知宛先のハードコード（機能欠陥）**・**概算料金のテスト欠如**・**危険物フォーム出し分けのテスト欠如**・**受入基準対応表の不整合**が高優先度として複数視点で重複指摘された。これらはクローズ前に対応済み。推定到着日の簡易実装は既知の負債として IT7 へ繰り越す。

## 高優先度指摘と対応

| # | 指摘（視点） | 対応 |
| :--- | :--- | :--- |
| H1 | 通知宛先が `shipper@cargotracker.example` に 4 箇所ハードコード。US14 は荷受人連絡先を使うのに状態変更・例外発生/解決・荷役反映の通知だけダミー固定＝荷主に届かない機能欠陥（programmer/tester/user/writer） | **対応済**: `resolve_recipient(pool, booking_id)` を導入し `cargo.consignee_email` へ解決（フォールバック付き）。Try#3 の宛先ハードコード解消。HTTP フローで `recipient_email` をアサート |
| H2 | 概算料金 `estimate_cost`（base 50000+weight×80+days×2000）のマジックナンバーがテスト非対象。金額リグレッションを検出できない（programmer/architect） | **対応済**: 定数を名前付き `const` 化し純粋関数として単体テスト 3 件追加 |
| H3 | 危険物フォーム出し分けのテストが単体・統合・E2E のどこにも無い（tester/writer） | **対応済**: E2E に HAZARDOUS 選択時のフィールド表示検証を追加 |
| H4 | 受入基準×テスト対応表の通知種別が `DELAY_NOTIFIED`（実装は `EXCEPTION_RAISED`）で不整合（writer） | **対応済**: 対応表を `EXCEPTION_RAISED`（宛先＝荷受人）に訂正 |
| H5 | 公開追跡ページに「共有 URL」導線・未認証で共有できる旨の案内が無い（writer/user） | **対応済**: 共有 URL 案内ブロックを追加、E2E で検証 |
| H6 | 推定到着日が「最新イベント日時＋頃」の簡易実装で受入基準（到着"予定"）を実質未達。導出テストも無い（tester/programmer/user） | **保留（IT7）**: 確定経路連携が前提のため。コード・計画にコメント済み。IT7 で確定経路からの推定到着日導出を実装 |
| A1 | 新規 ADR: Estimation Context 導入・Routing ACL 隔離（architect） | **対応済**: ADR-0007 起票 |
| A2 | 新規 ADR: 公開ルートの認証境界分離・per-handler 認可（architect） | **対応済**: ADR-0008 起票 |

## 中・低優先度指摘（IT7 繰り越し / 許容）

- **rank 採番の二重責務**（architect 中）: ACL（所要日数昇順）と集約 `replace_candidates`（rank 再ソート）に責務が二重化。ADR-0007 に負債として記録、IT7 で一元化を検討。
- **transit_ports の DB 切り詰め**（programmer 中）: ドメインは複数経由港を表現できるが永続化は先頭 1 港のみ。直行のみの現状は動作。多区間経路導入時に別テーブル化。
- **複数例外の一部解決の状態遷移テスト不足**（tester 中）: index 指定 resolve は単一例外のみ検証。IT7 で複数例外ケースを追加。
- **ルート候補の内容（経由港・日数・料金）値アサート不足**（tester 中）: 件数・航海番号は検証済み。値検証は H2 の料金単体テストで一部担保。
- **危険物フォームの JS 依存**（writer 中）: `onchange` の inline JS で出し分け。JS 無効時の noscript フォールバックは IT7 検討。
- **金額の桁区切り・貨物種別ラベルの表記ゆれ・例外種別 1 択 select**（writer/user 低）: UX 改善として IT7 以降。
- **見積の有効期限・危険物クラスの構造化**（user 中）: 見積の有効期限は業務価値が高い。IT7 の見積拡張で検討。
- **公開ページの再照会フォーム欠如**（writer 中）: 番号違い時の行き止まり。IT7 で入力フォーム追加を検討。

## 良い点（Keep）

- ドメイン層（`domain-estimation`/`domain-tracking`）の TDD テストが Red-Green を意識した粒度で網羅的。`current_status()` の純粋関数導出・例外解決後の状態復帰テストが秀逸。
- 冪等 `issue_tracking`（ADR-0006）の意図がコメント・テストで明示され追跡可能。
- BC 独立が Cargo.toml レベルで健全。BC 跨ぎ結線は ACL（`estimation_acl.rs`/`tracking_acl.rs`）に完全に閉じ込められている。
- `enum RouteCheck { OnRoute, OffRoute, Unknown }` による三値化で「確定経路無し＝判定不能で警告しない」非ブロッキング設計を型で表現。
- 通知件名・本文が荷主向けとして自然な日本語で質が高い。

## 品質ゲート結果

- `cargo fmt --check`: クリーン
- `cargo clippy --workspace --all-targets -- -D warnings`: クリーン
- 単体テスト（`--lib`）: 全 green（interface-web 7 件に料金テスト 3 件含む）
- HTTP フロー統合テスト（testcontainers）: 5 件 green（宛先解決アサート含む）
- E2E（Playwright）: IT1〜IT6 全 25 件 green（危険物出し分け・共有 URL 検証追加後）
