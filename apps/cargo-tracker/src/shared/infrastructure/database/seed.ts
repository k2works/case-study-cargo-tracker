import { ALL_ROLES, type Role } from '../../domain/model/role.js';
import { BcryptPasswordVerifier } from '../auth/bcrypt-password-verifier.js';
import type { AppDatabase } from './database.js';

/** 各ロールのデフォルトテストユーザー（username = ロール小文字, password 共通） */
const DEFAULT_PASSWORD = 'password';

const SEED_USERS: { username: string; roles: Role[] }[] = ALL_ROLES.map((role) => ({
  username: role.replace('ROLE_', '').toLowerCase(),
  roles: [role],
}));

/**
 * 開発・デモ用のシードデータを投入する。
 * 既にユーザーが存在する場合は何もしない（冪等）。
 */
export async function seedDefaultUsers(db: AppDatabase): Promise<void> {
  const existing = await db.selectFrom('users').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  const hash = await BcryptPasswordVerifier.hash(DEFAULT_PASSWORD);
  for (const seed of SEED_USERS) {
    const user = await db
      .insertInto('users')
      .values({
        username: seed.username,
        email: `${seed.username}@example.com`,
        password: hash,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    await db
      .insertInto('user_roles')
      .values(seed.roles.map((role) => ({ userId: user.id, role })))
      .execute();
  }
}

/** 主要 UN/LOCODE マスタ（見積・予約の出発地/目的地で使用） */
const SEED_LOCATIONS: { unlocode: string; name: string; countryCode: string }[] = [
  { unlocode: 'JPTYO', name: 'Tokyo', countryCode: 'JP' },
  { unlocode: 'JPOSA', name: 'Osaka', countryCode: 'JP' },
  { unlocode: 'JPYOK', name: 'Yokohama', countryCode: 'JP' },
  { unlocode: 'USLAX', name: 'Los Angeles', countryCode: 'US' },
  { unlocode: 'USNYC', name: 'New York', countryCode: 'US' },
  { unlocode: 'SGSIN', name: 'Singapore', countryCode: 'SG' },
  { unlocode: 'CNSHA', name: 'Shanghai', countryCode: 'CN' },
  { unlocode: 'NLRTM', name: 'Rotterdam', countryCode: 'NL' },
  { unlocode: 'DEHAM', name: 'Hamburg', countryCode: 'DE' },
  { unlocode: 'HKHKG', name: 'Hong Kong', countryCode: 'HK' },
];

/**
 * location マスタを投入する（冪等）。
 */
export async function seedLocations(db: AppDatabase): Promise<void> {
  const existing = await db.selectFrom('location').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  await db.insertInto('location').values(SEED_LOCATIONS).execute();
}

/** デモ用荷主（個人・法人）。予約時に shipperCode で選択する（US02/US03/US04） */
const SEED_SHIPPERS: {
  shipperCode: string;
  shipperType: 'INDIVIDUAL' | 'CORPORATE';
  name: string;
  email: string;
  phone: string;
  address: string;
  contractNumber: string | null;
  discountRate: number;
}[] = [
  {
    shipperCode: 'SHP-DEMO0001',
    shipperType: 'INDIVIDUAL',
    name: '山田 太郎',
    email: 'yamada@example.com',
    phone: '03-1000-0001',
    address: '東京都千代田区丸の内 1-1-1',
    contractNumber: null,
    discountRate: 0,
  },
  {
    shipperCode: 'SHP-DEMO0002',
    shipperType: 'INDIVIDUAL',
    name: '佐藤 花子',
    email: 'sato@example.com',
    phone: '06-1000-0002',
    address: '大阪府大阪市北区梅田 2-2-2',
    contractNumber: null,
    discountRate: 0,
  },
  {
    shipperCode: 'SHP-DEMO0003',
    shipperType: 'CORPORATE',
    name: '株式会社アクメ商事',
    email: 'contact@acme.example.com',
    phone: '03-2000-0003',
    address: '東京都港区海岸 3-3-3',
    contractNumber: 'CT-2026-0003',
    discountRate: 0.15,
  },
  {
    shipperCode: 'SHP-DEMO0004',
    shipperType: 'CORPORATE',
    name: 'グローバルロジ株式会社',
    email: 'sales@globallogi.example.com',
    phone: '045-2000-0004',
    address: '神奈川県横浜市西区みなとみらい 4-4-4',
    contractNumber: 'CT-2026-0004',
    discountRate: 0.3,
  },
];

/**
 * デモ用荷主マスタを投入する（冪等）。
 * 個人 2 件（割引なし）・法人 2 件（割引率 15% / 30%）で法人割引（US22）を確認できる。
 */
export async function seedShippers(db: AppDatabase): Promise<void> {
  const existing = await db.selectFrom('shipper').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  await db.insertInto('shipper').values(SEED_SHIPPERS).execute();
}

/**
 * デモ用航海スケジュールを投入する（冪等）。
 * 経路候補算出（US08）で「直行」「1 寄港接続」の両パターンを提示でき、
 * 到着日は now 基準の相対日付にして予約期限を十分に取れば期限内候補として算出される。
 * 荷役・追跡・精算まで一連の業務フローを回すための起点となる。
 */
export async function seedVoyages(db: AppDatabase, now: Date = new Date()): Promise<void> {
  const existing = await db.selectFrom('voyage').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  const day = (offset: number): Date => {
    const d = new Date(now);
    d.setDate(d.getDate() + offset);
    d.setHours(9, 0, 0, 0);
    return d;
  };
  // 直行便: JPTYO -> USLAX（GENERAL / REFRIGERATED 対応）
  const direct = await db
    .insertInto('voyage')
    .values({
      voyageNumber: 'V-DEMO-001',
      shipName: 'Pacific Star',
      carrierName: 'Oceanic Lines',
      supportedCargoTypes: 'GENERAL,REFRIGERATED',
    })
    .returning('id')
    .executeTakeFirstOrThrow();
  await db
    .insertInto('carrier_movement')
    .values({
      voyageId: direct.id,
      departureLocationUnlocode: 'JPTYO',
      arrivalLocationUnlocode: 'USLAX',
      departureDate: day(3),
      arrivalDate: day(17),
      seqNumber: 1,
    })
    .execute();
  // 経由便: JPTYO -> HKHKG -> SGSIN（GENERAL / HAZARDOUS 対応）
  const transit = await db
    .insertInto('voyage')
    .values({
      voyageNumber: 'V-DEMO-002',
      shipName: 'Asia Bridge',
      carrierName: 'Orient Carrier',
      supportedCargoTypes: 'GENERAL,HAZARDOUS',
    })
    .returning('id')
    .executeTakeFirstOrThrow();
  await db
    .insertInto('carrier_movement')
    .values([
      {
        voyageId: transit.id,
        departureLocationUnlocode: 'JPTYO',
        arrivalLocationUnlocode: 'HKHKG',
        departureDate: day(2),
        arrivalDate: day(6),
        seqNumber: 1,
      },
      {
        voyageId: transit.id,
        departureLocationUnlocode: 'HKHKG',
        arrivalLocationUnlocode: 'SGSIN',
        departureDate: day(8),
        arrivalDate: day(12),
        seqNumber: 2,
      },
    ])
    .execute();
}

/**
 * 業務フロー（予約 → 経路 → 荷役 → 追跡 → 精算）を手動実行するための
 * 全シードデータをまとめて投入する（各関数は冪等）。
 * アプリ起動時（pg-mem）とシードコマンド（実 DB）の双方から利用する。
 */
export async function seedAll(db: AppDatabase, now: Date = new Date()): Promise<void> {
  await seedDefaultUsers(db);
  await seedLocations(db);
  await seedShippers(db);
  await seedVoyages(db, now);
}
