# ADR-0010: 見積は合成ルート経由で Routing の経路探索を再利用する

Estimation Context は経路探索を自前で持たず、**ACL ポートを宣言し、
その実装（Routing の `RouteFinder` を呼ぶ翻訳）を合成ルートに置く**。

日付: 2026-08-04

## ステータス

2026-08-04 承認されました（IT8・US01 の実装と同時）

## コンテキスト

US01（輸送見積）は「航海スケジュール情報をもとにルート概算候補が表示される」
（受入基準 2）を要求する。IT7（US08）で実装した
[`RoutingRouteFinder.findCandidates`](../../apps/cargo-tracker/src/routing/domain/model/RouteFinder.flix)
が、まさにこの計算を行う——Datalog による到達可能性の判定、
時刻の接続、積替回数の上限、出発日による絞り込みを含む 200 行余りの純粋関数である。

[ドメインモデル設計](../design/domain-model.md) の Estimation Context は
ビジネスルール 6 で「ルート候補はスタブ実装（固定値）で生成される」と書いている。
これは**設計時点の割り切り**であり、`RouteFinder` が存在しなかった時期の記述である。

### 制約: `arch-lint` 規約 4

[アーキテクチャ規約](../design/arch_lint_rules.md) の規約 4 は
**Bounded Context 間の直接参照を禁じる**。`estimation/` 配下のいずれのファイルも
`RoutingRouteFinder` を参照できない。これは
[アーキテクチャ規約](../design/arch_lint_rules.md)の規約 4 が定めるものである。

規約 4 の検査はディレクトリで行われ、**`src/composition/` は対象外**である
（`CONTEXTS` にディレクトリ名が無く、`contextOf` が `null` を返す）。
合成ルートはすべての BC を知っている場所だからである。

## 検討した選択肢

### 1. Estimation に探索を再実装する

規約は守れる。しかし **200 行の探索アルゴリズムが 2 つになる**。

片方だけを直したとき、**見積で出した候補と経路割り当てで出る候補が食い違う**。
営業が「3 日で着きます」と答えた後、経路設計者の画面には該当する便が無い——
この食い違いは、どちらの画面も単体では正しく動くため気付けない。

積替回数の上限（2 回）・時刻の接続・出発日の絞り込みは、いずれも
**業務ルールとして 1 つ**である。1 つのルールを 2 箇所に書かない。

### 2. `RouteFinder` を Shared Domain へ移す

規約は守れる（`shared` への参照は許される）。

しかし `RouteFinder` は **Routing Context の中核**である。共有カーネルは
「どのコンテキストでも同じ意味を持つもの」（`Location`・`Money`・`CalendarText`）
に限る。経路探索を共有へ出すと、次に Routing 固有のルール
（船腹・寄港地の制約）を足すときに置き場所が無くなる。

**共有カーネルは小さく保つ**。大きくなった共有カーネルは、
コンテキストを分けた意味を失わせる。

### 3. ACL ポートを宣言し、実装を合成ルートに置く（採用）

Estimation は `EstimationRouteSearch` モジュールに `RouteSearch` 効果を宣言する。**Estimation が必要とする形**
（出発地・仕向地・期限・種別 → 航海番号・経由港・所要日数）でだけ定義し、
`RouteSpec` や `RouteCandidate` といった Routing の型は現れない。

実装（Routing の型への翻訳）は `src/composition/EstimationWiring.flix` に置く。
これは「必要とする側が必要な形に翻訳して引く」という本プロジェクトの ACL の
一般形（[ADR-0009](ADR-0009-routing-pulls-booking-via-acl.md)）に沿う。
**翻訳の置き場所だけが違う**——Booking → Shipper の ACL は SQL で翻訳できたが、
経路探索は SQL で書けないためである。

## 決定

**選択肢 3 を採る。**

- `EstimationRouteSearch` モジュールの `RouteSearch` 効果を `estimation/domain/port/` に置く
- 引数と戻り値は **Estimation の語彙だけ**で構成する
- 実装は `src/composition/EstimationWiring.flix` に置き、`RoutingRouteFinder` と
  `VoyageRepo` を呼ぶ
- 運賃の計算は **Estimation 側で行う**（`FreightRate`）。Routing は金額を知らない

## 帰結

### よい点

- 探索アルゴリズムが 1 つに保たれる。見積と経路割り当てで候補が食い違わない
- Estimation のドメイン・アプリケーション層は Routing の型を知らない。
  外部の経路最適化サービスへ差し替える場合、ポートの実装だけを替えればよい
  （[ADR-0007](ADR-0007-defer-external-acl-and-scope-v1.md) が先送りした形である）
- 単体テストでは `RouteSearch` にスタブを与えればよく、
  **見積のテストが航海の登録に引きずられない**

### 悪い点・引き受けるリスク

- **`arch-lint` はこの結合を検出できない**。合成ルートは規約 4 の対象外であり、
  そこに BC 間の翻訳が集まる。これは
  [`arch_lint_rules.md`](../design/arch_lint_rules.md) に記録済みの穴
  （SQL 越しの結合）と同じ性質である——**規約が見えないところに逃がした**のではなく、
  **見えないことを承知で置いた**。
- 合成ルートが「配線だけを述べる」場所ではなくなる。IT8 の TS14-1 で
  BC ごと 1 行に畳んだばかりであり、そこに翻訳のコードが入る。
  **翻訳は `EstimationWiring` の中に閉じ**、`Composition.flix` 本体には出さない。
- **合成ルートに置いた翻訳はテストの死角になる**。IT8 のクローズ時レビューで、
  `toFoundRoute` のコメントと実装が食い違っている（所要日数が読めない候補を
  「落とす」と書きながら 0 で埋めていた）ことが見つかった。`RouteSearch` に
  触れるテストが呼び出し回数のスタブしか無かったためである。
  **合成ルートに置く翻訳には、翻訳そのものを固定するテストを必ず添える**
  （`WiringTest.testCargoVocabulariesAgreeAcrossContexts` がその 1 例）。
- 翻訳が増えたら（Handling・Billing でも同型が起きたら）、
  **合成ルートの下に `acl/` ディレクトリを設けて規約 4 の対象にする**ことを検討する。
  1 件のうちは、置き場所を増やすほうが分かりにくい。

### 設計ドキュメントへの反映

[ドメインモデル設計](../design/domain-model.md) の Estimation Context
ビジネスルール 6「ルート候補はスタブ実装（固定値）で生成される」を、
**本 ADR の内容へ書き換える**。設計と実装が食い違ったまま残らないようにする。

## 関連

- [アーキテクチャ規約 規約 4: Bounded Context 間の直接参照](../design/arch_lint_rules.md)
- [ADR-0007: 外部 ACL の先送りと v1 スコープ](ADR-0007-defer-external-acl-and-scope-v1.md)
- [ADR-0009: 経路設計は Routing が Booking から引く](ADR-0009-routing-pulls-booking-via-acl.md)
