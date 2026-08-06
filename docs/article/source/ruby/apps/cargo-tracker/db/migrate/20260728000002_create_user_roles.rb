# frozen_string_literal: true

class CreateUserRoles < ActiveRecord::Migration[8.0]
  def change
    create_table :user_roles do |t|
      t.references :user, null: false, foreign_key: true
      t.string :role, limit: 50, null: false # sales / handler / tracker / billing / admin（5 ロール RBAC）
      t.timestamps
    end
    add_index :user_roles, %i[user_id role], unique: true
  end
end
