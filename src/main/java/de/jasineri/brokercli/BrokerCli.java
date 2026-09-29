package de.jasineri.brokercli;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin, process-level wrapper around the {@code sc} broker CLI.
 *
 * <p>This class shells out to the {@code sc} binary, streams its output,
 * extracts the device-code login URL, hands it to a caller-supplied
 * browser hook, and exposes generic {@code run(...)} / {@code runJson(...)}
 * methods for arbitrary subcommands.
 *
 * <p><strong>Trademark notice:</strong> This project is not affiliated with,
 * endorsed by, or sponsored by Scalable Capital GmbH. "Scalable Capital" and
 * the {@code sc} CLI are trademarks of their respective owners. This class is
 * an independent, unofficial wrapper.
 *
 * <p>Typical usage:
 * <pre>{@code
 * BrokerCli cli = new BrokerCli(url -> System.out.println("Open: " + url));
 * if (!cli.isLoggedIn()) {
 *     cli.login();
 * }
 * DocumentContext quote = cli.runJson("broker", "quote", "--isin", "US0378331005");
 * }</pre>
 *
 * <p>The class is not thread-safe. Each instance is intended to be used from
 * a single thread; the internal reader threads are daemon threads that are
 * joined before the public methods return.
 */
public class BrokerCli {

    /** Maximum time to wait for the {@code sc login} device flow to complete. */
    private static final long LOGIN_TIMEOUT_SECONDS = 5 * 60;

    /** Default timeout for non-interactive subcommands. */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Callback invoked with the device-code verification URL once it has been
     * parsed from the CLI output. Implementations are expected to open the URL
     * in a browser (desktop, mobile, or test stub). Must not block.
     */
    private final Consumer<String> browserOpener;

    /**
     * Creates a new CLI wrapper.
     *
     * @param browserOpener callback that opens the device-code URL in a browser;
     *                      must not be {@code null}
     */
    public BrokerCli(Consumer<String> browserOpener) {
        if (browserOpener == null) {
            throw new IllegalArgumentException("browserOpener must not be null");
        }
        this.browserOpener = browserOpener;
    }

    /**
     * Runs the device-code login flow ({@code sc login}).
     *
     * <p>Reads the CLI output line by line, extracts the {@code user_code}
     * from the verification URL, invokes {@link #browserOpener}, and waits
     * for the CLI to confirm success.
     *
     * @return {@code true} if the CLI reported a successful login and exited
     *         with code {@code 0}, {@code false} otherwise
     * @throws IOException          if the CLI process cannot be started or read
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws RuntimeException     if the CLI does not emit a user code or does
     *                              not finish within {@value #LOGIN_TIMEOUT_SECONDS} seconds
     */
    public boolean login() throws IOException, InterruptedException {
        final String[] code = {null};
        final Thread[] loginThread = {null};
        final boolean[] success = {false};

        ExecResult r = exec(LOGIN_TIMEOUT_SECONDS, line -> {
            String clean = stripAnsi(line);
            System.out.println(clean);

            if (code[0] == null) {
                String c = extract(clean, "user_code=([A-Za-z0-9-]+)");
                if (c != null) {
                    code[0] = c;
                    loginThread[0] = new Thread(() -> browserOpener.accept(c), "browser-opener");
                    loginThread[0].start();
                }
            }
            if (clean.contains("Logged in via device code")) {
                success[0] = true;
            }
        }, "login");

        if (loginThread[0] != null) {
            loginThread[0].join();
        }
        return success[0] && r.exit() == 0;
    }

    /**
     * Checks whether a valid session already exists by running {@code sc whoami}.
     *
     * @return {@code true} if the command exits with code {@code 0},
     *         {@code false} for any failure (non-zero exit, timeout, I/O error)
     */
    public boolean isLoggedIn() {
        try {
            run("whoami");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs an arbitrary {@code sc} subcommand and returns its standard output.
     *
     * <p>Uses a {@value #DEFAULT_TIMEOUT_SECONDS}-second timeout. Throws on
     * non-zero exit, including the command's standard error in the message.
     *
     * @param args subcommand and arguments, e.g. {@code "broker", "quote", "--isin", "US0378331005"}
     * @return the trimmed standard output of the process
     * @throws IOException          if the process cannot be started or read
     * @throws InterruptedException if the calling thread is interrupted
     * @throws RuntimeException     on non-zero exit or timeout
     */
    public String run(String... args) throws IOException, InterruptedException {
        StringBuilder out = new StringBuilder();
        ExecResult r = exec(DEFAULT_TIMEOUT_SECONDS, line -> out.append(line).append('\n'), args);
        if (r.exit() != 0) {
            throw new RuntimeException(
                    "sc " + String.join(" ", args) + " failed (exit " + r.exit() + "):\n" + r.stderr());
        }
        return out.toString().trim();
    }

    /**
     * Runs an arbitrary {@code sc} subcommand and parses its standard output as JSON.
     *
     * @param args subcommand and arguments
     * @return a JsonPath {@link DocumentContext} for querying the result
     * @throws IOException          if the process cannot be started, read, or emits invalid JSON
     * @throws InterruptedException if the calling thread is interrupted
     * @throws RuntimeException     on non-zero exit or timeout
     * @see #run(String...)
     */
    public DocumentContext runJson(String... args) throws IOException, InterruptedException {
        return JsonPath.parse(run(args));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Spawns {@code sc <args>}, streams stdout to {@code onLine}, captures stderr,
     * and returns the exit code and stderr once the process terminates.
     *
     * <p>Stderr is drained on a separate daemon thread to prevent the child
     * from blocking on a full pipe buffer.
     *
     * @param timeoutSec seconds to wait before forcibly destroying the process
     * @param onLine     callback invoked for each line of stdout, in order
     * @param args       arguments passed to the {@code sc} binary
     * @return the process exit code and captured stderr
     * @throws IOException          if the process cannot be started or read
     * @throws InterruptedException if the calling thread is interrupted
     * @throws RuntimeException     if the process exceeds {@code timeoutSec}
     */
    private ExecResult exec(long timeoutSec, Consumer<String> onLine, String... args)
            throws IOException, InterruptedException {

        Process proc = new ProcessBuilder(buildCommand(args)).start();

        StringBuilder err = new StringBuilder();
        Thread errThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    err.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // process terminated; nothing actionable here
            }
        }, "sc-stderr-reader");
        errThread.setDaemon(true);
        errThread.start();

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                onLine.accept(line);
            }
        }

        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            errThread.join(500);
            throw new RuntimeException("sc " + String.join(" ", args) + " timed out");
        }
        errThread.join(500);
        return new ExecResult(proc.exitValue(), err.toString());
    }

    /**
     * Builds the argument vector for {@link ProcessBuilder}, prepending the
     * {@code sc} binary name.
     */
    private static String[] buildCommand(String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "sc";
        System.arraycopy(args, 0, cmd, 1, args.length);
        return cmd;
    }

    /**
     * Removes ANSI SGR (color/style) escape sequences from a string so that
     * {@code contains(...)} and regex matching work on the visible text.
     */
    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    /**
     * Returns the first capture group of {@code regex} in {@code text}, or
     * {@code null} if there is no match.
     */
    private static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Immutable result of running an {@code sc} subcommand. */
    private static final class ExecResult {
        final int exit;
        final String stderr;

        ExecResult(int exit, String stderr) {
            this.exit = exit;
            this.stderr = stderr;
        }

        int exit() { return exit; }
        String stderr() { return stderr; }
    }
}