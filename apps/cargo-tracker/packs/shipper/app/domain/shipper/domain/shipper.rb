# frozen_string_literal: true

module Shipper
  module Domain
    # 荷主集約ルート（PORO）。個人・法人の 2 種別を扱う。
    class Shipper
      attr_reader :code, :name, :email, :phone, :address, :shipper_type

      # 新規荷主を生成する。不変条件をここで担保する。
      def self.register(code:, name:, email:, shipper_type:, phone: nil, address: nil)
        raise ArgumentError, "荷主名は必須です" if name.nil? || name.to_s.strip.empty?
        raise ArgumentError, "メールアドレスは必須です" if email.nil? || email.to_s.strip.empty?

        new(code: code, name: name, email: email, phone: phone, address: address,
            shipper_type: shipper_type)
      end

      def initialize(code:, name:, email:, shipper_type:, phone: nil, address: nil)
        @code = code
        @name = name
        @email = email
        @phone = phone
        @address = address
        @shipper_type = shipper_type
      end

      def corporate?
        shipper_type.corporate?
      end
    end
  end
end
