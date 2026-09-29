package de.jasineri.brokercli;

import com.jayway.jsonpath.DocumentContext;
import de.jasineri.tools.DesktopBrowserOpener;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link BrokerCli}.
 *
 * <p>These tests exercise the real {@code sc} binary and the real Scalable
 * Capital backend. They will:
 * <ul>
 *   <li>shell out to the locally installed {@code sc} executable,</li>
 *   <li>trigger the device-code login flow (opening a browser if no session
 *       exists), and</li>
 *   <li>issue live broker API calls.</li>
 * </ul>
 *
 * <p><strong>They are not hermetic.</strong> They require:
 * <ul>
 *   <li>the {@code sc} binary on the {@code PATH},</li>
 *   <li>a valid user session (or a human to complete the device flow),</li>
 *   <li>network access to Scalable Capital.</li>
 * </ul>
 *
 * <p>Because of this, the class is tagged {@code integration} and should be
 * excluded from the default {@code mvn test} run. Run it explicitly via:
 * <pre>{@code
 * mvn test -Dgroups=integration
 * }</pre>
 */
@Tag("integration")
class BrokerCliTest {

    /**
     * End-to-end smoke test: ensures a session exists, logs in if necessary,
     * fetches a live quote, and validates the response shape.
     *
     * <p>The device-code URL is handed to {@link DesktopBrowserOpener}, which
     * opens the system browser on Linux, Windows or macOS.
     *
     * @throws Exception if the CLI cannot be started, login fails, or the
     *                   JSON response does not match expectations
     */
    @Test
    void fetchesLiveQuoteForApple() throws Exception {
        System.out.println("==> Creating BrokerCli");
        BrokerCli cli = new BrokerCli(DesktopBrowserOpener.instance());

        System.out.println("==> Checking session (sc whoami)");
        if (!cli.isLoggedIn()) {
            System.out.println("    no session — starting device-code login");
            boolean ok = cli.login();
            System.out.println("    login result: " + ok);
            assertTrue(ok, "device-code login must succeed");
        } else {
            System.out.println("    already logged in");
        }

        System.out.println("==> Fetching quote for US0378331005");
        DocumentContext quote = cli.runJson(
                "broker", "quote", "--isin", "US0378331005");

        Double price = quote.read("$.result.quote_ask_price", Double.class);
        String currency = quote.read("$.result.quote_currency", String.class);

        System.out.println("    price    = " + price);
        System.out.println("    currency = " + currency);

        assertNotNull(price, "ask price for Apple (US0378331005)");
        assertTrue(price > 0, "ask price must be positive");
        assertNotNull(currency, "quote currency");
        assertFalse(currency.isEmpty(), "quote currency must not be empty");

        System.out.println("==> OK");
    }
}