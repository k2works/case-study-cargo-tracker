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
  contractNumber: string | null;
  discountRate: ColumnType<string, string | number | undefined, string | number>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** Kysely のルートスキーマ定義。テーブル追加時にここへ登録する */
export interface Database {
  users: UsersTable;
  user_roles: UserRolesTable;
  shipper: ShipperTable;
}
