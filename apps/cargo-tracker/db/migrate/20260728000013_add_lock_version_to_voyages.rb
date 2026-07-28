# frozen_string_literal: true

# 航海スケジュール更新（US25）の更新競合を検出するため楽観ロック列を追加する（T21）。
class AddLockVersionToVoyages < ActiveRecord::Migration[8.0]
  def change
    add_column :voyages, :lock_version, :integer, null: false, default: 0
  end
end
