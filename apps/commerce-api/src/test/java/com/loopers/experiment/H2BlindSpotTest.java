package com.loopers.experiment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 SERIALIZABLE 격리레벨의 맹점(Blind Spot)을 명시적으로 증명하는 테스트.
 *
 * <pre>
 * 이 테스트는 의도적으로 통과하도록 설계됐다.
 * 통과한다는 사실이 "안전하다"는 의미가 아님을 보여주는 것이 목적이다.
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  DB                  │ Write Skew @ SERIALIZABLE             │
 * ├──────────────────────┼───────────────────────────────────────┤
 * │  H2 (in-memory)      │  SUCCEEDED  — 이상 데이터 조용히 커밋  │  ← 이 테스트
 * │  MySQL (InnoDB)      │  FAILED     — Deadlock 예외 발생       │
 * │  PostgreSQL          │  FAILED     — SSI 직렬화 실패 예외     │
 * └──────────────────────┴───────────────────────────────────────┘
 *
 * H2 테스트만 보면 SERIALIZABLE 이 Write Skew 를 막아주는 것처럼 보이지 않는다.
 * (H2 는 아예 막으려 시도하지도 않는다.)
 * 하지만 MySQL/PostgreSQL 에서는 예외가 터지며, 이 예외 처리 경로는
 * H2 기반 테스트에서 단 한 번도 검증되지 않는다.
 *
 * Docker 불필요 — H2 in-memory 만 사용.
 * </pre>
 */
@Tag("experiment")
@DisplayName("H2 맹점: SERIALIZABLE 에서도 Write Skew 가 통과된다")
class H2BlindSpotTest {

    /**
     * DB_CLOSE_DELAY=-1: 마지막 커넥션이 닫혀도 DB 유지 (테스트 간 공유).
     * NON_KEYWORDS=VALUE: VALUE 예약어 충돌 방지.
     */
    private static final String H2_URL =
        "jdbc:h2:mem:blindspot;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";

    @BeforeEach
    void setup() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            c.setAutoCommit(true);
            s.execute("DROP TABLE IF EXISTS shift");
            // 당직 테이블: 최소 1명은 on_call=true 여야 한다는 불변식
            s.execute("CREATE TABLE shift (id BIGINT PRIMARY KEY, on_call BOOLEAN NOT NULL)");
            s.execute("INSERT INTO shift VALUES (1, TRUE), (2, TRUE)");
        }
    }

    @AfterEach
    void teardown() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS shift");
        }
    }

    // =========================================================================
    //
    //  시나리오: 당직 시스템 Write Skew
    //
    //  불변식: on_call=TRUE 인 사람이 반드시 1명 이상이어야 한다.
    //
    //  T1: SELECT COUNT(on_call=TRUE) = 2  →  "여유 있으니 나는 빠져도 된다"  →  id=1 off
    //  T2: SELECT COUNT(on_call=TRUE) = 2  →  "여유 있으니 나는 빠져도 된다"  →  id=2 off
    //
    //  결과 (H2 SERIALIZABLE):
    //    successCount = 2  (둘 다 커밋 성공, 예외 없음)
    //    onCall       = 0  (불변식 조용히 깨짐)
    //
    //  운영 DB 에서의 현실:
    //    MySQL SERIALIZABLE  → T2: DeadlockLoserDataAccessException
    //                          "Deadlock found when trying to get lock; try restarting transaction"
    //    PostgreSQL SERIAL   → T2: CannotSerializeTransactionException
    //                          "could not serialize access due to read/write dependencies among transactions"
    //    → 운영에서 이 예외를 처리하는 재시도 로직이 없으면 500 에러로 이어진다.
    //    → H2 테스트는 이 실패 경로를 한 번도 실행한 적이 없다.
    //
    // =========================================================================

    @Test
    @DisplayName("[H2 맹점 확인] SERIALIZABLE 이어도 Write Skew 두 트랜잭션 모두 커밋 → 불변식 위반")
    void h2_serializable_silently_allows_write_skew() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        CyclicBarrier barrier      = new CyclicBarrier(2);
        ExecutorService exec       = Executors.newFixedThreadPool(2);

        // T1: id=1 을 off-call 로 변경
        Future<?> t1 = exec.submit(() -> {
            try (Connection c = connect()) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                c.createStatement().execute("SET LOCK_TIMEOUT 3000");

                int onCall = countOnCall(c);
                barrier.await(5, TimeUnit.SECONDS);

                if (onCall > 1) {
                    c.createStatement().execute("UPDATE shift SET on_call = FALSE WHERE id = 1");
                }
                c.commit();
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });

        // T2: id=2 를 off-call 로 변경 (T1 과 다른 row — row 충돌 없음)
        Future<?> t2 = exec.submit(() -> {
            try (Connection c = connect()) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                c.createStatement().execute("SET LOCK_TIMEOUT 3000");

                int onCall = countOnCall(c);
                barrier.await(5, TimeUnit.SECONDS);
                Thread.sleep(50);   // T1 이 먼저 write 하도록 양보

                if (onCall > 1) {
                    c.createStatement().execute("UPDATE shift SET on_call = FALSE WHERE id = 2");
                }
                c.commit();
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });

        t1.get(10, TimeUnit.SECONDS);
        t2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        int finalOnCall = countFinalOnCall();

        // ── H2 SERIALIZABLE 의 실제 동작 ────────────────────────────────────────
        assertThat(successCount.get())
            .as("H2: 두 트랜잭션 모두 예외 없이 COMMIT 됨 (SERIALIZABLE 임에도 불구하고)")
            .isEqualTo(2);

        assertThat(failCount.get())
            .as("H2: 예외 발생 횟수 = 0 (테스트가 통과 → 개발자는 안전하다고 착각)")
            .isEqualTo(0);

        assertThat(finalOnCall)
            .as("H2: 최종 on_call=0 — '최소 1명 대기' 불변식이 조용히 깨졌다")
            .isEqualTo(0);

        /*
         * 이 테스트가 말하지 않는 것
         * ─────────────────────────────────────────────────────────────────────
         *
         * 1. MySQL SERIALIZABLE 에서는 T2 가 DeadlockLoserDataAccessException 을 던진다.
         *    운영 서비스에서 이 예외를 잡지 않으면 HTTP 500 응답이 반환된다.
         *
         * 2. PostgreSQL SERIALIZABLE 에서는 T2 가 CannotSerializeTransactionException 을 던진다.
         *    클라이언트가 재시도를 해야 하며, 이 재시도 로직이 없으면 데이터 일관성이 깨진다.
         *
         * 3. H2 기반의 이 테스트는 위 예외 처리 경로를 단 한 번도 실행하지 않았다.
         *    즉, "예외 발생 → 재시도" 또는 "예외 발생 → 사용자 안내" 코드가 있더라도
         *    H2 테스트에서는 검증되지 않는다.
         *
         * 4. H2 에서 Write Skew 를 막으려면 격리레벨이 아닌 애플리케이션 레벨 잠금
         *    (SELECT FOR UPDATE, 낙관적 락 등)을 사용해야 한다.
         */
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────────

    private Connection connect() throws Exception {
        return DriverManager.getConnection(H2_URL, "sa", "");
    }

    private int countOnCall(Connection c) throws Exception {
        ResultSet rs = c.createStatement()
            .executeQuery("SELECT COUNT(*) FROM shift WHERE on_call = TRUE");
        rs.next();
        return rs.getInt(1);
    }

    private int countFinalOnCall() throws Exception {
        try (Connection c = connect()) {
            return countOnCall(c);
        }
    }
}
