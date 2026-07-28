Rails.application.routes.draw do
  # 認証（US26/US27）
  get    "login",  to: "sessions#new"
  post   "login",  to: "sessions#create"
  delete "logout", to: "sessions#destroy"

  # 荷主登録（US02/US03 / Shipper Context）
  resources :shippers, only: %i[index new create]

  # --- 以下はウォーキングスケルトンのプレースホルダ画面（画面遷移図に沿った全ルート） ---

  # 貨物予約（Booking Context / US04・US05・US06）
  resources :bookings, only: %i[index new create show] do
    member do
      post :assign_routing
      post :notify_route
      post :confirm
      post :cancel
      post :reroute
    end
  end
  get   "bookings/:booking_id/route/edit", to: "bookings/routes#edit", as: :edit_booking_route
  patch "bookings/:booking_id/route",      to: "bookings/routes#update", as: :booking_route
  post  "bookings/:booking_id/route/consultation", to: "bookings/routes#request_consultation",
        as: :booking_route_consultation

  # 見積（Estimation Context）
  resources :estimates, only: %i[index new show]

  # 追跡（Tracking Context・認証あり）
  get "tracking", to: "trackings#new", as: :tracking
  get "tracking/:tracking_number", to: "trackings#show", as: :tracking_detail

  # 荷役（Handling Context）
  resources :handling_events, only: %i[index new]

  # 航路（Routing Context / US24・US25・US07）
  resources :voyages, only: %i[index new create show edit update] do
    member do
      post :confirm_update
    end
  end

  # 例外管理（Tracking Context）
  resources :exceptions, only: %i[index new]

  # 精算（Billing Context）
  namespace :billing do
    resources :invoices, only: %i[index show]
  end

  # 管理（割引ポリシー）
  namespace :admin do
    resources :discount_policies, only: %i[index new edit]
  end

  # 公開追跡（認証不要 / 未認証ユーザーの入口）
  namespace :public do
    get "tracking", to: "trackings#new", as: :tracking
    get "tracking/:tracking_id", to: "trackings#show", as: :tracking_detail
  end

  # ロール別ダッシュボード
  root "dashboard#show"

  # Reveal health status on /up that returns 200 if the app boots with no exceptions, otherwise 500.
  get "up" => "rails/health#show", as: :rails_health_check
end
