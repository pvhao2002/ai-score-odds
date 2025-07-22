package com.app.kira.util;

import com.app.kira.server.ServerInfoService;
import com.app.kira.spring.ApplicationContextProvider;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

@Log
@UtilityClass
public class PlaywrightUtil {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final ServerInfoService SERVER_BEAN = ApplicationContextProvider.getBean(ServerInfoService.class);
    private boolean isHeadless = false;
    private boolean isUseProxy = true;

    public <P> void withPlaywright(List<P> list, BiConsumer<Page, List<P>> logic) {
        try (var playwright = Playwright.create()) {
            var browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(isHeadless));
            var context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(USER_AGENT));

            var page = context.newPage();
            logic.accept(page, list);

            page.close();
            context.close();
            browser.close();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error during Playwright task", e);
        }
    }

    public boolean updateHeadless(boolean headless) {
        if (isHeadless == headless) {
            return false;
        }
        isHeadless = headless;
        log.log(Level.INFO, "Playwright headless mode updated to: {0}", isHeadless);
        return isHeadless;
    }
}
