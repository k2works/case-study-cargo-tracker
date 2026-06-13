package cargotracker.shared.interfaces.web

import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class HomeController @Inject() (cc: ControllerComponents) extends AbstractController(cc):

  def index(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.index())
  }
