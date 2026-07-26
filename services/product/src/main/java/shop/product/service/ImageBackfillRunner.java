package shop.product.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// One-time data migration (Р5): moves image bytes still sitting in the legacy `good.image` bytea
// column into object storage, then clears the column. Runs at startup after the bucket is ready
// (@Order after StorageConfig#storageInitializer, which is @Order(1)).
//
// Idempotent: it only touches rows that still hold bytes and have no key, so a second run — or a
// restart after a partial run — finds nothing. Reads the column with raw JDBC on purpose: the row
// is drained here so the catalog no longer serves bytea, but the column itself is only dropped in a
// later "contract" migration (Flyway applies migrations before this runner, so a DROP could not
// share a release with the backfill).
@Component
@Order(2)
public class ImageBackfillRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ImageBackfillRunner.class);

  private final JdbcTemplate jdbc;
  private final ImageStorage imageStorage;

  public ImageBackfillRunner(JdbcTemplate jdbc, ImageStorage imageStorage) {
    this.jdbc = jdbc;
    this.imageStorage = imageStorage;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<Long> ids =
        jdbc.queryForList(
            "SELECT id FROM good WHERE image_key IS NULL AND image IS NOT NULL", Long.class);
    if (ids.isEmpty()) {
      return;
    }
    log.info("Backfilling {} legacy image(s) into object storage", ids.size());
    for (Long id : ids) {
      byte[] bytes = jdbc.queryForObject("SELECT image FROM good WHERE id = ?", byte[].class, id);
      if (bytes == null || bytes.length == 0) {
        continue;
      }
      String key = imageStorage.put(bytes);
      jdbc.update("UPDATE good SET image_key = ?, image = NULL WHERE id = ?", key, id);
    }
    log.info("Backfilled {} legacy image(s) into object storage", ids.size());
  }
}
