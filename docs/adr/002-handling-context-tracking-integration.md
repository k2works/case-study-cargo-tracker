# ADR-002: Handling Context を Tracking Context に統合する

荷役コンテキストを独立した境界付けられたコンテキストとせず、Tracking Context 内のモジュールとして実装する。

日付: 2026-08-06

## ステータス

**置き換え済み（[ADR-010](010-handling-as-independent-context.md) により 2026-08-08 に置き換え）**

> **本 ADR の判断は現在有効ではない。** Handling は独立した境界付けられたコンテキストである。
>
> 置き換えの理由は 2 つある。**(1)** 本 ADR は「独立 BC = 結果整合が必要」を前提としていたが、
> ADR-009 により BC 間 ACL は同期・同一トランザクションで呼ぶと決めたため、その前提が消えた。
> **(2)** 実装してみると言語は分岐していた（`HandlingType` と `TrackingEventType`、
> `HandlingVoyageNumber` と `TrackingVoyageNumber`、`CargoBookingId` と `TrackingBookingId`）。
> 同じ BC の中に対応する型を 3 組も定義しており、**統合されていたのではなく境界が
> 引かれていなかった**。
>
> **本 ADR を残すのは、判断の経緯を追えるようにするためである。** 実装しないと
> 分からないことがあった、という記録そのものに価値がある。

## コンテキスト

`domain-model.md` のコンテキスト一覧には「Handling Context: 荷役作業登録・通関申告管理（**実装では Tracking Context に統合。詳細は ADR 参照**）」という注記があるが、参照先の ADR が存在しなかった。その結果、設計ドキュメント群が 4 通りの解釈に分岐している。

| ドキュメント | Handling の扱い |
| :--- | :--- |
| `domain-model.md` | Tracking に統合済みと注記 |
| `data-model.md` | `handling_activity` / `customs_declaration` を独立テーブルとして定義 |
| `architecture_backend.md` | 独立コンテキストとしてコンテキストマップに描画 |
| `ui_design.md` | `/handling` 配下に 4 画面を定義 |

この分岐は実装の形を直接左右する。統合されているなら `HandlingActivityRegisteredEvent` は**集約内ないしコンテキスト内のメソッド呼び出し**であり、独立しているなら**コンテキスト間ドメインイベント**として ACL を挟む必要がある。ArchUnit の `slices().matching("com.example.cargotracker.(*)..")` もパッケージ構成が BC 境界であることを前提としているため、どちらを採るかでルールの意味が変わる。

**コンテキスト境界の統合はコメント 1 行で処理してよい判断ではない。**

## 決定

**Handling を独立した境界付けられたコンテキストとせず、Tracking Context 内のモジュール（`tracking/handling/`）として実装する。**

判断根拠は次のとおり。

- 荷役イベントの登録と輸送状態（`TransportStatus`）の遷移は**同一の不変条件**を共有する。荷役イベントが登録されたら対応する輸送状態が必ず更新される、という関係は「一緒に守るべき不変条件」であり、集約境界の引き方と同じ論理でコンテキスト境界を引くなら分けない理由がある
- 独立 BC とすると、荷役イベントが起きるたびに BC 間の結果整合（イベント発行 → 購読 → Tracking 側の更新）が必要になる。荷役は本システムで最も頻度の高い操作であり、最も高頻度の操作に最も重い連携機構を課すことになる
- ユビキタス言語の観点でも、荷役作業員と追跡管理者は同じ「貨物が今どこで何をされているか」を扱っており、言語が分岐していない。言語が分岐しない領域を BC で分けると、翻訳の必要がないところに翻訳層を置くことになる

### 変更箇所

- `architecture_backend.md`: コンテキストマップから Handling を独立ボックスとして削除し、Tracking 内のモジュールとして描画する
- `architecture_backend.md`: パッケージ構成の `handling/` を `tracking/handling/` に変更する
- `domain-model.md`: 注記を本 ADR への参照に置き換える。コンテキスト一覧の Handling 行は「Tracking Context のモジュール」と明記する
- `data-model.md`: `handling_activity` / `customs_declaration` の所属コンテキストを Tracking と記載する（**テーブルは分割したまま維持する**。テーブル分割と BC 分割は別の判断であり、テーブルを統合する必要はない）
- `ui_design.md`: URL パス `/handling/*` は**変更しない**。URL は利用者から見た業務の区切りであり、内部のコンテキスト構成に追随させる必要はない
- `test_strategy.md`: ArchUnit の slices 定義が対象とする BC 一覧から Handling を外す

### 代替案

| 代替案 | 却下理由 |
| :--- | :--- |
| Handling を独立 BC として維持する | 最高頻度の操作に BC 間結果整合のコストを課す。かつ荷役と追跡でユビキタス言語が分岐していないため、翻訳層が不要なところに翻訳層を置くことになる |
| Handling を独立 BC にし、Tracking を Handling の下流として同期呼び出しでつなぐ | BC 間を同期呼び出しでつなぐのは BC 分割の利点（独立した変更可能性）を捨てて欠点（境界越えの複雑さ）だけを残す形になる |

## 影響

### ポジティブ

- 荷役イベント登録が単一トランザクションで完結し、`HandlingActivityRegisteredEvent` に AFTER_COMMIT の結果整合を挟む必要がなくなる
- 設計ドキュメント 4 本の分岐が解消し、ArchUnit の slices ルールが意味を持つようになる
- 「荷役を登録したのに追跡に反映されていない」という中間状態が構造的に発生しなくなる

### ネガティブ

- Tracking Context が肥大化する。追跡・例外イベント・荷役・通関申告の 4 領域を抱えることになり、将来 Tracking の分割が必要になる可能性がある
- 通関申告（`CustomsDeclaration`）は荷役よりも法制度側の関心事であり、追跡との結びつきが荷役ほど強くない。ここは将来的に切り出す候補として残る

## コンプライアンス

- ArchUnit の slices ルールが検出する BC 一覧に `handling` が独立して現れないこと
- `tracking/handling/` 配下のクラスが `tracking/` の他モジュールを直接参照してよい（ACL を挟まない）ことをレビューで確認する
- `data-model.md` の BC 別テーブル一覧で `handling_activity` / `customs_declaration` が Tracking に属していること

## 備考

- 著者: 設計レビュー（2026-08-06 マルチパースペクティブレビュー H2 / H5）
- 関連 ADR: ADR-005（共有カーネルの範囲）
- 出典: `docs/review/設計ドキュメント_review_20260806.md` H2 / H5 / 懸念事項 2
