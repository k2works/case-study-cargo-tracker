package cargotracker.shared.domain

/** 楽観ロック競合（IT1 レビュー H5 / IT2 タスク 0.11）。
  *
  *   - data-model.md 1175「楽観ロック規約: SET version = version + 1 ... WHERE id = ? AND version = ?」 で更新行数が 0 となった場合に投げる
  *   - IT2 時点ではマイグレーション V5 で `version` カラムを追加し、 リポジトリの UPDATE で `version = version + 1` するのみ。
  *     完全な競合検出（`Either[DomainError.ConcurrentModification, A]` 返却）の 有効化は IT3 で集約に `version` フィールドを追加するタイミングで実施する
  *
  * @param entityType
  *   競合した集約の種類（例: "Shipper", "Cargo"）
  * @param identifier
  *   業務キー（例: "SH-000001"）
  */
final case class OptimisticLockException(
    entityType: String,
    identifier: String
) extends RuntimeException(
      s"楽観ロック競合: $entityType $identifier は他のセッションによって更新されています"
    )
