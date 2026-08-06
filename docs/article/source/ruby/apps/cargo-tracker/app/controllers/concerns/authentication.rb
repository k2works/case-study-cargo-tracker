# frozen_string_literal: true

# セッションベース認証（US26/US27）。ログイン状態の管理と未認証アクセスの誘導を担う。
module Authentication
  extend ActiveSupport::Concern

  included do
    helper_method :current_user, :logged_in?
    before_action :require_login
  end

  private

  def current_user
    @current_user ||= User.find_by(id: session[:user_id]) if session[:user_id]
  end

  def logged_in?
    current_user.present?
  end

  # 認証成功時にセッションを確立する。
  def log_in(user)
    reset_session # セッション固定攻撃対策
    session[:user_id] = user.id
    @current_user = user
  end

  # ログアウト（US27）。セッションを破棄する。
  def log_out
    Rails.logger.info("ログアウト: #{current_user&.username}")
    reset_session
    @current_user = nil
  end

  # 未ログイン時はログイン画面へ誘導する。
  def require_login
    return if logged_in?

    redirect_to login_path, alert: "ログインしてください"
  end
end
