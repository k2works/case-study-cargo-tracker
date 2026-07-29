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

      # 追跡番号で追跡情報を取得する（US17 現在情報確認）。
      def find_by_tracking_number(tracking_number)
        activity = @repository.find_by_tracking_number(tracking_number)
        activity && to_view(activity)
      end

      # 貨物状態手動更新（US17）。結果を :ok / :not_found / :invalid で返す。
      def update_status_manually(tracking_number, transport_status:, location: nil, event_time: nil)
        Application::UpdateTrackingStatusManually.new(repository: @repository).call(
          tracking_number: tracking_number, transport_status: transport_status,
          location: location, event_time: event_time
        ).status
      end

      # 追跡イベント履歴（US17 更新後の履歴確認）。
      def events_for(booking_id_value)
        @repository.events_for(booking_id_value)
      end

      # 追跡例外の登録（US19 遅延 / US20 破損・紛失）。
      # 結果を :ok / :not_found / :invalid で返す。
      def register_exception(tracking_number, exception_type:, occurred_at:, description: nil, location: nil)
        Application::RegisterException.new(repository: @repository).call(
          tracking_number: tracking_number, exception_type: exception_type,
          occurred_at: occurred_at, description: description, location: location
        )
      end

      # 追跡例外の対応報告・解決（US19/US20）。結果を :ok / :not_found / :invalid で返す。
      def resolve_exception(tracking_number, resolution_notes:, resolved_at: nil)
        Application::ResolveException.new(repository: @repository).call(
          tracking_number: tracking_number, resolution_notes: resolution_notes, resolved_at: resolved_at
        )
      end

      # 例外イベント一覧（US19/US20 例外管理一覧・CQRS 読み取り）。
      def exceptions
        @repository.list_exceptions
      end

      private

      def to_view(activity)
        View.new(tracking_number: activity.tracking_number.value, booking_id: activity.booking_id,
                 transport_status: activity.transport_status.value)
      end
    end
  end
end
