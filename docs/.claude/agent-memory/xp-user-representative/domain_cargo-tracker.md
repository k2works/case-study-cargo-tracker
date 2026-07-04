---
name: domain-cargo-tracker
description: 国際貨物輸送管理システムのアクター・業務フロー・ユビキタス言語の要点
metadata:
  type: project
---

国際貨物輸送管理システム（Cargo Tracker）の業務ドメイン。

**7 アクター**: 荷主・荷受人・営業担当者・経路設計者・追跡管理者・荷役作業員・経理担当者。

**基幹業務フロー**: 見積 → 荷主登録 → 予約 → 経路設計（航海検索→経路候補算出→条件調整→経路確定→予約紐付け→荷主通知）→ 予約確定 → 追跡番号発行 → 荷役（受領/積込/荷降し/通関/引取）→ 追跡・例外対応 → 精算（料金算出→法人割引→請求→入金確認）。

**アクター別責務**:
- 営業担当者: 見積(US01)・荷主登録(US02/03)・予約(US04/05)・経路引き渡し(US06)・経路通知(US12)・予約確定(US13)
- 経路設計者: 航海検索(US07)・経路候補算出(US08)・経路選択(US09)・条件調整(US10)・経路紐付け(US11)・追跡番号発行(US14)・航海スケジュール登録更新(US24/25)
- 荷役作業員: 荷役作業記録(US15)・引取作業記録（荷受人署名/確認コード取得, US16）
- 追跡管理者: 貨物状態手動更新(US17)・遅延例外(US19)・破損紛失例外(US20)
- 荷主/荷受人: 追跡照会(US18, ログイン不要)
- 経理担当者: 料金算出(US21)・法人割引(US22)・精算(US23)

**ユビキタス言語の注意点**:
- Invoice = ドメインでは「精算書」だが UI では「請求書」表記（用語ゆれあり）
- BookingStatus 8 値: Preliminary→RouteProposed→Confirmed→TrackingIssued→InTransit→Delivered→Settled、任意で Cancelled
- 例外種別 ExceptionType: Delay/Damage/Lost/CustomsHold。Lost は escalationFlag=true で管理職エスカレーション
- 通関 CustomsStatus: Pending/Cleared/Held/Rejected。Cleared にならないと CLAIM(引取)不可
- 危険物は HazardousDeclaration 必須、冷凍は TemperatureRequirement 必須、指定港のみ取扱可
- 法人荷主割引上限 30%
