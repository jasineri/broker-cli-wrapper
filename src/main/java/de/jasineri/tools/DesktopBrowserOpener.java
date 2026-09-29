package de.jasineri.tools;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Opens a URL in the system's default browser, working across Linux, Windows
 * and macOS.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Try {@link java.awt.Desktop#browse(URI)} — the official, portable API.
 *       Works on Windows, macOS and most Linux desktops with a graphical session.</li>
 *   <li>Fall back to the platform-native command ({@code xdg-open},
 *       {@code open}, {@code rundll32}) when {@code Desktop} is unavailable
 *       or disabled (headless JVM, no desktop environment, etc.).</li>
 *   <li>If everything fails, print the URL to {@code System.err} so the user
 *       can open it manually. Never throws.</li>
 * </ol>
 *
 * <p>This class is stateless and thread-safe. It can be used directly as a
 * {@link Consumer}{@code <String>}:
 * <pre>{@code
 * BrokerCli cli = new BrokerCli(new DesktopBrowserOpener());
 * }</pre>
 *
 * <p>Not supported: Android. Android has no {@code java.awt.Desktop} and no
 * shell commands like {@code xdg-open}; opening a browser there requires an
 * {@code Intent}, which cannot be issued from plain JVM code.
 */
public class DesktopBrowserOpener implements Consumer<String> {

    /** {@inheritDoc} */
    @Override
    public void accept(String url) {
        open(url);
    }

    /**
     * Opens the given URL in the default browser. Never throws; on failure
     * the URL is printed to {@code System.err}.
     *
     * @param url the URL to open; must not be {@code null}
     */
    public static void open(String url) {
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }

        if (tryDesktop(url)) {
            return;
        }
        if (tryNativeCommand(url)) {
            return;
        }
        // last resort: tell the user
        System.err.println("Could not open a browser automatically. Please open:");
        System.err.println("  " + url);
    }

    // ------------------------------------------------------------------
    // Strategy 1: java.awt.Desktop
    // ------------------------------------------------------------------

    /**
     * Attempts to open the URL via {@link Desktop#browse(URI)}.
     *
     * @return {@code true} if the URL was handed off to the OS, {@code false}
     *         if the API is unavailable or threw an exception
     */
    private static boolean tryDesktop(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }
            desktop.browse(URI.create(url));
            return true;
        } catch (Exception e) {
            // HeadlessException, IOException, URISyntaxException, ...
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Strategy 2: platform-native commands
    // ------------------------------------------------------------------

    /**
     * Attempts to open the URL via the platform-native command.
     *
     * @return {@code true} if a known OS was detected and the command was
     *         launched, {@code false} otherwise
     */
    private static boolean tryNativeCommand(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                // Windows: rundll32 forwards the URL to the default handler.
                // Do not use "start" — it's a cmd.exe builtin, not an executable.
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
                return true;
            }
            if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
                return tryLinuxOpeners(url);
            }
        } catch (IOException e) {
            // fall through
        }
        return false;
    }

    /**
     * Tries a chain of Linux desktop openers, since no single one is
     * guaranteed to be installed. Order: {@code xdg-open} (freedesktop),
     * {@code gio open} (GNOME), {@code sensible-browser} (Debian/Ubuntu
     * helper), {@code x-www-browser} (Debian alternatives).
     *
     * @return {@code true} if one of the commands started successfully
     */
    private static boolean tryLinuxOpeners(String url) {
        String[][] candidates = {
                {"xdg-open", url},
                {"gio", "open", url},
                {"sensible-browser", url},
                {"x-www-browser", url},
        };
        for (String[] cmd : candidates) {
            if (tryStart(cmd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the given command, returning {@code true} if the process was
     * created successfully. Does not wait for the process to exit.
     */
    private static boolean tryStart(String[] cmd) {
        try {
            new ProcessBuilder(cmd).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Private constructor — use {@link #open(String)} or the instance form. */
    private DesktopBrowserOpener() {
        // static utility + Consumer; no state
    }

    /**
     * Returns a reusable, stateless instance suitable for injection into
     * {@code BrokerCli} or any other {@link Consumer}{@code <String>}.
     */
    public static DesktopBrowserOpener instance() {
        return new DesktopBrowserOpener();
    }
}