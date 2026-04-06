# ファクトリメソッドリファクタリング コードレビュー

## レビュー対象

| ファイル | 変更内容 |
|---------|---------|
| `Shipper.java` | ファクトリメソッド追加（`individual()`, `corporateBase()`, `asCorporate()`） |
| `RouteSpecification.java` | ファクトリメソッド追加（`fromUnLocodes()`） |
| `MyBatisShipperRepository.java` | ファクトリメソッドへの切り替え（三項演算子フォールバックあり） |
| `RegisterShipperCommandService.java` | ファクトリメソッドへの切り替え（`CorporateShipper` 直接参照除去） |
| `CargoBookingCommandService.java` | `fromUnLocodes()` への切り替え |
| `MyBatisCargoRepository.java` | `fromUnLocodes()` への切り替え |

- レビュー日: 2026-04-06
- コミット範囲: `3029764`〜`26d13a6`（ファクトリメソッドリファクタリング 2 件）

---

## 総合評価

ファクトリメソッド導入によりオブジェクト生成の意図が明確化され、コマンドサービス・リポジトリから `ShipperType` の明示的な指定や `CorporateShipper` の直接参照が排除された点は高く評価できます。一方、`MyBatisShipperRepository.toShipper()` に残る三項演算子のフォールバックコードは到達不能かつパターン違反であり、対応が必要です。また `Shipper` が自身のサブタイプ `CorporateShipper` を知るという設計上の緊張についても検討が必要です。

---

## 改善提案（重要度順）

### 高（マージ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | `toShipper()` の三項演算子フォールバックを削除し `throw` に変更 | `MyBatisShipperRepository.java:85-97` | xp-programmer / xp-architect | `ShipperType.valueOf()` が既に不正値で例外を投げるため到達不能。将来の enum 拡張時にパターン違反コードが実行されてしまう |

**修正案:**

```java
if (shipperType == ShipperType.INDIVIDUAL) {
    return Shipper.individual(
            new ShipperId(UUID.fromString(shipperRecord.getId())),
            new ShipperCode(shipperRecord.getShipperCode()),
            new ShipperName(shipperRecord.getName()),
            new Email(shipperRecord.getEmail()),
            toPhone(shipperRecord.getPhone()),
            address
    );
}
throw new IllegalStateException("Unsupported shipperType: " + shipperType);
```

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 2 | `Shipper.asCorporate()` を `CorporateShipper.from(Shipper, ...)` に移動検討 | `Shipper.java:77-79` | xp-architect | `Shipper`（基底）が `CorporateShipper`（具象サブタイプ）を知る構造は DIP に反する。IT3 のバックログ登録を推奨 |
| 3 | `fromUnLocodes()` に null 入力テストを追加 | `RouteSpecification.java:10-20` | xp-tester | null 引数時の振る舞い（`NullPointerException` vs `IllegalArgumentException`）が明確でない |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 4 | `fromUnLocodes`（複数形）と引数 `originUnlocode`（単数形）の表記揺れ統一 | `RouteSpecification.java:10` | xp-technical-writer | 一貫性のために `fromUnLocode` またはどちらか統一 |
| 5 | `corporateBase()` の命名見直し | `Shipper.java:66` | xp-user-representative | 業務語彙に存在しない中間状態を表す名前。`withoutContract()` 等の検討を推奨 |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | xp-programmer: `asCorporate()` は呼び出し側がシンプルになるため `Shipper` に置くべき | xp-architect: `Shipper` が `CorporateShipper` を知るべきでない（DIP 違反） | `asCorporate()` の所在 | 現状の使いやすさを優先しつつ IT3 バックログで `CorporateShipper.from(Shipper, ...)` 移行を検討 |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 1 / 中: 1 / 低: 1）</summary>

### 評価サマリー
ファクトリメソッドの導入によって呼び出し側コードの意図が格段に明確になった。ただし `MyBatisShipperRepository` の三項演算子フォールバックは将来の型追加時にパターンを破るコードを黙って実行する落とし穴になっている。

### 良い点
- `individual()` / `corporateBase()` の明示的な命名で `ShipperType` の引数指定ミスが防止される
- `RegisterShipperCommandService` から `CorporateShipper` の直接参照が除去され、アプリケーション層の純粋度が向上した
- `fromUnLocodes()` により `Location` オブジェクトの生成知識がドメイン層内に封じ込められた

### 改善提案
- 【重要度: 高】`MyBatisShipperRepository.java:88-97` — フォールバックの `new Shipper(...)` を `throw new IllegalStateException(...)` に変更

### 懸念事項
- `ShipperType` が将来拡張された際（例: `FREIGHT_FORWARDER`）、フォールバックが `ShipperType` を持つ素の `Shipper` を返す。早期失敗が正しい設計。
</details>

<details>
<summary>xp-tester（高: 0 / 中: 1 / 低: 1）</summary>

### 評価サマリー
23 件のテスト追加でブランチカバレッジを 81% まで改善した努力は高く評価できる。ただし新規追加ファクトリメソッド自体の null 引数テストが不十分。

### 良い点
- `ShipperTest` に null チェック境界値テストが追加されている
- `MyBatisShipperRepository` の Address 付き・Phone null のテストが追加されている
- 166 件全パス・命令 93%・ブランチ 81% は定量目標（80%）を達成している

### 改善提案
- 【重要度: 中】`RouteSpecification.java:10-20` — `fromUnLocodes(null, "JPOSA", 明日)` のような null 入力テストが存在するか確認が必要
- 【重要度: 低】`Shipper.asCorporate(null, null)` のテスト追加検討
</details>

<details>
<summary>xp-architect（高: 1 / 中: 1 / 低: 0）</summary>

### 評価サマリー
`Location` 生成の封じ込めと `CorporateShipper` 依存の排除はヘキサゴナルアーキテクチャとドメインモデルの保護という観点で正しい方向性。ただし `Shipper→CorporateShipper` の依存方向に設計上の緊張が生まれている。

### 良い点
- アプリケーション層（CommandService）がインフラ型（`Location` 生成）に依存しなくなった
- インフラ層リポジトリでのファクトリメソッド利用により、ドメインオブジェクト復元の意図が明確化された

### 改善提案
- 【重要度: 高】`MyBatisShipperRepository.java:85-97` — 三項演算子フォールバックを削除。ドメイン列挙型の制御フローを壊す可能性がある
- 【重要度: 中】`Shipper.java:77-79` — `Shipper` が `CorporateShipper` を参照することで基底→具象の依存が発生
</details>

<details>
<summary>xp-technical-writer（高: 0 / 中: 0 / 低: 2）</summary>

### 評価サマリー
ファクトリメソッドの命名は概ね意図を伝えているが、表記揺れと `corporateBase` という業務語彙に存在しない概念の使用が気になる。

### 良い点
- `individual()` / `asCorporate()` はコードを読むだけで意図が伝わる
- `fromUnLocodes()` は変換意図が名前から分かる

### 改善提案
- 【重要度: 低】`RouteSpecification.java:10` — メソッド名 `fromUnLocodes`（複数）vs 引数 `originUnlocode`（単数）の不一致
- 【重要度: 低】`Shipper.java:66` — `corporateBase` は業務語彙でない。コメント補足を推奨
</details>

<details>
<summary>xp-user-representative（高: 0 / 中: 0 / 低: 1）</summary>

### 評価サマリー
利用者観点ではファクトリメソッドはコード品質向上であり、エンドユーザーへの直接的な影響はない。ただし未知の荷主タイプがサイレントに処理された場合の影響が懸念される。

### 良い点
- `individual()` vs `corporateBase()` の明示的な二分化は、「個人荷主」と「法人荷主」という業務上の区分をコードに反映している

### 懸念事項
- 未知の `shipperType` が DB に存在した場合、現行フォールバックでは素の `Shipper` が返されてシステムが継続動作してしまう。業務的には「不正な荷主データが存在する」という重大事態であり、エラーで止めるべき
</details>
