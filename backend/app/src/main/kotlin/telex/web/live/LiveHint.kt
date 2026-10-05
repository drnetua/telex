package telex.web.live

/** A query the SPA should refetch; [wire] is the `LiveHint` value on the stream. Never Owner data. */
enum class LiveHint(
    val wire: String,
) {
    LINKED_ACCOUNTS("linked-accounts"),
}
