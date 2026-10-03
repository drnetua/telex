package telex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class TelexApplication

@Suppress("SpreadOperator") // runs once at startup
fun main(args: Array<String>) {
    runApplication<TelexApplication>(*args)
}
