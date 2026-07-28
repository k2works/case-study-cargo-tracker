# frozen_string_literal: true

module Routing
  module Domain
    # 航海スケジュール（値オブジェクト）。時系列順の CarrierMovement 一覧を保持する。
    class Schedule
      attr_reader :movements

      def initialize(movements)
        list = Array(movements).sort_by(&:seq_number)
        validate!(list)
        @movements = list.freeze
        freeze
      end

      def origin = movements.first.departure_unlocode
      def destination = movements.last.arrival_unlocode
      def departure_date = movements.first.departure_date
      def arrival_date = movements.last.arrival_date

      # 経由港（出発地〜到着地の UN/LOCODE 列）。
      def ports
        [ movements.first.departure_unlocode ] + movements.map(&:arrival_unlocode)
      end

      def ==(other)
        other.is_a?(Schedule) && other.movements == movements
      end
      alias eql? ==
      def hash = movements.hash

      private

      def validate!(list)
        raise ArgumentError, "スケジュールは 1 区間以上必要です" if list.empty?

        list.each_cons(2) do |prev, nxt|
          if prev.arrival_unlocode != nxt.departure_unlocode
            raise ArgumentError, "区間が連結していません（#{prev.arrival_unlocode} ≠ #{nxt.departure_unlocode}）"
          end
          if nxt.departure_date < prev.arrival_date
            raise ArgumentError, "区間が時系列順ではありません（後続区間が先行区間の到着より前に出発）"
          end
        end
      end
    end
  end
end
