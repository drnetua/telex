package telex.identity.internal.passkey

import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.RowMapper
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * A Passkey has no last-used date until its first sign-in ("Never used", AC-89; data-model §Aggregate: Passkey), so
 * `user_credentials.last_used` is NULL. The framework's (final) `JdbcUserCredentialRepository` maps rows with an
 * `Assert.notNull(lastUsed)`; this [JdbcOperations] lets its reads through: the framework mapper sees a placeholder
 * for the NULL, and the record comes back with no last-used date. Writes already accept a null date.
 */
internal class NeverUsedTolerantJdbcOperations(
    private val jdbc: JdbcOperations,
) : JdbcOperations by jdbc {
    override fun <T : Any?> query(
        sql: String,
        rowMapper: RowMapper<T>,
        vararg args: Any?,
    ): List<T> = jdbc.query(sql, NeverUsedTolerantRowMapper(rowMapper), *args)
}

private class NeverUsedTolerantRowMapper<T>(
    private val mapper: RowMapper<T>,
) : RowMapper<T> {
    override fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): T {
        if (rs.getTimestamp(LAST_USED) != null) return mapper.mapRow(rs, rowNum)
        val record = mapper.mapRow(withPlaceholderLastUsed(rs), rowNum) as CredentialRecord
        @Suppress("UNCHECKED_CAST")
        return neverUsed(record) as T
    }

    private fun withPlaceholderLastUsed(rs: ResultSet): ResultSet =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ResultSet::class.java)) { _, method, args ->
            if (method.name == "getTimestamp" && args?.firstOrNull() == LAST_USED) {
                PLACEHOLDER
            } else {
                try {
                    method.invoke(rs, *args.orEmpty())
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        } as ResultSet

    private companion object {
        const val LAST_USED = "last_used"
        val PLACEHOLDER: Timestamp = Timestamp.from(Instant.EPOCH)
    }
}

/**
 * The same credential with no last-used date. The framework types the date as non-null although its own JDBC
 * store writes a null one, so the null is passed through an unchecked generic.
 */
fun neverUsed(record: CredentialRecord): CredentialRecord =
    ImmutableCredentialRecord.fromCredentialRecord(record).lastUsed(absent()).build()

@Suppress("UNCHECKED_CAST")
private fun <T> absent(): T = null as T
