# frozen_string_literal: true

# ウォーキングスケルトンのプレースホルダ画面を描画する共通機構。
# 画面遷移図に沿った全ルートを「準備中」画面として先に用意し、ナビゲーション整合を確保する。
module Placeholder
  extend ActiveSupport::Concern

  private

  def placeholder(title)
    @screen_title = title
    render "shared/placeholder"
  end
end
