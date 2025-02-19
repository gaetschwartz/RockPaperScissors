package ch.epfl.sweng.rps.remote.friends

import ch.epfl.sweng.rps.models.remote.FriendRequest
import ch.epfl.sweng.rps.models.remote.FriendRequest.FIELDS
import ch.epfl.sweng.rps.remote.FirebaseRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await

class FirebaseFriendsRepository(val repository: FirebaseRepository) : FriendsRepository {


    val firebase get() = repository.firebase
    fun getCurrentUid() = repository.getCurrentUid()
    override suspend fun sendFriendRequestTo(uid: String) {
        firebase.usersFriendRequest
            .add(FriendRequest.build(from = getCurrentUid(), to = uid, timestamp = Timestamp.now()))
            .await()
    }

    override suspend fun listFriendRequests(): List<FriendRequest> {
        return firebase.usersFriendRequest
            .whereArrayContains(FIELDS.USERS, getCurrentUid())
            .whereNotEqualTo(FIELDS.STATUS, FriendRequest.Status.PENDING).get()
            .await().toObjects(FriendRequest::class.java)
    }

    override suspend fun getFriends(): List<String> {
        val me = getCurrentUid()
        return firebase.usersFriendRequest
            .whereEqualTo(FIELDS.STATUS, FriendRequest.Status.ACCEPTED)
            .whereArrayContains(FIELDS.USERS, getCurrentUid())
            .get().await().documents
            .map { it.toObject<FriendRequest>()!!.users firstNot me }
    }

    private infix fun <T> List<T>.firstNot(other: String): T {
        return first { it != other }
    }

    override suspend fun changeFriendRequestToStatus(
        userUid: String,
        status: FriendRequest.Status
    ) {
        firebase.usersFriendRequest
            .whereArrayContains(FIELDS.USERS, userUid)
            .whereArrayContains(FIELDS.USERS, getCurrentUid())
            .whereNotEqualTo(FIELDS.STATUS, FriendRequest.Status.PENDING)
            .limit(1)
            .get().await().documents.first().reference
            .update(FIELDS.STATUS, status).await()
    }

}