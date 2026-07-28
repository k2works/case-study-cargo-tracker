# frozen_string_literal: true

module Booking
  module Domain
    # ルート仕様。出発地・目的地（UN/LOCODE）・到着期限の要件。
    # Location 共有カーネル（IT3）は UN/LOCODE を参照キーとして扱う。各地点の実在は
    # アプリケーション層で Shared::Public::LocationDirectory により検証する（BC 境界・privacy 尊重）。
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
    end
  end
end
