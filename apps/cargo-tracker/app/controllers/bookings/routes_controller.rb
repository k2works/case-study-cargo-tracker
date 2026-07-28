# frozen_string_literal: true

module Bookings
  # 経路割り当て画面（US07 検索 / US08 経路候補提示）。MVP では営業担当者が経路設計者を代替。
  # Booking / Routing とも公開 API 経由でのみアクセスする（privacy）。
  class RoutesController < ApplicationController
    before_action -> { require_role(:sales) }

    def edit
      @booking = booking_service.find(params[:booking_id])
      return redirect_to bookings_path, alert: "予約が見つかりません" if @booking.nil?

      result = voyage_directory.calculate_route_candidates(
        origin: @booking.origin, destination: @booking.destination,
        arrival_deadline: @booking.arrival_deadline
      )
      @candidates = result.candidates || []
      @message = result.message
    end

    private

    def booking_service
      @booking_service ||= Booking::Public::CargoBookingService.new
    end

    def voyage_directory
      @voyage_directory ||= Routing::Public::VoyageDirectory.new
    end
  end
end
