package telex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TelexApplication

@Suppress("SpreadOperator") // runs once at startup
fun main(args: Array<String>) {
    runApplication<TelexApplication>(*args)
}
