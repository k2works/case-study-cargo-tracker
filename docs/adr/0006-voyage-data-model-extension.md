# 0006 航海データモデル追補（船名・運送会社・対応貨物種別）

US07（航海スケジュール検索）および US08（経路候補算出）を実現するため、`Voyage` 集約とそれを支える DB スキーマの追補を IT3 で行う。

日付: 2026-06-21

## ステータス

2026-06-21 提案されました（IT3 タスク 1.1）

## コンテキスト

IT2 で導入した `Voyage` 集約は航海番号と `CarrierMovement` の列のみを持ち、US07 の検索条件（船名・運送会社）および US08 の貨物種別フィルタを満たさない。

- **US07 受入基準**: 出発港 / 到着港 / 期間 / 貨物種別の絞り込みで航海スケジュールを検索できる。検索結果は船名・運送会社・出港時刻順で表示する。
- **US08 受入基準**: 貨物種別（一般 / 危険物 / 冷凍）に応じて対応航海のみを経路候補に組み入れる。

また、Routing コンテキスト固有の経路候補値オブジェクト（`RouteCandidate` / `RoutingLeg`）が Estimation コンテキストの既存 `RouteCandidate`（見積算定用、ADR 0003 PricingService との結合）と名称衝突するため、コンテキスト境界を尊重した分離を行う必要がある。

## 決定

### (a) `voyage` テーブルへのカラム追加

```sql
ALTER TABLE voyage
  ADD COLUMN vessel_name  VARCHAR(100) NOT NULL DEFAULT '',
  ADD COLUMN carrier_code VARCHAR(20)  NOT NULL DEFAULT '';
CREATE INDEX idx_voyage_carrier_code ON voyage (carrier_code);
```

- 既存レコードは空文字で埋め、IT3 タスク 1.3 で seed データを補完する。
- `carrier_code` には ALPHA-3 風コード（例: `MOL`・`NYK`・`MAERSK`）を入れる。コード正規化のための master テーブルは Phase 3 で検討する。

### (b) 中間テーブル `voyage_supported_cargo_type` の新設

```sql
CREATE TABLE voyage_supported_cargo_type (
  id          BIGSERIAL PRIMARY KEY,
  voyage_id   BIGINT      NOT NULL REFERENCES voyage(id) ON DELETE CASCADE,
  cargo_type  VARCHAR(20) NOT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_voyage_supported_cargo_type UNIQUE (voyage_id, cargo_type)
);
CREATE INDEX idx_voyage_supported_cargo_type_voyage ON voyage_supported_cargo_type (voyage_id);
```

- 命名規約: data-model.md の「テーブル単数形 + サロゲートキー BIGSERIAL + 監査カラム」に従う。
- `cargo_type` は `General` / `Refrigerated` / `Hazardous` の文字列を許容。enum 化はドメイン側で行う。
- 1 航海が複数の貨物種別をサポートする多対多関係を表現する。

### (c) Routing コンテキストの値オブジェクト分離

Routing コンテキストに以下を新設する。

- `cargotracker.routing.domain.model.valueobjects.RoutingLeg`: 1 区間（出発港 / 到着港 / 出港時刻 / 到着時刻 / 航海番号）
- `cargotracker.routing.domain.model.valueobjects.RouteCandidate`: 区間の列 + 合計所要日数 + 概算料金

既存の `cargotracker.estimation.domain.model.valueobjects.RouteCandidate`（PricingService 用）はそのまま残し、ACL 経由でのみ Routing 側から参照する。

## 帰結

**正の帰結**:

- US07 検索条件すべてをドメイン層で表現可能になる
- 貨物種別フィルタが SQL レベルで効くため、US08 経路候補算出のスループットが向上する
- コンテキスト境界が崩れずに各コンテキスト固有の `RouteCandidate` を持てる

**負の帰結**:

- `voyage` テーブルが NULL 不許容で拡張されるため、既存レコードのバックフィルが必須（IT3 タスク 1.3）
- 中間テーブル経由の JOIN が増えるため、検索クエリで `EXISTS (SELECT 1 FROM voyage_supported_cargo_type ... )` を使う等の最適化指針を IT3 で確立する必要がある
- 同名値オブジェクトが 2 コンテキストに存在することによる開発者の混乱リスク。完全修飾名で扱う運用ルールを ArchUnit ルール 4（命名規約）で補強する

## 関連ドキュメント

- `docs/design/data-model.md`（IT3 タスク 1.2 で voyage 拡張カラム + 中間テーブルを追記）
- `docs/design/domain-model.md`（IT3 タスク 1.3 で Voyage 集約拡張 + Routing 値オブジェクトを追記）
- `docs/design/ui_design.md`（IT3 で航海検索画面 / 経路候補画面を追加）
- ADR 0003: PricingService の共有判断
- ADR 0005: 経路探索アルゴリズム
