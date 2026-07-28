# frozen_string_literal: true

module Admin
  # 割引ポリシー管理。プレースホルダ。
  class DiscountPoliciesController < ApplicationController
    include Placeholder

    before_action -> { require_role(:admin) }

    def index = placeholder("割引ポリシー一覧")
    def new = placeholder("割引ポリシー登録")
    def edit = placeholder("割引ポリシー編集")
  end
end
