package dev.gustavoatt.spotifyql.api

import java.nio.charset.StandardCharsets
import java.time.temporal.ChronoUnit
import java.util.Base64

import com.google.gson.reflect.TypeToken
import com.google.gson.{FieldNamingPolicy, Gson, GsonBuilder}
import javax.servlet.http.HttpServletRequest
import okhttp3.{FormBody, HttpUrl, OkHttpClient, Request}
import scala.collection.JavaConverters._
import scala.reflect._

/**
  * Handles connections to the Spotify API for different methods.
  *
  * TODO(gustavoatt): Handle errors in the response (not on the HTTP server).
  */
class SpotifyAPI(private val clientId: String, private val clientSecret: String) {
  private val ACCOUNTS_DOMAIN: String = "accounts.spotify.com"
  private val API_DOMAIN              = "api.spotify.com"

  private val client: OkHttpClient = new OkHttpClient()
  private val gson: Gson =
    new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

  // Private representation of API Auth Token response.
  private case class ApiAuthTokenResponse(accessToken: String,
                                          tokenType: String,
                                          expiresIn: Int,
                                          refreshToken: String,
                                          scope: String)

  private def getApiResource(userInfo: UserInfo,
                             path: String,
                             queryParameters: Map[String, String]): String = {
    val url = new HttpUrl.Builder()
      .scheme("https")
      .host(API_DOMAIN)
      .addPathSegments(path)
    queryParameters.foreach(keyVal => url.addQueryParameter(keyVal._1, keyVal._2))

    val request = new Request.Builder()
      .url(url.build())
      .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
      .get()
      .build()

    var response: okhttp3.Response = null
    try {
      response = client.newCall(request).execute()
      response.body().string()
    } finally {
      Option(response).foreach(_.close())
    }
  }

  /**
    * Gets authorize URL to be sent to Spotify for getting permissions. Users should be redirected here as the first step
    * of the Oauth 2.0 flow.
    * @param redirectUrl url where Spotify should redirect once permissions have been granted. This should match a URL
    *                    given to the Spotify app.
    */
  def authorizeURL(redirectUrl: String): String = {
    new HttpUrl.Builder()
      .scheme("https")
      .host(ACCOUNTS_DOMAIN)
      .addPathSegment("authorize")
      .addQueryParameter("client_id", clientId)
      .addQueryParameter("response_type", "code")
      .addQueryParameter("redirect_uri", redirectUrl)
      .addQueryParameter(
        "scope",
        "user-read-private,user-read-email,user-library-read,user-read-playback-state,user-read-currently-playing")
      .build()
      .toString
  }

  /**
    * Call Spotify API to authorize an user and get authorization info.
    */
  def authorizeUser(callbackRequest: HttpServletRequest, redirectUrl: String): Option[UserInfo] = {
    val error = callbackRequest.getParameter("error")
    if (error != null) {
      return None
    }
    val code = callbackRequest.getParameter("code")

    val authTokenUrl = new HttpUrl.Builder()
      .scheme("https")
      .host(ACCOUNTS_DOMAIN)
      .addPathSegment("api")
      .addPathSegment("token")
      .build()
    val encodedClientInfo = Base64.getEncoder.encodeToString(
      s"$clientId:$clientSecret".getBytes(StandardCharsets.UTF_8)
    )

    val request = new Request.Builder()
      .url(authTokenUrl)
      .header("Authorization", s"Basic $encodedClientInfo")
      .post(
        new FormBody.Builder()
          .add("grant_type", "authorization_code")
          .add("code", code)
          .add("redirect_uri", redirectUrl)
          .build())
      .build()

    var response: okhttp3.Response = null
    try {
      response = client.newCall(request).execute()
      val authTokenResponse = gson.fromJson(response.body().string(), classOf[ApiAuthTokenResponse])

      Some(
        UserInfo(
          code = code,
          accessToken = authTokenResponse.accessToken,
          tokenType = authTokenResponse.tokenType,
          tokenExpirationTime =
            java.time.Instant.now().plus(authTokenResponse.expiresIn, ChronoUnit.SECONDS),
          refreshToken = authTokenResponse.refreshToken,
          scopes = authTokenResponse.scope.split(',')
        ))
    } finally {
      if (response != null) {
        response.close()
      }
    }
  }

  def expandPage[Item: ClassTag](userInfo: UserInfo, paging: Paging[Item]): Seq[Item] = {
    paging match {
      case Paging(_, items, _, null, _, _, _) => items.asScala
      case Paging(_, items, _, next: String, _, _, _) if next != null =>
        val request = new Request.Builder()
          .url(next)
          .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
          .build()

        var response: okhttp3.Response = null
        try {
          response = client.newCall(request).execute()
          items.asScala ++ expandPage(
            userInfo,
            gson.fromJson(response.body().string(),
                          TypeToken
                            .getParameterized(classOf[Paging[Item]], classTag[Item].runtimeClass)
                            .getType))
        } finally {
          Option(response).map(_.close)
        }
    }
  }

  def getUserProfile(userInfo: UserInfo): String = {
    getApiResource(userInfo = userInfo, path = "v1/me", queryParameters = Map())
  }

  def getCurrentlyPlayingTrack(userInfo: UserInfo): CurrentlyPlaying = {
    gson.fromJson(getApiResource(userInfo = userInfo,
                                 path = "v1/me/player/currently-playing",
                                 queryParameters = Map()),
                  classOf[CurrentlyPlaying])
  }

  def getUserTracks(userInfo: UserInfo): Paging[SavedTrack] = {
    gson.fromJson(
      getApiResource(userInfo = userInfo, path = "v1/me/tracks", queryParameters = Map()),
      TypeToken.getParameterized(classOf[Paging[SavedTrack]], classOf[SavedTrack]).getType
    )
  }

  def getRecommendedTracks(userInfo: UserInfo, tracks: Seq[String]): RecomendationResponse = {
    gson.fromJson(getApiResource(userInfo = userInfo,
                                 path = "v1/recommendations",
                                 queryParameters = Map("seed_tracks" -> tracks.mkString(","))),
                  classOf[RecomendationResponse])
  }

  def getAlbums(userInfo: UserInfo, albumIds: Seq[String]): Albums = {
    gson.fromJson(getApiResource(userInfo = userInfo,
                                 path = "v1/albums",
                                 queryParameters = Map("ids" -> albumIds.mkString(","))),
                  classOf[Albums])
  }

  def getArtists(userInfo: UserInfo, artistIds: Seq[String]): Artists = {
    gson.fromJson(getApiResource(userInfo = userInfo,
                                 path = "v1/artists",
                                 queryParameters = Map("ids" -> artistIds.mkString(","))),
                  classOf[Artists])
  }
}
