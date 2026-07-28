# frozen_string_literal: true

# 見積（Estimation Context）。プレースホルダ。
class EstimatesController < ApplicationController
  include Placeholder

  before_action -> { require_role(:sales) }

  def index = placeholder("見積一覧")
  def new = placeholder("見積作成")
  def show = placeholder("見積詳細")
end
