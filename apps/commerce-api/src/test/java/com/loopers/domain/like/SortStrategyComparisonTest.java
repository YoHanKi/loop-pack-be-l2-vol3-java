package com.loopers.domain.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정렬 구조 비교 실험: 비정규화(like_count) vs MaterializedView 시뮬레이션
 *
 * <p>비교 관점:
 * <ul>
 *   <li>즉시 정합성: 좋아요 등록 직후 정렬 결과 반영 여부</li>
 *   <li>정렬 정확성: 두 방식의 정렬 순서가 실제로 동일한가</li>
 *   <li>동시성 정확성: 동시 좋아요 50건 → like_count 정확히 50인가</li>
 *   <li>복구: 카운트 오염 후 각 방식의 복구 절차</li>
 *   <li>성능: 비정규화 정렬 쿼리 vs 실시간 집계 JOIN 정렬 쿼리 응답시간</li>
 * </ul>
 *
 * <p>MaterializedView 시뮬레이션 방식:
 * MySQL 8.x는 네이티브 MV를 미지원하므로, 다음 두 가지로 동작 원리를 재현한다.
 * <ol>
 *   <li>{@code aggregateLikeCount}: likes 테이블 실시간 집계 (MV 배치 실행 직전 "실제" 값)</li>
 *   <li>{@code refreshMaterializedView}: 집계 결과를 products.like_count에 반영 (배치 실행 시뮬레이션)</li>
 * </ol>
 * MV 시나리오 테스트에서는 LikeService 대신 JDBC 직접 INSERT로 likes만 적재하고,
 * products.like_count를 손대지 않아 배치 전 staleness를 재현한다.
 */
@SpringBootTest
@DisplayName("정렬 구조 비교 실험: 비정규화(like_count) vs MaterializedView 시뮬레이션")
class SortStrategyComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(SortStrategyComparisonTest.class);

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    /** likes 테이블 실시간 집계 — MV 배치 실행 직전의 "진짜" 카운트를 확인할 때 사용 */
    private long aggregateLikeCount(Long productRefId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE ref_product_id = ? AND deleted_at IS NULL",
                Long.class, productRefId
        );
        return count == null ? 0L : count;
    }

    /**
     * MV 배치 갱신 시뮬레이션.
     * 실제 운영에서는 @Scheduled 배치가 이 쿼리를 주기적으로 실행한다.
     * products.like_count = (likes 테이블 실시간 집계)
     */
    private void refreshMaterializedView() {
        jdbcTemplate.update(
                "UPDATE products p SET like_count = (" +
                "    SELECT COUNT(*) FROM likes l" +
                "    WHERE l.ref_product_id = p.id AND l.deleted_at IS NULL" +
                ")"
        );
    }

    /** MV 시나리오: LikeService 우회 — likes만 적재, products.like_count 비갱신 */
    private void insertLikeDirectly(Long memberId, Long productRefId) {
        jdbcTemplate.update(
                "INSERT INTO likes (ref_member_id, ref_product_id, created_at, updated_at)" +
                " VALUES (?, ?, NOW(6), NOW(6))",
                memberId, productRefId
        );
    }

    private ProductModel freshProduct(ProductModel product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    // ============================================================
    // 1. 즉시 정합성 (Immediate Consistency)
    // ============================================================

    @Test
    @DisplayName("[즉시 정합성 — 비정규화] 좋아요 등록 직후 like_count가 즉시 반영된다")
    void denormalization_addLike_immediatelyReflectedInLikeCount() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        likeService.addLike(1L, "prod1");

        // 비정규화: 좋아요 등록과 동일 트랜잭션에서 products.like_count += 1
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
        // 집계값과도 정확히 일치
        assertThat(aggregateLikeCount(product.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("[즉시 정합성 — 비정규화] 좋아요 취소 직후 like_count가 즉시 반영된다")
    void denormalization_removeLike_immediatelyReflectedInLikeCount() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");

        likeService.removeLike(1L, "prod1");

        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);
        assertThat(aggregateLikeCount(product.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("[즉시 정합성 — MV 시뮬레이션] 배치 갱신 전에는 like_count가 stale 상태다")
    void materializedView_beforeRefresh_likeCountIsStale() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        // MV 시나리오: likes 테이블에만 INSERT, products.like_count는 건드리지 않음
        insertLikeDirectly(1L, product.getId());
        insertLikeDirectly(2L, product.getId());

        // 배치 전: products.like_count = 0 (stale)
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(0);
        // 실제 집계: 2
        assertThat(aggregateLikeCount(product.getId())).isEqualTo(2);

        // 배치 갱신 실행
        refreshMaterializedView();

        // 배치 후: products.like_count = 2 (동기화 완료)
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[즉시 정합성 — MV 시뮬레이션] 좋아요 취소도 배치 전까지 반영되지 않는다")
    void materializedView_removeLike_alsoStaleBeforeRefresh() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        insertLikeDirectly(1L, product.getId());
        insertLikeDirectly(2L, product.getId());
        refreshMaterializedView();
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(2);

        // 좋아요 취소 (soft-delete only, products는 비갱신)
        jdbcTemplate.update(
                "UPDATE likes SET deleted_at = NOW(6), updated_at = NOW(6)" +
                " WHERE ref_product_id = ? AND ref_member_id = ? AND deleted_at IS NULL",
                product.getId(), 1L
        );

        // 배치 전: like_count = 2 (stale — 아직 취소 미반영)
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(2);
        assertThat(aggregateLikeCount(product.getId())).isEqualTo(1); // 실제는 1

        // 배치 갱신 후: like_count = 1
        refreshMaterializedView();
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(1);
    }

    // ============================================================
    // 2. 정렬 정확성 (Sort Correctness)
    // ============================================================

    @Test
    @DisplayName("[정렬 정확성] 비정규화 like_count 기반 정렬 순서가 실시간 집계 결과와 일치한다")
    void denormalization_sortOrder_matchesAggregateOrder() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);
        productService.createProduct("prodC", "nike", "Nike Air C", new BigDecimal("100000"), 10);

        // prodA: 3 likes, prodB: 1 like, prodC: 2 likes
        likeService.addLike(1L, "prodA");
        likeService.addLike(2L, "prodA");
        likeService.addLike(3L, "prodA");
        likeService.addLike(4L, "prodB");
        likeService.addLike(5L, "prodC");
        likeService.addLike(6L, "prodC");

        // 비정규화 기반 정렬 (인덱스 활용)
        List<String> sortedByDenorm = jdbcTemplate.queryForList(
                "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC",
                String.class
        );

        // 실시간 집계 JOIN 정렬 (MV 없는 실시간 쿼리)
        List<String> sortedByAggregate = jdbcTemplate.queryForList(
                "SELECT p.product_id FROM products p" +
                " LEFT JOIN (" +
                "     SELECT ref_product_id, COUNT(*) AS cnt" +
                "     FROM likes WHERE deleted_at IS NULL GROUP BY ref_product_id" +
                " ) l ON p.id = l.ref_product_id" +
                " WHERE p.deleted_at IS NULL" +
                " ORDER BY COALESCE(l.cnt, 0) DESC",
                String.class
        );

        assertThat(sortedByDenorm).isEqualTo(sortedByAggregate);
        assertThat(sortedByDenorm.get(0)).isEqualTo("prodA"); // 3 likes
        assertThat(sortedByDenorm.get(1)).isEqualTo("prodC"); // 2 likes
        assertThat(sortedByDenorm.get(2)).isEqualTo("prodB"); // 1 like
    }

    @Test
    @DisplayName("[정렬 정확성 — MV stale] 배치 갱신 전 좋아요 순 정렬은 이전 상태 기준이다")
    void materializedView_sortOrder_staleBeforeRefresh() {
        productService.createProduct("prodA", "nike", "Nike Air A", new BigDecimal("100000"), 10);
        productService.createProduct("prodB", "nike", "Nike Air B", new BigDecimal("100000"), 10);

        // prodA: 1 like, prodB: 0 likes → 초기 MV 갱신
        insertLikeDirectly(1L,
                productRepository.findById(
                        jdbcTemplate.queryForObject("SELECT id FROM products WHERE product_id = 'prodA'", Long.class)
                ).orElseThrow().getId()
        );
        refreshMaterializedView();

        // prodB에 2 likes 추가 (배치 전)
        Long prodBId = jdbcTemplate.queryForObject("SELECT id FROM products WHERE product_id = 'prodB'", Long.class);
        insertLikeDirectly(2L, prodBId);
        insertLikeDirectly(3L, prodBId);

        // MV 기준 정렬: prodA(1) > prodB(0) — prodB의 추가 좋아요 미반영
        List<String> staleOrder = jdbcTemplate.queryForList(
                "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC",
                String.class
        );
        assertThat(staleOrder.get(0)).isEqualTo("prodA"); // stale: prodB의 2 likes 미반영

        // 배치 갱신 후 정렬: prodB(2) > prodA(1)
        refreshMaterializedView();
        List<String> freshOrder = jdbcTemplate.queryForList(
                "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC",
                String.class
        );
        assertThat(freshOrder.get(0)).isEqualTo("prodB");
    }

    // ============================================================
    // 3. 동시성 정확성 (Concurrency Correctness)
    // ============================================================

    @Test
    @DisplayName("[동시성 — 비정규화] 50명 동시 좋아요 → like_count = 50 (원자 UPDATE 보장)")
    void denormalization_concurrent50Likes_likeCountEquals50() throws InterruptedException {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

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

        int finalLikeCount = freshProduct(product).getLikeCount();
        long aggregateCount = aggregateLikeCount(product.getId());

        log.info("[동시성 — 비정규화] successCount={}, like_count={}, aggregate={}",
                successCount.get(), finalLikeCount, aggregateCount);

        assertThat(successCount.get()).isEqualTo(threadCount);
        // 원자 UPDATE: like_count = like_count + 1 — 누락 없이 50 정확히 반영
        assertThat(finalLikeCount).isEqualTo(threadCount);
        // 집계값과 완전 일치
        assertThat(finalLikeCount).isEqualTo((int) aggregateCount);
    }

    // ============================================================
    // 4. 복구 시나리오 (Recovery)
    // ============================================================

    @Test
    @DisplayName("[복구 — 비정규화] like_count 오염 후 재집계 SQL로 수동 복구")
    void denormalization_recovery_manualRecalculation() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        likeService.addLike(1L, "prod1");
        likeService.addLike(2L, "prod1");
        likeService.addLike(3L, "prod1");
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(3);

        // 오염 시뮬레이션: 비정상 UPDATE (배포 버그, 직접 DB 조작 등)
        jdbcTemplate.update("UPDATE products SET like_count = 99 WHERE id = ?", product.getId());
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(99);

        // 비정규화 복구: DBA가 직접 실행하는 재집계 SQL (운영 패치)
        jdbcTemplate.update(
                "UPDATE products p" +
                " SET p.like_count = (" +
                "     SELECT COUNT(*) FROM likes l" +
                "     WHERE l.ref_product_id = p.id AND l.deleted_at IS NULL" +
                " ) WHERE p.id = ?",
                product.getId()
        );

        // 수동 복구 완료
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("[복구 — MV 시뮬레이션] like_count 오염 후 배치 갱신으로 자동 복구")
    void materializedView_recovery_automaticByBatchRefresh() {
        ProductModel product = productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        insertLikeDirectly(1L, product.getId());
        insertLikeDirectly(2L, product.getId());
        refreshMaterializedView();
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(2);

        // 오염 시뮬레이션
        jdbcTemplate.update("UPDATE products SET like_count = 999 WHERE id = ?", product.getId());
        assertThat(freshProduct(product).getLikeCount()).isEqualTo(999);

        // MV 복구: 정기 배치 실행만으로 자동 복구 (수동 개입 불필요)
        refreshMaterializedView();

        assertThat(freshProduct(product).getLikeCount()).isEqualTo(2);
    }

    // ============================================================
    // 5. 성능 비교 (Performance Comparison)
    // ============================================================

    @Test
    @DisplayName("[성능 비교] 비정규화 정렬 쿼리 vs 실시간 집계 JOIN 정렬 쿼리 — 응답시간 측정")
    void performance_denormSortVsAggregateJoinSort() {
        // 10개 상품, 각각 0~9 좋아요
        for (int i = 0; i < 10; i++) {
            String productId = "perf" + i;
            productService.createProduct(productId, "nike", "Nike Air " + i, new BigDecimal("100000"), 10);
            for (int j = 0; j < i; j++) {
                likeService.addLike((long) (j + 1), productId);
            }
        }

        // warm-up (JIT, 커넥션 풀 안정화)
        jdbcTemplate.queryForList(
                "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 10",
                String.class
        );

        // 비정규화 정렬 측정 (인덱스 활용)
        long denormStart = System.nanoTime();
        List<String> denormResult = jdbcTemplate.queryForList(
                "SELECT product_id FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 10",
                String.class
        );
        long denormNs = System.nanoTime() - denormStart;

        // 실시간 집계 JOIN 정렬 측정
        long aggregateStart = System.nanoTime();
        List<String> aggregateResult = jdbcTemplate.queryForList(
                "SELECT p.product_id FROM products p" +
                " LEFT JOIN (" +
                "     SELECT ref_product_id, COUNT(*) AS cnt" +
                "     FROM likes WHERE deleted_at IS NULL GROUP BY ref_product_id" +
                " ) l ON p.id = l.ref_product_id" +
                " WHERE p.deleted_at IS NULL" +
                " ORDER BY COALESCE(l.cnt, 0) DESC LIMIT 10",
                String.class
        );
        long aggregateNs = System.nanoTime() - aggregateStart;

        // 결과 일치 확인 (정확성 전제)
        assertThat(denormResult).isEqualTo(aggregateResult);

        double ratio = (double) aggregateNs / Math.max(denormNs, 1);
        log.info("[성능 비교] 비정규화 정렬: {}ms", String.format("%.3f", denormNs / 1_000_000.0));
        log.info("[성능 비교] 집계 JOIN 정렬: {}ms", String.format("%.3f", aggregateNs / 1_000_000.0));
        log.info("[성능 비교] 집계 JOIN / 비정규화 = {}배", String.format("%.2f", ratio));
    }
}
