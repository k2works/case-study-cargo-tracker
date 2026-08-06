# frozen_string_literal: true

FactoryBot.define do
  factory :user do
    sequence(:username) { |n| "user#{n}" }
    sequence(:email) { |n| "user#{n}@example.com" }
    password { "password123" }
    enabled { true }

    trait :sales do
      after(:create) { |u| u.user_roles.create!(role: "sales") }
    end

    trait :admin do
      after(:create) { |u| u.user_roles.create!(role: "admin") }
    end

    trait :locked do
      failed_attempts { 5 }
      locked_at { Time.current }
    end

    trait :disabled do
      enabled { false }
    end
  end
end
