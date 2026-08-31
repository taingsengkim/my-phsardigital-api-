package co.istad.projectpracticum.phsardigital.features.payments.khqr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Asks the Bakong Open API whether a KHQR was paid.
 *
 * <p>The only question this client asks is {@code check_transaction_by_md5}: given the
 * hash of a QR payload we generated, has a matching transfer settled? Bakong offers no
 * webhook, so this is the whole of the confirmation story — something has to ask.
 *
 * <p>The distinction this class exists to preserve is between <em>no</em> and
 * <em>don't know</em>. "No transaction yet" is an ordinary answer and comes back as an
 * unsettled {@link BakongTransaction}. Everything else — an expired token, a rate
 * limit, a timeout, Bakong being down — is raised, because a caller that treated those
 * as "not paid" would cancel subscriptions people had already paid for.
 */
@Component
@Slf4j
public class BakongClient {

    /** {@code responseCode} 0 is Bakong's success; any other value is a miss. */
    private static final int RESPONSE_OK = 0;

    private final RestClient restClient;
    private final BakongProps props;

    /**
     * Builds its own client rather than taking an injected {@code RestClient.Builder}.
     *
     * <p>That builder is only a bean when {@code spring-boot-restclient} is on the
     * classpath, and this application does not depend on it — asking for one would fail
     * at startup, on deployment, over a feature the rest of the API does not use.
     * {@link RestClient#builder()} lives in spring-web and is always there.
     *
     * <p>Nothing is lost by it: the timeouts below are the reason a bespoke request
     * factory is needed anyway, since the default has none at all and a hung Bakong
     * would otherwise hold a request thread indefinitely.
     */
    public BakongClient(BakongProps props) {
        this.props = props;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.getConnectTimeout());
        requestFactory.setReadTimeout(props.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * @param md5 the hash of a QR payload this API generated
     * @return the settled transaction, or {@link BakongTransaction#unsettled()} when
     *         nobody has paid it yet
     * @throws ResponseStatusException when Bakong could not be asked, or answered in a
     *         way that says something is wrong with our integration rather than with
     *         the payment
     */
    public BakongTransaction checkByMd5(String md5) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is not configured on this server.");
        }

        Envelope envelope;
        try {
            envelope = restClient.post()
                    .uri("/v1/check_transaction_by_md5")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiToken())
                    .body(Map.of("md5", md5))
                    .exchange((request, response) -> readEnvelope(response.getStatusCode(),
                            response.bodyTo(Envelope.class)));
        } catch (ResourceAccessException timeout) {
            // A timeout says nothing about whether the money moved, so it must not be
            // allowed to look like a definitive "unpaid".
            log.warn("Bakong did not answer within the timeout for md5 {}", md5, timeout);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "The payment network did not respond. Try again in a moment.");
        }

        if (envelope == null || envelope.responseCode() == null
                || envelope.responseCode() != RESPONSE_OK || envelope.data() == null) {
            return BakongTransaction.unsettled();
        }

        Data data = envelope.data();
        return new BakongTransaction(true, data.hash(), data.fromAccountId(),
                data.toAccountId(), data.amount(), data.currency());
    }

    /**
     * Turns one HTTP response into either an envelope or an exception.
     *
     * <p>404 is the interesting case: Bakong uses it for "no such transaction", which
     * is the normal state of an unpaid QR rather than a fault, so it is folded into the
     * ordinary unsettled answer. 401 and 403 are the opposite — they almost always mean
     * the developer token has expired, and saying so is far more useful than a generic
     * upstream failure, because nothing recovers on its own until somebody renews it.
     */
    private Envelope readEnvelope(HttpStatusCode status, Envelope body) {
        if (status.is2xxSuccessful()) {
            return body;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return null;
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()) {
            log.error("Bakong rejected our credentials ({}). The developer token has "
                    + "most likely expired; renew it and set bakong.api-token.", status);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The payment network rejected this server's credentials.");
        }
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The payment network is rate limiting this server. Try again shortly.");
        }
        log.error("Bakong answered {} for a transaction check", status);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The payment network could not be reached.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(Integer responseCode, String responseMessage,
                            Integer errorCode, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Data(String hash, String fromAccountId, String toAccountId,
                        BigDecimal amount, String currency) {
    }
}
