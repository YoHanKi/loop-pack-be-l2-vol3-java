package com.loopers.domain.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정렬 구조 비교 실험: 비정규화(like_count) vs 다차원 MaterializedView
 *
 * <p>비교 관점:
 * <ul>
 *   <li>MV 갱신 전략 3종 (Sync / Async / Batch) — 정합성 지연 및 동시성 정확도</li>
 *   <li>다차원 쿼리 — 비정규화가 답할 수 없는 브랜드별 랭킹, 시간 윈도우 트렌딩</li>
 *   <li>비정규화 vs 잘 설계된 MV — 단순 집계 성능, 다차원 집계, 복구 비교</li>
 *   <li>동시성 — 비정규화 원자 UPDATE vs MV Sync 동시 갱신 정확도</li>
 * </ul>
 *
 * <p>product_like_stats 테이블 설계:
 * <pre>
 *   (product_id, brand_id, time_window, like_count, refreshed_at)
 *   PK: (product_id, time_window)
 *   time_window: 'all' | '7d' | '1d'
 * </pre>
 * 단일 테이블에 여러 집계 축을 저장함으로써 brand_id + time_window 조합 쿼리를
 * likes 테이블 재스캔 없이 인덱스만으로 처리 가능.
 */
@SpringBootTest
@DisplayName("정렬 구조 비교 실험: 비정규화(like_count) vs 다차원 MaterializedView")
class SortStrategyComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(SortStrategyComparisonTest.class);

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS product_like_stats (" +
            "  product_id   BIGINT       NOT NULL," +
            "  brand_id     BIGINT       NOT NULL," +
            "  time_window  VARCHAR(10)  NOT NULL," +
            "  like_count   BIGINT       NOT NULL DEFAULT 0," +
            "  refreshed_at DATETIME(6)  NOT NULL," +
            "  PRIMARY KEY (product_id, time_window)" +
            ")"
        );
        brandService.createBrand("nike", "Nike");
        brandService.createBrand("adidas", "Adidas");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS product_like_stats");
        databaseCleanUp.truncateAllTables();
    }

    // ============================================================
    // 헬퍼
    // ============================================================

    private Long getProductDbId(String productId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE product_id = ?", Long.class, productId
        );
    }

    private Long getBrandDbId(String productId) {
        return jdbcTemplate.queryForObject(
            "SELECT ref_brand_id FROM products WHERE product_id = ?", Long.class, productId
        );
    }

    private long getStatsCount(Long productDbId, String timeWindow) {
        List<Long> result = jdbcTemplate.queryForList(
            "SELECT like_count FROM product_like_stats WHERE product_id = ? AND time_window = ?",
            Long.class, productDbId, timeWindow
        );
        return result.isEmpty() ? 0L : result.get(0);
    }

    private long aggregateLikeCount(Long productDbId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM likes WHERE ref_product_id = ? AND deleted_at IS NULL",
            Long.class, productDbId
        );
        return count == null ? 0L : count;
    }

    /** like INSERT만 수행. stats 미갱신 — Batch/Async 전략 시뮬레이션용 */
    private void insertLikeOnly(Long memberId, Long productRefId) {
        jdbcTemplate.update(
            "INSERT INTO likes (ref_member_id, ref_product_id, created_at, updated_at)" +
            " VALUES (?, ?, NOW(6), NOW(6))",
            memberId, productRefId
        );
    }

    /** created_at을 10일 전으로 설정 — 7일 윈도우 밖의 오래된 좋아요 시뮬레이션 */
    private void insertOldLike(Long memberId, Long productRefId) {
        jdbcTemplate.update(
            "INSERT INTO likes (ref_member_id, ref_product_id, created_at, updated_at)" +
            " VALUES (?, ?, NOW(6) - INTERVAL 10 DAY, NOW(6))",
            memberId, productRefId
        );
    }

    /**
     * Sync 전략: like INSERT와 stats UPSERT를 동일 트랜잭션으로 묶음.
     * ON DUPLICATE KEY UPDATE로 stats의 like_count를 원자 증가.
     */
    private void insertLikeSync(Long memberId, Long productRefId, Long brandRefId) {
        transactionTemplate.execute(status -> {
            jdbcTemplate.update(
                "INSERT INTO likes (ref_member_id, ref_product_id, created_at, updated_at)" +
                " VALUES (?, ?, NOW(6), NOW(6))",
                memberId, productRefId
            );
            jdbcTemplate.update(
                "INSERT INTO product_like_stats (product_id, brand_id, time_window, like_count, refreshed_at)" +
                " VALUES (?, ?, 'all', 1, NOW(6))" +
                " ON DUPLICATE KEY UPDATE like_count = like_count + 1, refreshed_at = NOW(6)",
                productRefId, brandRefId
            );
            return null;
        });
    }

    /** Batch 전략: 전체 윈도우 일괄 재집계 */
    private void batchRefreshAll() {
        jdbcTemplate.update(
            "INSERT INTO product_like_stats (product_id, brand_id, time_window, like_count, refreshed_at)" +
            " SELECT l.ref_product_id, p.ref_brand_id, 'all', COUNT(*), NOW(6)" +
            " FROM likes l JOIN products p ON l.ref_product_id = p.id" +
            " WHERE l.deleted_at IS NULL" +
            " GROUP BY l.ref_product_id, p.ref_brand_id" +
            " ON DUPLICATE KEY UPDATE like_count = VALUES(like_count), refreshed_at = NOW(6)"
        );
    }

    /** Batch 전략: 7일 윈도우 재집계 */
    private void batchRefresh7d() {
        jdbcTemplate.update(
            "INSERT INTO product_like_stats (product_id, brand_id, time_window, like_count, refreshed_at)" +
            " SELECT l.ref_product_id, p.ref_brand_id, '7d', COUNT(*), NOW(6)" +
            " FROM likes l JOIN products p ON l.ref_product_id = p.id" +
            " WHERE l.deleted_at IS NULL AND l.created_at >= NOW(6) - INTERVAL 7 DAY" +
            " GROUP BY l.ref_product_id, p.ref_brand_id" +
            " ON DUPLICATE KEY UPDATE like_count = VALUES(like_count), refreshed_at = NOW(6)"
        );
    }

    // ============================================================
    // Section 1: MV 갱신 전략 3종 비교
    // ============================================================

    @Test
    @DisplayName("[Sync 전략] like INSERT와 stats UPSERT 동일 트랜잭션 → 즉시 정합성")
    void mvSync_sameTransaction_immediateConsistency() {
        productService.createProduct("prodA", "nike", "Nike Air", new BigDecimal("100000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long nikeDbId = getBrandDbId("prodA");

        insertLikeSync(1L, prodADbId, nikeDbId);
        insertLikeSync(2L, prodADbId, nikeDbId);

        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(2);
        assertThat(aggregateLikeCount(prodADbId)).isEqualTo(2);
        log.info("[Sync] stats.all={} / likes.count={}", getStatsCount(prodADbId, "all"), aggregateLikeCount(prodADbId));
    }

    @Test
    @DisplayName("[Async 전략] like INSERT 후 stats 갱신 전 — 불일치 구간 존재 → 갱신 후 동기화")
    void mvAsync_inconsistencyWindow_thenEventuallyConsistent() {
        productService.createProduct("prodA", "nike", "Nike Air", new BigDecimal("100000"), 10);
        Long prodADbId = getProductDbId("prodA");

        // Step 1: like만 INSERT (async consumer 아직 미실행)
        insertLikeOnly(1L, prodADbId);
        insertLikeOnly(2L, prodADbId);

        // 불일치 구간: likes=2, stats=0
        assertThat(aggregateLikeCount(prodADbId)).isEqualTo(2);
        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(0);
        log.info("[Async — 불일치 구간] likes={}, stats.all={}", aggregateLikeCount(prodADbId), getStatsCount(prodADbId, "all"));

        // Step 2: 비동기 consumer 실행 (별도 트랜잭션)
        batchRefreshAll();

        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(2);
        log.info("[Async — 동기화 후] likes={}, stats.all={}", aggregateLikeCount(prodADbId), getStatsCount(prodADbId, "all"));
    }

    @Test
    @DisplayName("[Batch 전략] 다수 좋아요 후 배치 실행 — stale 구간 확인, 배치 후 일괄 갱신")
    void mvBatch_accumulatedLikes_staleUntilBatch() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long prodBDbId = getProductDbId("prodB");

        for (int i = 0; i < 5; i++) insertLikeOnly((long) i + 1, prodADbId);
        for (int i = 0; i < 2; i++) insertLikeOnly((long) i + 10, prodBDbId);

        // 배치 전: stats 없음
        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(0);
        assertThat(getStatsCount(prodBDbId, "all")).isEqualTo(0);

        batchRefreshAll();

        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(5);
        assertThat(getStatsCount(prodBDbId, "all")).isEqualTo(2);
        log.info("[Batch] prodA.all={}, prodB.all={}", getStatsCount(prodADbId, "all"), getStatsCount(prodBDbId, "all"));
    }

    @Test
    @DisplayName("[전략 비교] Sync는 즉시 정확, Batch는 stale → 배치 후 정확")
    void strategy_syncVsBatch_consistencyDifference() throws InterruptedException {
        productService.createProduct("prodSync", "nike", "Nike Sync", new BigDecimal("100000"), 10);
        productService.createProduct("prodBatch", "nike", "Nike Batch", new BigDecimal("100000"), 10);
        Long syncDbId = getProductDbId("prodSync");
        Long batchDbId = getProductDbId("prodBatch");
        Long nikeDbId = getBrandDbId("prodSync");

        int threadCount = 20;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    insertLikeSync(memberId, syncDbId, nikeDbId);
                    insertLikeOnly(memberId + 100L, batchDbId);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Sync: 즉시 정확
        assertThat(getStatsCount(syncDbId, "all")).isEqualTo(threadCount);
        // Batch: 배치 전 stale
        assertThat(getStatsCount(batchDbId, "all")).isEqualTo(0);
        // Batch 실행 후 정확
        batchRefreshAll();
        assertThat(getStatsCount(batchDbId, "all")).isEqualTo(threadCount);

        log.info("[전략 비교] Sync(즉시)={} / Batch(stale→갱신 후)={}",
            getStatsCount(syncDbId, "all"), getStatsCount(batchDbId, "all"));
    }

    // ============================================================
    // Section 2: 다차원 쿼리 — 비정규화가 답할 수 없는 것
    // ============================================================

    @Test
    @DisplayName("[다차원 — 브랜드별 랭킹] 브랜드 내 인기 순위를 stats 단일 쿼리로 — products 스캔 없음")
    void multidimensional_brandRanking_noProductsScan() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        productService.createProduct("prodC", "adidas", "Adidas Run C", new BigDecimal("90000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long prodBDbId = getProductDbId("prodB");
        Long prodCDbId = getProductDbId("prodC");
        Long nikeDbId = getBrandDbId("prodA");
        Long adidasDbId = getBrandDbId("prodC");

        // nike: prodA=3, prodB=1 / adidas: prodC=5
        for (int i = 0; i < 3; i++) insertLikeSync((long) i + 1, prodADbId, nikeDbId);
        insertLikeSync(10L, prodBDbId, nikeDbId);
        for (int i = 0; i < 5; i++) insertLikeSync((long) i + 20, prodCDbId, adidasDbId);

        // nike 브랜드 내 인기 순위: stats 테이블 단독 쿼리 (products JOIN 불필요)
        List<Long> nikeRanking = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_like_stats" +
            " WHERE brand_id = ? AND time_window = 'all'" +
            " ORDER BY like_count DESC",
            Long.class, nikeDbId
        );

        assertThat(nikeRanking).hasSize(2);
        assertThat(nikeRanking.get(0)).isEqualTo(prodADbId); // 3 likes
        assertThat(nikeRanking.get(1)).isEqualTo(prodBDbId); // 1 like
        // adidas prodC는 nike 랭킹에 포함되지 않음
        assertThat(nikeRanking).doesNotContain(prodCDbId);
        log.info("[브랜드 랭킹] nike 순위: {}위={}, {}위={}", 1, nikeRanking.get(0), 2, nikeRanking.get(1));
    }

    @Test
    @DisplayName("[다차원 — 시간 윈도우] 전체 랭킹 vs 7일 트렌딩 — 오래된 인기 상품과 최신 급상승 상품 순위 역전")
    void multidimensional_timeWindow_rankingReverts() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long prodBDbId = getProductDbId("prodB");
        Long nikeDbId = getBrandDbId("prodA");

        // prodA: 오래된 좋아요 5개 (7일 윈도우 밖) → 전체 순위는 높지만 트렌딩은 낮음
        for (int i = 0; i < 5; i++) insertOldLike((long) i + 1, prodADbId);
        // prodB: 최근 좋아요 3개 (7일 이내) → 전체 순위는 낮지만 트렌딩은 높음
        for (int i = 0; i < 3; i++) insertLikeOnly((long) i + 10, prodBDbId);

        batchRefreshAll();
        batchRefresh7d();

        // 전체 랭킹: prodA(5) > prodB(3)
        List<Long> allRanking = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_like_stats" +
            " WHERE brand_id = ? AND time_window = 'all' ORDER BY like_count DESC",
            Long.class, nikeDbId
        );
        assertThat(allRanking.get(0)).isEqualTo(prodADbId);

        // 7일 트렌딩: prodB(3) > prodA(0) — 순위 역전
        List<Long> trending7d = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_like_stats" +
            " WHERE brand_id = ? AND time_window = '7d' ORDER BY like_count DESC",
            Long.class, nikeDbId
        );
        assertThat(trending7d.get(0)).isEqualTo(prodBDbId);

        log.info("[시간 윈도우] 전체 1위={} / 7일 트렌딩 1위={} — 순위 역전",
            allRanking.get(0), trending7d.get(0));
    }

    @Test
    @DisplayName("[다차원 — 브랜드 + 기간 조합] nike의 7일 트렌딩 — stats 단일 쿼리, adidas 자동 격리")
    void multidimensional_brandAndTimeWindow_combined() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        productService.createProduct("prodC", "adidas", "Adidas Run C", new BigDecimal("90000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long prodBDbId = getProductDbId("prodB");
        Long prodCDbId = getProductDbId("prodC");
        Long nikeDbId = getBrandDbId("prodA");
        Long adidasDbId = getBrandDbId("prodC");

        // nike prodA: 최근 2개
        for (int i = 0; i < 2; i++) insertLikeOnly((long) i + 1, prodADbId);
        // nike prodB: 최근 4개
        for (int i = 0; i < 4; i++) insertLikeOnly((long) i + 10, prodBDbId);
        // adidas prodC: 최근 100개 — 다른 브랜드, 섞이지 않아야 함
        for (int i = 0; i < 100; i++) insertLikeOnly((long) i + 100, prodCDbId);

        batchRefresh7d();

        // nike의 7일 트렌딩: brand_id = nike AND time_window = '7d'
        List<Long> nikeTrending7d = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_like_stats" +
            " WHERE brand_id = ? AND time_window = '7d' ORDER BY like_count DESC",
            Long.class, nikeDbId
        );

        assertThat(nikeTrending7d).hasSize(2);
        assertThat(nikeTrending7d.get(0)).isEqualTo(prodBDbId); // 4 likes
        assertThat(nikeTrending7d.get(1)).isEqualTo(prodADbId); // 2 likes
        assertThat(nikeTrending7d).doesNotContain(prodCDbId);   // adidas 자동 격리
        log.info("[브랜드+기간] nike 7일 트렌딩: 1위={}, 2위={}", nikeTrending7d.get(0), nikeTrending7d.get(1));
    }

    // ============================================================
    // Section 3: 비정규화 vs 잘 설계된 MV 비교
    // ============================================================

    @Test
    @DisplayName("[비교 — 단순 집계] 비정규화(인덱스) vs MV+JOIN — 응답시간 및 결과 동일")
    void comparison_simpleAggregate_denormVsMv_sameResultDifferentPath() {
        for (int i = 1; i <= 5; i++) {
            String productId = "prod" + i;
            productService.createProduct(productId, "nike", "Nike " + i, new BigDecimal("100000"), 10);
            Long dbId = getProductDbId(productId);
            Long nikeDbId = getBrandDbId(productId);
            for (int j = 0; j < i; j++) {
                insertLikeSync((long) (j + i * 10), dbId, nikeDbId);
            }
        }

        // warm-up
        jdbcTemplate.queryForList(
            "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 5",
            String.class
        );

        // 비정규화: products.like_count 인덱스 직접 활용
        long denormStart = System.nanoTime();
        List<String> denormResult = jdbcTemplate.queryForList(
            "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 5",
            String.class
        );
        long denormNs = System.nanoTime() - denormStart;

        // MV: stats 집계 후 products JOIN
        long mvStart = System.nanoTime();
        List<String> mvResult = jdbcTemplate.queryForList(
            "SELECT p.product_id FROM product_like_stats s" +
            " JOIN products p ON s.product_id = p.id" +
            " WHERE s.time_window = 'all' AND p.deleted_at IS NULL" +
            " ORDER BY s.like_count DESC LIMIT 5",
            String.class
        );
        long mvNs = System.nanoTime() - mvStart;

        assertThat(denormResult).isEqualTo(mvResult);
        log.info("[단순 집계] 비정규화={}ms / MV+JOIN={}ms / 비율={}배",
            String.format("%.3f", denormNs / 1_000_000.0),
            String.format("%.3f", mvNs / 1_000_000.0),
            String.format("%.2f", (double) mvNs / Math.max(denormNs, 1)));
    }

    @Test
    @DisplayName("[비교 — 다차원 집계] MV는 인덱스 스캔, 비정규화는 likes 전체 스캔 + GROUP BY 불가피")
    void comparison_multidimensionalAggregate_mvRequiresNoFullScan() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        productService.createProduct("prodC", "adidas", "Adidas Run C", new BigDecimal("90000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long prodBDbId = getProductDbId("prodB");
        Long prodCDbId = getProductDbId("prodC");
        Long nikeDbId = getBrandDbId("prodA");
        Long adidasDbId = getBrandDbId("prodC");

        // nike prodA: 최근 3개, nike prodB: 최근 5개
        for (int i = 0; i < 3; i++) insertLikeOnly((long) i + 1, prodADbId);
        for (int i = 0; i < 5; i++) insertLikeOnly((long) i + 10, prodBDbId);
        // adidas prodC: 최근 10개 — 비교에서 제외되어야 함
        for (int i = 0; i < 10; i++) insertLikeOnly((long) i + 20, prodCDbId);

        batchRefresh7d();

        // 비정규화로 "nike의 7일 트렌딩"을 구하려면 → likes 전체 스캔 + GROUP BY 불가피
        List<Long> denormApproach = jdbcTemplate.queryForList(
            "SELECT p.id FROM products p" +
            " LEFT JOIN (" +
            "   SELECT ref_product_id, COUNT(*) AS cnt FROM likes" +
            "   WHERE deleted_at IS NULL AND created_at >= NOW(6) - INTERVAL 7 DAY" +
            "   GROUP BY ref_product_id" +
            " ) l ON p.id = l.ref_product_id" +
            " WHERE p.ref_brand_id = ? AND p.deleted_at IS NULL AND COALESCE(l.cnt, 0) > 0" +
            " ORDER BY COALESCE(l.cnt, 0) DESC",
            Long.class, nikeDbId
        );

        // MV로 "nike의 7일 트렌딩" → stats 인덱스 단독 스캔
        List<Long> mvApproach = jdbcTemplate.queryForList(
            "SELECT product_id FROM product_like_stats" +
            " WHERE brand_id = ? AND time_window = '7d'" +
            " ORDER BY like_count DESC",
            Long.class, nikeDbId
        );

        assertThat(mvApproach).isEqualTo(denormApproach);
        assertThat(mvApproach).doesNotContain(prodCDbId);
        assertThat(mvApproach.get(0)).isEqualTo(prodBDbId); // 5 likes

        log.info("[다차원 집계] 비정규화(likes 전체 스캔+GROUP BY) vs MV(인덱스): 결과 일치");
        log.info("[다차원 집계] MV는 likes 테이블을 전혀 읽지 않음 — 데이터 증가 시 격차 기하급수적으로 커짐");
    }

    @Test
    @DisplayName("[비교 — 복구] 비정규화는 수동 DBA SQL 필요, MV는 배치 재실행으로 자동 복구")
    void comparison_recovery_denormManualVsMvAutomatic() {
        productService.createProduct("prodA", "nike", "Nike Air", new BigDecimal("100000"), 10);
        Long prodADbId = getProductDbId("prodA");
        Long nikeDbId = getBrandDbId("prodA");

        for (int i = 0; i < 3; i++) insertLikeSync((long) i + 1, prodADbId, nikeDbId);
        batchRefreshAll();

        // 오염 시뮬레이션 (배포 버그, 직접 DB 조작 등)
        jdbcTemplate.update(
            "UPDATE product_like_stats SET like_count = 999 WHERE product_id = ? AND time_window = 'all'",
            prodADbId
        );
        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(999);

        // MV 복구: 배치 재실행만으로 자동 복구 (멱등)
        batchRefreshAll();

        assertThat(getStatsCount(prodADbId, "all")).isEqualTo(3);
        log.info("[복구] MV 배치 재실행으로 999 → 3 자동 복구. 비정규화는 DBA가 재집계 SQL 수동 실행 필요.");
    }

    // ============================================================
    // Section 4: 동시성 정확성
    // ============================================================

    @Test
    @DisplayName("[동시성 — 비정규화] 50 스레드 동시 좋아요 → like_count = 50 (원자 UPDATE 보장)")
    void denormalization_concurrent50Likes_likeCountEquals50() throws InterruptedException {
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        int threadCount = 50;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    likeService.addLike(memberId, "prod1");
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        Long likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM products WHERE product_id = 'prod1'", Long.class
        );
        long aggregateCount = aggregateLikeCount(getProductDbId("prod1"));

        log.info("[동시성 — 비정규화] success={}, like_count={}, aggregate={}", successCount.get(), likeCount, aggregateCount);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(likeCount).isEqualTo((long) threadCount);
        assertThat(likeCount).isEqualTo(aggregateCount);
    }

    @Test
    @DisplayName("[동시성 — MV Sync] 30 스레드 동시 Sync 갱신 → stats.all = 30 (ON DUPLICATE KEY UPDATE 원자 보장)")
    void mvSync_concurrent30Likes_statsEquals30() throws InterruptedException {
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        Long prodDbId = getProductDbId("prod1");
        Long nikeDbId = getBrandDbId("prod1");

        int threadCount = 30;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    insertLikeSync(memberId, prodDbId, nikeDbId);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        long statsCount = getStatsCount(prodDbId, "all");
        long aggregateCount = aggregateLikeCount(prodDbId);

        log.info("[동시성 — MV Sync] stats.all={}, aggregate={}", statsCount, aggregateCount);
        assertThat(statsCount).isEqualTo(aggregateCount);
        assertThat(statsCount).isEqualTo((long) threadCount);
    }
}
