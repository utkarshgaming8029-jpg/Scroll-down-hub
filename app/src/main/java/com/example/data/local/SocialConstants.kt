package com.example.data.local

object UserRoles {
    const val USER = "USER"
    const val SUPER_ADMIN = "SUPER_ADMIN"
}

object FollowStatus {
    const val ACCEPTED = "ACCEPTED"
    const val PENDING = "PENDING"
}

object BlockType {
    const val BLOCKED = "BLOCKED"
    const val RESTRICTED = "RESTRICTED"
}

object MediaTypes {
    const val PHOTO = "PHOTO"
    const val CAROUSEL = "CAROUSEL"
    const val VIDEO = "VIDEO"
    const val REEL = "REEL"
    const val TEXT = "TEXT"
    const val AUDIO = "AUDIO"
}

object PostAudience {
    const val ALL = "ALL"
    const val CLOSE_FRIENDS = "CLOSE_FRIENDS"
}

object NotificationTypes {
    const val FOLLOW = "FOLLOW"
    const val FOLLOW_REQUEST = "FOLLOW_REQUEST"
    const val LIKE = "LIKE"
    const val COMMENT = "COMMENT"
    const val REPLY = "REPLY"
    const val MENTION = "MENTION"
    const val STORY_REACTION = "STORY_REACTION"
    const val MESSAGE = "MESSAGE"
    const val ADMIN_ANNOUNCEMENT = "ADMIN_ANNOUNCEMENT"
    const val VERIFICATION = "VERIFICATION"
    const val CALL = "CALL"
    const val LIVE = "LIVE"
}

object ReportStatus {
    const val OPEN = "OPEN"
    const val RESOLVED = "RESOLVED"
    const val DISMISSED = "DISMISSED"
}

object VerificationStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
}

object CallType {
    const val VOICE = "VOICE"
    const val VIDEO = "VIDEO"
}

object CallStatus {
    const val RINGING = "RINGING"
    const val ACCEPTED = "ACCEPTED"
    const val DECLINED = "DECLINED"
    const val ENDED = "ENDED"
}

object LiveStreamStatus {
    const val LIVE = "LIVE"
    const val ENDED = "ENDED"
}

data class FollowEntity(
    val followerId: String,
    val followingId: String,
    val status: String = FollowStatus.ACCEPTED,
    val createdAt: Long = 0L
)
