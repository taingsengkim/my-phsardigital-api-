package co.istad.projectpracticum.phsardigital.features.auth.dto;

/**
 * The one answer the verification-resend and password-reset endpoints ever give.
 *
 * <p>It carries no account details on purpose. Anyone may call those endpoints
 * without a token, so a reply that differed between a registered address and an
 * unregistered one would let a stranger test email addresses against the site one
 * request at a time. Constant, and constructed here rather than at each call site so
 * the two endpoints cannot drift apart into telling different stories.
 */
public record AccountEmailResponse(String message) {

    private static final String CONSTANT_MESSAGE =
            "If an account matches that email address, we have sent it an email. "
                    + "Check your inbox, including the spam folder.";

    public static AccountEmailResponse accepted() {
        return new AccountEmailResponse(CONSTANT_MESSAGE);
    }
}
