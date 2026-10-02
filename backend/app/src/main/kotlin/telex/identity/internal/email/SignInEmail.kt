package telex.identity.internal.email

import telex.mail.OutgoingEmail

object SignInEmail {
    fun build(
        to: String,
        link: String,
        code: String,
    ) = OutgoingEmail(
        to = to,
        subject = "Sign in to teleX",
        text =
            "Your sign-in code is $code\n\n" +
                "Or open this link to sign in:\n$link\n\n" +
                "The link and the code work once and expire in 15 minutes.\n",
        template = "sign-in",
    )
}
