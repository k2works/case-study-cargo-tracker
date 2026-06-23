# 0013 tracking_number 採番を PostgreSQL シーケンスに変更

IT5 セルフレビュー観察事項 O2 を解消する。`MAX(id) + 1` 方式の競合危険性を排除し、PostgreSQL のシーケンスを使った安全な採番に移行する（ADR 0010 の更新版）。

日付: 2026-06-23

## ステータス

2026-06-23 受理（IT6 タスク 0.8）。ADR 0010「追跡番号の採番ポリシー」の採番実装詳細を本 ADR で更新する。

## コンテキスト

`ScalikeJdbcTrackingActivityRepository.nextTrackingNumber()` は当初 `SELECT COALESCE(MAX(id), 0) + 1` で次番号を算出していた。これは以下の問題を抱えていた。

- **並行採番レース**: 同一トランザクション境界外で 2 回連続して `nextTrackingNumber` を呼ぶと、両者が同じ値を返し、後続 INSERT で `uk_tracking_activity_tracking_number` UNIQUE 違反になる
- **トランザクション分離レベルへの依存**: READ COMMITTED では他セッションの未コミット挿入が見えないため、`MAX` が実時点と乖離する
- **削除後のリサイクル**: 行を削除すると同じ番号が再利用され、過去番号と衝突する可能性がある

## 決定

`tracking_activity.id` 列の `BIGSERIAL` が暗黙生成する `tracking_activity_id_seq` を `nextval(...)` で直接消費する。

```sql
SELECT nextval('tracking_activity_id_seq') AS next
```

- 取得した値を `TrackingNumber.fromSequence(n)` に渡して `TN-NNNNNN` 形式に整形する
- INSERT 時の `id` 列は引き続き暗黙の `DEFAULT nextval(...)` で自動採番される
  - この副作用として「採番した tracking_number の seq 値」と「INSERT 後の id 値」が 1 ずれる
  - これは設計上許容する（unique 性が保証されれば、id と tracking_number の数値が一致する必要は業務上ない）

## 検討した代替案

1. **専用シーケンス `tracking_number_seq` を追加**: 暗黙の `tracking_activity_id_seq` ではなく、独立した `CREATE SEQUENCE tracking_number_seq` を Flyway で定義する
   - 却下理由: BIGSERIAL が既に作っているシーケンスを使えば追加のマイグレーションが不要。命名上の独立性は ORM レイヤーで吸収可能
2. **トランザクション内 `SELECT ... FOR UPDATE`**: `tracking_activity` 全体をロックして MAX+1 を実行する
   - 却下理由: ロック範囲が広く、TPS が劣化する
3. **UUID 採用**: 採番不要にする
   - 却下理由: ADR 0010 で人間可読な `TN-NNNNNN` 形式を決定済み

## 帰結

### 正の帰結

- 並行採番のレース条件が原理的に排除される
- マイグレーション不要（既存スキーマで対応）
- `nextval` は PostgreSQL の標準機能で軽量

### 負の帰結

- `id` と「tracking_number の連番部分」が 1 ずれる（業務上は影響なし、調査時に注意）
- シーケンスは TRUNCATE/RESTART でリセットしない限り欠番が発生する（ロールバックされても消費される）

### 関連

- ADR 0010「追跡番号の採番ポリシー」: 採番形式 `TN-NNNNNN` を維持
- IT5 セルフレビュー O2: 本 ADR で解消
- IT6 イテレーション計画 タスク 0.8
