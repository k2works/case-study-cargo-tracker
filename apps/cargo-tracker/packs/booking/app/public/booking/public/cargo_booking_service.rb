# frozen_string_literal: true

module Booking
  module Public
    # 貨物予約の公開ファサード（アプリ層＝合成ルート向け）。
    # 内部のユースケース・リポジトリ・ACL を隠蔽し、公開ビューを返す。
    class CargoBookingService
      STATUS_LABELS = {
        "PRELIMINARY" => "仮受付", "ROUTE_REQUESTED" => "経路設計中",
        "ROUTE_PROPOSED" => "経路提案済", "CONFIRMED" => "確定",
        "TRACKING_ISSUED" => "追跡発行済", "IN_TRANSIT" => "輸送中",
        "DELIVERED" => "配達済", "SETTLED" => "精算済", "CANCELLED" => "キャンセル"
      }.freeze
      TYPE_LABELS = { "GENERAL" => "一般", "HAZARDOUS" => "危険物", "REFRIGERATED" => "冷凍・冷蔵" }.freeze

      # 予約の公開ビュー（内部集約を晒さない投影）。
      View = Data.define(:booking_id, :shipper_id, :type_label, :cargo_type, :status_label, :status_value,
                         :weight_kg, :origin, :destination, :arrival_deadline, :description,
                         :itinerary_legs, :expected_arrival_time, :tracking_number, :routing_status) do
        def preliminary? = status_value == "PRELIMINARY"
        def route_requested? = status_value == "ROUTE_REQUESTED"
        def route_proposed? = status_value == "ROUTE_PROPOSED"
        def confirmed? = status_value == "CONFIRMED"
        def tracking_issued? = status_value == "TRACKING_ISSUED"
        def delivered? = status_value == "DELIVERED"
        def routed? = itinerary_legs.present?
      end

      # 旅程脚の公開ビュー。
      LegView = Data.define(:load_location, :unload_location, :voyage_number, :load_time, :unload_time)

      # ドメイン値オブジェクトのファクトリ（アプリ層が Domain を直接参照しないための公開口）。
      def self.hazardous_declaration(hazardous_class:, un_number:, proper_shipping_name:)
        Domain::HazardousDeclaration.new(hazardous_class: hazardous_class, un_number: un_number,
                                         proper_shipping_name: proper_shipping_name)
      end

      def self.temperature_requirement(min_temperature:, max_temperature:, unit:)
        Domain::TemperatureRequirement.new(min_temperature: min_temperature,
                                           max_temperature: max_temperature, unit: unit)
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new,
                     shipper_existence_checker: Infrastructure::ShipperDirectoryExistenceChecker.new,
                     location_existence_checker: Infrastructure::SharedLocationExistenceChecker.new,
                     notifier: Infrastructure::LogRoutingNotifier.new,
                     recorder: Shared::Public::NotificationRecorder.new)
        @repository = repository
        @checker = shipper_existence_checker
        @location_checker = location_existence_checker
        @notifier = notifier
        @recorder = recorder
      end

      # 予約登録（US04/US05）。結果は BookCargo::Result。
      def book(**params)
        Application::BookCargo.new(repository: @repository, shipper_existence_checker: @checker,
                                  location_existence_checker: @location_checker,
                                  notifier: @notifier).call(**params)
      end

      # 経路設計者への引き渡し（US06）。結果を :ok / :not_found / :invalid で返す。
      # 悲観ロックで read-modify-write をアトミックにし、二重遷移・二重通知を防ぐ。
      def assign_to_routing(booking_id_value)
        booking_id = Domain::BookingId.new(value: booking_id_value)
        cargo = @repository.with_locked_cargo(booking_id, &:assign_to_routing)
        return :not_found if cargo.nil?

        @notifier.notify_routing_requested(booking_id_value) # US06 経路設計依頼通知
        :ok
      rescue Domain::BookingStatus::InvalidTransition
        :invalid
      rescue ArgumentError
        :not_found # 予約番号の形式が不正
      end

      # 経路（旅程）紐付け（US09/US11）。legs はプリミティブ Hash の配列（Routing 公開ビュー由来）。
      # 結果を :ok / :not_found / :invalid で返す。
      def assign_itinerary(booking_id_value, legs)
        result = Application::AssignItinerary.new(repository: @repository).call(
          booking_id_value: booking_id_value, legs: legs
        )
        result.status
      end

      # 予約確定（US13・→CONFIRMED）。結果を :ok / :not_found / :invalid で返す。
      def confirm(booking_id_value)
        Application::ConfirmBooking.new(repository: @repository).call(booking_id_value: booking_id_value).status
      end

      # 予約キャンセル（US13・→CANCELLED）。結果を :ok / :not_found / :invalid で返す。
      def cancel(booking_id_value)
        Application::CancelBooking.new(repository: @repository).call(booking_id_value: booking_id_value).status
      end

      # ルート変更差戻し（US13・ROUTE_PROPOSED→ROUTE_REQUESTED）。結果を :ok / :not_found / :invalid で返す。
      def request_rerouting(booking_id_value)
        Application::RequestRerouting.new(repository: @repository).call(booking_id_value: booking_id_value).status
      end

      # 確定経路を荷主へ通知（US12・明示操作）。結果を :ok / :not_found / :invalid で返す。
      def notify_route(booking_id_value)
        Application::NotifyShipperOfRoute.new(repository: @repository).call(booking_id_value: booking_id_value).status
      end

      # 荷主との条件協議を依頼（US10・満たす経路がない場合）。結果を :ok / :not_found で返す。
      def request_consultation(booking_id_value)
        Application::RequestRouteConsultation.new(repository: @repository).call(booking_id_value: booking_id_value).status
      end

      # 追跡番号を発行して予約に紐付ける（US14・CONFIRMED→TRACKING_ISSUED）。
      # Tracking Context が越境 API として呼び出す。結果を :ok / :not_found / :invalid で返す。
      def issue_tracking_number(booking_id_value, tracking_number)
        cargo = @repository.with_locked_cargo(Domain::BookingId.new(value: booking_id_value)) do |c|
          c.issue_tracking_number(tracking_number)
        end
        cargo.nil? ? :not_found : :ok
      rescue Domain::BookingStatus::InvalidTransition
        :invalid
      rescue ArgumentError
        :not_found
      end

      # 荷役イベントを予約へ反映する（US15/US16・handling_activity_registered 購読ハンドラから呼ぶ）。
      # 最新荷役を記録し、必要に応じ BookingStatus を進める（LOAD→IN_TRANSIT・CLAIM→DELIVERED）。
      def sync_handling_event(booking_id_value, type:, location:, voyage: nil, misrouted: false)
        cargo = @repository.with_locked_cargo(Domain::BookingId.new(value: booking_id_value)) do |c|
          c.record_handling(type: type, location: location, voyage: voyage, misrouted: misrouted)
        end
        cargo.nil? ? :not_found : :ok
      rescue ArgumentError
        :not_found
      end

      # 予約に紐付いた通知送信記録の一覧（US12 確認用）。
      def notifications(booking_id_value)
        recorder.for(notifiable_type: "Cargo", notifiable_id: booking_id_value)
      end

      def all
        @repository.all.map { |c| to_view(c) }
      end

      def find(booking_id_value)
        cargo = load(booking_id_value)
        cargo && to_view(cargo)
      end

      # 追跡番号で予約を検索する（US15 荷役作業員が追跡番号で貨物を特定）。
      def find_by_tracking_number(tracking_number)
        cargo = @repository.find_by_tracking_number(tracking_number)
        cargo && to_view(cargo)
      end

      private

      attr_reader :recorder

      def load(booking_id_value)
        @repository.find_by_booking_id(Domain::BookingId.new(value: booking_id_value))
      rescue ArgumentError
        nil # 予約番号の形式が不正な場合は「見つからない」として扱う
      end

      def to_view(cargo)
        View.new(
          booking_id: cargo.booking_id.value,
          shipper_id: cargo.shipper_id,
          type_label: TYPE_LABELS.fetch(cargo.cargo_type.value),
          cargo_type: cargo.cargo_type.value,
          status_label: STATUS_LABELS.fetch(cargo.booking_status.value),
          status_value: cargo.booking_status.value,
          weight_kg: cargo.weight_kg,
          origin: cargo.route_specification.origin,
          destination: cargo.route_specification.destination,
          arrival_deadline: cargo.route_specification.arrival_deadline,
          description: cargo.description,
          itinerary_legs: itinerary_legs_of(cargo),
          expected_arrival_time: cargo.cargo_itinerary&.expected_arrival_time,
          tracking_number: cargo.tracking_number,
          routing_status: cargo.routing_status
        )
      end

      def itinerary_legs_of(cargo)
        return [] if cargo.cargo_itinerary.nil?

        cargo.cargo_itinerary.legs.map do |leg|
          LegView.new(load_location: leg.load_location, unload_location: leg.unload_location,
                      voyage_number: leg.voyage_number, load_time: leg.load_time, unload_time: leg.unload_time)
        end
      end
    end
  end
end
