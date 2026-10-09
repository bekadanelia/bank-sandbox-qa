package io.github.bekadanelia.sandboxbank.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Gives every UI test a clean browser context and a {@link Page} parameter.
 *
 * <p>One browser is launched per test class and one isolated context per test, so tests share
 * no cookies or storage. Playwright objects are bound to the thread that created them, which
 * fits the suite's parallel mode: classes run side by side, methods of a class run in order.
 *
 * <p>When a test fails, a screenshot and a Playwright trace go into the report. When a test
 * passes but the page threw an uncaught JavaScript error, the test is failed for that.
 */
public final class BrowserExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(BrowserExtension.class);
    private static final Path TRACES = Path.of("target", "playwright-traces");
    private static final double ACTION_TIMEOUT_MILLIS = 10_000;

    /** Closed by JUnit when the test class has finished. */
    private record ClassBrowser(Playwright playwright, Browser browser) implements AutoCloseable {

        @Override
        public void close() {
            browser.close();
            playwright.close();
        }
    }

    private record TestSession(BrowserContext context, Page page, List<String> pageErrors) {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).computeIfAbsent(ClassBrowser.class, key -> launch(), ClassBrowser.class);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Browser browser = context.getStore(NAMESPACE).get(ClassBrowser.class, ClassBrowser.class).browser();
        BrowserContext browserContext = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(TestConfig.baseUrl())
                .setViewportSize(1280, 900)
                .setLocale("en-GB"));
        browserContext.setDefaultTimeout(ACTION_TIMEOUT_MILLIS);
        browserContext.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));

        Page page = browserContext.newPage();
        List<String> pageErrors = new CopyOnWriteArrayList<>();
        page.onPageError(pageErrors::add);
        context.getStore(NAMESPACE).put(TestSession.class, new TestSession(browserContext, page, pageErrors));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestSession session = context.getStore(NAMESPACE).remove(TestSession.class, TestSession.class);
        if (session == null) {
            return;
        }
        boolean failed = context.getExecutionException().isPresent();
        try {
            if (failed) {
                attachEvidence(context, session);
            } else {
                session.context().tracing().stop();
            }
        } finally {
            session.context().close();
        }
        if (!failed && !session.pageErrors().isEmpty()) {
            throw new AssertionError("The page threw uncaught JavaScript errors: " + session.pageErrors());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).get(TestSession.class, TestSession.class).page();
    }

    private static ClassBrowser launch() {
        Playwright playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(TestConfig.headless())
                .setSlowMo(TestConfig.slowMotionMillis());
        TestConfig.browserExecutable().ifPresent(options::setExecutablePath);
        BrowserType type = switch (TestConfig.browser()) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException(
                    "Unknown browser '" + TestConfig.browser() + "'. Use chromium, firefox or webkit.");
        };
        return new ClassBrowser(playwright, type.launch(options));
    }

    private static void attachEvidence(ExtensionContext context, TestSession session) {
        byte[] screenshot = session.page().screenshot(new Page.ScreenshotOptions().setFullPage(true));
        Allure.attachment("Screenshot at failure", "image/png", new ByteArrayInputStream(screenshot),
                AttachmentOptions.withFileExtension("png"));
        if (!session.pageErrors().isEmpty()) {
            Allure.attachment("Uncaught JavaScript errors", String.join("\n", session.pageErrors()));
        }
        try {
            Files.createDirectories(TRACES);
            Path trace = TRACES.resolve(context.getRequiredTestClass().getSimpleName() + "."
                    + context.getRequiredTestMethod().getName() + ".zip");
            session.context().tracing().stop(new Tracing.StopOptions().setPath(trace));
            // Open the file at https://trace.playwright.dev to replay the test step by step.
            Allure.attachment("Playwright trace", "application/zip",
                    new ByteArrayInputStream(Files.readAllBytes(trace)), AttachmentOptions.withFileExtension("zip"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
