# frozen_string_literal: true

module Billing
  # 請求管理（Billing Context）。プレースホルダ。
  class InvoicesController < ApplicationController
    include Placeholder

    before_action -> { require_role(:billing) }

    def index = placeholder("請求書一覧")
    def show = placeholder("請求書詳細")
  end
end
