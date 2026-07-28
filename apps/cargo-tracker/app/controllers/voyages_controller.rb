# frozen_string_literal: true

# 航路（Routing Context）。MVP では営業担当者が経路設計者を代替。プレースホルダ。
class VoyagesController < ApplicationController
  include Placeholder

  before_action -> { require_role(:sales) }

  def index = placeholder("航路一覧")
  def show = placeholder("航路詳細")
end
