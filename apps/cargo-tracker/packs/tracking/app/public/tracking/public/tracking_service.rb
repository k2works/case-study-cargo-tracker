# frozen_string_literal: true

module Tracking
  module Public
    # 追跡の公開ファサード（アプリ層＝合成ルート／他 BC 向け）。
    # 内部のユースケース・リポジトリを隠蔽し、公開ビューを返す。
    class TrackingService
      # 追跡の公開ビュー（内部集約を晒さない投影）。
      View = Data.define(:tracking_number, :booking_id, :transport_status)

      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new)
        @repository = repository
      end

      # 追跡番号発行（US14）。結果を :ok / :not_found / :invalid / :already_issued で返す。
      def issue_tracking_number(booking_id_value)
        Application::AssignTrackingNumber.new(repository: @repository).call(booking_id_value: booking_id_value)
      end

      def find_by_booking_id(booking_id_value)
        activity = @repository.find_by_booking_id(booking_id_value)
        activity && to_view(activity)
      end

      private

      def to_view(activity)
        View.new(tracking_number: activity.tracking_number.value, booking_id: activity.booking_id,
                 transport_status: activity.transport_status.value)
      end
    end
  end
end
