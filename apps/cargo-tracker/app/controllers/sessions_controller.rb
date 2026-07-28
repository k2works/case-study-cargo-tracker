# frozen_string_literal: true

# ログイン・ログアウト（US26/US27）。
class SessionsController < ApplicationController
  skip_before_action :require_login, only: %i[new create]

  # 開発環境ではシード済みの営業担当者アカウントをデフォルト入力する。
  DEV_DEFAULT_USERNAME = "sales"
  DEV_DEFAULT_PASSWORD = "password123"

  def new
    redirect_to root_path and return if logged_in?

    if Rails.env.development?
      @username = DEV_DEFAULT_USERNAME
      @password = DEV_DEFAULT_PASSWORD
    else
      @username = ""
      @password = ""
    end
  end

  def create
    result = AuthenticationService.new.authenticate(params[:username], params[:password])

    if result.success?
      log_in(result.user)
      redirect_to root_path, notice: "ログインしました"
    else
      @username = params[:username].to_s
      flash.now[:alert] = result.error_message
      render :new, status: :unprocessable_entity
    end
  end

  def destroy
    log_out
    redirect_to login_path, notice: "ログアウトしました"
  end
end
