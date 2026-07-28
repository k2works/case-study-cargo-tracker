# frozen_string_literal: true

module Tracking
  module Application
    # handling_activity_registered 購読ハンドラ（US15）。荷役作業を追跡状態へ反映する。
    # 荷役種別に応じて輸送状態を進め、追跡イベント履歴を追加する（ADR-0002）。
    class HandlingEventHandler
      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new)
        @repository = repository
      end

      def call(payload)
        activity = @repository.find_by_booking_id(payload[:booking_id])
        return if activity.nil? # 追跡番号未発行の貨物への荷役は追跡へ反映しない

        activity.apply_handling(payload[:event_type])
        @repository.append_event(
          activity, event_type: payload[:event_type], event_time: payload[:completion_time],
          location: payload[:location], voyage_number: payload[:voyage_number]
        )
      end
    end
  end
end
