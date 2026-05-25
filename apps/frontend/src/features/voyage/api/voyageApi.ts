export interface CarrierMovement {
  departureUnlocode: string;
  arrivalUnlocode: string;
  departureTime: string;
  arrivalTime: string;
}

export interface Voyage {
  voyageNumber: string;
  carrierCode: string;
  carrierName: string;
  shipName: string;
  originUnlocode: string;
  destUnlocode: string;
  departureDate: string;
  arrivalDate: string;
  status: string;
  movements: CarrierMovement[];
  acceptedCargoTypes: string[];
}

export interface RegisterVoyageRequest {
  voyageNumber: string;
  carrierCode: string;
  carrierName: string;
  shipName: string;
  originUnlocode: string;
  destUnlocode: string;
  departureDate: string;
  arrivalDate: string;
  movements: CarrierMovement[];
  acceptedCargoTypes: string[];
}

export interface UpdateVoyageScheduleRequest {
  departureDate: string;
  arrivalDate: string;
  movements: CarrierMovement[];
  acceptedCargoTypes: string[];
}

function authHeader(): Record<string, string> {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function fetchVoyages(): Promise<Voyage[]> {
  const res = await fetch('/api/v1/voyages', {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('航海スケジュールの取得に失敗しました');
  return res.json();
}

export async function fetchVoyage(voyageNumber: string): Promise<Voyage> {
  const res = await fetch(`/api/v1/voyages/${voyageNumber}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('航海スケジュールの取得に失敗しました');
  return res.json();
}

export async function registerVoyage(req: RegisterVoyageRequest): Promise<void> {
  const res = await fetch('/api/v1/voyages', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '登録に失敗しました' }));
    throw new Error(err.message ?? '登録に失敗しました');
  }
}

export async function updateVoyage(
  voyageNumber: string,
  req: UpdateVoyageScheduleRequest
): Promise<void> {
  const res = await fetch(`/api/v1/voyages/${voyageNumber}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '更新に失敗しました' }));
    throw new Error(err.message ?? '更新に失敗しました');
  }
}

export interface VoyageSearchCriteria {
  origin?: string;
  destination?: string;
  departureFrom?: string; // ISO 8601 date-time
  departureTo?: string; // ISO 8601 date-time
  cargoType?: string;
}

/**
 * 航海スケジュール検索（US07）。
 *
 * <p>指定された条件のみをクエリパラメータに変換して GET する。</p>
 */
export async function searchVoyages(criteria: VoyageSearchCriteria): Promise<Voyage[]> {
  const params = new URLSearchParams();
  if (criteria.origin) params.set('origin', criteria.origin);
  if (criteria.destination) params.set('destination', criteria.destination);
  if (criteria.departureFrom) params.set('departureFrom', criteria.departureFrom);
  if (criteria.departureTo) params.set('departureTo', criteria.departureTo);
  if (criteria.cargoType) params.set('cargoType', criteria.cargoType);
  const res = await fetch(`/api/v1/voyages/search?${params.toString()}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('航海スケジュールの検索に失敗しました');
  return res.json();
}
