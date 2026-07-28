import type { Generated, ColumnType } from 'kysely';

/**
 * Kysely スキーマ型。CamelCasePlugin を用いるため、フィールドは camelCase で宣言する。
 * 実行時に PostgreSQL の snake_case カラムへ自動変換される。
 */

/** 監査タイムスタンプ（挿入・更新時は省略可） */
type Timestamp = ColumnType<Date, Date | string | undefined, Date | string | undefined>;

export interface UsersTable {
  id: Generated<number>;
  username: string;
  email: string;
  password: string;
  enabled: ColumnType<boolean, boolean | undefined, boolean>;
  failedLoginAttempts: ColumnType<number, number | undefined, number>;
  createdAt: Timestamp;
}

export interface UserRolesTable {
  userId: number;
  role: string;
}

export interface ShipperTable {
  id: Generated<number>;
  shipperCode: string;
  shipperType: string;
  name: string;
  email: string;
  phone: string | null;
  address: string | null;
  contractNumber: string | null;
  discountRate: ColumnType<string, string | number | undefined, string | number>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** 数値カラム（NUMERIC）は pg ドライバが文字列で返すため string で受ける */
type Numeric = ColumnType<string, string | number, string | number>;

export interface LocationTable {
  id: Generated<number>;
  unlocode: string;
  name: string;
  countryCode: string | null;
  timeZone: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface EstimateTable {
  id: Generated<number>;
  estimateId: string;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: ColumnType<Date, Date | string, Date | string>;
  cargoType: string;
  weightKg: Numeric;
  status: ColumnType<string, string | undefined, string>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface RouteCandidateTable {
  id: Generated<number>;
  estimateId: number;
  voyageNumber: string;
  transitPort: string | null;
  transitDays: number;
  estimatedCost: Numeric;
  rank: ColumnType<number, number | undefined, number>;
}

export interface CargoTable {
  id: Generated<number>;
  bookingId: string;
  shipperId: number;
  cargoType: ColumnType<string, string | undefined, string>;
  weight: Numeric;
  originUnlocode: string;
  destinationUnlocode: string;
  arrivalDeadline: ColumnType<Date, Date | string, Date | string>;
  bookingStatus: ColumnType<string, string | undefined, string>;
  consigneeName: string | null;
  consigneeEmail: string | null;
  consigneeAddress: string | null;
  dimensionLength: Numeric | null;
  dimensionWidth: Numeric | null;
  dimensionHeight: Numeric | null;
  quantity: number | null;
  description: string | null;
  hazardousClass: string | null;
  unNumber: string | null;
  properShippingName: string | null;
  minTemperature: Numeric | null;
  maxTemperature: Numeric | null;
  temperatureUnit: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** Kysely のルートスキーマ定義。テーブル追加時にここへ登録する */
export interface Database {
  users: UsersTable;
  user_roles: UserRolesTable;
  shipper: ShipperTable;
  location: LocationTable;
  estimate: EstimateTable;
  route_candidate: RouteCandidateTable;
  cargo: CargoTable;
}
