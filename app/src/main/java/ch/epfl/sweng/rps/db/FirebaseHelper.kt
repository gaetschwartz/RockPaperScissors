package ch.epfl.sweng.rps.db

import ch.epfl.sweng.rps.models.Hand
import ch.epfl.sweng.rps.models.RoundStat
import ch.epfl.sweng.rps.models.User
import ch.epfl.sweng.rps.models.UserStat
import ch.epfl.sweng.rps.services.ServiceLocator
import java.text.SimpleDateFormat
import java.util.*

sealed class FirebaseHelper {
    companion object {
        fun processUserArguments(vararg pairs: Pair<User.Field, Any>): Map<String, Any> {
            return pairs.associate { t -> t.first.value to t.second }
        }

        fun userFrom(uid: String, name: String?, email: String?): User {
            return User(
                email = email,
                username = name,
                games_history_privacy = User.Privacy.PUBLIC.name,
                has_profile_photo = false,
                uid = uid,
            )
        }

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

        suspend fun getStatsData(selectMode: Int): List<UserStat> {
            val firebaseRepository = ServiceLocator.getInstance().repository
            val userid = firebaseRepository.rawCurrentUid()
                ?: return listOf(
                    UserStat(
                        gameId = "gameId1",
                        date = dateFormat.format(Date()),
                        opponents = "userIdOpp",
                        roundMode = "1",
                        score = "1 - 0"
                    )
                )
            val userGameList = firebaseRepository.gamesOfUser(userid)
            val allStatsResult = mutableListOf<UserStat>()
            for (userGame in userGameList) {
                val userStat = UserStat()
                val game = firebaseRepository.getGame(userGame.id) ?: continue
                val players = game.players
                val opponents = mutableListOf<String>()
                for (p in players) {
                    if (p != userid) {
                        val user = firebaseRepository.getUser(p)
                        if (user != null)
                            opponents.add(user.username ?: p)
                    }
                }

                val gameRounds = game.rounds
                val allRoundScores = gameRounds.map { it.value.computeScores() }

                val userScore = allRoundScores.asSequence().map { scores ->
                    val max = scores.maxOf { it.points }
                    if (scores.any { it.points == max && it.uid == userid && !scores.all { it.points == max } })
                        1
                    else
                        0

                }.sum()

                val roundMode = game.mode.rounds
                //by default 1v1 here, so just use overall rounds minus his score
                val opponentScore = roundMode.minus(userScore)
                // should be shown like "3 - 2 "
                val score = "$userScore - $opponentScore"
                val date =
                    dateFormat.format(game.timestamp.toDate())
                userStat.gameId = game.id
                userStat.date = date
                // Pass the id temporarily since I am not sure how to get name from id
                userStat.opponents = opponents.joinToString(",") { it }
                userStat.roundMode = roundMode.toString()
                userStat.score = score

                //mode filter, selectMode = 0 means by default to fetch all result
                // todo: map selectMode (best of X) in UI to Game round number
                if (roundMode == selectMode || selectMode == 0) {
                    allStatsResult.add(userStat)
                }
            }
            return allStatsResult


        }

        suspend fun getMatchDetailData(gid: String): List<RoundStat> {
            val userid = ServiceLocator.getInstance().repository.rawCurrentUid()
                ?: return listOf(
                    RoundStat(
                        index = 0,
                        date = Date(),
                        userHand = Hand.PAPER,
                        opponentHand = Hand.SCISSORS,
                        outcome = Hand.Result.LOSS
                    )
                )
            val game = ServiceLocator.getInstance().repository.getGame(gid)
                ?: throw Exception("Game not found")
            // note: 1 v 1 logic, if we support pvp mode, the table should be iterated to change as well.
            // get opponent user id from player list
            val opponentId: String = game.players.first { it != userid }
            val allDetailsList = game.rounds.entries.mapIndexed { i, round ->
                val hand = round.value.hands
                val score = round.value.computeScores()
                // id means choice ()
                val userHand = hand[userid]!!
                val opponentHand = hand[opponentId]!!
                val outcome = when {
                    score[0].uid == userid -> Hand.Result.WIN
                    score[0].points == 0 -> Hand.Result.TIE
                    else -> Hand.Result.LOSS
                }

                RoundStat(
                    outcome = outcome,
                    index = i,
                    opponentHand = opponentHand,
                    userHand = userHand,
                    date = game.timestamp.toDate()
                )
            }

            return allDetailsList
        }


    }
}