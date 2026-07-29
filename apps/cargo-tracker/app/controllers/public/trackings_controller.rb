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

      # 最終イベント・現在地のみを公開（個人情報は含めない）。
      @last_event = tracking_service.events_for(@tracking.booking_id).last
    end

    private

    def tracking_service
      @tracking_service ||= Tracking::Public::TrackingService.new
    end
  end
end
