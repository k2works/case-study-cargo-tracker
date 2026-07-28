# frozen_string_literal: true

module Booking
  module Domain
    # ルート仕様。出発地・目的地（UN/LOCODE）・到着期限の要件。
    # 注: IT2 は UN/LOCODE を String で保持。Location 共有カーネル化は IT3。
    RouteSpecification = Data.define(:origin, :destination, :arrival_deadline) do
      def initialize(origin:, destination:, arrival_deadline:)
        raise ArgumentError, "出発地は必須です" if origin.nil? || origin.to_s.strip.empty?
        raise ArgumentError, "目的地は必須です" if destination.nil? || destination.to_s.strip.empty?
        raise ArgumentError, "出発地と目的地は異なる必要があります" if origin == destination
        raise ArgumentError, "到着期限は必須です" if arrival_deadline.nil?

        super
      end
    end
  end
end
