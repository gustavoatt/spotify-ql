package dev.gustavoatt.spotifyql

import dev.gustavoatt.spotifyql.api.{SpotifyAPI, UserInfo}
import org.scalatra._

class SpotifyQl(private val spotifyClientId: String, private val spotifyClientSecret: String)
    extends ScalatraServlet {
  private lazy val SPOTIFY_REDIRECT_URL: String = fullUrl("/auth_callback")
  private val SESSION_USER_PROFILE_KEY: String  = "spotify_use_profile"

  private lazy val spotifyApi: SpotifyAPI =
    new SpotifyAPI(clientId = spotifyClientId, clientSecret = spotifyClientSecret)

  get("/") {
    if (!session.contains(SESSION_USER_PROFILE_KEY)) {
      redirect(url("/login"))
    }

    session.get(SESSION_USER_PROFILE_KEY) match {
      case Some(info: UserInfo) =>
        val currentTrack = spotifyApi.getCurrentlyPlayingTrack(info)
        val recommended  = spotifyApi.getRecommendedTracks(info, Seq(currentTrack.item.id))
        views.html.home(redirectUrl = url("/login"), currentTrack = recommended.tracks.get(0))
      case Some(_) => NotFound()
      case None    => NotFound()
    }
  }

  get("/login") {
    redirect(spotifyApi.authorizeURL(redirectUrl = SPOTIFY_REDIRECT_URL))
  }

  get("/auth_callback") {
    val userAuth = spotifyApi.authorizeUser(request, redirectUrl = SPOTIFY_REDIRECT_URL)
    if (userAuth.isDefined) {
      session.setAttribute(SESSION_USER_PROFILE_KEY, userAuth.get)
    }

    redirect(url("/"))
  }
}
