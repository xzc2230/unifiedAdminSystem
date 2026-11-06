package org.jeecg.modules.analytics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "siteplatform.analytics.clickhouse")
public class ClickHouseProperties {
    private boolean enabled;
    private String endpoint = "http://127.0.0.1:18123";
    private String writerUser = "";
    private String writerPassword = "";
    private String readerUser = "";
    private String readerPassword = "";
    private int capacity = 10000;
    private int batchSize = 1000;
    private int flushSeconds = 5;
    private int timeoutSeconds = 5;
}
