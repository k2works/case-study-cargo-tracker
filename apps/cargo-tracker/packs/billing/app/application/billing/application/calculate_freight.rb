# frozen_string_literal: true

module Billing
  module Application
    # 輸送料金算出ユースケース（US21 料金算出・US22 法人割引）。
    # 配送完了（DELIVERED）予約の輸送実績を Booking 公開 API（ACL）で取得し、
    # 荷主種別に応じた割引（Shipper 公開 API・ACL）を適用して請求書を PENDING で発行する。
    class CalculateFreight
      DISTANCE_FACTOR_PER_LEG = BigDecimal("50") # 距離係数の暫定値（実距離データ導入まで区間数で代替）
      FUEL_SURCHARGE_RATE = BigDecimal("0.05")   # 燃油サーチャージ 5%

      Result = Struct.new(:status, :invoice_number, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new,
                     calculator: Domain::FreightCalculationService.new,
                     booking_service: Booking::Public::CargoBookingService.new,
                     shipper_directory: Shipper::Public::ShipperDirectory.new,
                     clock: -> { Time.current })
        @repository = repository
        @calculator = calculator
        @booking_service = booking_service
        @shipper_directory = shipper_directory
        @clock = clock
      end

      def call(booking_id)
        booking = @booking_service.find(booking_id)
        return Result.new(status: :not_found) if booking.nil?
        return Result.new(status: :invalid, error_message: "引取済（配送完了）の予約のみ料金算出できます") unless booking.delivered?
        return Result.new(status: :already_invoiced) if @repository.exists_for_booking?(booking_id)

        discount = discount_rate_for(booking.shipper_id)
        freight = @calculator.calculate(
          distance_factor: distance_factor(booking), weight_kg: booking.weight_kg, cargo_type: booking.cargo_type,
          discount_rate: discount, surcharges: [ Domain::Surcharge.new(type: "FUEL", rate: FUEL_SURCHARGE_RATE) ]
        )

        invoice = Domain::Invoice.generate(
          invoice_number: next_invoice_number, booking_id: booking_id, shipper_id: booking.shipper_id,
          base_amount: freight.base_amount, discount_rate: discount,
          tax_amount: freight.tax_amount, total_amount: freight.total_amount, issued_at: @clock.call
        )
        @repository.save(invoice)

        DomainEvents.publish("invoice_created", {
          booking_id: booking_id, shipper_id: booking.shipper_id, invoice_number: invoice.invoice_number,
          total_amount: invoice.total_amount.amount.to_i, due_date: invoice.due_date
        })
        Result.new(status: :ok, invoice_number: invoice.invoice_number)
      end

      private

      # 荷主種別に応じた割引率（法人＝契約割引・個人＝0%）。Shipper 公開 API（ACL）で取得。
      def discount_rate_for(shipper_id)
        shipper = @shipper_directory.find(shipper_id)
        percentage = shipper&.corporate? ? shipper.discount_percentage.to_i : 0
        Domain::DiscountRate.from_percentage(percentage)
      end

      # 距離係数（暫定・旅程の区間数で代替）。
      def distance_factor(booking)
        legs = Array(booking.itinerary_legs).size
        DISTANCE_FACTOR_PER_LEG * [ legs, 1 ].max
      end

      def next_invoice_number
        format("INV-%<seq>06d", seq: (@repository.all.size + 1))
      end
    end
  end
end
