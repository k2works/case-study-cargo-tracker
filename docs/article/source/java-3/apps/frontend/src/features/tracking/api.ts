import { API_PATHS } from "../../config/api";
import { apiClient } from "../../lib/api-client";
import type {
  ExceptionTypeChoice,
  ManagedTracking,
  ManualUpdateRequest,
  OpenExceptionSummary,
  PublicTracking,
  RaiseExceptionRequest,
  ResolveExceptionRequest,
  ShipperTrackingDetail,
  ShipperTrackingList,
  TrackingStatusChoice,
} from "./types";

/**
 * 公開の追跡照会（US18・認証不要）。
 *
 * **業務 API と接頭辞を分ける**（`/api/v1/public/...`）。公開範囲が一目で分かる形にして
 * おかないと、あとから「これは認証が要るのか」を毎回読み解くことになる。
 */
export function fetchPublicTracking(
  trackingNumber: string,
): Promise<PublicTracking> {
  return apiClient.get<PublicTracking>(
    API_PATHS.publicTracking(encodeURIComponent(trackingNumber)),
  );
}

/** 追跡管理者が 1 件を開く（US17-1）。 */
export function fetchManagedTracking(
  trackingNumber: string,
): Promise<ManagedTracking> {
  return apiClient.get<ManagedTracking>(
    `${API_PATHS.trackingManagement}/${encodeURIComponent(trackingNumber)}`,
  );
}

/** 荷主がログインして、自社貨物だけを一覧で見る（US33）。 */
export function fetchShipperTracking(): Promise<ShipperTrackingList> {
  return apiClient.get<ShipperTrackingList>(API_PATHS.shipperTracking);
}

/** 荷主向け詳細。自社貨物でなければサーバが 404 にする（US33-2）。 */
export function fetchShipperTrackingDetail(
  trackingNumber: string,
): Promise<ShipperTrackingDetail> {
  return apiClient.get<ShipperTrackingDetail>(
    API_PATHS.shipperTrackingDetail(trackingNumber),
  );
}

/**
 * 起票できる例外の種別（[ADR-024] 決定 11）。
 *
 * **画面が一覧を持たない。** `MISROUTE` は US28 が自動で、`CUSTOMS_HOLD` は US29 が
 * 起票する。画面に書くと、自動検知の側を足した日に選択肢だけが残る。
 */
export function fetchExceptionTypes(): Promise<ExceptionTypeChoice[]> {
  return apiClient.get<ExceptionTypeChoice[]>(
    `${API_PATHS.trackingManagement}/exception-types`,
  );
}

/**
 * その貨物から手で進められる状態（US17-2）。
 *
 * **進める先だけを返す。** 戻る向きの選択肢を出しておいて 409 で断るのは、押せるのに
 * 断られる操作を出すことである。判定はサーバの `canAdvanceTo` 1 つに置く。
 */
export function fetchAdvanceableStatuses(
  trackingNumber: string,
): Promise<TrackingStatusChoice[]> {
  return apiClient.get<TrackingStatusChoice[]>(
    `${API_PATHS.trackingManagement}/${encodeURIComponent(trackingNumber)}/statuses`,
  );
}

/** 状態を手で更新する（US17-2）。**戻る向きは 409 で断られる**（[ADR-024] 決定 1）。 */
export function updateTrackingStatus(
  request: ManualUpdateRequest,
): Promise<ManagedTracking> {
  return apiClient.post<ManagedTracking>(API_PATHS.trackingManagement, request);
}

/** 例外を起票する（US19-1・US20-1）。 */
export function raiseTrackingException(
  request: RaiseExceptionRequest,
): Promise<ManagedTracking> {
  return apiClient.post<ManagedTracking>(
    `${API_PATHS.trackingManagement}/exceptions`,
    request,
  );
}

/** 例外を解決する（US19-4）。**発生前の状態に戻る**（[ADR-024] 決定 2）。 */
export function resolveTrackingException(
  request: ResolveExceptionRequest,
): Promise<ManagedTracking> {
  return apiClient.post<ManagedTracking>(
    `${API_PATHS.trackingManagement}/exceptions/${request.exceptionId}/resolve`,
    request,
  );
}

/**
 * 未解決例外の件数（横断規約）。
 *
 * **件数を出すだけにしない。** 気づく手段は次の行動へ繋ぐ——ここから一覧へ辿れる。
 */
export function fetchOpenExceptions(): Promise<OpenExceptionSummary> {
  return apiClient.get<OpenExceptionSummary>(
    `${API_PATHS.trackingManagement}/exceptions/open`,
  );
}

/** 未解決の例外がある貨物の一覧。件数の遷移先である。 */
export function fetchOpenExceptionList(): Promise<ManagedTracking[]> {
  return apiClient.get<ManagedTracking[]>(
    `${API_PATHS.trackingManagement}/exceptions`,
  );
}
