package dev.gustavoatt.spotifyql.api

import java.util

import com.google.gson.annotations.SerializedName

case class Paging[Item](href: String,
                        items: util.List[Item],
                        limit: Int,
                        next: String,
                        offset: Integer,
                        previous: String,
                        total: Int)

case class SavedTrack(timestamp: String, track: Track)

case class Track(name: String,
                 album: Album,
                 artists: util.List[Artist],
                 availableMarkets: util.List[String],
                 discNumber: Int,
                 durationMs: Int,
                 explicit: Boolean,
                 externalIds: util.Map[String, String],
                 externalUrls: util.Map[String, String],
                 href: String,
                 id: String,
                 isPlayable: Boolean,
                 uri: String)

case class Album(albumGroup: String,
                 albumType: String,
                 artists: util.List[Artist],
                 images: util.List[Image])

case class Image(height: Int, width: Int, url: String)

case class Artist(href: String, id: String, name: String, uri: String)

case class CurrentlyPlaying(context: Context,
                            timestamp: Long,
                            progressMs: Long,
                            isPlaying: Boolean,
                            item: Track,
                            currentlyPlayingType: String)

class Context(uri: String,
              href: String,
              externalUrls: util.Map[String, String],
              @SerializedName("type") contextType: String)

case class RecomendationResponse(tracks: util.List[Track])