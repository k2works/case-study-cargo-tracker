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
/** NULL 許容のタイムスタンプカラム（未設定は null） */
type NullableTimestamp = ColumnType<Date, Date | string | null, Date | string | null> | null;

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
  trackingNumber: string | null;
  routingStatus: ColumnType<string, string | undefined, string>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface LegTable {
  id: Generated<number>;
  cargoId: number;
  voyageNumber: string;
  loadLocationUnlocode: string;
  unloadLocationUnlocode: string;
  loadTime: ColumnType<Date, Date | string, Date | string>;
  unloadTime: ColumnType<Date, Date | string, Date | string>;
  seqNumber: number;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface NotificationRecordTable {
  id: Generated<number>;
  bookingId: string;
  notificationType: string;
  recipient: string;
  sentAt: Timestamp;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface VoyageTable {
  id: Generated<number>;
  voyageNumber: string;
  shipName: string;
  carrierName: string;
  supportedCargoTypes: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface CarrierMovementTable {
  id: Generated<number>;
  voyageId: number;
  departureLocationUnlocode: string;
  arrivalLocationUnlocode: string;
  departureDate: ColumnType<Date, Date | string, Date | string>;
  arrivalDate: ColumnType<Date, Date | string, Date | string>;
  seqNumber: number;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** Kysely のルートスキーマ定義。テーブル追加時にここへ登録する */
export interface HandlingActivityTable {
  id: Generated<number>;
  bookingId: string;
  eventType: string;
  eventCompletionTime: ColumnType<Date, Date | string, Date | string>;
  locationUnlocode: string;
  voyageNumber: string | null;
  operatorName: string | null;
  consigneeConfirmation: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface CustomsDeclarationTable {
  id: Generated<number>;
  handlingActivityId: number;
  declarationNumber: string;
  declaredAt: ColumnType<Date, Date | string, Date | string>;
  status: string;
  clearedAt: NullableTimestamp;
  remarks: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface TrackingActivityTable {
  id: Generated<number>;
  trackingNumber: string;
  bookingId: string;
  transportStatus: ColumnType<string, string | undefined, string>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface TrackingHandlingEventTable {
  id: Generated<number>;
  trackingId: number;
  eventType: string;
  eventTime: ColumnType<Date, Date | string, Date | string>;
  locationUnlocode: string | null;
  voyageNumber: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface TrackingExceptionEventTable {
  id: Generated<number>;
  trackingId: number;
  exceptionType: string;
  occurredAt: ColumnType<Date, Date | string, Date | string>;
  escalationFlag: ColumnType<boolean, boolean | undefined, boolean>;
  locationUnlocode: string | null;
  description: string | null;
  statusBeforeException: string;
  resolvedAt: NullableTimestamp;
  resolutionNotes: string | null;
  reportedAt: NullableTimestamp;
  newEstimatedArrival: NullableTimestamp;
  reportNotes: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface Database {
  users: UsersTable;
  user_roles: UserRolesTable;
  shipper: ShipperTable;
  location: LocationTable;
  estimate: EstimateTable;
  route_candidate: RouteCandidateTable;
  cargo: CargoTable;
  leg: LegTable;
  notification_record: NotificationRecordTable;
  voyage: VoyageTable;
  carrier_movement: CarrierMovementTable;
  handling_activity: HandlingActivityTable;
  customs_declaration: CustomsDeclarationTable;
  tracking_activity: TrackingActivityTable;
  tracking_handling_event: TrackingHandlingEventTable;
  tracking_exception_event: TrackingExceptionEventTable;
}
