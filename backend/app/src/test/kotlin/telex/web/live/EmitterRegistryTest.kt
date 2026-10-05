package telex.web.live

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import telex.identity.OwnerId
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.shared.Uuid7
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EmitterRegistryTest {
    /** What a servlet connection would see: the data an emitter wrote, and the callback that ends the stream. */
    private class Connection {
        val written = ConcurrentLinkedQueue<String>()

        @Volatile var onCompletion: Runnable? = null
    }

    /** Attaches a recording stand-in for the (package-private) servlet handler, as the framework does on dispatch. */
    private fun connect(emitter: SseEmitter): Connection {
        val connection = Connection()
        val handlerType = Class.forName("${ResponseBodyEmitter::class.java.name}\$Handler")
        val handler =
            Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, method, args ->
                when (method.name) {
                    "send" -> {
                        val data = args[0]
                        if (data is Set<*>) {
                            data.forEach {
                                connection.written +=
                                    (it as ResponseBodyEmitter.DataWithMediaType).data.toString()
                            }
                        } else {
                            connection.written += data.toString()
                        }
                    }

                    "onCompletion" -> {
                        connection.onCompletion = args[0] as Runnable
                    }
                }
                null
            }
        val initialize = ResponseBodyEmitter::class.java.getDeclaredMethod("initialize", handlerType)
        initialize.isAccessible = true
        initialize.invoke(emitter, handler)
        return connection
    }

    // AC-116, AC-121, AC-122: a stream opened while the Owner's last stream closes still receives hints.
    @Test
    fun `a stream opened while the last one closes is never orphaned from hints`() {
        val registry =
            EmitterRegistry(Mockito.mock(SignInSessions::class.java), Duration.ofHours(1), Duration.ZERO)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val orphaned = mutableListOf<Int>()
            repeat(ITERATIONS) { i ->
                val owner = OwnerId(Uuid7.next())
                val session = SignInSessionId(Uuid7.next())
                val first = connect(registry.open(owner, session))
                val barrier = CyclicBarrier(2)
                val closing =
                    pool.submit {
                        barrier.await()
                        first.onCompletion?.run()
                    }
                val opening =
                    pool.submit<SseEmitter> {
                        barrier.await()
                        registry.open(owner, session)
                    }
                closing.get(10, TimeUnit.SECONDS)
                val second = connect(opening.get(10, TimeUnit.SECONDS))
                registry.hint(owner, LiveHint.LINKED_ACCOUNTS)
                if (second.written.none { "event: hint" in it }) orphaned += i
            }
            assertThat(orphaned).describedAs("iterations whose open stream got no hint").isEmpty()
        } finally {
            pool.shutdownNow()
            registry.shutdown()
        }
    }

    private companion object {
        const val ITERATIONS = 20_000
    }
}
