package com.secphils.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Notification email runs OFF the request thread (portal-wide rule).
 *
 * Why: every mail send carries a 10s SMTP connect timeout. On the request
 * path a slow/unreachable relay turned "create project" (1 rep + N staff
 * emails) into a 30–60s silent freeze — the wizard closed, the list never
 * refreshed, the user stared at a stale portal and blamed the list.
 * MailService.sendHtmlAsync is the fire-and-forget door all notification
 * mail uses; @EnableAsync here is what makes it real.
 *
 * Two senders is plenty (mail is I/O-bound and relays rate-limit anyway);
 * the queue absorbs bursts like company-wide announcements. CallerRunsPolicy
 * on a FULL queue deliberately blocks the caller instead of dropping mail —
 * worst case degrades to the old synchronous behavior, never lost email.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("mail-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
