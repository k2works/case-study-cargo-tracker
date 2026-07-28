# frozen_string_literal: true

# 例外管理（Tracking Context）。プレースホルダ。
class ExceptionsController < ApplicationController
  include Placeholder

  before_action -> { require_role(:tracker) }

  def index = placeholder("例外管理一覧")
  def new = placeholder("例外イベント登録")
end
