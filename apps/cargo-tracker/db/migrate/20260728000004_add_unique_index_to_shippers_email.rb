# frozen_string_literal: true

# 荷主メールアドレスの一意性を DB レベルで担保する（US02 重複防止・TOCTOU 対策）。
class AddUniqueIndexToShippersEmail < ActiveRecord::Migration[8.0]
  def change
    add_index :shippers, :email, unique: true
  end
end
