# frozen_string_literal: true

# ログイン・ログアウト（US26/US27）。
class SessionsController < ApplicationController
  skip_before_action :require_login, only: %i[new create]

  def new
    redirect_to root_path and return if logged_in?

    @username = ""
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
