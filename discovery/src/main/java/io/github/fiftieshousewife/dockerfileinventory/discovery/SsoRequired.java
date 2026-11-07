package io.github.fiftieshousewife.dockerfileinventory.discovery;

final class SsoRequired extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SsoRequired(String header) {
        super("the token is not authorised for this organisation's SAML single sign-on; authorise it at "
                + header.replaceFirst("^.*url=", "") + " and rerun (discovery resumes)");
    }
}
