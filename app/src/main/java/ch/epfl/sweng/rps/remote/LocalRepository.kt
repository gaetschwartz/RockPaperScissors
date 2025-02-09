package ch.epfl.sweng.rps.remote

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import ch.epfl.sweng.rps.models.remote.*
import ch.epfl.sweng.rps.remote.friends.FriendsRepository
import ch.epfl.sweng.rps.remote.games.GamesRepository
import com.google.firebase.Timestamp
import java.net.URI

class LocalRepository(private var uid: String? = null) : Repository, GamesRepository,
    FriendsRepository {

    val users = mutableMapOf<String, User>()
    private val friendRequests = mutableMapOf<String, MutableMap<String, FriendRequest>>()
    override val friends: FriendsRepository
        get() = this
    override val games: GamesRepository
        get() = this
    val gamesMap = mutableMapOf<String, Game>()
    var leaderBoardScore = mutableListOf<TotalScore>()

    @VisibleForTesting
    val invitations = mutableMapOf<String, Invitation>()
    fun setCurrentUid(newUid: String?) {
        uid = newUid
    }

    override suspend fun updateUser(vararg pairs: Pair<User.Field, Any>) {
        var user = getUser(getCurrentUid())!!
        pairs.forEach {
            user = when (it.first) {
                User.Field.EMAIL -> user.copy(email = it.second as String)
                User.Field.USERNAME -> user.copy(username = it.second as String)
                User.Field.GAMES_HISTORY_PRIVACY -> user.copy(games_history_privacy = it.second as String)
                User.Field.HAS_PROFILE_PHOTO -> user.copy(has_profile_photo = it.second as Boolean)
                User.Field.UID -> throw IllegalArgumentException("Cannot change uid")
            }
        }
        users[getCurrentUid()] = user
    }

    override fun rawCurrentUid(): String? {
        return uid
    }

    override suspend fun getUser(uid: String): User? {
        return users[uid]
    }

    override suspend fun getUserProfilePictureUrl(uid: String): URI? {
        val cond = getUser(uid)!!.has_profile_photo
        return if (cond) {
            URI("https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_960_720.png")
        } else {
            null
        }
    }

    override suspend fun createThisUser(name: String?, email: String?): User {
        val user = FirebaseHelper.userFrom(
            uid = getCurrentUid(),
            name = name.orEmpty(),
            email = email
        )
        users[user.uid] = user
        return user
    }

    override suspend fun sendFriendRequestTo(uid: String) {
        val map = (friendRequests[uid] ?: mutableMapOf())
        map[getCurrentUid()] = FriendRequest.build(getCurrentUid(), uid, Timestamp.now())
        friendRequests[uid] = map
    }

    override suspend fun listFriendRequests(): List<FriendRequest> {
        return friendRequests[getCurrentUid()]?.entries?.map { it.value } ?: emptyList()
    }

    override suspend fun getFriends(): List<String> {
        return friendRequests[getCurrentUid()]
            ?.filter { it.value.status == FriendRequest.Status.ACCEPTED }
            ?.map { entry -> entry.value.users.first { it != getCurrentUid() } }
            ?.toList() ?: emptyList()
    }

    override suspend fun changeFriendRequestToStatus(
        userUid: String,
        status: FriendRequest.Status
    ) {
        friendRequests[getCurrentUid()]?.set(
            userUid,
            friendRequests[getCurrentUid()]!![userUid]!!.copy(status = status)
        )
    }

    override suspend fun getGame(gameId: String): Game? {
        return gamesMap[gameId]
    }

    override suspend fun getLeaderBoardScore(scoreMode: String): List<TotalScore> {
        return leaderBoardScore
    }

    override suspend fun gamesOfUser(uid: String): List<Game> {
        return gamesMap.values.filter { uid in it.players }
    }

    override suspend fun myActiveGames(): List<Game> {
        return gamesMap.values.filter { it.players.contains(getCurrentUid()) && !it.done }
    }

    override suspend fun statsOfUser(uid: String): UserStats {
        return UserStats(
            total_games = gamesOfUser(uid).size,
            wins = 0,
            userId = uid
        )
    }

    override suspend fun listInvitations(): List<Invitation> {
        return invitations.values.toList()
    }

    override suspend fun setUserProfilePicture(image: Bitmap, waitForUploadTask: Boolean) {

    }

    override suspend fun getUserProfilePictureImage(uid: String): Bitmap? {
        TODO("Not yet implemented")
    }
}