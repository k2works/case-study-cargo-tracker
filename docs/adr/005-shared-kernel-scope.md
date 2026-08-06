# ADR-005: 共有カーネルの範囲を Location と ShipperId に限定する

共有カーネルを `Location` と `ShipperId` の 2 つに限定し、`TransportStatus` と `RoutingStatus` は所有コンテキストに戻す。

日付: 2026-08-06

## ステータス

承認済み（2026-08-06 承認）

## コンテキスト

共有カーネルの範囲が 2 つの設計ドキュメントで食い違っていた。

| ドキュメント | 共有カーネルの範囲 |
| :--- | :--- |
| `architecture_backend.md` | `Location`（UN/LOCODE）**のみ** |
| `domain-model.md` | `Location` + `ShipperId` + `TransportStatus` + `RoutingStatus` |

共有カーネルはシステムで最も変更コストが高い部分である。範囲が曖昧なままだと「どこにも属さないもの置き場」に劣化し、時間とともに肥大化する。肥大化した共有カーネルは、あらゆる変更が全コンテキストのレビューを必要とする状態を生み、境界付けられたコンテキストで分割した意味を失わせる。

## 決定

**共有カーネルを `Location` と `ShipperId` の 2 つに限定する。**

| 要素 | 判断 | 理由 |
| :--- | :--- | :--- |
| `Location` | **共有カーネルに残す** | UN/LOCODE は国際標準であり、コンテキストごとに解釈が分岐しない。港の識別という意味はどのコンテキストでも同一 |
| `ShipperId` | **共有カーネルに残す** | 識別子は値としての同一性のみを持ち、業務的な振る舞いを持たない。BC 間で識別子を受け渡すには共有が最も簡潔 |
| `TransportStatus` | **Tracking Context 所有に戻す** | 下記参照 |
| `RoutingStatus` | **Routing Context 所有に戻す** | 下記参照 |

`TransportStatus` を共有カーネルから外す理由が本 ADR の中心である。

- `TransportStatus` は **Tracking の集約状態そのもの**であり、Tracking の業務ルールの表現である。集約の内部状態を共有カーネルに置くことは、集約のカプセル化を BC 境界を越えて破ることに等しい
- 共有カーネルに置くと、**Tracking に新しい輸送状態を 1 つ追加するだけで Booking・Handling・Billing の再ビルドとレビューが強制される**。最も変更されうる部分に最も高い変更コストを課す配置になっている
- 他コンテキストが必要としているのは「Tracking の状態そのもの」ではなく「自分の関心事に翻訳された状態」である。たとえば Billing が知りたいのは `DELIVERED` かどうかの一点であり、9 値すべてではない

`RoutingStatus`（`NOT_ROUTED` / `ROUTED` / `MISROUTED`）も同様に Routing の業務判断の結果であり、Routing 所有とする。

### 変更箇所

- `domain-model.md`: コンテキスト一覧の「Shared Domain: 共有カーネル（Location・ShipperId・TransportStatus）」を `Location`・`ShipperId` の 2 要素に修正する
- `domain-model.md`: `TransportStatus` を Tracking Context の節に、`RoutingStatus` を Routing Context の節に移す
- `domain-model.md`: 他コンテキストが輸送状態を参照する箇所に ACL ポートを定義する。他コンテキストは自前の型に変換して受け取る
- `architecture_backend.md`: 「`Location` のみ共有カーネル」を「`Location` と `ShipperId`」に修正する（現行記述も不正確なため）
- `data-model.md`: テーブル上の `transport_status` カラムは**そのまま維持する**。永続化された文字列値の共有と、Java の型の共有は別の問題であり、DB 側で分ける必要はない

### 代替案

| 代替案 | 却下理由 |
| :--- | :--- |
| `domain-model.md` の 4 要素を正とする | 集約状態を共有カーネルに置くことで、Tracking の状態追加が全 BC の再ビルドを強制する。最も変わる部分に最も高い変更コストを課す |
| `architecture_backend.md` の `Location` のみを正とする | `ShipperId` を共有しない場合、Booking が Shipper を参照するたびに識別子型の変換が必要になる。識別子は振る舞いを持たないため、共有のコストが極めて低く、除外する利点がない |
| 共有カーネルを廃止し全て ACL で変換する | `Location`（UN/LOCODE）のようにコンテキスト間で解釈が完全に一致するものまで変換するのは、翻訳の必要がないところに翻訳層を置くことになる |

## 影響

### ポジティブ

- Tracking の輸送状態を追加・変更しても、他コンテキストのビルドとレビューに波及しなくなる
- 共有カーネルが「どこにも属さないもの置き場」に劣化する経路を、明示的な 2 要素の列挙で塞げる
- 各コンテキストが「自分にとっての状態」を自分の言葉で表現できるようになる（ユビキタス言語の BC 内での純度が上がる）

### ネガティブ

- `TransportStatus` を参照している既存の設計記述を ACL ポート経由に書き換える必要がある
- コンテキストごとに状態の表現が増えるため、「Tracking の `IN_PORT` は Billing のどれに対応するのか」という対応表を維持する必要がある。対応表は ACL ポートの実装として一箇所に集約する
- 短期的にはコード量が増える（変換処理の分）

## コンプライアンス

- ArchUnit ルールにより、共有カーネルのパッケージに `Location` と `ShipperId` 以外のクラスが追加されていないことを検証する（**共有カーネルは放置すると必ず肥大化するため、人間のレビューではなくテストで固定する**）
- `TransportStatus` が `tracking` パッケージ配下に存在し、他 BC のパッケージから直接参照されていないこと
- 他 BC が輸送状態を必要とする箇所で ACL ポートを経由していること

## 備考

- 著者: 設計レビュー（2026-08-06 マルチパースペクティブレビュー H10）
- 関連 ADR: ADR-002（Handling Context の統合）
- 出典: `docs/review/設計ドキュメント_review_20260806.md` H10 / C3
