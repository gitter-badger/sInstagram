package com.yukihirai0505.sInstagram.responses.likes

import com.yukihirai0505.sInstagram.responses.common.{Meta, User}

/**
  * author Yuki Hirai on 2016/11/09.
  */
case class LikesFeed(
  data: List[User],
  meta: Option[Meta]
)

import play.api.libs.json.Json
object LikesFeed {
  implicit val LikesFeedFormat = Json.format[LikesFeed]
}