package ac.su.kdt.beaievaluationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리를 위한 Spring 설정
 * AI 평가 서비스의 성능 향상을 위해 비동기 처리를 활성화
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * AI 평가 처리용 비동기 스레드 풀 설정
     * - 코어 스레드: 2개 (기본 처리량)
     * - 최대 스레드: 10개 (피크 시간 대응)
     * - 큐 용량: 50개 (대기 작업 버퍼)
     * - 스레드 유지 시간: 60초 (리소스 절약)
     */
    @Bean(name = "evaluationTaskExecutor")
    public Executor evaluationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("EvaluationAsync-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.warn("Evaluation task rejected, queue is full. Active: {}, Pool: {}, Queue: {}", 
                exec.getActiveCount(), exec.getPoolSize(), exec.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException("Evaluation task queue is full");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Kafka Consumer의 즉시 응답을 위한 경량 스레드 풀
     * - 빠른 작업 위임용 (메시지 수신 → 비동기 처리 시작)
     * - 코어 스레드: 1개 (최소한의 리소스)
     * - 최대 스레드: 5개 (동시 메시지 처리)
     */
    @Bean(name = "kafkaHandlerExecutor")
    public Executor kafkaHandlerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("KafkaHandler-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("Critical: Kafka handler task rejected! This may cause message loss.");
            throw new java.util.concurrent.RejectedExecutionException("Kafka handler queue is full");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}