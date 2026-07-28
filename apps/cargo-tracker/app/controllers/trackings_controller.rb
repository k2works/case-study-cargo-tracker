# frozen_string_literal: true

# 貨物追跡（Tracking Context・認証あり）。追跡管理者・荷役作業員ロール。
# US17（貨物状態手動更新）を含む。Tracking Context へは公開 API 経由でのみアクセスする。
class TrackingsController < ApplicationController
  before_action -> { require_role(:tracker, :handler) }

  # 手動更新で選択できる輸送状態（US17）。
  MANUAL_STATUSES = %w[RECEIVED LOADED ONBOARD_CARRIER UNLOADED AWAITING_CLAIM CLAIMED].freeze

  # 追跡番号入力（US17 の入口・US18 追跡照会の基盤）。入力があれば詳細（show）へ遷移する。
  def new
    redirect_to tracking_detail_path(params[:tracking_number]) if params[:tracking_number].present?
  end

  def show
    @tracking_number = params[:tracking_number]
    @tracking = tracking_service.find_by_tracking_number(@tracking_number)
    return redirect_to tracking_path, alert: "追跡番号 #{@tracking_number} は見つかりません" if @tracking.nil?

    @events = tracking_service.events_for(@tracking.booking_id)
  end

  # 貨物状態を手動更新する（US17・追跡管理者）。
  def update_status
    tracking_number = params[:tracking_number]
    case tracking_service.update_status_manually(
      tracking_number, transport_status: params[:transport_status],
      location: params[:location].presence, event_time: params[:event_time].presence
    )
    when :ok
      redirect_to tracking_detail_path(tracking_number), notice: "貨物状態を更新しました"
    when :invalid
      redirect_to tracking_detail_path(tracking_number), alert: "指定された状態が不正です"
    else
      redirect_to tracking_path, alert: "追跡番号が見つかりません"
    end
  end

  private

  def tracking_service
    @tracking_service ||= Tracking::Public::TrackingService.new
  end
end
