package com.yukihirai0505.sInstagram

import java.net.URLEncoder

import play.api.libs.json.Reads

import com.netaporter.uri.Uri._
import com.yukihirai0505.sInstagram.http.{Request, Verbs}
import com.yukihirai0505.sInstagram.instagram.InstagramClient
import com.yukihirai0505.sInstagram.model.{Constants, Methods, OAuthConstants, QueryParam, Relationship}
import com.yukihirai0505.sInstagram.responses.auth.{AccessToken, Auth, SignedAccessToken}
import com.yukihirai0505.sInstagram.responses.comments.{MediaCommentResponse, MediaCommentsFeed}
import com.yukihirai0505.sInstagram.responses.common.Pagination
import com.yukihirai0505.sInstagram.responses.likes.LikesFeed
import com.yukihirai0505.sInstagram.responses.locations.{LocationInfo, LocationSearchFeed}
import com.yukihirai0505.sInstagram.responses.media.{MediaFeed, MediaInfoFeed}
import com.yukihirai0505.sInstagram.responses.relationships.RelationshipFeed
import com.yukihirai0505.sInstagram.responses.tags.{TagInfoFeed, TagSearchFeed}
import com.yukihirai0505.sInstagram.responses.users.basicinfo.UserInfo
import com.yukihirai0505.sInstagram.responses.users.feed.UserFeed
import com.yukihirai0505.sInstagram.utils.PaginationHelper
import dispatch._

import scala.language.postfixOps


/**
  * author Yuki Hirai on 2016/11/09.
  */
class Instagram(auth: Auth) extends InstagramClient {

  /**
    * Transform an Authentication type to be used in a URL.
    *
    * @param a Authentication
    * @return  String
    */
  protected def authToGETParams(a: Auth): String = a match {
    case AccessToken(token) => s"${OAuthConstants.ACCESS_TOKEN}=$token"
    case SignedAccessToken(token, _) => s"${OAuthConstants.ACCESS_TOKEN}=$token"
  }

  protected def addSecureSigIfNeeded(url: String, postData: Option[Map[String,String]] = None)
  : String = auth match {
    case SignedAccessToken(_, secret) =>
      val uri = parse(url)
      val params = uri.query.params
      val auth: InstagramAuth = new InstagramAuth
      val sig = auth.createSignedParam(
        secret,
        uri.pathRaw.replace(Constants.VERSION, ""),
        concatMapOpt(postData, params.toMap)
      )
      uri.addParam(QueryParam.SIGNATURE, sig).toStringRaw
    case _ => url
  }

  protected def concatMapOpt(postData: Option[Map[String,String]], params: Map[String,Option[String]])
  : Map[String,Option[String]] = postData match {
    case Some(m) => params ++ m.mapValues(Some(_))
    case _ => params
  }

  def request[T](verb: Verbs, apiPath: String, params: Option[Map[String, Option[String]]] = None)(implicit r: Reads[T]): Future[Option[T]] = {
    val parameters: Map[String, String] = params match {
      case Some(m) => m.filter(_._2.isDefined).mapValues(_.getOrElse("")).filter(!_._2.isEmpty)
      case None => Map.empty
    }
    val accessTokenUrl = s"${Constants.API_URL}$apiPath?${authToGETParams(auth)}"
    val effectiveUrl: String = verb match {
      case Verbs.GET => addSecureSigIfNeeded(accessTokenUrl)
      case _ => addSecureSigIfNeeded(accessTokenUrl, Some(parameters))
    }
    val request: Req = url(effectiveUrl).setMethod(verb.label)
    val requestWithParams = if (verb.label == Verbs.GET.label) { request <<? parameters } else { request << parameters }
    println(requestWithParams.url)
    Request.send[T](requestWithParams)
  }

  override def getUserInfo(userId: String): Future[Option[UserInfo]] = {
    val apiPath: String = Methods.USERS_WITH_ID format userId
    request[UserInfo](Verbs.GET, apiPath)
  }

  override def getCurrentUserInfo: Future[Option[UserInfo]] = {
    request[UserInfo](Verbs.GET, Methods.USERS_SELF)
  }

  override def getRecentMediaFeed(userId: Option[String] = None, count: Option[Int] = None, minId: Option[String] = None, maxId: Option[String] = None): Future[Option[MediaFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.COUNT -> Option(count.mkString),
      QueryParam.MIN_ID -> Option(minId.mkString),
      QueryParam.MAX_ID -> Option(maxId.mkString)
    )
    val apiPath: String = userId match {
      case Some(id) => Methods.USERS_RECENT_MEDIA format id
      case None => Methods.USERS_SELF_RECENT_MEDIA
    }
    request[MediaFeed](Verbs.GET, apiPath, Some(params))
  }

  override def getMediaComments(mediaId: String): Future[Option[MediaCommentsFeed]] = {
    val apiPath: String = Methods.MEDIA_COMMENTS format mediaId
    request[MediaCommentsFeed](Verbs.GET, apiPath)
  }

  override def getUserFollowList(userId: String): Future[Option[UserFeed]] = {
    getUserFollowListNextPage(userId)
  }

  override def getUserFollowListNextPage(userId: String, cursor: Option[String] = None): Future[Option[UserFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.CURSOR -> Option(cursor.mkString)
    )
    val apiPath: String = Methods.USERS_ID_FOLLOWS format userId
    request[UserFeed](Verbs.GET, apiPath, Some(params))
  }

  override def getUserFollowListNextPageByPage(pagination: Pagination): Future[Option[UserFeed]] = {
    getUserFeedInfoNextPage(pagination)
  }

  override def getUserLikedMediaFeed(maxLikeId: Option[Long] = None, count: Option[Int] = None): Future[Option[MediaFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.MAX_LIKE_ID -> Option(maxLikeId.mkString),
      QueryParam.COUNT -> Option(count.mkString)
    )
    request[MediaFeed](Verbs.GET, Methods.USERS_SELF_LIKED_MEDIA, Some(params))
  }

  override def searchUser(query: String, count: Option[Int] = None): Future[Option[UserFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.SEARCH_QUERY -> Some(query),
      QueryParam.COUNT -> Option(count.mkString)
    )
    request[UserFeed](Verbs.GET, Methods.USERS_SEARCH, Some(params))
  }

  override def getLocationInfo(locationId: String): Future[Option[LocationInfo]] = {
    val apiPath: String = Methods.LOCATIONS_BY_ID format locationId
    request[LocationInfo](Verbs.GET, apiPath)
  }

  override def getTagInfo(tagName: String): Future[Option[TagInfoFeed]] = {
    val apiPath: String = Methods.TAGS_BY_NAME format URLEncoder.encode(tagName, "UTF-8")
    request[TagInfoFeed](Verbs.GET, apiPath)
  }

  override def searchLocation(latitude: Double, longitude: Double, distance: Option[Int] = None): Future[Option[LocationSearchFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.LATITUDE -> Some(latitude.toString),
      QueryParam.LONGITUDE -> Some(longitude.toString),
      QueryParam.DISTANCE -> Some(distance.getOrElse(Constants.LOCATION_DEFAULT_DISTANCE).toString)
    )
    request[LocationSearchFeed](Verbs.GET, Methods.LOCATIONS_SEARCH, Some(params))
  }

  override def getRecentMediaByLocation(locationId: String, minId: Option[String] = None, maxId: Option[String] = None): Future[Option[MediaFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.MIN_ID -> Option(minId.mkString),
      QueryParam.MAX_ID -> Option(maxId.mkString)
    )
    val apiMethod: String = Methods.LOCATIONS_RECENT_MEDIA_BY_ID format locationId
    request[MediaFeed](Verbs.GET, apiMethod, Some(params))
  }

  override def setUserLike(mediaId: String): Future[Option[LikesFeed]] = {
    val apiMethod: String = Methods.LIKES_BY_MEDIA_ID format mediaId
    request[LikesFeed](Verbs.POST, apiMethod)
  }

  override def getMediaInfo(mediaId: String): Future[Option[MediaInfoFeed]] = {
    val apiPath = Methods.MEDIA_BY_ID format mediaId
    request[MediaInfoFeed](Verbs.GET, apiPath)
  }

  override def getRecentMediaNextPage(pagination: Pagination): Future[Option[MediaFeed]] = {
    val page: PaginationHelper.Page = PaginationHelper.parseNextUrl(pagination, Constants.API_URL)
    request[MediaFeed](Verbs.GET, page.apiPath, Some(page.queryStringParams))
  }

  override def getUserFeedInfoNextPage(pagination: Pagination): Future[Option[UserFeed]] = {
    val page: PaginationHelper.Page = PaginationHelper.parseNextUrl(pagination, Constants.API_URL)
    request[UserFeed](Verbs.GET, page.apiPath, Option(page.queryStringParams))
  }

  override def deleteUserLike(mediaId: String): Future[Option[LikesFeed]] = {
    val apiPath: String = Methods.LIKES_BY_MEDIA_ID format mediaId
    request[LikesFeed](Verbs.DELETE, apiPath)
  }

  override def deleteMediaCommentById(mediaId: String, commentId: String): Future[Option[MediaCommentResponse]] = {
    val apiPath: String = Methods.DELETE_MEDIA_COMMENTS format (mediaId, commentId)
    request[MediaCommentResponse](Verbs.DELETE, apiPath)
  }

  override def getUserFollowedByList(userId: String, cursor: Option[String] = None): Future[Option[UserFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.CURSOR -> Option(cursor.mkString)
    )
    val apiPath: String = Methods.USERS_ID_FOLLOWED_BY format userId
    request[UserFeed](Verbs.GET, apiPath, Some(params))
  }

  override def getUserFollowedByListNextPage(pagination: Pagination): Future[Option[UserFeed]] = {
    getUserFeedInfoNextPage(pagination)
  }

  override def setMediaComments(mediaId: String, text: String): Future[Option[MediaCommentResponse]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.TEXT -> Some(text)
    )
    val apiPath: String = Methods.MEDIA_COMMENTS format mediaId
    request[MediaCommentResponse](Verbs.POST, apiPath, Some(params))
  }

  override def getMediaInfoByShortCode(shortCode: String): Future[Option[MediaInfoFeed]] = {
    val apiPath: String = Methods.MEDIA_BY_SHORT_CODE format shortCode
    request[MediaInfoFeed](Verbs.GET, apiPath)
  }

  override def searchTags(tagName: String): Future[Option[TagSearchFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.SEARCH_QUERY -> Some(tagName)
    )
    request[TagSearchFeed](Verbs.GET, Methods.TAGS_SEARCH, Some(params))
  }

  override def getRecentMediaFeedTags(tagName: String, minTagId: Option[String] = None, maxTagId: Option[String] = None, count: Option[Long] = None): Future[Option[MediaFeed]] = {
    val apiPath: String = Methods.TAGS_RECENT_MEDIA format tagName
    val params: Map[String, Option[String]] = Map(
      QueryParam.MIN_TAG_ID -> Option(minTagId.mkString),
      QueryParam.MAX_TAG_ID -> Option(maxTagId.mkString),
      QueryParam.COUNT -> Option(count.mkString)
    )
    request[MediaFeed](Verbs.GET, apiPath, Some(params))
  }

  override def setUserRelationship(userId: String, relationship: Relationship): Future[Option[RelationshipFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.ACTION -> Some(relationship.value)
    )
    val apiPath: String = Methods.USERS_ID_RELATIONSHIP format userId
    request[RelationshipFeed](Verbs.POST, apiPath, Some(params))
  }

  override def getUserRequestedBy: Future[Option[UserFeed]] = {
    request[UserFeed](Verbs.GET, Methods.USERS_SELF_REQUESTED_BY)
  }

  override def getUserRelationship(userId: String): Future[Option[RelationshipFeed]] = {
    val apiPath: String = Methods.USERS_ID_RELATIONSHIP format userId
    request[RelationshipFeed](Verbs.GET, apiPath)
  }

  override def searchFacebookPlace(facebookPlacesId: String): Future[Option[LocationSearchFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.FACEBOOK_PLACES_ID -> Some(facebookPlacesId)
    )
    request[LocationSearchFeed](Verbs.GET, Methods.LOCATIONS_SEARCH, Some(params))
  }

  override def searchMedia(latitude: Double, longitude:Double, distance: Option[Int] = None): Future[Option[MediaFeed]] = {
    val params: Map[String, Option[String]] = Map(
      QueryParam.LATITUDE -> Some(latitude.toString),
      QueryParam.LONGITUDE -> Some(longitude.toString),
      QueryParam.DISTANCE -> Option(distance.mkString)
    )
    request[MediaFeed](Verbs.GET, Methods.MEDIA_SEARCH, Some(params))
  }

  override def getUserLikes(mediaId: String): Future[Option[LikesFeed]] = {
    val apiPath: String = Methods.LIKES_BY_MEDIA_ID format mediaId
    request[LikesFeed](Verbs.GET, apiPath)
  }
}