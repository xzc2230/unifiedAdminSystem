package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStatDetectConfig;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStatDetectHistory;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatDetectConfigMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatDetectHistoryMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatMapper;

/**
 * Isolated store-page detection round. Only scans drop_date IS NULL.
 */
@Service
public class PackStatDetectService
{
    private static final Logger log = LoggerFactory.getLogger(PackStatDetectService.class);
    private static final int HISTORY_KEEP_DAYS = 5;
    private static final int DEFAULT_INTERVAL_SECONDS = 2;
    private static final int DEFAULT_THREAD_COUNT = 1;
    private static final int DEFAULT_FREQUENCY_MINUTES = 10;

    private final Object apiIntervalLock = new Object();
    private final PackStatHttpClient httpClient = new PackStatHttpClient();
    private volatile long lastApiCallAtMillis;
    private volatile int currentIntervalSeconds = DEFAULT_INTERVAL_SECONDS;

    @Autowired
    private PackStatMapper packStatMapper;

    @Autowired
    private PackStatDetectConfigMapper detectConfigMapper;

    @Autowired
    private PackStatDetectHistoryMapper detectHistoryMapper;

    @Autowired
    private PackStatMailNotifier mailNotifier;

    public void runRound()
    {
        PackStatDetectConfig config = getOrCreateConfig();
        currentIntervalSeconds = config.getApiIntervalSeconds() == null
                ? DEFAULT_INTERVAL_SECONDS : config.getApiIntervalSeconds();
        List<PackStat> rows = packStatMapper.selectPendingDetectList();
        if (rows == null || rows.isEmpty())
        {
            detectHistoryMapper.deleteOlderThanDays(HISTORY_KEEP_DAYS);
            return;
        }
        int threadCount = resolveThreadCount(config);
        if (threadCount <= 1)
        {
            for (int i = 0; i < rows.size(); i++)
            {
                detectOne(rows.get(i), config);
            }
        }
        else
        {
            runConcurrent(rows, config, threadCount);
        }
        detectHistoryMapper.deleteOlderThanDays(HISTORY_KEEP_DAYS);
    }

    private void runConcurrent(final List<PackStat> rows, final PackStatDetectConfig config, int threadCount)
    {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try
        {
            List<Future<?>> futures = new ArrayList<Future<?>>();
            for (int i = 0; i < rows.size(); i++)
            {
                final PackStat row = rows.get(i);
                futures.add(executor.submit(new Callable<Void>()
                {
                    @Override
                    public Void call()
                    {
                        detectOne(row, config);
                        return null;
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++)
            {
                try
                {
                    futures.get(i).get();
                }
                catch (Exception ex)
                {
                    log.warn("packstat detect worker failed", ex);
                }
            }
        }
        finally
        {
            executor.shutdownNow();
            try
            {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void detectOne(PackStat row, PackStatDetectConfig config)
    {
        if (row == null || StringUtils.isEmpty(row.getStoreUrl()))
        {
            return;
        }
        awaitApiInterval();
        PackStatHttpClient.ProbeOutcome outcome = httpClient.probe(row.getStoreUrl());
        persistHistory(row, outcome);
        PackStatDetectOutcomeApply.StatsUpdate stats = PackStatDetectOutcomeApply.apply(
                outcome.getResult(),
                toLocalDate(row.getPassDate()),
                toLocalDate(row.getCustomerUseDate()),
                LocalDate.now());
        if (stats != null)
        {
            boolean wasDropped = row.getDropDate() != null;
            packStatMapper.updateDetectStats(row.getId(), stats.getDropDate(),
                    stats.getSurviveDays(), stats.getUseDays());
            if (!wasDropped && stats.getDropDate() != null)
            {
                mailNotifier.notifyDelisted(row, config, new Date(), outcome.getMessage());
            }
        }
        adjustApiInterval(config, outcome.getResult() == PackStatDetectResult.INVALID);
    }

    private static LocalDate toLocalDate(Date value)
    {
        if (value == null)
        {
            return null;
        }
        return new java.sql.Date(value.getTime()).toLocalDate();
    }

    private void persistHistory(PackStat row, PackStatHttpClient.ProbeOutcome outcome)
    {
        PackStatDetectHistory history = new PackStatDetectHistory();
        history.setStatId(row.getId());
        history.setStoreUrl(row.getStoreUrl());
        history.setRegion(row.getRegionCode() == null ? "" : row.getRegionCode());
        history.setDetectResult(outcome.getResult().name());
        history.setMessage(outcome.getMessage());
        history.setCheckedAt(new Date());
        detectHistoryMapper.insertDetectHistory(history);
    }

    private void awaitApiInterval()
    {
        int seconds = currentIntervalSeconds;
        if (seconds <= 0)
        {
            synchronized (apiIntervalLock)
            {
                lastApiCallAtMillis = System.currentTimeMillis();
            }
            return;
        }
        long intervalMs = seconds * 1000L;
        synchronized (apiIntervalLock)
        {
            long now = System.currentTimeMillis();
            long waitMs = lastApiCallAtMillis == 0L ? 0L : intervalMs - (now - lastApiCallAtMillis);
            if (waitMs > 0L)
            {
                try
                {
                    Thread.sleep(waitMs);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            lastApiCallAtMillis = System.currentTimeMillis();
        }
    }

    private void adjustApiInterval(PackStatDetectConfig config, boolean hadInvalidResult)
    {
        int current = currentIntervalSeconds;
        int next = PackStatIntervalSupport.nextIntervalSeconds(current, hadInvalidResult);
        if (next == current)
        {
            return;
        }
        currentIntervalSeconds = next;
        if (config.getId() == null)
        {
            return;
        }
        config.setApiIntervalSeconds(Integer.valueOf(next));
        config.setUpdatedAt(new Date());
        detectConfigMapper.updateApiIntervalSeconds(config);
    }

    private PackStatDetectConfig getOrCreateConfig()
    {
        PackStatDetectConfig config = detectConfigMapper.selectOne();
        if (config != null)
        {
            return config;
        }
        PackStatDetectConfig created = new PackStatDetectConfig();
        created.setApiIntervalSeconds(Integer.valueOf(DEFAULT_INTERVAL_SECONDS));
        created.setThreadCount(Integer.valueOf(DEFAULT_THREAD_COUNT));
        created.setDetectionFrequencyMinutes(Integer.valueOf(DEFAULT_FREQUENCY_MINUTES));
        created.setUpdatedAt(new Date());
        detectConfigMapper.insertDetectConfig(created);
        return created;
    }

    private static int resolveThreadCount(PackStatDetectConfig config)
    {
        if (config.getThreadCount() == null || config.getThreadCount().intValue() < 1)
        {
            return DEFAULT_THREAD_COUNT;
        }
        return config.getThreadCount().intValue();
    }
}
