package ac.su.kdt.beaievaluationservice.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

/**
 * DevTrip 표준 Kafka 로깅 헬퍼
 * 모든 서비스에서 일관된 Kafka 로그 형식을 위한 유틸리티 클래스
 */
@Slf4j
@Component
public class KafkaLogHelper {
    
    // MDC Keys
    private static final String KAFKA_OPERATION = "kafka.operation";
    private static final String KAFKA_TOPIC = "kafka.topic";
    private static final String KAFKA_KEY = "kafka.key";
    private static final String KAFKA_PARTITION = "kafka.partition";
    private static final String KAFKA_OFFSET = "kafka.offset";
    private static final String KAFKA_EVENT_TYPE = "kafka.eventType";
    
    /**
     * 이벤트 발행 성공 로그
     */
    public void logEventPublished(String topic, String key, String eventType) {
        setKafkaMDC("PUBLISH", topic, key, null, null, eventType);
        log.info("Event published successfully");
        clearKafkaMDC();
    }
    
    /**
     * 이벤트 발행 실패 로그
     */
    public void logEventPublishFailed(String topic, String key, String eventType, Throwable error) {
        setKafkaMDC("PUBLISH", topic, key, null, null, eventType);
        log.error("Event publish failed: {}", error.getMessage(), error);
        clearKafkaMDC();
    }
    
    /**
     * 이벤트 수신 로그
     */
    public void logEventReceived(String topic, Integer partition, Long offset, String eventType) {
        setKafkaMDC("CONSUME", topic, null, partition, offset, eventType);
        log.info("Event received");
        clearKafkaMDC();
    }
    
    /**
     * 이벤트 처리 시작 로그
     */
    public void logEventProcessingStarted(String topic, Integer partition, Long offset, String eventType) {
        setKafkaMDC("PROCESS", topic, null, partition, offset, eventType);
        log.info("Event processing started");
        clearKafkaMDC();
    }
    
    /**
     * 이벤트 처리 완료 로그
     */
    public void logEventProcessingCompleted(String topic, Integer partition, Long offset, String eventType, long processingTimeMs) {
        setKafkaMDC("PROCESS", topic, null, partition, offset, eventType);
        MDC.put("kafka.processingTime", processingTimeMs + "ms");
        log.info("Event processing completed");
        clearKafkaMDC();
    }
    
    /**
     * 이벤트 처리 실패 로그
     */
    public void logEventProcessingFailed(String topic, Integer partition, Long offset, String eventType, Throwable error) {
        setKafkaMDC("PROCESS", topic, null, partition, offset, eventType);
        log.error("Event processing failed: {}", error.getMessage(), error);
        clearKafkaMDC();
    }
    
    /**
     * 헤더에서 자동으로 정보 추출하여 로그
     */
    public void logEventReceived(MessageHeaders headers, String eventType) {
        String topic = (String) headers.get(KafkaHeaders.RECEIVED_TOPIC);
        Integer partition = (Integer) headers.get(KafkaHeaders.RECEIVED_PARTITION);
        Long offset = (Long) headers.get(KafkaHeaders.OFFSET);
        
        logEventReceived(topic, partition, offset, eventType);
    }
    
    /**
     * Consumer에서 상세 로그 (DEBUG 레벨)
     */
    public void logEventDetails(String topic, Integer partition, Long offset, String eventType, Object eventData) {
        if (log.isDebugEnabled()) {
            setKafkaMDC("CONSUME", topic, null, partition, offset, eventType);
            log.debug("Event details: {}", eventData);
            clearKafkaMDC();
        }
    }
    
    /**
     * Producer에서 상세 로그 (DEBUG 레벨)
     */
    public void logEventDetails(String topic, String key, String eventType, Object eventData) {
        if (log.isDebugEnabled()) {
            setKafkaMDC("PUBLISH", topic, key, null, null, eventType);
            log.debug("Event details: {}", eventData);
            clearKafkaMDC();
        }
    }
    
    /**
     * Kafka 관련 MDC 설정
     */
    private void setKafkaMDC(String operation, String topic, String key, Integer partition, Long offset, String eventType) {
        MDC.put(KAFKA_OPERATION, operation);
        if (topic != null) MDC.put(KAFKA_TOPIC, topic);
        if (key != null) MDC.put(KAFKA_KEY, key);
        if (partition != null) MDC.put(KAFKA_PARTITION, partition.toString());
        if (offset != null) MDC.put(KAFKA_OFFSET, offset.toString());
        if (eventType != null) MDC.put(KAFKA_EVENT_TYPE, eventType);
    }
    
    /**
     * Kafka 관련 MDC 정리
     */
    private void clearKafkaMDC() {
        MDC.remove(KAFKA_OPERATION);
        MDC.remove(KAFKA_TOPIC);
        MDC.remove(KAFKA_KEY);
        MDC.remove(KAFKA_PARTITION);
        MDC.remove(KAFKA_OFFSET);
        MDC.remove(KAFKA_EVENT_TYPE);
        MDC.remove("kafka.processingTime");
    }
    
    /**
     * 전체 MDC 정리 (필요시 사용)
     */
    public void clearAllMDC() {
        MDC.clear();
    }
}