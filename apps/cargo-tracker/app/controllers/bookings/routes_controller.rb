# frozen_string_literal: true

module Bookings
  # 経路割り当て画面（US07 検索 / US08 経路候補提示）。MVP では営業担当者が経路設計者を代替。
  # Booking / Routing とも公開 API 経由でのみアクセスする（privacy）。
  class RoutesController < ApplicationController
    before_action -> { require_role(:sales) }

    def edit
      @booking = booking_service.find(params[:booking_id])
      return redirect_to bookings_path, alert: "予約が見つかりません" if @booking.nil?

      load_candidates
    end

    # 経路候補を選択して予約に紐付ける（US09/US11・PATCH /bookings/:booking_id/route・PRG）。
    def update
      @booking = booking_service.find(params[:booking_id])
      return redirect_to bookings_path, alert: "予約が見つかりません" if @booking.nil?

      load_candidates
      candidate = @candidates[params[:candidate_index].to_i] if params[:candidate_index].present?
      if candidate.nil?
        flash.now[:alert] = "経路候補を選択してください"
        return render :edit, status: :unprocessable_entity
      end

      assign_selected_candidate(candidate)
    end

    private

    def assign_selected_candidate(candidate)
      status = booking_service.assign_itinerary(@booking.booking_id, candidate.legs)
      case status
      when :ok
        redirect_to booking_path(@booking.booking_id), status: :see_other,
                    notice: "経路を予約に紐付けました（経路提案済）"
      when :not_found
        redirect_to bookings_path, alert: "予約が見つかりません"
      else
        flash.now[:alert] = "この経路は予約の条件を満たしません"
        render :edit, status: :unprocessable_entity
      end
    end

    def load_candidates
      result = voyage_directory.calculate_route_candidates(
        origin: @booking.origin, destination: @booking.destination,
        arrival_deadline: @booking.arrival_deadline
      )
      @candidates = result.candidates || []
      @message = result.message
    end

    def booking_service
      @booking_service ||= Booking::Public::CargoBookingService.new
    end

    def voyage_directory
      @voyage_directory ||= Routing::Public::VoyageDirectory.new
    end
  end
end
