package org.sInstagram.responses.auth

sealed trait Authentication
case class ClientId(id: String) extends Authentication
case class AccessToken(token: String) extends Authentication
case class SignedAccessToken(token: String, clientSecret: String) extends Authentication