import cargotracker.auth.interfaces.web.AuthFilter
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import play.filters.hosts.AllowedHostsFilter

import javax.inject.Inject

/** Play HTTP フィルター登録（CSRF・SecurityHeaders・AllowedHosts に AuthFilter を追加）。 */
class Filters @Inject() (
    csrfFilter: CSRFFilter,
    securityHeadersFilter: SecurityHeadersFilter,
    allowedHostsFilter: AllowedHostsFilter,
    authFilter: AuthFilter
) extends HttpFilters:
  override val filters: Seq[EssentialFilter] = Seq(
    csrfFilter,
    securityHeadersFilter,
    allowedHostsFilter,
    authFilter
  )
