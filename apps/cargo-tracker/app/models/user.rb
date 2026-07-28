# frozen_string_literal: true

# 認証・認可基盤の利用者（共通コンテキスト）。
# Rails 8 標準認証（has_secure_password + セッション）と 5 ロール RBAC を担う。
class User < ApplicationRecord
  MAX_FAILED_ATTEMPTS = 5

  has_secure_password
  has_many :user_roles, dependent: :destroy

  validates :username, presence: true, uniqueness: true, length: { maximum: 50 }
  validates :email, presence: true, uniqueness: true, length: { maximum: 200 }

  # 保持するロール名の一覧（例: ["sales"]）。
  def roles
    user_roles.map(&:role)
  end

  # 指定ロールを保持しているか。
  def role?(role)
    roles.include?(role.to_s)
  end

  # 有効な（無効化されていない）利用者か。
  def active?
    enabled?
  end

  # アカウントがロックされているか。
  def locked?
    locked_at.present?
  end

  # 認証失敗を 1 回記録する。上限に達したらロックする。
  def register_failure!
    increment!(:failed_attempts)
    lock! if failed_attempts >= MAX_FAILED_ATTEMPTS
  end

  # 認証成功時に失敗回数をリセットする。
  def reset_failures!
    update!(failed_attempts: 0)
  end

  # アカウントをロックする。
  def lock!
    update!(locked_at: Time.current)
  end
end
