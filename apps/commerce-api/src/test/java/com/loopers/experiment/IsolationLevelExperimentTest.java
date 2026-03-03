package com.loopers.experiment;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB 격리레벨 × 동시성 시나리오 실험 테스트
 *
 * <pre>
 * 실험 목적
 *   H2 / MySQL(InnoDB) / PostgreSQL 에서 격리레벨별로
 *   대표 동시성 이상(anomaly)이 어떻게 나타나는지 관측한다.
 *
 * 관측 기준
 *   SUCCEEDED — 트랜잭션 정상 커밋 (최종 데이터값 함께 기록)
 *   BLOCKED   — lock_wait_timeout 초과 (3s 기준, 잠금 대기 포기)
 *   FAILED    — 예외로 롤백 (직렬화 실패·데드락·제약 위반 등, 에러 메시지 캡처)
 *
 * 시나리오
 *   S1 Lost Update         : 같은 row 를 읽고 계산 후 write (재고 차감)
 *   S2 Range Check→Insert  : 범위 조건 SELECT 후 INSERT (쿠폰 과발급)
 *   S3 Write Skew          : 각자 다른 row 를 write, 전체 제약 위반
 *
 * 필요 추가 의존성 (apps/commerce-api/build.gradle.kts)
 *   testImplementation("org.testcontainers:postgresql")
 *   testRuntimeOnly("org.postgresql:postgresql")
 *   testImplementation("com.h2database:h2")
 *
 * 실행
 *   ./gradlew :apps:commerce-api:test --tests "*.experiment.*" -i
 * </pre>
 */
@Tag("experiment")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class IsolationLevelExperimentTest {

    // ─── 열거형 ───────────────────────────────────────────────────────────────

    private static final Logger log = LoggerFactory.getLogger(IsolationLevelExperimentTest.class);

    enum Db { H2, MYSQL, POSTGRES }

    enum Level {
        RC (Connection.TRANSACTION_READ_COMMITTED,  "READ COMMITTED"),
        RR (Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE READ"),
        SER(Connection.TRANSACTION_SERIALIZABLE,    "SERIALIZABLE");

        final int jdbc;
        final String label;

        Level(int jdbc, String label) {
            this.jdbc  = jdbc;
            this.label = label;
        }
    }

    enum Verdict { SUCCEEDED, BLOCKED, FAILED }

    record TxResult(Verdict verdict, String error, String finalValue) {}
    record Row(String scenario, Db db, Level level, TxResult result) {}

    // ─── TestContainers ───────────────────────────────────────────────────────

    /**
     * H2 2.x: MVCC 기본 활성화, NON_KEYWORDS=VALUE 로 예약어 충돌 방지.
     * 격리레벨 구현은 MySQL/PostgreSQL 과 다르므로 결과 해석에 주의할 것.
     */
    static final String H2_URL =
        "jdbc:h2:mem:experiment;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";

    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("experiment")
            .withUsername("exp")
            .withPassword("exp")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci",
                "--innodb-lock-wait-timeout=50"  // 기본값; 세션별 3s 로 재설정
            );

    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("experiment")
            .withUsername("exp")
            .withPassword("exp");

    private final List<Row> results = new CopyOnWriteArrayList<>();

    // ─── 생명주기 ─────────────────────────────────────────────────────────────

    @BeforeAll
    void startContainers() throws Exception {
        MYSQL.start();
        PG.start();
        for (Db db : Db.values()) {
            try (Connection c = connect(db)) {
                initSchema(c, db);
            }
        }
        log.info("=== 실험 컨테이너 기동 완료 ===");
    }

    @AfterAll
    void stopContainers() {
        printResultTable();
        MYSQL.stop();
        PG.stop();
    }

    // ─── DDL / 데이터 초기화 ──────────────────────────────────────────────────

    private void initSchema(Connection c, Db db) throws SQLException {
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            if (db == Db.POSTGRES) {
                s.execute("DROP TABLE IF EXISTS exp_user_coupon CASCADE");
                s.execute("DROP TABLE IF EXISTS exp_coupon CASCADE");
                s.execute("DROP TABLE IF EXISTS exp_stock CASCADE");
                s.execute("DROP TABLE IF EXISTS exp_duty CASCADE");
            } else {
                s.execute("DROP TABLE IF EXISTS exp_user_coupon");
                s.execute("DROP TABLE IF EXISTS exp_coupon");
                s.execute("DROP TABLE IF EXISTS exp_stock");
                s.execute("DROP TABLE IF EXISTS exp_duty");
            }

            // S1 - 재고
            s.execute("CREATE TABLE exp_stock (id BIGINT PRIMARY KEY, qty INT NOT NULL)");

            // S2 - 쿠폰
            s.execute(
                "CREATE TABLE exp_coupon "
                + "(id BIGINT PRIMARY KEY, total_qty INT NOT NULL, issued_qty INT NOT NULL)"
            );
            String seqDdl = switch (db) {
                case MYSQL    -> "id BIGINT AUTO_INCREMENT PRIMARY KEY";
                case POSTGRES -> "id BIGSERIAL PRIMARY KEY";
                case H2       -> "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
            };
            s.execute(
                "CREATE TABLE exp_user_coupon ("
                + seqDdl
                + ", member_id BIGINT NOT NULL"
                + ", coupon_id BIGINT NOT NULL"
                + ", UNIQUE (member_id, coupon_id))"
            );

            // S3 - 당직
            s.execute(
                "CREATE TABLE exp_duty "
                + "(id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL, on_call BOOLEAN NOT NULL)"
            );
        }
    }

    private void resetData(Db db) throws Exception {
        try (Connection c = connect(db)) {
            c.setAutoCommit(true);
            try (Statement s = c.createStatement()) {
                s.execute("DELETE FROM exp_user_coupon");
                s.execute("DELETE FROM exp_coupon");
                s.execute("DELETE FROM exp_stock");
                s.execute("DELETE FROM exp_duty");
                s.execute("INSERT INTO exp_stock VALUES (1, 10)");
                s.execute("INSERT INTO exp_coupon VALUES (1, 5, 4)");  // 발급 가능 수 = 1
                s.execute("INSERT INTO exp_duty VALUES (1, 10, TRUE), (2, 20, TRUE)");
            }
        }
    }

    // ─── 연결 / 세션 설정 ─────────────────────────────────────────────────────

    private Connection connect(Db db) throws Exception {
        return switch (db) {
            case H2       -> DriverManager.getConnection(H2_URL, "sa", "");
            case MYSQL    -> DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            case POSTGRES -> DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        };
    }

    /** 세션 레벨 lock_wait_timeout = 3s */
    private void setLockTimeout(Connection c, Db db) throws SQLException {
        try (Statement s = c.createStatement()) {
            switch (db) {
                case H2       -> s.execute("SET LOCK_TIMEOUT 3000");
                case MYSQL    -> s.execute("SET SESSION innodb_lock_wait_timeout = 3");
                case POSTGRES -> s.execute("SET lock_timeout = '3s'");
            }
        }
    }

    // ─── 예외 분류 ────────────────────────────────────────────────────────────

    private Verdict classify(Exception e, Db db) {
        if (!(e instanceof SQLException se)) {
            return Verdict.FAILED;
        }
        return switch (db) {
            case H2 -> {
                String msg = se.getMessage() != null ? se.getMessage() : "";
                yield msg.contains("Timeout trying to lock") ? Verdict.BLOCKED : Verdict.FAILED;
            }
            // MySQL 1205: ER_LOCK_WAIT_TIMEOUT
            case MYSQL    -> se.getErrorCode() == 1205 ? Verdict.BLOCKED : Verdict.FAILED;
            // PostgreSQL 55P03: lock_not_available
            case POSTGRES -> "55P03".equals(se.getSQLState()) ? Verdict.BLOCKED : Verdict.FAILED;
        };
    }

    private String shortMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
    }

    // ─── 파라미터 행렬: 3 DB × 3 격리레벨 = 9 조합 ───────────────────────────

    static Stream<Arguments> matrix() {
        return Stream.of(Db.values())
            .flatMap(db -> Stream.of(Level.values())
                .map(level -> Arguments.of(db, level)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1 — Lost Update (재고 차감)
    //
    // 시나리오:
    //   T1, T2 모두 qty=10 을 snapshot 으로 읽고 각자 qty-3=7 을 SET 으로 write.
    //   두 write 가 모두 커밋되면 최종 qty=7 (should be 4) → Lost Update
    //
    // 이상 발생 시: qty=7
    // 정상 보호 시: qty=4 또는 T2 FAILED
    // 현재 프로젝트 해결책: 조건부 UPDATE (WHERE qty >= :qty) — ProductJpaRepository
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "S1 LostUpdate [{0}/{1}]")
    @MethodSource("matrix")
    void s1_LostUpdate(Db db, Level level) throws Exception {
        resetData(db);

        CyclicBarrier barrier = new CyclicBarrier(2);   // 둘 다 SELECT 후 동시 진입
        ExecutorService exec  = Executors.newFixedThreadPool(2);
        AtomicReference<TxResult> t2Res = new AtomicReference<>();

        // T1: 읽기 → 장벽 → write → commit
        Future<?> t1 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int qty = readQty(c);
                syncBarrier(barrier);

                writeQty(c, qty - 3);
                c.commit();
            } catch (Exception e) {
                log.debug("[S1/T1][{}/{}] {}", db, level.label, e.getMessage());
            }
        });

        // T2: 읽기 → 장벽 → 50ms 대기(T1이 write-lock 선점) → write → commit
        Future<?> t2 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int qty = readQty(c);
                syncBarrier(barrier);
                Thread.sleep(50);  // T1 이 먼저 write 하도록 양보

                writeQty(c, qty - 3);
                c.commit();

                String finalVal = "qty=" + readFinalQty(db);
                t2Res.set(new TxResult(Verdict.SUCCEEDED, null, finalVal));
            } catch (Exception e) {
                t2Res.set(new TxResult(classify(e, db), shortMsg(e), null));
            }
        });

        t1.get(10, TimeUnit.SECONDS);
        t2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        record_(new Row("S1 LostUpdate", db, level, t2Res.get()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 2 — Range Check Then Insert (쿠폰 과발급)
    //
    // 시나리오:
    //   초기: issued_qty=4, total_qty=5 (발급 가능 1장)
    //   T1, T2 모두 SELECT 에서 issued_qty=4 < total_qty=5 확인 → 발급 가능 판단
    //   각자 다른 member 로 INSERT user_coupon 후 issued_qty += 1
    //   두 트랜잭션 모두 커밋 시: issued_qty=6 (총 2장 과발급)
    //
    // 이상 발생 시: issued_qty=6
    // 현재 프로젝트 해결책: PESSIMISTIC_WRITE (SELECT FOR UPDATE) — CouponTemplateJpaRepository
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "S2 RangeCheck→Insert [{0}/{1}]")
    @MethodSource("matrix")
    void s2_RangeCheckThenInsert(Db db, Level level) throws Exception {
        resetData(db);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService exec  = Executors.newFixedThreadPool(2);
        AtomicReference<TxResult> t2Res = new AtomicReference<>();

        Future<?> t1 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int issued = readIssuedQty(c);
                int total  = readTotalQty(c);
                syncBarrier(barrier);

                if (issued < total) {
                    insertUserCoupon(c, 1L, 1L);
                    incrementIssuedQty(c);
                }
                c.commit();
            } catch (Exception e) {
                log.debug("[S2/T1][{}/{}] {}", db, level.label, e.getMessage());
            }
        });

        Future<?> t2 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int issued = readIssuedQty(c);
                int total  = readTotalQty(c);
                syncBarrier(barrier);
                Thread.sleep(50);

                if (issued < total) {
                    insertUserCoupon(c, 2L, 1L);
                    incrementIssuedQty(c);
                }
                c.commit();

                String finalVal = "issued=" + readFinalIssuedQty(db);
                t2Res.set(new TxResult(Verdict.SUCCEEDED, null, finalVal));
            } catch (Exception e) {
                t2Res.set(new TxResult(classify(e, db), shortMsg(e), null));
            }
        });

        t1.get(10, TimeUnit.SECONDS);
        t2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        record_(new Row("S2 Range→Insert", db, level, t2Res.get()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 3 — Write Skew ("둘 중 하나는 반드시 on_call=true")
    //
    // 시나리오:
    //   초기: member10.on_call=true, member20.on_call=true
    //   불변식: on_call=true 인 사람이 반드시 1명 이상이어야 한다
    //   T1: COUNT(on_call=true)=2 확인 → "여유 있으니 내가 빠져도 됨" → member10 off
    //   T2: COUNT(on_call=true)=2 확인 → "여유 있으니 내가 빠져도 됨" → member20 off
    //   둘 다 커밋 시: on_call=true 인원 = 0 → 불변식 위반
    //
    // 이상 발생 시: onCall=0
    // 현재 프로젝트 해결책: 없음 (Write Skew 는 SERIALIZABLE 만 방어)
    //   → MySQL SERIALIZABLE: 공유락 경쟁 → 데드락(FAILED)
    //   → PostgreSQL SERIALIZABLE: SSI 사이클 탐지 → FAILED
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "S3 WriteSkew [{0}/{1}]")
    @MethodSource("matrix")
    void s3_WriteSkew(Db db, Level level) throws Exception {
        resetData(db);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService exec  = Executors.newFixedThreadPool(2);
        AtomicReference<TxResult> t2Res = new AtomicReference<>();

        Future<?> t1 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int onCallCnt = countOnCall(c);
                syncBarrier(barrier);

                if (onCallCnt > 1) {
                    setOnCall(c, 1L, false);
                }
                c.commit();
            } catch (Exception e) {
                log.debug("[S3/T1][{}/{}] {}", db, level.label, e.getMessage());
            }
        });

        Future<?> t2 = exec.submit(() -> {
            try (Connection c = connect(db)) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(level.jdbc);
                setLockTimeout(c, db);

                int onCallCnt = countOnCall(c);
                syncBarrier(barrier);
                Thread.sleep(50);

                if (onCallCnt > 1) {
                    setOnCall(c, 2L, false);
                }
                c.commit();

                String finalVal = "onCall=" + countFinalOnCall(db);
                t2Res.set(new TxResult(Verdict.SUCCEEDED, null, finalVal));
            } catch (Exception e) {
                t2Res.set(new TxResult(classify(e, db), shortMsg(e), null));
            }
        });

        t1.get(10, TimeUnit.SECONDS);
        t2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        record_(new Row("S3 WriteSkew", db, level, t2Res.get()));
    }

    // ─── SQL 헬퍼 ─────────────────────────────────────────────────────────────

    private int readQty(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT qty FROM exp_stock WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private void writeQty(Connection c, int newQty) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "UPDATE exp_stock SET qty = ? WHERE id = 1")) {
            ps.setInt(1, newQty);
            ps.executeUpdate();
        }
    }

    private String readFinalQty(Db db) throws Exception {
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT qty FROM exp_stock WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return String.valueOf(rs.getInt(1));
        }
    }

    private int readIssuedQty(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT issued_qty FROM exp_coupon WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private int readTotalQty(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT total_qty FROM exp_coupon WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertUserCoupon(Connection c, long memberId, long couponId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO exp_user_coupon (member_id, coupon_id) VALUES (?, ?)")) {
            ps.setLong(1, memberId);
            ps.setLong(2, couponId);
            ps.executeUpdate();
        }
    }

    private void incrementIssuedQty(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("UPDATE exp_coupon SET issued_qty = issued_qty + 1 WHERE id = 1");
        }
    }

    private String readFinalIssuedQty(Db db) throws Exception {
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT issued_qty FROM exp_coupon WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return String.valueOf(rs.getInt(1));
        }
    }

    private int countOnCall(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM exp_duty WHERE on_call = TRUE");
            rs.next();
            return rs.getInt(1);
        }
    }

    private void setOnCall(Connection c, long dutyId, boolean onCall) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "UPDATE exp_duty SET on_call = ? WHERE id = ?")) {
            ps.setBoolean(1, onCall);
            ps.setLong(2, dutyId);
            ps.executeUpdate();
        }
    }

    private String countFinalOnCall(Db db) throws Exception {
        try (Connection c = connect(db);
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM exp_duty WHERE on_call = TRUE");
            rs.next();
            return String.valueOf(rs.getInt(1));
        }
    }

    // ─── 동기화 헬퍼 ─────────────────────────────────────────────────────────

    /** CyclicBarrier.await() 를 checked → unchecked 로 위임 */
    private void syncBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (BrokenBarrierException | InterruptedException
                 | java.util.concurrent.TimeoutException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("barrier 동기화 실패", e);
        }
    }

    private void record_(Row row) {
        results.add(row);
        TxResult r = row.result();
        log.info("[{}/{}] {} → {} | {} | {}",
            row.db(), row.level().label, row.scenario(),
            r.verdict(), r.error() != null ? r.error() : "-",
            r.finalValue() != null ? r.finalValue() : "-");
    }

    // ─── 결과 테이블 출력 ─────────────────────────────────────────────────────

    private void printResultTable() {
        StringBuilder sb = new StringBuilder(
            "\n\n"
            + "╔══════════════════╤══════════╤═══════════════╤═══════════════╤══════════════════════════════════════════╗\n"
            + "║ Scenario         │ DB       │ Isolation     │ Verdict       │ Detail / Final Value                     ║\n"
            + "╠══════════════════╪══════════╪═══════════════╪═══════════════╪══════════════════════════════════════════╣\n"
        );

        String lastScenario = "";
        for (Row row : results) {
            if (!row.scenario().equals(lastScenario) && !lastScenario.isEmpty()) {
                sb.append(
                    "╟──────────────────┼──────────┼───────────────┼───────────────┼──────────────────────────────────────────╢\n"
                );
            }
            lastScenario = row.scenario();

            String detail = row.result().error() != null
                ? row.result().error()
                : (row.result().finalValue() != null ? row.result().finalValue() : "-");
            if (detail.length() > 40) {
                detail = detail.substring(0, 37) + "...";
            }

            sb.append(String.format(
                "║ %-16s │ %-8s │ %-13s │ %-13s │ %-40s ║%n",
                row.scenario(), row.db(), row.level().label,
                row.result().verdict(), detail
            ));
        }

        sb.append("╚══════════════════╧══════════╧═══════════════╧═══════════════╧══════════════════════════════════════════╝\n");
        log.info(sb.toString());
    }
}
