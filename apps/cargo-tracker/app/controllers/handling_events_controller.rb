# frozen_string_literal: true

# 荷役作業（Handling Context）。プレースホルダ。
class HandlingEventsController < ApplicationController
  include Placeholder

  before_action -> { require_role(:handler, :tracker) }

  def index = placeholder("荷役作業一覧")
  def new = placeholder("荷役作業登録")
end
