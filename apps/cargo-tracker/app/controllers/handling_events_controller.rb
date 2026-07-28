# frozen_string_literal: true

# 荷役作業（Handling Context / US15・US16）。荷役作業員・追跡管理者ロール。
# Handling Context へは公開 API（Handling::Public::HandlingService）経由でのみアクセスする。
class HandlingEventsController < ApplicationController
  before_action -> { require_role(:handler, :tracker) }

  EVENT_TYPES = %w[RECEIVE LOAD UNLOAD CLAIM].freeze

  def index
    @tracking_number = params[:tracking_number].presence
    @history = @tracking_number ? history_for(@tracking_number) : []
  end

  def new
    @form = default_form
  end

  def create
    @form = form_params
    result = handling_service.register(**use_case_args(@form))
    case result.status
    when :ok
      # 記録は成功だが経路逸脱（MISROUTED）/想定外港（warning）は警告として分離表示し、
      # 成功トーストに紛れて異常を見落とすことを防ぐ（誤配送は精算・遅延に直結）。
      warning = route_warning(result)
      flash_message = warning ? { alert: warning } : { notice: "荷役作業を記録しました" }
      redirect_to handling_events_path(tracking_number: @form[:tracking_number]), **flash_message
    when :not_found
      flash.now[:alert] = "追跡番号が存在しません"
      render :new, status: :unprocessable_entity
    else
      flash.now[:alert] = result.error_message || "荷役作業を記録できません"
      render :new, status: :unprocessable_entity
    end
  end

  private

  def handling_service
    @handling_service ||= Handling::Public::HandlingService.new
  end

  def booking_service
    @booking_service ||= Booking::Public::CargoBookingService.new
  end

  # 追跡番号から予約を特定し荷役履歴を取得する（US15 一覧）。
  def history_for(tracking_number)
    booking = booking_service.find_by_tracking_number(tracking_number)
    booking ? handling_service.history_for(booking.booking_id) : []
  end

  # 経路逸脱の警告文言（正常時は nil）。記録成功だが要注意という半異常を明示する。
  def route_warning(result)
    case result.route_check
    when :misrouted then "荷役は記録しましたが、作業場所が予定ルートと異なります（誤配送の可能性・要確認）"
    when :warning then "荷役は記録しましたが、作業場所が想定港と異なります（要確認）"
    end
  end

  def default_form
    { tracking_number: "", event_type: "RECEIVE", location: "", completion_time: "",
      voyage_number: "", operator_name: "", recipient_name: "", confirmation_code: "" }
  end

  def form_params
    params.permit(:tracking_number, :event_type, :location, :completion_time, :voyage_number,
                  :operator_name, :recipient_name, :confirmation_code)
          .to_h.symbolize_keys.reverse_merge(default_form)
  end

  def use_case_args(form)
    {
      tracking_number: form[:tracking_number], event_type: form[:event_type],
      location: form[:location], completion_time: form[:completion_time].presence,
      voyage_number: form[:voyage_number].presence, operator_name: form[:operator_name].presence,
      recipient: { name: form[:recipient_name].presence, confirmation_code: form[:confirmation_code].presence }
    }
  end
end
