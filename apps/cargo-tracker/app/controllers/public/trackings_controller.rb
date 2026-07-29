# frozen_string_literal: true

module Public
  # 公開貨物追跡（認証不要 / 未認証ユーザーの入口・US18）。
  # 荷主が URL 共有で照会できる。個人情報は表示せず、輸送状態・現在地・最終イベントのみを示す。
  class TrackingsController < ApplicationController
    skip_before_action :require_login

    # 追跡番号入力フォーム。入力があれば照会結果（show）へ遷移する。
    def new
      redirect_to public_tracking_detail_path(params[:tracking_number]) if params[:tracking_number].present?
    end

    def show
      @tracking_number = params[:tracking_id]
      @tracking = tracking_service.find_by_tracking_number(@tracking_number)
      return if @tracking.nil?

      # 追跡イベント履歴（時系列・US18 受入基準）と最終イベントを公開（個人情報は含めない）。
      @events = tracking_service.events_for(@tracking.booking_id)
      @last_event = @events.last
      # 推定到着日: 遅延例外の新到着予定日（T37）を最優先。なければ確定経路の最終 leg 到着時刻、
      # それも無ければ到着期限（設計反映 IT6 項目6 の更新）。
      booking = booking_service.find(@tracking.booking_id)
      revised = tracking_service.revised_arrival_date_for(@tracking.booking_id)
      @estimated_arrival = revised || booking&.expected_arrival_time || booking&.arrival_deadline
    end

    private

    def tracking_service
      @tracking_service ||= Tracking::Public::TrackingService.new
    end

    def booking_service
      @booking_service ||= Booking::Public::CargoBookingService.new
    end
  end
end
