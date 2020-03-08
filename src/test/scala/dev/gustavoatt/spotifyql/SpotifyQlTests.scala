package dev.gustavoatt.spotifyql

import org.scalatra.test.scalatest._

class SpotifyQlTests extends ScalatraFunSuite {

  addServlet(classOf[SpotifyQl], "/*")

  test("GET / on SpotifyQl should return status 200") {
    get("/") {
      status should equal (200)
    }
  }

}
