# frozen_string_literal: true

# 貨物追跡（Tracking Context・認証あり）。プレースホルダ。
class TrackingsController < ApplicationController
  include Placeholder

  before_action -> { require_role(:tracker, :handler) }

  def new = placeholder("貨物追跡入力")
  def show = placeholder("追跡詳細")
end
