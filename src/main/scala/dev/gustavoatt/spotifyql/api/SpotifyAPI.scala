package dev.gustavoatt.spotifyql.api

import java.nio.charset.StandardCharsets
import java.time.temporal.ChronoUnit
import java.util.Base64

import com.google.gson.reflect.TypeToken
import com.google.gson.{FieldNamingPolicy, Gson, GsonBuilder}
import javax.servlet.http.HttpServletRequest
import okhttp3.{FormBody, HttpUrl, OkHttpClient, Request}

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

  def getUserProfile(userInfo: UserInfo): String = {
    val url = new HttpUrl.Builder()
      .scheme("https")
      .host(API_DOMAIN)
      .addPathSegment("v1")
      .addPathSegment("me")
      .build()

    val request = new Request.Builder()
      .url(url)
      .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
      .build()

    var response: okhttp3.Response = null
    try {
      response = client.newCall(request).execute()
      response.body().string()
    } finally {
      if (response != null) {
        response.close()
      }
    }
  }

  def getCurrentlyPlayingTrack(userInfo: UserInfo): CurrentlyPlaying = {
    val url = new HttpUrl.Builder()
      .scheme("https")
      .host(API_DOMAIN)
      .addPathSegments("v1/me/player/currently-playing")
      .build()

    val request = new Request.Builder()
      .url(url)
      .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
      .build()

    var response: Option[okhttp3.Response] = None
    try {
      response = Some(client.newCall(request).execute())
      gson.fromJson(response.get.body().string(), classOf[CurrentlyPlaying])
    } finally {
      response.foreach(_.close())
    }
  }

  def getUserTracks(userInfo: UserInfo): Paging[SavedTrack] = {
    val url = new HttpUrl.Builder()
      .scheme("https")
      .host(API_DOMAIN)
      .addPathSegment("v1")
      .addPathSegment("me")
      .addPathSegment("tracks")
      .build()

    val request = new Request.Builder()
      .url(url)
      .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
      .build()

    var response: okhttp3.Response = null
    try {
      response = client.newCall(request).execute()
      gson.fromJson(
        response.body().string(),
        TypeToken.getParameterized(classOf[Paging[SavedTrack]], classOf[SavedTrack]).getType)
    } finally {
      if (response != null) {
        response.close()
      }
    }
  }

  def getRecommendedTracks(userInfo: UserInfo, tracks: Seq[String]): RecomendationResponse = {
    val url = new HttpUrl.Builder()
      .scheme("https")
      .host(API_DOMAIN)
      .addPathSegments("v1/recommendations")
      .addQueryParameter("seed_tracks", tracks.mkString(","))
      .build()

    val request = new Request.Builder()
      .url(url)
      .header("Authorization", s"${userInfo.tokenType} ${userInfo.accessToken}")
      .get()
      .build()

    var response: Option[okhttp3.Response] = None
    try {
      response = Some(client.newCall(request).execute())
      gson.fromJson(response.get.body().string(), classOf[RecomendationResponse])
    } finally {
      response.foreach(_.close())
    }
  }
}
