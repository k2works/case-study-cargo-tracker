# frozen_string_literal: true

module Bookings
  # 経路割り当て画面（US07 検索 / US08 経路候補提示）。MVP では営業担当者が経路設計者を代替。
  # Booking / Routing とも公開 API 経由でのみアクセスする（privacy）。
  class RoutesController < ApplicationController
    before_action -> { require_role(:sales) }

    BOOKING_NOT_FOUND = "予約が見つかりません"

    def edit
      @booking = booking_service.find(params[:booking_id])
      return redirect_to bookings_path, alert: BOOKING_NOT_FOUND if @booking.nil?

      load_candidates
    end

    # 経路候補を選択して予約に紐付ける（US09/US11・PATCH /bookings/:booking_id/route・PRG）。
    def update
      @booking = booking_service.find(params[:booking_id])
      return redirect_to bookings_path, alert: BOOKING_NOT_FOUND if @booking.nil?

      load_candidates
      candidate = @candidates[params[:candidate_index].to_i] if params[:candidate_index].present?
      if candidate.nil?
        flash.now[:alert] = "経路候補を選択してください"
        return render :edit, status: :unprocessable_entity
      end

      assign_selected_candidate(candidate)
    end

    # 荷主との条件協議を依頼する（US10・満たす経路がない場合）。
    def request_consultation
      case booking_service.request_consultation(params[:booking_id])
      when :ok
        redirect_to booking_path(params[:booking_id]), status: :see_other,
                    notice: "荷主との条件協議を依頼しました"
      else
        redirect_to bookings_path, alert: BOOKING_NOT_FOUND
      end
    end

    private

    def assign_selected_candidate(candidate)
      status = booking_service.assign_itinerary(@booking.booking_id, candidate.legs)
      case status
      when :ok
        redirect_to booking_path(@booking.booking_id), status: :see_other,
                    notice: "経路を予約に紐付けました（経路提案済）"
      when :not_found
        redirect_to bookings_path, alert: BOOKING_NOT_FOUND
      else
        flash.now[:alert] = "この経路は予約の条件を満たしません"
        render :edit, status: :unprocessable_entity
      end
    end

    def load_candidates
      # US10: 条件調整（期限延長・出発日指定）で再算出できる。未指定なら予約の希望着日で算出。
      @adjusted_deadline = params[:arrival_deadline].presence
      @departure_date = params[:departure_date].presence
      deadline = @adjusted_deadline || @booking.arrival_deadline

      result = voyage_directory.calculate_route_candidates(
        origin: @booking.origin, destination: @booking.destination,
        arrival_deadline: deadline, departure_date: @departure_date
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
