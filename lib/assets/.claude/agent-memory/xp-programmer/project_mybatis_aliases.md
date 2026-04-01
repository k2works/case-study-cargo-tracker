---
name: mybatis_aliases_config
description: MyBatis type-aliases-package の設定が com.example.cargotracker 全体を対象にしているため、複数コンテキストに同名クラスがあると TypeException が発生する
type: project
---

MyBatis の `type-aliases-package` は `com.example.cargotracker` 全体をスキャンする設定だったが、各 bounded context に同名の `CargoType` や `BookingNotFoundException` が存在すると TypeAlias 衝突が発生し Spring Context の起動に失敗する。

**Why:** mapper XML が完全修飾クラス名を使用しているため型エイリアスは不要だが、スキャン範囲が広すぎてドメインモデルクラスも登録対象になっていた。

**How to apply:** `application.yml` の `type-aliases-package` を `com.example.cargotracker.booking.infrastructure.repositories, com.example.cargotracker.shipper.infrastructure.repositories` に絞ることで解決済み。新たなコンテキストで同名クラス（特に `CargoType`, `XXNotFoundException` 等）を追加する場合は MyBatis エイリアス衝突に注意する。
