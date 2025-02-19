package ch.epfl.sweng.rps.services

import ch.epfl.sweng.rps.models.remote.Game
import ch.epfl.sweng.rps.models.remote.GameMode
import ch.epfl.sweng.rps.models.remote.Hand
import ch.epfl.sweng.rps.models.remote.Round
import ch.epfl.sweng.rps.models.xbstract.ComputerPlayer
import ch.epfl.sweng.rps.remote.Repository
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay

class OfflineGameService(
    override val gameId: String,
    private val repository: Repository,
    private val computerPlayers: List<ComputerPlayer>,
    private val gameMode: GameMode,
    @Suppress("UNUSED_PARAMETER")
    val artificialMovesDelay: Long = DEFAULT_DELAY
) : GameService() {


    private var _disposed = false

    /**
     * Initialises the game after the game choice and play again button.
     * (play again currently not available)
     */


    override val isGameOver: Boolean
        get() {
            val game = game ?: return false
            return game.current_round == game.gameMode.rounds - 1 && game.rounds[game.current_round.toString()]?.hands?.keys?.containsAll(
                game.players
            ) ?: false
        }
    private val currentHands get() = currentRound.hands as MutableMap
    override val currentGame: Game
        get() {
            checkNotDisposed()
            if (game == null) {
                throw error
                    ?: GameServiceException("Game service has not received a game yet")
            } else {
                return game!!
            }
        }
    override val currentRound: Round
        get() = game!!.rounds[game!!.current_round.toString()]!!
    override val isGameFull: Boolean
        get() = true
    override val isDisposed: Boolean
        get() = _disposed
    override val started: Boolean
        get() = game != null
    override val imTheOwner get() = true
    override suspend fun addRound(): Round {
        checkNotDisposed()
        val round = Round.Rps(
            hands = mutableMapOf(),
            timestamp = Timestamp.now(),
        )
        game = (game!! as Game.Rps).copy(current_round = game!!.current_round.plus(1))
        setRound(round)
        return round
    }

    private fun setRound(round: Round) {
        (currentGame.rounds as MutableMap)[game!!.current_round.toString()] = round
    }

    override suspend fun playHand(hand: Hand) {
        checkNotDisposed()
        val me: String = repository.getCurrentUid()
        currentHands[me] = hand
        makeComputerMoves()
    }

    private suspend fun makeComputerMoves() {
        for (pc in computerPlayers) {
            delay(artificialMovesDelay)
            currentHands[pc.uid] = pc.makeMove()
        }
    }

    override fun dispose() {
        checkNotDisposed()
        _disposed = true
        super.dispose()
    }

    override fun stopListening() {
    }

    override suspend fun refreshGame(): Game = game!!
    override fun startListening(): GameService {
        checkNotDisposed()
        val round = Round.Rps(
            hands = mutableMapOf(),
            timestamp = Timestamp.now(),
        )
        game = Game.Rps(
            gameId,
            computerPlayers.map { it.uid },
            mutableMapOf("0" to round),
            0,
            gameMode.toGameModeString(),
            false,
            Timestamp.now(),
            gameMode.playerCount
        )
        return this
    }

    private fun checkNotDisposed() {
        if (_disposed) {
            throw GameServiceException("GameService is disposed")
        }
    }

    override suspend fun awaitForAllHands() {
        awaitFor { currentRound.hands.size == 2 }// 2 is the number of players, for now hardcoded
    }

    override suspend fun awaitForRoundAdded() {
        awaitFor { true }
    }

    companion object {
        private const val DEFAULT_DELAY = 100L
    }
}