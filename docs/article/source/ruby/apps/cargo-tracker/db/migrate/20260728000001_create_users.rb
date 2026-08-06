# frozen_string_literal: true

class CreateUsers < ActiveRecord::Migration[8.0]
  def change
    create_table :users do |t|
      t.string   :username, limit: 50, null: false
      t.string   :email, limit: 200, null: false
      t.string   :password_digest, limit: 255, null: false
      t.boolean  :enabled, null: false, default: true
      t.integer  :failed_attempts, null: false, default: 0 # 連続認証失敗回数（US26 アカウントロック）
      t.datetime :locked_at # ロック日時（NULL=未ロック）
      t.timestamps
    end
    add_index :users, :username, unique: true
    add_index :users, :email, unique: true
  end
end
