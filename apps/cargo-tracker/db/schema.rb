# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.0].define(version: 2026_07_28_000010) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pg_catalog.plpgsql"

  create_table "cargos", force: :cascade do |t|
    t.string "booking_id", limit: 20, null: false
    t.bigint "shipper_id", null: false
    t.string "cargo_type", limit: 30, null: false
    t.decimal "weight_kg", precision: 10, scale: 3, null: false
    t.string "origin_unlocode", limit: 5, null: false
    t.string "destination_unlocode", limit: 5, null: false
    t.date "arrival_deadline", null: false
    t.string "booking_status", limit: 30, default: "preliminary", null: false
    t.decimal "dimension_length", precision: 10, scale: 3
    t.decimal "dimension_width", precision: 10, scale: 3
    t.decimal "dimension_height", precision: 10, scale: 3
    t.integer "quantity"
    t.string "description", limit: 500
    t.string "hazardous_class", limit: 10
    t.string "un_number", limit: 10
    t.string "proper_shipping_name", limit: 200
    t.decimal "min_temperature", precision: 10, scale: 3
    t.decimal "max_temperature", precision: 10, scale: 3
    t.string "temperature_unit", limit: 20
    t.integer "lock_version", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["booking_id"], name: "index_cargos_on_booking_id", unique: true
    t.index ["shipper_id"], name: "index_cargos_on_shipper_id"
  end

  create_table "carrier_movements", force: :cascade do |t|
    t.bigint "voyage_id", null: false
    t.string "departure_location_unlocode", limit: 5, null: false
    t.string "arrival_location_unlocode", limit: 5, null: false
    t.datetime "departure_date", null: false
    t.datetime "arrival_date", null: false
    t.integer "seq_number", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["voyage_id", "seq_number"], name: "index_carrier_movements_on_voyage_id_and_seq_number", unique: true
    t.index ["voyage_id"], name: "index_carrier_movements_on_voyage_id"
  end

  create_table "legs", force: :cascade do |t|
    t.bigint "cargo_id", null: false
    t.string "voyage_number", limit: 30, null: false
    t.string "load_location_unlocode", limit: 5, null: false
    t.string "unload_location_unlocode", limit: 5, null: false
    t.datetime "load_time"
    t.datetime "unload_time"
    t.integer "seq_number", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["cargo_id", "seq_number"], name: "index_legs_on_cargo_id_and_seq_number", unique: true
    t.index ["cargo_id"], name: "index_legs_on_cargo_id"
  end

  create_table "locations", force: :cascade do |t|
    t.string "unlocode", limit: 5, null: false
    t.string "name", limit: 100, null: false
    t.string "country_code", limit: 2
    t.string "time_zone", limit: 50
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["unlocode"], name: "index_locations_on_unlocode", unique: true
  end

  create_table "notifications", force: :cascade do |t|
    t.string "notifiable_type", limit: 100, null: false
    t.bigint "notifiable_id", null: false
    t.string "event_type", limit: 50, null: false
    t.string "recipient_type", limit: 30, null: false
    t.string "recipient_address", limit: 200, null: false
    t.string "subject", limit: 200
    t.text "body"
    t.string "status", limit: 20, default: "pending", null: false
    t.datetime "sent_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["notifiable_type", "notifiable_id", "event_type"], name: "idx_on_notifiable_type_notifiable_id_event_type_7613f3bbd6"
  end

  create_table "shippers", force: :cascade do |t|
    t.string "shipper_code", limit: 20, null: false
    t.string "shipper_type", limit: 20, null: false
    t.string "name", limit: 200, null: false
    t.string "address", limit: 500
    t.string "email", limit: 200, null: false
    t.string "phone", limit: 50
    t.string "contract_number", limit: 50
    t.decimal "discount_rate", precision: 5, scale: 4, default: "0.0"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_shippers_on_email", unique: true
    t.index ["shipper_code"], name: "index_shippers_on_shipper_code", unique: true
  end

  create_table "user_roles", force: :cascade do |t|
    t.bigint "user_id", null: false
    t.string "role", limit: 50, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["user_id", "role"], name: "index_user_roles_on_user_id_and_role", unique: true
    t.index ["user_id"], name: "index_user_roles_on_user_id"
  end

  create_table "users", force: :cascade do |t|
    t.string "username", limit: 50, null: false
    t.string "email", limit: 200, null: false
    t.string "password_digest", limit: 255, null: false
    t.boolean "enabled", default: true, null: false
    t.integer "failed_attempts", default: 0, null: false
    t.datetime "locked_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_users_on_email", unique: true
    t.index ["username"], name: "index_users_on_username", unique: true
  end

  create_table "voyages", force: :cascade do |t|
    t.string "voyage_number", limit: 20, null: false
    t.string "carrier_name", limit: 100, null: false
    t.string "ship_name", limit: 100
    t.string "supported_cargo_types", limit: 100, default: "GENERAL", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["voyage_number"], name: "index_voyages_on_voyage_number", unique: true
  end

  add_foreign_key "cargos", "shippers"
  add_foreign_key "carrier_movements", "voyages"
  add_foreign_key "legs", "cargos"
  add_foreign_key "user_roles", "users"
end
