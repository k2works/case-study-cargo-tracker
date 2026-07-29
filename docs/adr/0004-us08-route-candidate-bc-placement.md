# ADR 0004: US08 経路候補算出の BC 帰属（Routing 一時計算 / Estimation 永続化）

US08（経路候補を算出する）を IT3 で Routing Context に一時計算値として実装し、経路候補の永続化は Estimation Context を作る IT7 に委ねる。

日付: 2026-07-28

## ステータス

2026-07-28 承認されました（IT3）

## コンテキスト

- ADR-0001 は 8 コンテキストを定義し、domain-model.md・architecture_backend.md では **`RouteCandidate` を Estimation Context の要素**（`Estimate` 集約の子）とし、US08 経路候補算出を Estimation の受け皿として位置づけている（`route_candidates` は `estimates` 子テーブル）。
- 一方、開発戦略（development_strategy.md）は **IT3（中盤・Routing Context）に US24/US25/US07/US08 を割り当て**ている。Estimation Context は Phase 4（IT7）まで着手しない。
- そのまま US08 の `RouteCandidate` を Routing に新設すると、IT7 で Estimation が同名要素を定義した際に **BC 間で二重定義**となり、Packwerk 境界がドリフトする。逆に US08 を IT7 まで遅延させると、IT3 の「経路割り当て画面で候補を提示する」デモ価値が失われる。
- IT3 開始準備の横断検証（validating-design）でこの正典衝突が検出された。

## 決定

1. **IT3 では経路候補を Routing Context で「一時計算値（非永続）」として算出**する。Routing の `CalculateRouteCandidates` ユースケースが、Voyage スケジュールと外部経路システム（`ExternalCargoRoutingService`）から `RouteCandidate`（Routing 内の一時的な値オブジェクト）を計算し、経路割り当て画面に提示する。**`route_candidates` テーブルへの永続化は行わない**。

2. **経路候補の永続化・見積との紐付けは Estimation Context（IT7）の責務**とする。IT7 で Estimation を実装する際、`Estimate` 集約が確定した経路候補を `route_candidates` として永続化する。その時点で Routing の一時 `RouteCandidate` から Estimation の永続 `RouteCandidate` への変換（またはユビキタス言語の整理）を行う。

3. **Routing → 外部経路システムの ACL は Routing Context に配置**する。`ExternalCargoRoutingService`（出力ポート・Routing ドメイン層の抽象）と `ExternalCargoRoutingClient`（Faraday HTTP アダプタ・Routing インフラ層）。外部経路システムは真の外部連携のため HTTP アダプタが妥当（ADR-0003 のインプロセス方針は BC 間参照に適用され、外部システム連携には適用されない）。接続タイムアウト時は過去実績データからフォールバック候補を返す。

4. **Booking → Routing の越境は公開 API 経由**とする。Booking の経路割り当て画面は `Routing::Public::VoyageDirectory`（および経路候補の公開 API）を通じてのみ Routing を参照し、Packwerk privacy で直接参照を静的に禁止する（ADR-0001/0003 と同型）。

## 影響

- domain-model.md: Routing Context に `RouteCandidate`（一時計算値）・`ExternalCargoRoutingService` ポートを追記し、Estimation の `RouteCandidate`（永続）との役割分担を注記する。
- architecture_backend.md: 外部経路 ACL の配置を Routing Context として明確化する（Booking 記載との整合）。
- `route_candidates` テーブルは IT3 では作成しない（IT7 の Estimation で作成）。
- IT7（Estimation）着手時に本 ADR を再検討し、Routing 一時候補と Estimation 永続候補の統合方針を確定する。

## IT7 での統合方針（決定4 の確定・2026-07-29）

IT7 で Estimation Context を実装するにあたり、決定4 の統合方針を以下のとおり確定した。

- **Routing 一時候補（`Routing::Public::VoyageDirectory#calculate_route_candidates` の `RouteCandidateView`）を「正」とし、Estimation はこれを ACL 変換で取り込む**。Estimation は Routing の内部 `RouteCandidate` を共有せず、公開 API のビュー（プリミティブ）を経由して自身の `Estimation::Domain::RouteCandidate`（永続値オブジェクト）へ変換する（`CreateEstimate#build_candidates`）。両 BC は同名 `RouteCandidate` を持つが、それぞれの BC 内に閉じた別型であり、変換は Estimation のアプリ層で行う（Packwerk privacy ゼロ違反）。
- **概算料金は Billing の `FreightCalculationService`（`Billing::Public::FreightCalculator`）で算出**し、Estimation は候補ごとに概算料金を付与する。距離係数は暫定的に所要日数で代替する（実距離データ導入まで）。
- **`route_candidates` テーブルは Estimation が所有**し、`Estimate` 集約の子として `estimate_id` FK で永続化する（`CASCADE` 削除）。
- これにより「Routing=一時計算・Estimation=永続化」の役割分担が実装で確定し、ユビキタス言語の二重定義は各 BC 内に閉じた同名別型として解消した。
