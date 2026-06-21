package cargotracker.auth.interfaces.web

import cargotracker.auth.application.commandservices.AuthCommandService
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import javax.inject.{Inject, Singleton}

/** ログイン・ログアウトのコントローラ。
  *
  * 認証ロジック（資格情報照合・セッション情報組み立て）は [[AuthCommandService]] に委譲し、 ここでは入力バインディングと Play セッション Cookie の発行のみを行う。
  */
@Singleton
class AuthController @Inject() (
    cc: ControllerComponents,
    authCommandService: AuthCommandService
) extends AbstractController(cc)
    with I18nSupport:

  private case class LoginForm(username: String, password: String)

  private val loginForm: Form[LoginForm] = Form(
    mapping(
      "username" -> nonEmptyText(maxLength = 50),
      "password" -> nonEmptyText(maxLength = 100)
    )(LoginForm.apply)(lf => Some((lf.username, lf.password)))
  )

  def loginPage(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.auth.login(loginForm, errorMessage = None))
  }

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
          authCommandService.authenticate(data.username, data.password) match
            case Right(session) =>
              Redirect("/").withSession(
                "username" -> session.username,
                "roles" -> session.rolesCsv,
                "lastAccessedAt" -> session.lastAccessedAt.toString
              )
            case Left(AuthCommandService.AuthError.InvalidCredentials) =>
              Unauthorized(
                views.html.auth.login(
                  loginForm,
                  errorMessage = Some("ユーザー名またはパスワードが正しくありません")
                )
              )
      )
  }

  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect("/login").withNewSession
  }
