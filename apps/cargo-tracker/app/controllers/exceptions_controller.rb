# frozen_string_literal: true

# 例外管理（Tracking Context・US19 遅延 / US20 破損・紛失）。追跡管理者ロール。
# Tracking Context へは公開 API 経由でのみアクセスする。
class ExceptionsController < ApplicationController
  before_action -> { require_role(:tracker) }

  # 登録できる例外種別（US19 遅延・US20 破損/紛失）。税関保留は外部連携で自動登録（IT6 スコープ外）。
  EXCEPTION_TYPES = %w[DELAY DAMAGE LOST].freeze

  def index
    @exceptions = tracking_service.exceptions
  end

  def new
    @tracking_number = params[:tracking_number]
  end

  # 例外イベントを登録する（US19/US20）。
  def create
    tracking_number = params[:tracking_number].to_s
    result = tracking_service.register_exception(
      tracking_number, exception_type: params[:exception_type], description: params[:description].presence,
      location: params[:location].presence, occurred_at: params[:occurred_at].presence || Time.current
    )
    case result.status
    when :ok
      redirect_to exceptions_path, notice: "例外を登録しました"
    when :not_found
      redirect_to new_exception_path, alert: "追跡番号 #{tracking_number} は見つかりません"
    else
      redirect_to new_exception_path(tracking_number: tracking_number), alert: "入力内容が不正です"
    end
  end

  # 例外への対応報告を送信し解決する（US19/US20）。
  def report
    tracking_number = params[:id].to_s
    result = tracking_service.resolve_exception(tracking_number, resolution_notes: params[:resolution_notes].to_s)
    if result.status == :ok
      redirect_to exceptions_path, notice: "対応報告を送信しました"
    else
      redirect_to exceptions_path, alert: "対応対象の例外が見つかりません"
    end
  end

  # 例外の状態を更新する（IT6 は report で解決を扱う。将来の状態更新用）。
  def update_status
    redirect_to exceptions_path
  end

  private

  def tracking_service
    @tracking_service ||= Tracking::Public::TrackingService.new
  end
end
