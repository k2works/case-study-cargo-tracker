# ADR-007: 共有カーネルの拡張方針とルート候補算出のスタブ ACL

`Location` と `CargoType` を共有カーネル（`shared/domain/model`）に配置する。ルート候補算出は Routing Context 実装まで意図的にスタブ ACL（`StubRouteCandidateCalculator`）で代替する。

日付: 2026-07-28

## ステータス

承認済み（ADR-005 のイベント方針を補完。domain-model.md の CargoType 記述を本 ADR に合わせて更新する）

## コンテキスト

IT2 で Estimation / Booking Context を実装するにあたり、以下の設計判断が必要になった。

1. **共通概念の配置**: `Location`（UN/LOCODE）と `CargoType`（GENERAL/HAZARDOUS/REFRIGERATED）は Estimation と Booking の両方で同一の値・意味で使われる。domain-model.md は当初、CargoType を各 BC が個別定義（値は共通）、Location を共有カーネルと記述していた。実装で二重定義を避けるため配置方針を確定する必要がある。
2. **ルート候補算出**: US01 の見積はルート候補（経由港・所要日数・概算料金）を必要とするが、それを供給する Routing Context は IT3 まで未実装。

## 決定

### 共有カーネルの拡張

- `Location` を共有カーネルとする（UN/LOCODE は国際標準の普遍的識別子であり、全 BC で不変の意味を持つ）。
- `CargoType` を共有カーネルとする（現時点で Booking / Estimation が同一の分類軸・値を使い、分岐する兆候がない）。
- **拡張の歯止め**: 共有カーネルへの追加は「全 BC で意味が一致し変更頻度が低いもの」に限る。ある BC が独自の分類軸・振る舞いを必要とし始めた時点で、その型は共有カーネルから当該 BC 固有型へ分離し直す（本 ADR を更新して判断を記録する）。
- `VoyageNumber` は各コンテキスト固有型として共有しない（domain-model の既定方針を踏襲）。

### ルート候補算出のスタブ ACL（意図的負債）

- Estimation の `RouteCandidateCalculator` ポートに対し、`StubRouteCandidateCalculator`（重量 × 貨物種別係数の固定計算・直行/経由の 2 候補）を暫定実装として採用する。
- これは **意図的な技術的負債**であり、料金ロジックの正典は将来 Routing Context / 外部ルーティングサービスに移す。
- **返済トリガー**: Routing Context 着手時（IT3）に外部経路 ACL（`ExternalRoutingServicePort`）へ差し替える。余力次第の繰越にはせず、IT3 の計画スコープに明示する。

## 影響

- `docs/design/domain-model.md` の CargoType 記述（各 BC 定義）を「共有カーネル」に更新し、実装と正典を一致させる。
- 料金計算ロジックが `StubRouteCandidateCalculator` にハードコードされている点は、返済トリガーまで既知の負債として `retrospective` に残す。

### 代替案

- **CargoType を各 BC で重複定義**: BC 独立性は最大化するが、現時点で値・意味が完全一致しており DRY の利が勝る。将来分岐時に分離する方針で却下。
- **ルート候補を IT2 で本実装**: Routing Context を先行実装する必要があり IT2 スコープを超える。段階的縦切り（ウォーキングスケルトン）の方針に反するため却下。
