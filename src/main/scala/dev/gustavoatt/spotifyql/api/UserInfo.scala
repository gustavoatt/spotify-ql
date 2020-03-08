package dev.gustavoatt.spotifyql.api

case class UserInfo(code: String,
                    accessToken: String,
                    tokenExpirationTime: java.time.Instant,
                    tokenType: String,
                    refreshToken: String,
                    scopes: Array[String])
