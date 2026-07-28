# frozen_string_literal: true

class ApplicationController < ActionController::Base
  include Authentication
  include Pundit::Authorization

  # Only allow modern browsers supporting webp images, web push, badges, import maps, CSS nesting, and CSS :has.
  allow_browser versions: :modern

  rescue_from Pundit::NotAuthorizedError, with: :forbidden

  private

  # 指定ロールのいずれも持たない場合は 403 とする（RBAC）。
  def require_role(*roles)
    return if roles.any? { |r| current_user&.role?(r) }

    raise Pundit::NotAuthorizedError
  end

  def forbidden
    respond_to do |format|
      format.html { render plain: "403 Forbidden（この操作を行う権限がありません）", status: :forbidden }
      format.any  { head :forbidden }
    end
  end
end
