---
name: test-strategy-gaps
description: cargo-tracker C# 版 test_strategy.md のテスタビリティ上の構造的欠陥（2026-07-04 レビュー時点）
metadata:
  type: project
---

2026-07-04 の設計レビューで検出した test_strategy.md の弱点。将来レビュー時に再確認すること。

- **トレーサビリティ番号の乖離**: `docs/design/test_strategy.md` セクション 5 の US 番号・タイトルが `docs/requirements/user_story.md`（US01〜US25 が正）と一致しない。test 側 US08=予約確定 は実際 US13、US10=荷役 は US15、US13=追跡照会 は US18。test 側は US18 までしか扱わず、US19〜US25（航海スケジュール登録/更新 等）がトレーサビリティ表に不在。E2E 優先シナリオの US 参照も誤り。
  **Why:** テスト戦略が古い or 別系統のストーリー版に基づいて書かれた疑い。
  **How to apply:** 修正済みか毎回照合する。番号は user_story.md を正とする。

- **post-commit イベントディスパッチのテスト空白**: architecture_backend.md 450-454 行が「トランザクションコミット成功後にディスパッチ」を要求するが、この振る舞い（ロールバック時にハンドラ非実行 / コミット後に実行）を検証するテストが戦略に無い。MediatR 同期 Publish のため保証が壊れても既存テストは緑のまま。
  **How to apply:** 統合テストにコミット/ロールバック × ハンドラ呼び出し順序の検証を要求する。

- **楽観ロックの宣言と実体の不一致**: test_strategy 3.2 が Repository 検証対象に「楽観的ロック」を挙げるが data-model.md に version 列が無い。
- **DbUp スクリプトの SQLite/PostgreSQL 両対応スモークテスト**が未定義（データアクセスは PostgreSQL に寄せる方針は妥当）。
- **SLO 検証**: non_functional は k6/JMeter・追跡 API 1000 RPS を要求するが test_strategy はツール/シナリオ/合否未定義。CI 10 分枠での 1000 RPS 検証は非現実的。
