# frozen_string_literal: true

# ロール別ダッシュボード（US26 ログイン後の入口）。
class DashboardController < ApplicationController
  # ロールに応じた入口カードはビュー（dashboard/show）が current_user.role? で出し分けるため、
  # コントローラでの追加処理は不要。
  def show; end
end
