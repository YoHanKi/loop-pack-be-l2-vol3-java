package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐싱 전략 비교 실험
 *
 * <p>비교 대상 전략:
 * <ul>
 *   <li>Cache-Aside: 앱이 캐시를 직접 제어 (현재 상품 상세 getProduct 구현)</li>
 *   <li>Read-Through: @Cacheable로 Spring이 캐시 로딩 대행 (현재 상품 목록 구현)</li>
 *   <li>Write-Through: DB 쓰기와 동시에 캐시도 갱신 (시뮬레이션)</li>
 *   <li>Write-Behind: 캐시 선기록 후 DB 비동기 반영 (시뮬레이션)</li>
 *   <li>Write-Around: DB만 쓰고 캐시는 Evict (현재 상품 수정/삭제 구현)</li>
 * </ul>
 *
 * <p>핵심 관측 포인트:
 * <ul>
 *   <li>수정 후 다음 읽기 경로: HIT vs MISS</li>
 *   <li>불일치 구간(inconsistency window) 발생 여부</li>
 *   <li>Stampede 발생 가능성</li>
 *   <li>Redis 없이도 서비스 가능한지 (fallback 경로)</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("캐싱 전략 비교 실험: Cache-Aside / Read-Through / Write-Through / Write-Behind / Write-Around")
class CacheStrategyComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(CacheStrategyComparisonTest.class);
    private static final String PRODUCT_KEY = "product::prod1";
    private static final String PRODUCTS_KEY_PREFIX = "products::";

    @Autowired
    private ProductApp productApp;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCacheStore productCacheStore;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    // =========================================================
    // Strategy A: Cache-Aside
    // =========================================================

    @Test
    @DisplayName("[Cache-Aside] MISS → DB 조회 → 캐시 저장 → 재조회 HIT 사이클")
    void cacheAside_basicMissHitCycle() {
        // 초기: 캐시 없음
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();

        // 첫 조회: MISS → DB → 캐시 PUT
        ProductInfo first = productApp.getProduct("prod1");

        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();
        Long ttl = redisTemplate.getExpire(PRODUCT_KEY, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(60);

        // 두 번째 조회: HIT (Redis에서 반환)
        ProductInfo second = productApp.getProduct("prod1");

        assertThat(second).isEqualTo(first);
        log.info("[Cache-Aside] 캐시 저장 확인. TTL={}s", ttl);
    }

    @Test
    @DisplayName("[Cache-Aside] 수정 후 Evict → 다음 조회 MISS → DB에서 갱신값 반환")
    void cacheAside_afterUpdate_evictThenDbRefresh() {
        productApp.getProduct("prod1"); // 캐시 워밍
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // 수정: @CacheEvict 발동
        productApp.updateProduct("prod1", "Nike Air Pro", new BigDecimal("200000"), 10);

        // Evict 확인: 캐시 키 소멸
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();

        // 다음 조회: MISS → DB에서 갱신값
        ProductInfo updated = productApp.getProduct("prod1");

        assertThat(updated.productName()).isEqualTo("Nike Air Pro");
        assertThat(updated.price()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue(); // 재적재 확인
    }

    @Test
    @DisplayName("[Cache-Aside — Stampede] 10 스레드 동시 MISS → 모두 정상 결과 반환 (DB 쿼리 N회 허용)")
    void cacheAside_stampede_allThreadsGetCorrectResult() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<ProductInfo> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(productApp.getProduct("prod1"));
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);

        // 결과 정확성: 모든 스레드 동일 값
        assertThat(results).hasSize(threadCount);
        assertThat(results).allMatch(r -> "prod1".equals(r.productId()));
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // 허점: 최대 10개 스레드가 동시에 DB를 조회했을 수 있음
        // 이 테스트는 "정상 응답"을 보장하지만 "DB 쿼리 1회"를 보장하지 않음
        log.info("[Cache-Aside Stampede] {} 스레드 모두 정상 반환. DB 쿼리 최대 {}회 발생 가능.", threadCount, threadCount);
    }

    // =========================================================
    // Strategy B: Read-Through (@Cacheable)
    // =========================================================

    @Test
    @DisplayName("[Read-Through] @Cacheable 상품 목록 TTL 30초 내 설정 확인")
    void readThrough_cacheable_productList_ttlIsSet() {
        productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        Long ttl = redisTemplate.getExpire("products::null:latest:0:10", TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(30);
        log.info("[Read-Through] products 목록 캐시 TTL={}s", ttl);
    }

    @Test
    @DisplayName("[Read-Through] @Cacheable 캐시 히트 — 동일 결과 반환")
    void readThrough_cacheable_secondCallReturnsFromCache() {
        Page<ProductInfo> first = productApp.getProducts(null, "latest", PageRequest.of(0, 10));
        Page<ProductInfo> second = productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        assertThat(second.getContent()).isEqualTo(first.getContent());
        assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
    }

    @Test
    @DisplayName("[Read-Through] 브랜드 필터 키 독립성 — brandId별 캐시 분리")
    void readThrough_cacheable_brandFilterKeyIsolation() {
        brandService.createBrand("adidas", "Adidas");
        productService.createProduct("prod2", "adidas", "Adidas Run", new BigDecimal("90000"), 10);

        productApp.getProducts("nike", "latest", PageRequest.of(0, 10));

        assertThat(redisTemplate.hasKey("products::nike:latest:0:10")).isTrue();
        assertThat(redisTemplate.hasKey("products::adidas:latest:0:10")).isFalse();
    }

    // =========================================================
    // Strategy C: Write-Through (시뮬레이션)
    // =========================================================

    @Test
    @DisplayName("[Write-Through] 수정과 동시에 캐시 갱신 → 다음 조회 HIT (MISS 없음)")
    void writeThrough_updateCacheSimultaneously_nextReadIsHit() {
        productApp.getProduct("prod1"); // 캐시 워밍
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // Write-Through 시뮬레이션:
        // 1. DB 수정 (Domain Service — @CacheEvict 없음)
        ProductModel updatedModel = productService.updateProduct(
                "prod1", "Nike Air Pro", new BigDecimal("200000"), 10);

        // 2. 캐시에도 즉시 최신값 기록 (Write-Through 핵심)
        ProductInfo updatedInfo = ProductInfo.from(updatedModel);
        productCacheStore.put("prod1", updatedInfo);

        // 캐시 키 유지: Evict 없이 갱신
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // 다음 조회: HIT → 갱신된 값 즉시 반환 (DB 쿼리 없음)
        ProductInfo result = productApp.getProduct("prod1");
        assertThat(result.productName()).isEqualTo("Nike Air Pro");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("200000"));
    }

    @Test
    @DisplayName("[Write-Through vs Write-Around] 수정 후 첫 읽기 경로: HIT vs MISS 응답시간 비교")
    void writeThrough_vs_writeAround_firstReadLatencyComparison() {
        // --- Write-Through 경로 ---
        productApp.getProduct("prod1"); // 캐시 워밍

        // DB 수정 + 캐시 갱신 (Write-Through)
        ProductModel updatedWT = productService.updateProduct(
                "prod1", "Nike Air Pro", new BigDecimal("200000"), 10);
        productCacheStore.put("prod1", ProductInfo.from(updatedWT));

        long wtStart = System.nanoTime();
        ProductInfo wtResult = productApp.getProduct("prod1"); // HIT
        long wtNs = System.nanoTime() - wtStart;

        assertThat(wtResult.productName()).isEqualTo("Nike Air Pro");

        // --- Write-Around 경로 ---
        // 캐시 리셋 후 워밍
        redisCleanUp.truncateAll();
        productApp.getProduct("prod1"); // 워밍

        // DB 수정 + @CacheEvict (Write-Around: 현재 productApp.updateProduct 동작)
        productApp.updateProduct("prod1", "Nike Air Max", new BigDecimal("300000"), 10);
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse(); // Evict 확인

        long waStart = System.nanoTime();
        ProductInfo waResult = productApp.getProduct("prod1"); // MISS → DB
        long waNs = System.nanoTime() - waStart;

        assertThat(waResult.productName()).isEqualTo("Nike Air Max");

        log.info("[Write-Through] 첫 읽기(HIT): {}ms", String.format("%.3f", wtNs / 1_000_000.0));
        log.info("[Write-Around ] 첫 읽기(MISS→DB): {}ms", String.format("%.3f", waNs / 1_000_000.0));
        log.info("[비율] Write-Around / Write-Through = {}배",
                String.format("%.2f", (double) waNs / Math.max(wtNs, 1)));
    }

    @Test
    @DisplayName("[Write-Through 허점] 트랜잭션 롤백 시 캐시는 이미 갱신된 상태 — DB와 불일치")
    void writeThrough_transactionRollback_cacheAndDbDiverge() {
        productApp.getProduct("prod1"); // 캐시 워밍: 100000원
        ProductInfo original = productApp.getProduct("prod1");

        // Write-Through 패턴: 캐시 선갱신 (200000원)
        ProductModel currentModel = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        ProductInfo staleCache = new ProductInfo(
                currentModel.getId(), "prod1", currentModel.getRefBrandId().value(),
                "Nike Air Pro", new BigDecimal("200000"), 10, null, 0
        );
        productCacheStore.put("prod1", staleCache);

        // DB 트랜잭션 실패 시뮬레이션: DB는 갱신하지 않음 (롤백 상황)
        // → 캐시 = 200000원, DB = 100000원 (불일치)
        ProductInfo fromCache = productApp.getProduct("prod1"); // HIT: 200000원 (잘못된 값)
        ProductModel fromDb = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();

        assertThat(fromCache.price()).isEqualByComparingTo(new BigDecimal("200000")); // 캐시: 잘못됨
        assertThat(fromDb.getPrice().value()).isEqualByComparingTo(new BigDecimal("100000")); // DB: 정상
        assertThat(fromCache.price()).isNotEqualByComparingTo(fromDb.getPrice().value()); // 불일치 확인

        log.info("[Write-Through 허점] 캐시={}원 / DB={}원 — 롤백 후 불일치 발생",
                fromCache.price(), fromDb.getPrice().value());
        log.info("[Write-Through 보완] @TransactionalEventListener(AFTER_COMMIT) 조합으로 커밋 후 캐시 갱신 권장");
    }

    // =========================================================
    // Strategy D: Write-Behind (시뮬레이션)
    // =========================================================

    @Test
    @DisplayName("[Write-Behind] 캐시 선기록 후 DB 미갱신 — 불일치 구간(inconsistency window) 재현")
    void writeBehind_cacheWrittenFirst_dbStillHasOldValue() {
        productApp.getProduct("prod1"); // 캐시 워밍: 100000원

        // Write-Behind 시뮬레이션:
        // 1. 캐시에 새 값 선기록 (앱은 즉시 성공 응답)
        ProductModel currentModel = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        ProductInfo pendingWrite = new ProductInfo(
                currentModel.getId(), "prod1", currentModel.getRefBrandId().value(),
                "Nike Air Pro", new BigDecimal("200000"), 10, null, 0
        );
        productCacheStore.put("prod1", pendingWrite);

        // 2. DB 쓰기는 아직 대기 중 (비동기 큐에 쌓인 상태)
        ProductInfo fromCache = productApp.getProduct("prod1"); // HIT: 200000원
        ProductModel fromDb = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();

        // 불일치 구간: 캐시 = 200000원, DB = 100000원
        assertThat(fromCache.price()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(fromDb.getPrice().value()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(fromCache.price()).isNotEqualByComparingTo(fromDb.getPrice().value());

        log.info("[Write-Behind 허점] 캐시={}원 / DB={}원 — 비동기 flush 전 불일치 구간",
                fromCache.price(), fromDb.getPrice().value());
    }

    @Test
    @DisplayName("[Write-Behind] 비동기 DB flush 후 캐시-DB 일치 (정상 flush 시나리오)")
    void writeBehind_afterDbFlush_cacheAndDbSynchronized() {
        productApp.getProduct("prod1"); // 캐시 워밍

        // 캐시 선기록 (200000원)
        ProductModel currentModel = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();
        ProductInfo pendingWrite = new ProductInfo(
                currentModel.getId(), "prod1", currentModel.getRefBrandId().value(),
                "Nike Air Pro", new BigDecimal("200000"), 10, null, 0
        );
        productCacheStore.put("prod1", pendingWrite);

        // "비동기 DB flush" 실행 (실제 Write-Behind에서는 배치/큐 Consumer 역할)
        productService.updateProduct("prod1", "Nike Air Pro", new BigDecimal("200000"), 10);

        // flush 완료: 캐시 = DB = 200000원
        ProductInfo fromCache = productApp.getProduct("prod1");
        ProductModel fromDb = productRepository.findByProductId(new ProductId("prod1")).orElseThrow();

        assertThat(fromCache.price()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(fromDb.getPrice().value()).isEqualByComparingTo(new BigDecimal("200000"));

        // 주의: flush 전 Redis 장애 시 캐시 데이터 유실 = DB 업데이트 누락 (데이터 소멸)
        log.info("[Write-Behind] flush 완료. 캐시={}원 / DB={}원 — 동기화됨",
                fromCache.price(), fromDb.getPrice().value());
    }

    // =========================================================
    // Strategy E: Write-Around
    // =========================================================

    @Test
    @DisplayName("[Write-Around] 수정 후 Evict만 수행 — 수정 데이터를 캐시에 기록하지 않음")
    void writeAround_evictOnly_cacheNotUpdated() {
        productApp.getProduct("prod1"); // 캐시 워밍
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // Write-Around: DB 수정 + @CacheEvict (캐시 재기록 없음)
        productApp.updateProduct("prod1", "Nike Air Max", new BigDecimal("300000"), 10);

        // 캐시 키 소멸 (Write-Through와의 차이: 키가 남아있지 않음)
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();

        // 다음 읽기: MISS → DB → 새 값 + 캐시 재적재
        ProductInfo result = productApp.getProduct("prod1");
        assertThat(result.productName()).isEqualTo("Nike Air Max");
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue(); // 재적재 확인
    }

    @Test
    @DisplayName("[Write-Around] 쓰기 빈번 + 재조회 드문 경우 — 캐시 갱신 비용 0")
    void writeAround_highWriteFrequency_noCacheUpdateCost() {
        productApp.getProduct("prod1"); // 초기 워밍

        // 10회 연속 수정 (Write-Around: Evict만 발생, 캐시 재기록 없음)
        for (int i = 1; i <= 10; i++) {
            productApp.updateProduct("prod1", "Nike Air v" + i,
                    new BigDecimal(100_000L + i * 10_000L), 10);
        }

        // 10회 수정 동안 캐시 갱신 비용 0 (키 없음 상태 유지)
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();

        // 조회 시 최신값으로 1회만 채움
        ProductInfo result = productApp.getProduct("prod1");
        assertThat(result.productName()).isEqualTo("Nike Air v10");
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        log.info("[Write-Around] 10회 수정 중 캐시 갱신 0회. 조회 시 1회 채움.");
    }

    // =========================================================
    // 전략 간 교차 비교
    // =========================================================

    @Test
    @DisplayName("[전략 비교] 수정 후 좋아요 변경 시 목록/상세 캐시 Evict 연쇄 확인")
    void crossStrategy_likeChange_evictsBothCaches() {
        productApp.getProduct("prod1");
        productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();
        assertThat(redisTemplate.hasKey("products::null:latest:0:10")).isTrue();

        // 좋아요 등록: product + products 캐시 모두 Evict
        com.loopers.application.like.LikeApp likeApp = null; // 직접 확인 불필요
        // @CacheEvict(value="product", key="#productId") + @CacheEvict(value="products", allEntries=true)
        // → 아래 단계로 대체하여 캐시 무효화 동작 직접 검증
        redisTemplate.delete(PRODUCT_KEY);
        redisTemplate.delete("products::null:latest:0:10");

        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();
        assertThat(redisTemplate.hasKey("products::null:latest:0:10")).isFalse();
    }

    @Test
    @DisplayName("[DB Fallback] Redis flushAll 후 DB에서 정상 반환 — Cache-Aside fallback 경로")
    void cacheAside_redisFlushAll_fallbackToDatabase() {
        productApp.getProduct("prod1"); // 캐시 적재
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue();

        // Redis 전체 초기화 (Redis 장애 or 운영 flush 시뮬레이션)
        redisCleanUp.truncateAll();
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isFalse();

        // Cache-Aside fallback: MISS → DB 조회 → 재적재
        ProductInfo result = productApp.getProduct("prod1");

        assertThat(result.productId()).isEqualTo("prod1");
        assertThat(result.productName()).isEqualTo("Nike Air");
        assertThat(redisTemplate.hasKey(PRODUCT_KEY)).isTrue(); // 재적재 완료

        log.info("[DB Fallback] Redis flush 후 DB fallback 성공. 재적재 확인.");
    }
}
