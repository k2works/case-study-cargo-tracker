---
title: ビジネスルール ⇄ テスト トレーサビリティ
description: ドメインモデル設計のビジネスルールと、それを検証するテスト関数の対応表。カバレッジ計測不能への代替統制。
published: true
date: 2026-08-03T00:00:00.000Z
tags: design, test, traceability, flix
---

# ビジネスルール ⇄ テスト トレーサビリティ

## 目的

Flix にはカバレッジ計測ツールが存在しないため、行カバレッジ率を品質ゲートに用いない。
代わりに本表で **[ドメインモデル設計](domain-model.md) に記載された各ビジネスルールに、それを検証するテスト関数が存在すること**を担保する。

これは [テスト戦略](test_strategy.md) 6.1 の代替統制の中核であり、[ADR-0002](../adr/ADR-0002-self-built-web-and-security.md) の補償策の 1 つである。

## 運用規約

| 規約 | 内容 |
| :--- | :--- |
| **入力元** | [ドメインモデル設計](domain-model.md) の各コンテキストの「ビジネスルール」節 |
| **更新タイミング** | ルールを実装する前に行を追加する（テストを書く前）。実装と同一コミットで状態を更新する |
| **状態の意味** | `未着手`（当該 IT のスコープ外）/ `実装中`（テストのみ存在）/ `済`（テストが通っている） |
| **クローズ時の確認** | イテレーションクローズ時に「当該 IT で実装したルールに `未着手` が残っていないこと」を確認する |
| **ルールの追加** | ドメインモデル設計にルールを追加したら、本表にも同一コミットで行を追加する |

> **本表がカバーしないもの**: 技術的な関心事（HTTP・永続化・画面生成）は原則として対象外である。
> ただし [ADR-0002](../adr/ADR-0002-self-built-web-and-security.md) が自作すると決めた範囲
> （認証・認可・セッション・CSRF）は、自作に伴うリスクの補償策として本表で追跡する。
> それらは [テスト戦略](test_strategy.md) 5 章のストーリートレーサビリティ表で担保する。

## Shared Domain

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| SD-1 | `Location` の変更は全コンテキストの合意のもとに行う（Shared Kernel の制約） | （プロセス規約。テスト対象外） | - | - |
| SD-2 | UN/LOCODE は国際規格（ISO 3166-1 alpha-2 + 3 文字）に従う | `CargoTest.testRejectsMalformedLocationCode`<br>`CargoTest.testNormalizesLocationCodeToUpperCase`（**書き込み経路での形式検証**。共有カーネルの `Location` 値オブジェクトへの切り出しは IT5） | **済** | IT4 |
| SD-3 | `TransportStatus` と `RoutingStatus` は Booking / Tracking / Handling 間で整合性を保つ | （IT8 で荷役 → 追跡の連携実装時に対応） | 未着手 | IT8 |

## 認証・認可（共有カーネル）

[非機能要件](non_functional.md) 4.1 と [バックエンドアーキテクチャ](architecture_backend.md) の
セキュリティ設計に由来するルール。業務ドメインのルールではないが、
**自作であるため同じ枠組みで追跡する**（[ADR-0002](../adr/ADR-0002-self-built-web-and-security.md)）。

| # | ルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| AU-1 | パスワードは BCrypt（コスト 12）でハッシュ化し、平文を保存・出力しない | `PasswordTest.testHashDoesNotContainPlaintext`<br>`PasswordTest.testUsesCostTwelve` | **済** | IT3 |
| AU-2 | 認証成功時にセッション ID を再生成する（セッション固定攻撃対策） | `SessionTest.testLoginRegeneratesSessionId` | **済** | IT3 |
| AU-3 | 同一利用者の同時セッション数は 1（[ADR-0003](../adr/ADR-0003-session-concurrency.md)） | `SessionTest.testLoginInvalidatesExistingSession` | **済** | IT3 |
| AU-4 | ログイン失敗が 5 回連続するとアカウントを 30 分ロックする | `LoginHttpTest.testLocksAccountAfterFiveFailures`<br>`LoginHttpTest.testSuccessResetsFailureCount` | **済** | IT3 |
| AU-5 | 無効化されたアカウントではログインできない | `LoginHttpTest.testRejectsDisabledAccount` | **済** | IT3 |
| AU-6 | 認証情報の誤りは理由を区別せず一律の文言で通知する | `LoginHttpTest.testShowsGenericFailureMessage` | **済** | IT3 |
| AU-7 | 認可はルーティング表の `RequiredRole` のみに基づく。`Admin` は全ルートを通る | `AuthorizationTest.testDecisionTableForAllRoles`<br>`AuthorizationTest.testAdminPassesEveryRoute` | **済** | IT3 |
| AU-8 | 状態を変更するリクエストは CSRF トークンを検証する（未認証ルートを除く） | `CsrfTest`（7 件）<br>`LoginHttpTest.testLogoutRequiresCsrfToken` | **済** | IT3 |
| AU-9 | セッション Cookie は `HttpOnly` / `SameSite=Lax` を持ち、本番では `Secure` を付ける | `CookieTest.testSessionCookieHasSecurityAttributes`<br>`CookieTest.testSecureAttributeInProduction` | **済** | IT3 |
| AU-10 | 未知のロールを持つ利用者は認証されない（既定ロールへ倒さない） | `SecurityTest.testUnknownPersistedValueIsRejected` | **済** | IT3 |
| AU-11 | セッションのタイムアウトはロール別（`Handler` 2 時間・その他 30 分）。最終アクセスから起算する | `SessionTest.testGeneralRoleAliveJustBeforeTimeout`<br>`SessionTest.testGeneralRoleExpiresAfterThirtyMinutes`<br>`SessionTest.testHandlerRoleHasLongerTimeout`<br>`SessionTest.testHandlerRoleExpiresAfterTwoHours`<br>`SessionTest.testTimeoutIsMeasuredFromLastAccess` | **済** | IT4 |

## Tracking Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| TR-1 | 追跡活動は必ず一意の `TrackingNumber` を持つ | 一意制約は `V1__init.sql` の `UNIQUE` で担保。**追跡番号の発行は US14（IT8）**であり、IT4 の書き込み経路には含まれない | 未着手 | IT8 |
| TR-2 | `TrackingActivityEvent` は時系列順で管理される。イベントごとに位置と時刻が必須 | `TrackingQueryTest.testEventsAreOrderedByTime` | **済** | IT1 |
| TR-3 | `ExceptionType` が `LOST` の場合、`escalationFlag` を `true` に設定する | （IT9 で例外処理実装時に対応） | 未着手 | IT9 |
| TR-4 | `CUSTOMS_HOLD` 例外は税関システムからの通知で自動登録される | （IT9） | 未着手 | IT9 |
| TR-5 | `ResolveExceptionCommand` の実行で `TrackingStatus` は例外発生前の状態に復帰する | （IT9） | 未着手 | IT9 |
| TR-6 | 推定到着日は経路が確定している場合にのみ定まる。未確定の貨物では値を持たない（照会時は「未定」） | `TrackingPublicPagesTest.testShowRendersEstimatedArrival`<br>`TrackingPublicPagesTest.testShowRendersUndeterminedArrival`<br>`PublicTrackingHttpTest.testShowsEstimatedArrival` | **済**（読み取り経路） | IT2 |

## Booking Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| BK-1 | 貨物は必ず `BookingId`・`ShipperId`・`CargoType` を持つ | `CargoTest.testBooksCargoWithRequiredFields`<br>`CargoTest.testRejectsEmptyShipperId` | **済** | IT4 |
| BK-2 | `RouteSpecification` の出発地と目的地は異なる | `CargoTest.testRejectsSameOriginAndDestination`<br>`CargoTest.testRejectsSameLocationIgnoringCase`<br>`CargoTest.testRejectsMalformedLocationCode` | **済** | IT4 |
| BK-3 | `CargoItinerary` は 1 つ以上の `Leg` で構成され、`Leg[n].unloadLocation == Leg[n+1].loadLocation` を満たす | - | 未着手 | IT7 |
| BK-4 | `BookingStatus` の遷移順序。いずれの状態からも `CANCELLED` に遷移可能 | `CargoTest.testNewCargoIsPreliminary`<br>`CargoTest.testBookingStatusRoundTrip`（**遷移そのものは IT5 以降**。IT4 で作れるのは `PRELIMINARY` のみ） | 実装中 | IT5 |
| BK-5 | `CORPORATE` の荷主は割引適用の対象となる（上限 30%） | - | 未着手 | IT10 |
| BK-6 | `HAZARDOUS` / `REFRIGERATED` は指定港のみ取扱可能 | - | 未着手 | **IT7** |
| BK-7 | `HAZARDOUS` の場合、`HazardousDeclaration` は必須 | `CargoTest.testRequiresHazardousDeclarationForHazardousCargo`<br>`CargoTest.testRejectsHazardousDeclarationForNonHazardousCargo`<br>`BookingHttpTest.testShowsErrorWhenHazardousDeclarationIsMissing` | 実装中 | IT5 |
| BK-8 | `REFRIGERATED` の場合、`TemperatureRequirement` は必須 | `CargoTest.testRequiresTemperatureRequirementForRefrigeratedCargo`<br>`CargoTest.testRejectsTemperatureRequirementForNonRefrigeratedCargo`<br>`BookingHttpTest.testShowsErrorWhenTemperatureIsMissing` | 実装中 | IT5 |
| BK-9 | Booking は Shipper に直接依存せず、ACL ポート経由で確認する | `BookCargoTest.testRejectsUnknownShipper`<br>`BookCargoTest.testChecksShipperBeforeValidatingInput`<br>`BookingHttpTest.testRejectsUnknownShipperWithInputError`<br>`arch-lint` 規約 4 | **済** | IT4 |

## Shipper Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| SH-1 | 荷主は必ず `ShipperId`・`ShipperCode`・`ShipperName`・`Email`・`ShipperType` を持つ | `ShipperTest.testRegistersIndividualWithRequiredFieldsOnly`<br>`ShipperTest.testRejectsEmptyName`<br>`ShipperTest.testRejectsEmptyEmail`<br>`ShipperTest.testRejectsEmptyShipperId` | **済** | IT4 |
| SH-2 | `Email` はシステム全体で一意 | `RegisterShipperTest.testReturnsExistingShipperOnDuplicateEmail`<br>`RegisterShipperTest.testDuplicateCheckIsCaseInsensitive`<br>`JdbcShipperRepoTest.testUniqueEmailConstraintIsEnforced`<br>`ShipperHttpTest.testDuplicateEmailShowsChoices` | **済** | IT4 |
| SH-3 | `CORPORATE` の場合、`ContractNumber` と `DiscountRate` が必須 | `ShipperTest.testRejectsCorporateWithoutContractNumber`<br>`RegisterShipperTest.testCorporateWithoutContractIsInvalid`<br>`ShipperHttpTest.testRejectsCorporateWithoutContractNumber` | **済** | IT4 |
| SH-4 | `DiscountRate` の値域は 0.0000〜0.3000 | `ShipperTest.testRejectsDiscountRateOver30Percent`<br>`ShipperTest.testAcceptsDiscountRateAtUpperBound`<br>`ShipperTest.testAcceptsZeroDiscountRate`<br>`ShipperTest.testRejectsNegativeDiscountRate` | **済** | IT4 |
| SH-5 | `ShipperCode` は自動生成（`SHP-` + UUID 先頭 8 文字） | `ShipperTest.testGeneratesShipperCodeFromId`<br>`JdbcShipperRepoTest.testShipperCodeCollisionIsRejected`（衝突の限界を仕様として固定） | **済** | IT4 |

## Routing Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| RT-1 | 航海は必ず一意の `VoyageNumber` を持つ | - | 未着手 | IT5 |
| RT-2 | `Schedule` は時系列順の `CarrierMovement` で構成される | - | 未着手 | IT5 |
| RT-3 | `CarrierMovement` の出発地と到着地は異なる | - | 未着手 | IT5 |
| RT-4 | `Location` は UN/LOCODE で一意に識別される | 主キー制約は `V1__init.sql` で担保。形式検証は `CargoTest.testRejectsMalformedLocationCode`（SD-2 と同じ実装） | **済** | IT4 |

## Handling Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| HD-1 | `RECEIVE`: VoyageNumber 不要。出発港と不一致で警告 | - | 未着手 | IT8 |
| HD-2 | `LOAD`: VoyageNumber 必須。積込港と不一致で MISROUTED | - | 未着手 | IT8 |
| HD-3 | `UNLOAD`: VoyageNumber 必須。荷降港と不一致で MISROUTED | - | 未着手 | IT8 |
| HD-4 | `CLAIM`: VoyageNumber 不要。目的港と不一致で警告 | - | 未着手 | IT8 |
| HD-5 | MISROUTED 確定時、Booking の `RoutingStatus` を MISROUTED に更新する | - | 未着手 | IT8 |
| HD-6 | `CustomsDeclaration` が `CLEARED` になるまで `CLAIM` は実施できない | - | 未着手 | IT8 |
| HD-7 | `HandlingActivityHistory` は Read Model として集約と切り離す | - | 未着手 | IT8 |

## Billing Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| BL-1 | `Invoice` は `DELIVERED` 後にのみ発行できる | - | 未着手 | IT10 |
| BL-2 | 法人荷主には最大 30% の割引が適用される | - | 未着手 | IT10 |
| BL-3 | 支払期限（発行 + 30 日）超過で `OVERDUE` に更新する | - | 未着手 | IT10 |
| BL-4 | `CONFIRMED` 後のキャンセルは `REFUNDED` に遷移する | - | 未着手 | IT10 |
| BL-5 | 基本料金 = 距離係数 × 重量 × 貨物種別係数（GENERAL 1.0 / HAZARDOUS 1.8 / REFRIGERATED 1.5） | - | 未着手 | IT10 |

## Estimation Context

| # | ビジネスルール | テスト関数 | 状態 | IT |
| :--: | :--- | :--- | :---: | :--: |
| ES-1 | 見積は必ず `EstimateId`・origin・destination・期限・`CargoType`・重量を持つ | - | 未着手 | IT5 |
| ES-2 | origin と destination は異なる | - | 未着手 | IT5 |
| ES-3 | 重量は正の値でなければならない | - | 未着手 | IT5 |
| ES-4 | `RouteCandidate` の voyageNumber は非空、transitDays・estimatedCost は正の値 | - | 未着手 | IT5 |
| ES-5 | 見積作成時のデフォルトステータスは `CREATED` | - | 未着手 | IT5 |

## 集計

| コンテキスト | ルール総数 | 済 | 実装中 | 未着手 | 対象外 |
| :--- | :--: | :--: | :--: | :--: | :--: |
| Shared Domain | 3 | 0 | 0 | 2 | 1 |
| Tracking | 5 | 1 | 0 | 4 | 0 |
| Booking | 9 | 0 | 0 | 9 | 0 |
| Shipper | 5 | 0 | 0 | 5 | 0 |
| Routing | 4 | 0 | 0 | 4 | 0 |
| Handling | 7 | 0 | 0 | 7 | 0 |
| Billing | 5 | 0 | 0 | 5 | 0 |
| Estimation | 5 | 0 | 0 | 5 | 0 |
| **合計** | **43** | **1** | **0** | **41** | **1** |

## 更新履歴

| 日付 | 更新内容 |
| :--- | :--- |
| 2026-08-03 | 初版作成（IT1 返済枠）。ドメインモデル設計の全 43 ルールを登録 |
| 2026-08-14 | IT1 完了。TR-2 を済に更新。値オブジェクト検証を伴うルール（SD-2・TR-1・RT-4）は書き込み経路を実装する IT4 へ移動 |
| 2026-08-28 | IT2 完了。TR-6（推定到着日）を追加し読み取り経路として済に更新。書き込み側（経路確定時の設定）は IT5 の経路割り当てで扱う |
| 2026-08-31 | IT3: 認証・認可のルール（AU-1〜AU-11）を追加。AU-11 は時刻の注入が必要なため IT4 へ |
| 2026-09-25 | IT4: AU-11・SD-2・RT-4・BK-1/2/9・SH-1〜5 を「済」へ。TR-1 は追跡番号の発行が US14 のため IT8 へ、BK-4 の遷移と BK-6/7/8 は US05・US06 とともに IT5 へ移した |
