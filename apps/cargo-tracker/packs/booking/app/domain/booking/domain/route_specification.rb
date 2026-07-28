# frozen_string_literal: true

module Booking
  module Domain
    # ルート仕様。出発地・目的地（UN/LOCODE）・到着期限の要件。
    # Location 共有カーネル（IT3 で導入）は UN/LOCODE を参照キーとして扱う。ここでは形式のみ検証する。
    # 注: 各地点が locations マスタに実在するかの検証（Shared::Public::LocationDirectory#exists?）は
    # Booking↔Routing の連携を深める IT4 でアプリケーション層に配線する（現状は未配線）。
    RouteSpecification = Data.define(:origin, :destination, :arrival_deadline) do
      UNLOCODE_FORMAT = /\A[A-Z0-9]{5}\z/

      def initialize(origin:, destination:, arrival_deadline:)
        raise ArgumentError, "出発地は必須です" if origin.nil? || origin.to_s.strip.empty?
        raise ArgumentError, "目的地は必須です" if destination.nil? || destination.to_s.strip.empty?
        raise ArgumentError, "出発地は UN/LOCODE 形式（5 文字）です: #{origin}" unless UNLOCODE_FORMAT.match?(origin.to_s)
        raise ArgumentError, "目的地は UN/LOCODE 形式（5 文字）です: #{destination}" unless UNLOCODE_FORMAT.match?(destination.to_s)
        raise ArgumentError, "出発地と目的地は異なる必要があります" if origin == destination
        raise ArgumentError, "到着期限は必須です" if arrival_deadline.nil?

        super
      end

      # 旅程が本ルート仕様（出発地・目的地・到着期限）を満たすか。
      # 到着期限は DATE、旅程到着時刻は時刻付き。期限当日着を刈らないよう日付単位で比較する。
      def satisfied_by?(itinerary)
        return false unless itinerary.origin == origin
        return false unless itinerary.destination == destination

        itinerary.expected_arrival_time.to_date <= arrival_deadline
      end
    end
  end
end
