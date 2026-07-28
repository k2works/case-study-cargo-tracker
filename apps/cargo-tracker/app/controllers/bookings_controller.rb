# frozen_string_literal: true

# 貨物予約（Booking Context）。ウォーキングスケルトンのプレースホルダ。
class BookingsController < ApplicationController
  include Placeholder

  before_action -> { require_role(:sales) }

  def index = placeholder("貨物予約一覧")
  def new = placeholder("貨物予約登録")
  def show = placeholder("予約詳細")
end
