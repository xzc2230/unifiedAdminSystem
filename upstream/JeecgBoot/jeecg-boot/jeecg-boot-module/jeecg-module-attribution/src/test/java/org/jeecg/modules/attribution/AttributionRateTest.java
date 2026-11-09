package org.jeecg.modules.attribution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class AttributionRateTest {
  @Test
  void concurrentInsertBelowLimitRetriesIncrement() throws Exception {
    race(2);
  }

  @Test
  void concurrentInsertAtLimitStillRejects() throws Exception {
    race(1);
  }

  private void race(int limit) throws Exception {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    var missed = new CountDownLatch(1);
    var inserted = new CountDownLatch(1);
    var pause = new AtomicBoolean(true);
    var jdbc =
        new JdbcTemplate(source) {
          @Override
          public int update(String sql, Object... args) {
            int changed = super.update(sql, args);
            // Pause precisely after an UPDATE misses the absent row, before the SELECT.
            if (sql.startsWith("UPDATE attr_rate_limit")
                && changed == 0
                && pause.compareAndSet(true, false)) {
              missed.countDown();
              try {
                if (!inserted.await(10, TimeUnit.SECONDS))
                  throw new AssertionError("Concurrent insert timed out");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
            }
            return changed;
          }
        };
    jdbc.execute(
        "CREATE TABLE attr_rate_limit(bucket_digest VARCHAR(64),window_start BIGINT,request_count"
            + " INT,PRIMARY KEY(bucket_digest,window_start))");
    var repository = new AttributionRepository(jdbc, new DataSourceTransactionManager(source));
    var executor = Executors.newSingleThreadExecutor();
    try {
      var pending = executor.submit(() -> repository.rate("same-bucket", limit));
      assertTrue(missed.await(10, TimeUnit.SECONDS));
      repository.rate("same-bucket", limit);
      inserted.countDown();
      if (limit == 1) {
        var failure =
            assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
        assertEquals(429, assertInstanceOf(AttributionFailure.class, failure.getCause()).status);
      } else pending.get(10, TimeUnit.SECONDS);
      assertEquals(
          limit,
          jdbc.queryForObject("SELECT SUM(request_count) FROM attr_rate_limit", Integer.class));
      assertEquals(
          429,
          assertThrows(AttributionFailure.class, () -> repository.rate("same-bucket", limit))
              .status);
      assertEquals(
          limit,
          jdbc.queryForObject("SELECT SUM(request_count) FROM attr_rate_limit", Integer.class));
    } finally {
      inserted.countDown();
      executor.shutdownNow();
      jdbc.execute("DROP ALL OBJECTS");
    }
  }
}
