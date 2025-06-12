package com.app.kira.model;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BrowserPool {
    private final BlockingQueue<Browser> pool;
    private final Playwright playwright;
    private final int size;

    public BrowserPool(int size) {
        this.size = size;
        playwright = Playwright.create();
        pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            pool.add(browser);
        }
    }

    public Browser borrow() throws InterruptedException {
        return pool.take();
    }

    public void release(Browser browser) {
        pool.offer(browser);
    }

    public void closeAll() {
        for (Browser browser : pool) {
            browser.close();
        }
        playwright.close();
    }
}

