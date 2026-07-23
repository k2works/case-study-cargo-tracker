# ADR 0007: Estimation Context の新設と Routing 参照の ACL 隔離（IT6）

## ステータス

承認（IT6 時点）

## コンテキスト

IT6 で US01（輸送見積の作成）を実装するにあたり、新しい境界づけられたコンテキスト **Estimation Context** を導入した。見積は「営業担当者が輸送要件（出発地・目的地・希望期限・貨物種別・重量）を入力すると、既存の航海スケジュール（Routing）を用いてルート概算候補（経由港・所要日数・概算料金・航海番号）を算出し、見積番号を発行する」機能である。

この設計には次の判断が必要だった。

1. **コンテキスト境界**: 見積は Routing（航海スケジュール）の情報に依存する。Estimation を Routing に直接依存させるか、依存を隔離するか。
2. **概算料金ロジックの配置**: 概算料金（基本料金＋重量単価＋日数係数）は Estimation ドメインの関心事だが、IT6 時点では確定した料金ポリシーが無くスタブである。ドメイン層に置くか、結線層に置くか。
3. **貨物種別の突き合わせ**: Estimation の `CargoType` と Routing の `CargoType` は別 BC の型であり、直接共有できない。

## 決定

### 1. Estimation Context を独立クレートとして新設し、Routing 参照を ACL に隔離する

`domain-estimation`（`Estimate` 集約・`RouteCandidate`・`EstimateRepository` ポート）と `app-estimation`（`CreateEstimateService`・`RouteCandidateProvider` ポート）を新設する。`domain-estimation` は他 domain クレートに依存せず、`shared-kernel` のみを共有する（BC 独立を Cargo.toml レベルで担保）。

Routing への参照は `app-estimation::RouteCandidateProvider` ポート（入力 DTO `RouteCandidateQuery`）として抽象化し、その実装 `RoutingRouteCandidateProvider` を composition 層（`interface-web/src/estimation_acl.rs`）に置く ACL とする。Estimation は「ルート候補を供給する何か」を知るのみで、それが Routing であることは知らない（[ADR-0003](0003-dependency-injection-composition-root.md) の composition root 方針を踏襲）。

### 2. 貨物種別の突き合わせは ACL 内の文字列契約とする

Estimation の `CargoType` を `"GENERAL"/"HAZARDOUS"/"REFRIGERATED"` へ写像し、Routing の `CargoType::as_str()` と文字列一致でフィルタする（`estimation_acl.rs::cargo_type_str`）。両 BC の列挙は独立に進化してよく、対応関係は ACL の責務として明示する。

### 3. 概算料金スタブは暫定的に ACL 層に置き、料金ポリシー確定時にドメインへ引き上げる

IT6 時点の概算料金は確定ポリシーが無いため、名前付き定数（`BASE_FARE_YEN`/`PER_KG_YEN`/`PER_DAY_YEN`）と純粋関数 `estimate_cost` として `estimation_acl.rs` に置き、単体テストで固定する。これは **意図的な暫定配置**であり、料金ポリシー（割引・通貨・サーチャージ等）が確定した時点で `domain-estimation` 側（`RouteCandidate` 生成の値計算）へ引き上げることを負債として記録する。

## 影響

- Estimation Context が BC 独立（`domain-estimation`/`app-estimation` は他 domain 非依存）で追加され、Release 1.1（例外対応・請求）の見積起点が確立する。
- Routing との結線が `estimation_acl.rs` に集約され、Routing 側の変更が Estimation ドメインに波及しない。
- 概算料金がテスト可能な純粋関数として固定され、リグレッションを検出できる。料金ロジックの配置は「ドメインへ引き上げる」トリガー（料金ポリシー確定）とともに既知の負債として整理される。
- rank 採番は ACL（所要日数昇順）と集約 `replace_candidates`（rank 昇順で再ソート）の双方に存在する二重責務が残る。IT7 以降で「集約に一元化する」か「ACL 確定を集約が信頼する」かに寄せる（[IT6 レビュー](../review/it6_development_review_20260723.md) architect 指摘）。
