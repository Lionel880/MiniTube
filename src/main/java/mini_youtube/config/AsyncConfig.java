package mini_youtube.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 提供 @Async 方法（目前只有 TranscodingService.transcodeToMp4）使用的有界執行緒池。
 *
 * 不設定的話，Spring 預設會用 SimpleAsyncTaskExecutor：不限制執行緒數量，
 * 每次呼叫都開新執行緒。多人同時上傳影片時，會同時開出對應數量的 FFmpeg process，
 * 沒有任何上限，可能把 CPU/記憶體吃滿。這裡限制同時最多 4 支影片並行轉碼，
 * 其餘先進佇列等待，超過佇列上限才觸發 CallerRunsPolicy（退回呼叫端執行緒執行，
 * 避免任務被直接丟棄而導致影片永遠卡在 UPLOADING 狀態）。
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean(name = "videoTranscodingExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("video-transcode-");
        executor.setRejectedExecutionHandler(rejectedExecutionHandler());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectedExecutionHandler() {
        // 佇列也滿了的極端情況，退回呼叫端執行緒執行，而不是直接丟棄任務
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // @Async 方法內部若拋出未被 catch 的例外，預設只會靜默印出而不容易被注意到，
        // 這裡明確記錄下來，方便日後排查「轉碼卡住/失敗但看不出原因」的問題。
        return (throwable, method, params) ->
                log.error("非同步方法執行發生未捕捉例外：{}", method.getName(), throwable);
    }
}
