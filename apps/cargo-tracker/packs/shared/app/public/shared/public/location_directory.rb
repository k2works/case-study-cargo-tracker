# frozen_string_literal: true

module Shared
  module Public
    # 場所マスタの公開 API（全 Bounded Context 向けの共有カーネル参照）。
    # UN/LOCODE をキーに Location 値オブジェクトを提供する。
    class LocationDirectory
      # 場所を登録する（冪等: 同一 UN/LOCODE は上書きしない）。
      def register(unlocode:, name:, country_code: nil, time_zone: nil)
        # Location 値オブジェクトで形式を検証してから永続化する。
        location = Domain::Location.new(unlocode: unlocode, name: name)
        record = Infrastructure::LocationRecord.find_or_initialize_by(unlocode: location.unlocode)
        return to_domain(record) if record.persisted?

        record.assign_attributes(name: name, country_code: country_code, time_zone: time_zone)
        record.save!
        to_domain(record)
      end

      def exists?(unlocode)
        Infrastructure::LocationRecord.exists?(unlocode: unlocode)
      end

      def find(unlocode)
        record = Infrastructure::LocationRecord.find_by(unlocode: unlocode)
        record && to_domain(record)
      end

      def all
        Infrastructure::LocationRecord.order(:unlocode).map { |r| to_domain(r) }
      end

      private

      def to_domain(record)
        Domain::Location.new(unlocode: record.unlocode, name: record.name)
      end
    end
  end
end
