# frozen_string_literal: true

module Bookings
  # 経路割り当て画面（MVP では営業担当者が経路設計者を代替）。プレースホルダ。
  class RoutesController < ApplicationController
    include Placeholder

    before_action -> { require_role(:sales) }

    def edit = placeholder("経路割り当て")
  end
end
