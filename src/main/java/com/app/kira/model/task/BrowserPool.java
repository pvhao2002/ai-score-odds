package com.app.kira.model.task;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.Data;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

@Data
public class BrowserPool {
    private final Queue<Browser> pool;
    private final Playwright playwright;
    private final int size;

    public BrowserPool(int size) {
        this.size = size;
        playwright = Playwright.create();
        pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            pool.add(browser);
        }
    }

    public Browser borrow() {
        return pool.remove();
    }

    public void release(Browser browser) {
        pool.remove(browser);
    }

    public void closeAll() {
        for (Browser browser : pool) {
            browser.close();
        }
        playwright.close();
    }
}

