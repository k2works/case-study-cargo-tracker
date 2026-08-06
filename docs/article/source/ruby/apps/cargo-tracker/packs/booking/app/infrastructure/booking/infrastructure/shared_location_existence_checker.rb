# frozen_string_literal: true

module Booking
  module Infrastructure
    # 地点存在確認 ACL のインプロセス・アダプタ（T17・ADR-0001/0003）。
    # Shared Kernel の公開 API（Shared::Public::LocationDirectory）を呼び出す。
    class SharedLocationExistenceChecker < Domain::LocationExistenceChecker
      def initialize(directory: Shared::Public::LocationDirectory.new)
        @directory = directory
      end

      def exists?(unlocode)
        @directory.exists?(unlocode)
      end
    end
  end
end
