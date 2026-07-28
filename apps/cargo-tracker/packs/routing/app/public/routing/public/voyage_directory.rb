# frozen_string_literal: true

module Routing
  module Public
    # 航海の公開ファサード（アプリ層＝合成ルート／他 BC 向け）。
    # 内部のユースケース・リポジトリを隠蔽し、公開ビューを返す（ADR-0001/0003/0004）。
    class VoyageDirectory
      # 航海の公開ビュー（内部集約を晒さない投影）。
      View = Data.define(:voyage_number, :carrier_name, :ship_name, :origin, :destination,
                         :departure_date, :arrival_date, :ports, :supported_cargo_types)

      def initialize(repository: Infrastructure::ActiveRecordVoyageRepository.new)
        @repository = repository
      end

      # 航海スケジュール登録（US24）。結果は RegisterVoyage::Result。
      def register(**params)
        Application::RegisterVoyage.new(repository: @repository).call(**params)
      end

      # 航海スケジュール更新（US25）。結果は UpdateSchedule::Result。
      def update(**params)
        Application::UpdateSchedule.new(repository: @repository).call(**params)
      end

      # 航海検索（US07）。結果は検索ビューの配列。
      def search(**params)
        Application::SearchVoyages.new(repository: @repository).call(**params)
      end

      def all
        @repository.all.map { |v| to_view(v) }
      end

      def find(voyage_number)
        voyage = @repository.find_by_number(Domain::VoyageNumber.new(value: voyage_number))
        voyage && to_view(voyage)
      rescue ArgumentError
        nil
      end

      private

      def to_view(voyage)
        View.new(
          voyage_number: voyage.voyage_number.value, carrier_name: voyage.carrier_name,
          ship_name: voyage.ship_name, origin: voyage.origin, destination: voyage.destination,
          departure_date: voyage.schedule.departure_date, arrival_date: voyage.schedule.arrival_date,
          ports: voyage.ports, supported_cargo_types: voyage.supported_cargo_types
        )
      end
    end
  end
end
