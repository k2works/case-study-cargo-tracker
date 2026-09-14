import type { Role } from './roles';
import { useAuthStore } from './authStore';

/**
 * いま見ている人がそのロールを持つか。
 *
 * <p><b>画面ごとに `?? false` を書かない。</b> 1 つの画面で 4 つも 5 つも
 * 役割を見ると、そのたびに分岐が増えて「どの条件で何が出るのか」が読めなく
 * なる（S22 で実際に上限に触れた）。<b>これは表示の出し分けで、守りは
 * Gateway の認可が担う</b>（[ADR-0006]）。</p>
 */
export function useRole(role: Role): boolean {
  return useAuthStore((state) => state.user?.roles.includes(role) ?? false);
}
