# frozen_string_literal: true

module Handling
  module Public
    # 荷役の公開ファサード（アプリ層＝合成ルート向け）。
    class HandlingService
      HistoryView = Data.define(:event_type, :location, :voyage_number, :completion_time,
                                :operator_name, :recipient_name)

      def initialize(repository: Infrastructure::ActiveRecordHandlingRepository.new)
        @repository = repository
      end

      # 荷役作業記録（US15/US16）。結果は RegisterHandlingActivity::Result。
      def register(**params)
        Application::RegisterHandlingActivity.new(repository: @repository).call(**params)
      end

      # 予約の荷役履歴（Read Model・US15 一覧）。
      def history_for(booking_id)
        @repository.history_for(booking_id).map do |e|
          HistoryView.new(event_type: e.event_type, location: e.location, voyage_number: e.voyage_number,
                          completion_time: e.completion_time, operator_name: e.operator_name,
                          recipient_name: e.recipient_name)
        end
      end
    end
  end
end
