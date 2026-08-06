# frozen_string_literal: true

module Routing
  module Application
    # 入力（ハッシュ・文字列日時）から CarrierMovement を安全に構築する（T13）。
    # 空・非日時は ArgumentError（日本語メッセージ）に集約し、内部例外を露出させない。
    module MovementBuilder
      module_function

      def build(movements)
        Array(movements).map.with_index(1) do |m, index|
          attrs = m.transform_keys(&:to_sym)
          Domain::CarrierMovement.new(
            departure_unlocode: attrs[:departure_unlocode],
            arrival_unlocode: attrs[:arrival_unlocode],
            departure_date: parse_time(attrs[:departure_date], "出発日時", index),
            arrival_date: parse_time(attrs[:arrival_date], "到着日時", index),
            seq_number: (attrs[:seq_number] || index).to_i
          )
        end
      end

      def parse_time(value, label, index)
        return value if value.is_a?(Time) || value.is_a?(DateTime)
        raise ArgumentError, "区間 #{index} の#{label}は必須です" if value.nil? || value.to_s.strip.empty?

        Time.zone.parse(value.to_s) || raise(ArgumentError, "区間 #{index} の#{label}が不正です")
      rescue ArgumentError, TypeError
        raise ArgumentError, "区間 #{index} の#{label}が不正です"
      end
    end
  end
end
