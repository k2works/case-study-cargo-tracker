# ADR-008: 経路候補 Port の境界と経路確定・追跡番号採番の暫定配置

Estimation Context の見積概算候補 Port と Routing Context の経路候補 Port を統合せず別ポートとして維持する。Booking への経路紐付けは Booking 固有の経路候補 ACL（`RouteCandidateAcl`）を境界とし、追跡番号採番は IT4 では Booking 側で暫定的に行う。

日付: 2026-07-29

## ステータス

承認済み（IT4）

## コンテキスト

IT4 で経路確定（US09/US11）・予約確定（US13）・追跡番号発行（US14）を実装するにあたり、[ADR-007](007-shared-kernel-and-stub-acl.md) が IT4 へ持ち越した以下の判断が必要になった。

1. **候補 Port の統合可否**: Estimation Context は見積作成時に概算候補・概算料金を返す `RouteCandidateCalculator`（`RouteCandidate`: `voyageNumber` / `transitPort` / `transitDays` / `estimatedCost`）を持つ。Routing Context は IT3 で `ExternalRoutingServicePort` + fallback による経路候補算出（`RouteCandidate`: `voyageNumbers[]` / `transitPorts[]` / 出発・到着時刻 / `transitDays` / `estimatedCost`）を実装済み。両者を統合すべきか、別ポートとして維持すべきか。
2. **Booking への候補受け渡し**: US09/US11 で選択した経路候補を Booking の `CargoItinerary`（区間別 `load_time` / `unload_time` を持つ `Leg` 群）へ変換する必要がある。Routing の `RouteCandidate` は区間別スケジュールを持たないため、変換に必要な情報が不足する（IT4 計画 注 6）。
3. **Routing ACL の Booking 参照**（IT3 レビュー M4）: Routing のスタブ ACL が Booking の DB スキーマを直接読む懸念への方針。
4. **追跡番号の採番主体**: domain-model のイベントフローは Tracking Context が採番して Booking へ戻す設計だが、Tracking 集約は IT5-6 実装予定であり IT4 では未実装。

## 決定

### 1. 候補 Port は統合せず別ポートとして維持する

- Estimation の見積概算候補（`RouteCandidateCalculator`）と Routing の経路候補（`ExternalRoutingServicePort`）は**別ポートとして維持**する。
- 根拠: 両者は目的・粒度・利用フェーズが異なる。見積概算は「営業が荷主へ概算を提示する」ための軽量計算（航海スケジュール非依存）であり、経路候補は「経路設計者が実在の航海から確定経路を選ぶ」ための実データ依存の算出である。同一 Port へ無理に統合すると、見積の軽量性と経路確定の実データ精度の双方を損なう。
- 見積→予約の引き継ぎは従来どおり `EstimateId` 参照のみとし（domain-model の疎結合方針）、経路確定は下記の Booking 固有 ACL を用いる。

### 2. Booking への経路紐付けは Booking 固有の `RouteCandidateAcl` を境界とする

- Booking Context に読み取り ACL ポート `RouteCandidateAcl`（`findCandidates(query): RouteCandidateOption[]`）を新設する。`RouteCandidateOption` は選択に必要な `id` と、`CargoItinerary` 変換に必要な区間別 `LegDraft`（`voyageNumber` / `loadLocation` / `unloadLocation` / `loadTime` / `unloadTime`）を持つ。
- これにより注 6 の「区間別スケジュール不足」を解消する。Booking は Routing のドメイン型（`RouteCandidate` / `Voyage`）に依存せず、この ACL の DTO を境界とする（BC 独立性）。
- 実装アダプタ `KyselyRouteCandidateReader` は `voyage` / `carrier_movement` を読み取り、直行および 1 寄港接続の候補を Leg ドラフト付きで組み立てる。これは **Booking 側の読み取りモデル（Published Language 相当）** であり、Routing のドメインを侵さない読み取り専用境界である（M4 への回答）。
- **統制上の盲点（IT4 レビュー M2）**: この共有 DB 直読は dependency-cruiser の `no-cross-context` ルールでは検出できない（同ルールはコード import のみを検証する）。Routing が `voyage` / `carrier_movement` のカラム名・意味を変更すると、arch チェックは緑のまま Booking の候補算出が静かに壊れうる。緩和策として、テーブル所有境界を明文化しスキーマ変更時の契約テストで守る。中期的には Routing 側の読み取り ACL API 化を検討する（IT5 以降）。
- 候補選択の POST ではクライアント提供の時刻を信頼せず、`candidateId` を受けてサーバ側で候補を再解決し、その `LegDraft` から `CargoItinerary` を組み立てる。

### 3. 追跡番号は IT4 では Booking 側で暫定採番する

- IT4 では `AssignTrackingNumberService`（Booking application）が採番し、`cargo.tracking_number`（nullable + 発行済のみ部分 UNIQUE）へ保存する。
- Tracking Context（`tracking_activity`）実装（IT5-6）時に、domain-model のイベントフローどおり **採番主体を Tracking Context へ再配置**する。本 ADR を更新して移行を記録する。

## 影響

- `docs/design/domain-model.md`: Booking のビジネスルール 4 に差戻し遷移（`ROUTE_PROPOSED → ROUTING_IN_PROGRESS`）を追記。`CargoItinerary` / `Leg` / `ROUTE_PROPOSED` 以降の遷移を IT4 実装済みへ更新。ACL Ports に Booking の `RouteCandidateAcl` を追記。
- `docs/design/data-model.md`: `leg` テーブル・`cargo.tracking_number`（nullable + 部分 UNIQUE）・`notification_record` を IT4 実装済みへ同期。
- `docs/design/ui_design.md`: `/bookings/{id}/route`・`/notify`・`/confirm`・`/return-to-routing`・`/tracking-number` を画面遷移へ追補。追跡番号発行は経路設計者操作のため予約詳細への経路設計者到達性を明記。
- Estimation の `StubRouteCandidateCalculator` は見積専用概算として残存（既知の負債。精算フェーズで料金ロジックの正典化を再検討）。

### 代替案

- **候補 Port を統合**: DRY だが、見積概算（軽量・スケジュール非依存）と経路確定（実データ依存）の要件差を吸収できず却下。
- **Routing に Booking 向けの候補 API を持たせる**: Booking→Routing の同期依存が増え、BC 独立性と Booking のデプロイ独立性を損なうため却下。Booking 側読み取り ACL に閉じる方が結合が弱い。
- **追跡番号を IT4 で Tracking Context 実装まで先送り**: US14 が IT4 スコープで未完となり Release 0.5 基幹フローが途切れるため却下。暫定採番 + 将来再配置とする。

## 追記（IT5 レビュー）

共有 DB 直読は「参照専用スナップショット / 読み取り ACL をポート裏に隠蔽する」場合に限る（例: `KyselyRouteCandidateReader`・`KyselyCargoSnapshot`）。IT5 の通知アダプタ（Handling / Tracking の `notifyStatusChange`）は荷主メール解決のため cargo × shipper を生 JOIN しておりこの方針から逸脱している。連絡先解決は Booking の `ShipperContactAcl` と同様のポート抽象に寄せる（IT6 で notification_record の所有と合わせて整理。3 BC が同テーブルへ書き込む現状は Notification Context 分割の候補サイン）。
