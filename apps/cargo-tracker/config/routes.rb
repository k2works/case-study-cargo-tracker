Rails.application.routes.draw do
  # 認証（US26/US27）
  get    "login",  to: "sessions#new"
  post   "login",  to: "sessions#create"
  delete "logout", to: "sessions#destroy"

  # 荷主登録（US02/US03 / Shipper Context）
  resources :shippers, only: %i[index new create]

  # ロール別ダッシュボード
  root "dashboard#show"

  # Reveal health status on /up that returns 200 if the app boots with no exceptions, otherwise 500.
  get "up" => "rails/health#show", as: :rails_health_check
end
