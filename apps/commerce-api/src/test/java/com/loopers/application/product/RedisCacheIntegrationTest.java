package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@DisplayName("Redis 캐시 통합 테스트 — 키 적재, TTL, fallback, 예외 상황")
class RedisCacheIntegrationTest {

    @Autowired
    private ProductApp productApp;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("상품 상세 조회 후 Redis에 실제 키가 적재된다")
    void getProduct_storesKeyDirectlyInRedis() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        assertThat(redisTemplate.hasKey("product::prod1")).isFalse();

        productApp.getProduct("prod1");

        assertThat(redisTemplate.hasKey("product::prod1")).isTrue();
    }

    @Test
    @DisplayName("상품 상세 캐시 키에 TTL이 설정된다 (최대 60초)")
    void getProduct_cachedKeyHasTtl() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        productApp.getProduct("prod1");

        Long ttl = redisTemplate.getExpire("product::prod1", TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("상품 목록 캐시 키에 TTL이 설정된다 (최대 30초)")
    void getProducts_cachedKeyHasTtl() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        Long ttl = redisTemplate.getExpire("products::null:latest:0:10", TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("Redis flushAll 후 상품 조회 시 DB에서 정상 반환된다 (DB fallback)")
    void getProduct_afterRedisFlush_fallsBackToDatabase() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        productApp.getProduct("prod1");
        assertThat(redisTemplate.hasKey("product::prod1")).isTrue();

        redisCleanUp.truncateAll();

        ProductInfo result = productApp.getProduct("prod1");
        assertThat(result.productId()).isEqualTo("prod1");
        assertThat(redisTemplate.hasKey("product::prod1")).isTrue();
    }

    @Test
    @DisplayName("브랜드별 목록 캐시 키는 독립적으로 관리된다")
    void getProducts_differentBrands_cacheKeysAreIsolated() {
        brandService.createBrand("nike", "Nike");
        brandService.createBrand("adidas", "Adidas");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);
        productService.createProduct("prod2", "adidas", "Adidas Run", new BigDecimal("90000"), 10);

        productApp.getProducts("nike", "latest", PageRequest.of(0, 10));

        assertThat(redisTemplate.hasKey("products::nike:latest:0:10")).isTrue();
        assertThat(redisTemplate.hasKey("products::adidas:latest:0:10")).isFalse();

        productApp.getProducts("adidas", "latest", PageRequest.of(0, 10));

        assertThat(redisTemplate.hasKey("products::adidas:latest:0:10")).isTrue();
    }

    @Test
    @DisplayName("[Cache Stampede 시뮬레이션] 캐시 미스 상태에서 동시 요청이 몰려도 모든 스레드가 올바른 결과를 받는다")
    void getProduct_concurrentCacheMiss_allThreadsGetCorrectResult() throws InterruptedException {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ProductInfo> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    results.add(productApp.getProduct("prod1"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(results).hasSize(threadCount);
        assertThat(results).allMatch(r -> "prod1".equals(r.productId()));
        assertThat(redisTemplate.hasKey("product::prod1")).isTrue();
    }

    @Test
    @DisplayName("[Hotkey 시뮬레이션] 단일 키에 100회 반복 조회 시 모두 동일한 결과를 반환한다")
    void getProduct_repeatedAccessToSameKey_alwaysReturnsConsistentResult() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("hotprod", "nike", "Nike Air", new BigDecimal("100000"), 10);

        ProductInfo expected = productApp.getProduct("hotprod");

        for (int i = 0; i < 99; i++) {
            ProductInfo result = productApp.getProduct("hotprod");
            assertThat(result).isEqualTo(expected);
        }

        assertThat(redisTemplate.hasKey("product::hotprod")).isTrue();
    }

    @Test
    @DisplayName("[Hotkey 시뮬레이션] 단일 목록 키에 반복 조회 시 모두 동일한 결과를 반환한다")
    void getProducts_repeatedAccessToSameKey_alwaysReturnsConsistentResult() {
        brandService.createBrand("nike", "Nike");
        productService.createProduct("prod1", "nike", "Nike Air", new BigDecimal("100000"), 10);

        Page<ProductInfo> expected = productApp.getProducts(null, "latest", PageRequest.of(0, 10));

        for (int i = 0; i < 49; i++) {
            Page<ProductInfo> result = productApp.getProducts(null, "latest", PageRequest.of(0, 10));
            assertThat(result.getContent()).isEqualTo(expected.getContent());
            assertThat(result.getTotalElements()).isEqualTo(expected.getTotalElements());
        }

        assertThat(redisTemplate.hasKey("products::null:latest:0:10")).isTrue();
    }
}
