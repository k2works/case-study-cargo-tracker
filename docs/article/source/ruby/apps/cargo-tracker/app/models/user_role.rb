# frozen_string_literal: true

# 利用者に割り当てられるロール（5 ロール RBAC）。
class UserRole < ApplicationRecord
  ROLES = %w[sales handler tracker billing admin].freeze

  belongs_to :user

  validates :role, presence: true, inclusion: { in: ROLES },
                   uniqueness: { scope: :user_id }
end
