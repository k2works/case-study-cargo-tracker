---
name: ui-us-mapping-drift
description: ui_design.md 画面一覧の「対応 US」列が user_story.md の現行 US 番号と食い違っている
type: project
---

ui_design.md 画面一覧の「対応 US」列が user_story.md（US01-US25）と整合していない。

**Why:** UI 設計時点の旧 US 番号を参照したまま更新されていないと推測。例）UI「貨物追跡入力=US13」だが現行 US13 は「予約を確定する」、追跡照会は US18。UI「荷役作業登録=US10,US11」だが現行では US15,US16。UI「請求書詳細=US18」だが精算は US21-23。

**How to apply:** UI レビュー・トレーサビリティ確認の際は、この対応列を鵜呑みにせず user_story.md 本文と突き合わせること。US 番号採番の正は user_story.md。
