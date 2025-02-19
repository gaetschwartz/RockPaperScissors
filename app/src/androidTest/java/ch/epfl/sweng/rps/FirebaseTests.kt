package ch.epfl.sweng.rps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.epfl.sweng.rps.models.remote.Hand
import ch.epfl.sweng.rps.models.remote.User
import ch.epfl.sweng.rps.remote.Env
import ch.epfl.sweng.rps.remote.Repository
import ch.epfl.sweng.rps.services.FirebaseGameService
import ch.epfl.sweng.rps.services.GameService.GameServiceException
import ch.epfl.sweng.rps.services.ProdServiceLocator
import ch.epfl.sweng.rps.services.ServiceLocator
import ch.epfl.sweng.rps.utils.FirebaseEmulatorsUtils
import ch.epfl.sweng.rps.utils.europeWest1
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class FirebaseTests {
    lateinit var db: Repository

    @Before
    fun setUp() {
        db = ServiceLocator.getInstance(Env.Test).repository

        for (env in Env.values()) {
            ServiceLocator.getInstance(env).disposeAllGameServices()
        }
        FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext)
        FirebaseAuth.getInstance().signOut()
    }

    @After
    fun tearDown() {
        FirebaseApp.clearInstancesForTest()
    }

    @Test
    fun testEuropeWest1() {
        assertDoesNotThrow { Firebase.europeWest1 }
    }

    @Test
    fun testThrowsWhenNotLoggedIn() = runTest(UnconfinedTestDispatcher()) {
        FirebaseAuth.getInstance().signOut()

        assertEquals(false, db.isLoggedIn)
        assertThrows(Exception::class.java) {
            db.getCurrentUid()
        }

        assertThrows(Exception::class.java) {
            runBlocking {
                db.updateUser(User.Field.USERNAME to "test")
            }
        }

        assertThrows(Exception::class.java) {
            runBlocking {
                db.createThisUser("user1", "test@example.com")
            }
        }

        assertThrows(Exception::class.java) {
            runBlocking {
                db.friends.sendFriendRequestTo("user1")
            }
        }

        assertThrows(Exception::class.java) {
            runBlocking {
                db.friends.listFriendRequests()
            }
        }

        assertThrows(Exception::class.java) {
            runBlocking {
                db.friends.acceptFriendRequest("user1")
            }
        }
    }

    @Test
    fun testServiceLocator() {
        val serviceLocatorProd = ServiceLocator.getInstance(env = Env.Prod)
        assertEquals(Env.Prod, serviceLocatorProd.env)

        val serviceLocatorDev = ServiceLocator.getInstance(env = Env.Test)
        assertEquals(Env.Test, serviceLocatorDev.env)

        assertEquals(
            serviceLocatorProd.repository,
            serviceLocatorProd.repository
        )
    }

    @Test
    fun testGameService() {
        val serviceLocatorProd = ServiceLocator.getInstance(env = Env.Prod) as ProdServiceLocator
        assertEquals("1234", serviceLocatorProd.getGameServiceForGame("1234", start = false).gameId)
        assertTrue(
            serviceLocatorProd.getGameServiceForGame(
                "1",
                start = false
            ) === serviceLocatorProd.getGameServiceForGame("1", start = false)
        )

        val service: FirebaseGameService =
            serviceLocatorProd.getGameServiceForGame("1234", start = false)

        val throwingActions: List<suspend (service: FirebaseGameService) -> Unit> =
            listOf(
                { it.refreshGame() },
                { it.addRound() },
                { it.playHand(Hand.PAPER) },
            )

        for (action in throwingActions) {
            assertThrows(Exception::class.java) {
                runBlocking {
                    action(service)
                }
            }
        }

        service.dispose()

        assertTrue(service.isDisposed)
    }

    @Test
    fun disposingGameServices() {
        val serviceLocator = ServiceLocator.getInstance(env = Env.Prod) as ProdServiceLocator
        val service: FirebaseGameService =
            serviceLocator.getGameServiceForGame("1234", start = false)
        assertEquals(listOf("1234"), serviceLocator.cachedGameServices)
        assertFalse(service.isDisposed)
        serviceLocator.disposeAllGameServices()
        assertTrue(service.isDisposed)
        assertEquals(emptyList<String>(), serviceLocator.cachedGameServices)
    }

    @Test
    fun disposingGameServices2() {
        val serviceLocator = ServiceLocator.getInstance(env = Env.Prod) as ProdServiceLocator
        serviceLocator.disposeAllGameServices()
        val service: FirebaseGameService =
            serviceLocator.getGameServiceForGame("1234", start = false)
        assertEquals(listOf("1234"), serviceLocator.cachedGameServices)
        assertFalse(service.isDisposed)
        service.dispose()
        assertTrue(service.isDisposed)
        serviceLocator.disposeAllGameServices()
        assertTrue(service.isDisposed)
        assertEquals(emptyList<String>(), serviceLocator.cachedGameServices)
    }

    @Test
    fun usingAfterDisposedThrows() {
        val serviceLocator = ServiceLocator.getInstance(env = Env.Prod) as ProdServiceLocator
        val service: FirebaseGameService =
            serviceLocator.getGameServiceForGame("1234", start = false)
        service.dispose()
        assertTrue(service.isDisposed)
        val throwingActions: List<suspend (service: FirebaseGameService) -> Unit> =
            listOf(
                { it.refreshGame() },
                { it.addRound() },
                { it.playHand(Hand.PAPER) },
                { it.currentRound },
                { it.currentGame },
                { it.startListening() },
                { it.stopListening() },
            )

        for (action in throwingActions) {
            assertThrows(GameServiceException::class.java) {
                runBlocking {
                    action(service)
                }
            }
        }

        assertNull(service.error)

    }

    @Test
    fun firebaseReferences() {
        val firebase =
            (ServiceLocator.getInstance(env = Env.Prod) as ProdServiceLocator).firebaseReferences
        assertEquals("games", firebase.gamesCollection.path)

        assertEquals("/profile_pictures", firebase.profilePicturesFolder.path)
    }

    @Test
    fun testEmulator() {
        FirebaseEmulatorsUtils.useEmulators()
        assertEquals(true, FirebaseEmulatorsUtils.emulatorUsed)
    }
}
