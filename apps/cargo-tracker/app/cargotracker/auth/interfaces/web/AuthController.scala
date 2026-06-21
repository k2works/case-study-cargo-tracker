package cargotracker.auth.interfaces.web

import cargotracker.auth.domain.model.repositories.UserRepository
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}

/** ログイン・ログアウトのコントローラ。 */
@Singleton
class AuthController @Inject() (
    cc: ControllerComponents,
    userRepository: UserRepository,
    clock: Clock
) extends AbstractController(cc)
    with I18nSupport:

  private case class LoginForm(username: String, password: String)

  private val loginForm: Form[LoginForm] = Form(
    mapping(
      "username" -> nonEmptyText(maxLength = 50),
      "password" -> nonEmptyText(maxLength = 100)
    )(LoginForm.apply)(lf => Some((lf.username, lf.password)))
  )

  /** ログインフォーム表示 */
  def loginPage(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.auth.login(loginForm, errorMessage = None))
  }

  /** ログイン実行（PRG） */
  def login(): Action[AnyContent] = Action { implicit request =>
    loginForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.auth
              .login(formWithErrors, errorMessage = Some("入力内容を確認してください"))
          ),
        data =>
          userRepository.findByUsername(data.username) match
            case Some(user) if user.authenticate(data.password) =>
              val rolesStr = user.roles.map(_.toString).mkString(",")
              Redirect("/")
                .withSession(
                  "username" -> user.username,
                  "roles" -> rolesStr,
                  "lastAccessedAt" -> Instant.now(clock).toString
                )
            case _ =>
              Unauthorized(
                views.html.auth.login(
                  loginForm,
                  errorMessage = Some("ユーザー名またはパスワードが正しくありません")
                )
              )
      )
  }

  /** ログアウト */
  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect("/login").withNewSession
  }
