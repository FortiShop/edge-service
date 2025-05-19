package org.fortishop.edgeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.MemberPoint;
import org.fortishop.edgeservice.domain.PointSourceService;
import org.fortishop.edgeservice.domain.RefreshToken;
import org.fortishop.edgeservice.domain.Role;
import org.fortishop.edgeservice.dto.event.PointChangedEvent;
import org.fortishop.edgeservice.dto.request.LoginRequest;
import org.fortishop.edgeservice.dto.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.dto.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.dto.request.SignupRequest;
import org.fortishop.edgeservice.dto.response.MemberResponse;
import org.fortishop.edgeservice.dto.response.MemberUpdateNicknameResponse;
import org.fortishop.edgeservice.repository.MemberPointRepository;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.PointHistoryRepository;
import org.fortishop.edgeservice.repository.RefreshTokenRepository;
import org.fortishop.edgeservice.service.PointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=classpath:/application-test.yml",
                "spring.profiles.active=test"
        }
)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EdgeServiceIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PointService pointService;

    @Autowired
    MemberPointRepository memberPointRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PointHistoryRepository pointHistoryRepository;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;


    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fortishop")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.11");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.1")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> zookeeper = new GenericContainer<>(DockerImageName.parse("bitnami/zookeeper:3.8.1"))
            .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes")
            .withExposedPorts(2181)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("zookeeper");

    @Container
    static GenericContainer<?> kafka = new GenericContainer<>(DockerImageName.parse("bitnami/kafka:3.6.0"))
            .withExposedPorts(9092, 9093)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka")
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName("kafka");
                cmd.withHostConfig(
                        Objects.requireNonNull(cmd.getHostConfig())
                                .withPortBindings(
                                        new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)),
                                        new PortBinding(Ports.Binding.bindPort(9093), new ExposedPort(9093))
                                )
                );
            })
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
            .withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", "zookeeper:2181")
            .withEnv("KAFKA_CFG_LISTENERS", "PLAINTEXT://0.0.0.0:9092,EXTERNAL://0.0.0.0:9093")
            .withEnv("KAFKA_CFG_ADVERTISED_LISTENERS",
                    "PLAINTEXT://kafka:9092,EXTERNAL://localhost:9093")
            .withEnv("KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP",
                    "PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE", "true")
            .waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+] started.*\\n", 1));

    @Container
    static GenericContainer<?> kafkaUi = new GenericContainer<>(DockerImageName.parse("provectuslabs/kafka-ui:latest"))
            .withExposedPorts(8080)
            .withEnv("KAFKA_CLUSTERS_0_NAME", "fortishop-cluster")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "PLAINTEXT://kafka:9092")
            .withEnv("KAFKA_CLUSTERS_0_ZOOKEEPER", "zookeeper:2181")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka-ui");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        mysql.start();
        rabbit.start();
        redis.start();
        zookeeper.start();
        kafka.start();
        kafkaUi.start();

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getHost() + ":" + kafka.getMappedPort(9093));
    }

    private String accessToken;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/members";
    }

    private static boolean topicCreated = false;

    @BeforeAll
    static void printKafkaUiUrl() throws Exception {
        System.out.println("Kafka UI is available at: http://" + kafkaUi.getHost() + ":" + kafkaUi.getMappedPort(8080));
        if (!topicCreated) {
            String bootstrap = kafka.getHost() + ":" + kafka.getMappedPort(9093);
            createTopicIfNotExists("point.changed", bootstrap);
            topicCreated = true;
        }
    }

    @BeforeEach
    void setUpUser(TestInfo testInfo) {
        String methodName = testInfo.getTestMethod().map(Method::getName).orElse("");
        if (methodName.contains("checkEmailDuplicate") || methodName.contains("checkNicknameDuplicate")
                || methodName.contains("login_wrongPassword") ||
                methodName.contains("reissueWithFakeRefreshToken_fail")) {
            return;
        }

        SignupRequest signup = new SignupRequest("test@fortishop.com", "pw1234", "테스트유저");
        restTemplate.postForEntity(getBaseUrl() + "/signup", signup, Void.class);

        LoginRequest login = new LoginRequest("test@fortishop.com", "pw1234");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(login, headers);

        ResponseEntity<Void> loginRes = restTemplate.postForEntity("http://localhost:" + port + "/api/auths/login",
                entity, Void.class);

        String authHeader = loginRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        } else {
            throw new RuntimeException("AccessToken 헤더가 존재하지 않거나 형식이 잘못됨");
        }

        System.out.println("AccessToken: " + accessToken);
    }

    @AfterEach
    void deleteUser() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        member.markDeleted();
        memberRepository.save(member);
    }

    @Test
    @DisplayName("내 정보 조회에 성공한다")
    void getMyInfo_success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<MemberResponse> response = restTemplate.exchange(
                getBaseUrl() + "/me", HttpMethod.GET, entity, MemberResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("test@fortishop.com");
    }

    @Test
    @DisplayName("닉네임 수정에 성공한다")
    void updateNickname_success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        MemberUpdateNicknameRequest request = new MemberUpdateNicknameRequest("변경된닉네임");
        HttpEntity<?> entity = new HttpEntity<>(request, headers);

        ResponseEntity<MemberUpdateNicknameResponse> response = restTemplate.exchange(
                getBaseUrl() + "/nickname", HttpMethod.PATCH, entity, MemberUpdateNicknameResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getNickname()).isEqualTo("변경된닉네임");
    }

    @DisplayName("이메일 중복으로 회원가입에 실패한다")
    @Test
    void signup_duplicateEmail_fail() {
        SignupRequest dup = new SignupRequest("test@fortishop.com", "newPw123!", "새닉네임");
        ResponseEntity<Void> response = restTemplate.postForEntity(getBaseUrl() + "/signup", dup,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("비밀번호 변경에 성공한다")
    void updatePassword_success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        PasswordUpdateRequest request = new PasswordUpdateRequest("pw1234", "newpass123");
        HttpEntity<?> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/password", HttpMethod.PATCH, entity, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("회원 탈퇴에 성공한다")
    void withdraw_success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/me", HttpMethod.DELETE, entity, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("이메일 중복 확인에 성공한다")
    void checkEmailDuplicate_success() {
        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/check-email?email=test2@fortishop.com",
                HttpMethod.GET,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("닉네임 중복 확인에 성공한다")
    void checkNicknameDuplicate_success() {
        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/check-nickname?nickname=테스트유저2",
                HttpMethod.GET,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AccessToken 재발급에 성공한다")
    void reissueAccessToken_success() {
        RefreshToken refreshToken = getRefreshTokenFromDB();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "refreshToken=" + refreshToken.getToken());

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/reissue", HttpMethod.PATCH, entity, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("Response: " + response.getBody());
    }

    @Test
    @DisplayName("로그아웃에 성공한다")
    void logout_success() {
        RefreshToken refreshToken = getRefreshTokenFromDB();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "refreshToken=" + refreshToken.getToken());
        headers.setBearerAuth(accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/logout", HttpMethod.PATCH, entity, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("Response: " + response.getBody());
    }

    @DisplayName("비밀번호가 틀리면 로그인에 실패한다")
    @Test
    void login_wrongPassword_fail() {
        LoginRequest wrong = new LoginRequest("test@fortishop.com", "wrongPassword");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(wrong, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/auths/login",
                entity, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 없이 내 정보 요청 시 실패한다")
    void getMyInfoWithoutToken_fail() {
        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/me",
                HttpMethod.GET,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("위조된 RefreshToken으로 액세스 토큰 재발급 시 실패한다")
    void reissueWithFakeRefreshToken_fail() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "refreshToken=fake.refresh.token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/reissue",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ROLE_ADMIN 계정으로 전체 회원 조회에 성공한다")
    void getAllMembers_withAdminRole_success() {
        String uniqueId = String.valueOf(System.currentTimeMillis());
        String email = "admin-" + uniqueId + "@fortishop.com";
        String nickname = "관리자-" + uniqueId;

        restTemplate.postForEntity(getBaseUrl() + "/signup", new SignupRequest(email, "admin123", nickname),
                Void.class);

        // 로그인
        LoginRequest login = new LoginRequest(email, "admin123");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> loginRes = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auths/login",
                new HttpEntity<>(login, loginHeaders),
                Void.class
        );

        String accessToken = Objects.requireNonNull(loginRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);
        String refreshTokenCookie = loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE); // refreshToken 쿠키 추출

        // 관리자 권한 부여
        Member admin = memberRepository.findByEmail(email).orElseThrow();
        admin.updateRole(Role.ROLE_ADMIN);
        memberRepository.save(admin);

        // 토큰 재발급
        HttpHeaders reissueHeaders = new HttpHeaders();
        reissueHeaders.setBearerAuth(accessToken);
        reissueHeaders.set(HttpHeaders.COOKIE, refreshTokenCookie);
        ResponseEntity<Void> reissueRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/reissue",
                HttpMethod.PATCH,
                new HttpEntity<>(reissueHeaders),
                Void.class
        );
        String adminAccessToken = Objects.requireNonNull(reissueRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);

        // 전체 회원 조회
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl(), HttpMethod.GET, entity, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("ROLE_ADMIN 계정이 다른 사용자의 권한을 변경할 수 있다")
    void updateRole_withAdminRole_success() {
        String uid = String.valueOf(System.currentTimeMillis());

        // 일반 유저 회원가입
        String userEmail = "user-" + uid + "@fortishop.com";
        String userNickname = "일반유저-" + uid;
        restTemplate.postForEntity(getBaseUrl() + "/signup",
                new SignupRequest(userEmail, "user123", userNickname), Void.class);
        Member user = memberRepository.findByEmail(userEmail).orElseThrow();

        // 관리자 유저 회원가입
        String adminEmail = "admin-" + uid + "@fortishop.com";
        String adminNickname = "관리자유저-" + uid;
        restTemplate.postForEntity(getBaseUrl() + "/signup",
                new SignupRequest(adminEmail, "admin123", adminNickname), Void.class);

        // 로그인
        LoginRequest login = new LoginRequest(adminEmail, "admin123");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> loginRes = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auths/login",
                new HttpEntity<>(login, loginHeaders),
                Void.class
        );

        String accessToken = Objects.requireNonNull(loginRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);
        String refreshTokenCookie = loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        // 관리자 권한 부여
        Member admin = memberRepository.findByEmail(adminEmail).orElseThrow();
        admin.updateRole(Role.ROLE_ADMIN);
        memberRepository.save(admin);

        // 토큰 재발급
        HttpHeaders reissueHeaders = new HttpHeaders();
        reissueHeaders.setBearerAuth(accessToken);
        reissueHeaders.set(HttpHeaders.COOKIE, refreshTokenCookie);
        ResponseEntity<Void> reissueRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/reissue",
                HttpMethod.PATCH,
                new HttpEntity<>(reissueHeaders),
                Void.class
        );
        String adminToken = Objects.requireNonNull(reissueRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);

        // 권한 변경 요청
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/" + user.getId() + "/role?role=ROLE_ADMIN",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Member updated = memberRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("ROLE_USER 계정으로 전체 회원 조회를 시도하면 실패한다")
    void getAllMembers_withUserRole_forbidden() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl(), HttpMethod.GET, entity, String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ROLE_USER 계정이 다른 사용자의 권한을 변경하려 하면 실패한다")
    void updateRole_withUserRole_forbidden() {
        // given
        String uid = String.valueOf(System.currentTimeMillis());
        String targetEmail = "target-" + uid + "@fortishop.com";
        String targetNickname = "대상유저-" + uid;
        restTemplate.postForEntity(getBaseUrl() + "/signup",
                new SignupRequest(targetEmail, "pw1234", targetNickname), Void.class);
        Member target = memberRepository.findByEmail(targetEmail).orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/" + target.getId() + "/role?role=ROLE_ADMIN",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("포인트 적립에 성공하고 잔액이 DB에 반영된다")
    void adjustPoint_save_success() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        savePoint(member.getId(), 3000);

        MemberPoint updatedPoint = memberPointRepository.findByMember(member)
                .orElseThrow(() -> new AssertionError("MemberPoint 없음"));
        assertThat(updatedPoint.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
    }

    @Test
    @DisplayName("포인트 차감에 성공하고 잔액이 감소한다")
    void adjustPoint_use_success() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        String adminAccessToken = savePoint(member.getId(), 3000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String transactionId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString().substring(0, 5);
        String body = """
                    {
                      "memberId": %d,
                      "amount": 1000,
                      "changeType": "USE",
                      "description": "차감",
                      "transactionId": "%s",
                      "traceId": "%s"
                    }
                """.formatted(member.getId(), transactionId, traceId);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/points/adjust",
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MemberPoint updated = memberPointRepository.findByMember(member).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    }

    @Test
    @DisplayName("포인트 차감 시 잔액 부족으로 실패한다")
    void adjustPoint_use_fail_dueToInsufficient() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        String adminAccessToken = savePoint(member.getId(), 3000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String transactionId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString().substring(0, 5);
        String body = """
                    {
                      "memberId": %d,
                      "amount": 5000,
                      "changeType": "USE",
                      "description": "초과 차감",
                      "transactionId": "%s",
                      "traceId": "%s"
                    }
                """.formatted(member.getId(), transactionId, traceId);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/points/adjust",
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("중복 트랜잭션 ID는 두 번째 요청을 무시한다")
    void adjustPoint_duplicateTransactionId_ignored() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        String adminAccessToken = savePoint(member.getId(), 3000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String txId = UUID.randomUUID().toString();

        String body = """
                    {
                      "memberId": %d,
                      "amount": 2000,
                      "changeType": "SAVE",
                      "description": "중복 테스트",
                      "transactionId": "%s",
                      "traceId": "%s"
                    }
                """.formatted(member.getId(), txId, UUID.randomUUID());

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> first = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/points/adjust",
                entity,
                Void.class);
        pointHistoryRepository.flush();
        ResponseEntity<Void> second = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/points/adjust",
                entity,
                Void.class);

        MemberPoint point = memberPointRepository.findByMember(member).orElseThrow();
        assertThat(point.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("포인트 전송 시 송신자 차감, 수신자 적립이 정상적으로 처리된다")
    void transferPoint_success() {
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        savePoint(member.getId(), 3000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String receiverEmail = "receiver-" + UUID.randomUUID() + "@fortishop.com";
        restTemplate.postForEntity(getBaseUrl() + "/signup",
                new SignupRequest(receiverEmail, "pw1234", "수신자"), Void.class);
        Member receiver = memberRepository.findByEmail(receiverEmail).orElseThrow();
        String senderTransactionId = UUID.randomUUID().toString();
        String senderTraceId = UUID.randomUUID().toString().substring(0, 5);
        String receiverTransactionId = UUID.randomUUID().toString();
        String receiverTraceId = UUID.randomUUID().toString().substring(0, 5);
        String body = """
                    {
                      "receiverId": %d,
                      "amount": 1000,
                      "reason": "선물",
                      "senderTransactionId": "%s",
                      "senderTraceId": "%s",
                      "receiverTransactionId": "%s",
                      "receiverTraceId": "%s"
                    }
                """.formatted(receiver.getId(), senderTransactionId, senderTraceId, receiverTransactionId,
                receiverTraceId);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Void> res = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/points/transfer",
                entity,
                Void.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Member sender = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        MemberPoint senderPoint = memberPointRepository.findByMember(sender).orElseThrow();
        MemberPoint receiverPoint = memberPointRepository.findByMember(receiver).orElseThrow();

        assertThat(senderPoint.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(receiverPoint.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    @DisplayName("Kafka 이벤트 기반 포인트 적립 처리 - 성공")
    void handlePointChangedEvent_success() throws Exception {
        // Given
        Member member = memberRepository.findByEmail("test@fortishop.com").orElseThrow();
        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);

        // Kafka topic 존재 여부 대기 (point.changed)
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> admin.listTopics().names().get().contains("point.changed"));
        }

        String transactionId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        PointChangedEvent event = PointChangedEvent.builder()
                .memberId(member.getId())
                .orderId(9999L)
                .changeType("SAVE")
                .amount(BigDecimal.valueOf(1500))
                .reason("적립 테스트")
                .transactionId(transactionId)
                .timestamp(LocalDateTime.now().toString())
                .traceId(traceId)
                .sourceService("ORDER_REWARD")
                .build();

        // When: Kafka 메시지 발행
        KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        ));
        producer.send(new ProducerRecord<>("point.changed", member.getId().toString(), event));
        producer.flush();
        producer.close();

        // Then: 적립금 반영 여부 확인
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    MemberPoint point = memberPointRepository.findByMember(member).orElseThrow();
                    assertThat(point.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
                });
    }

    private RefreshToken getRefreshTokenFromDB() {
        Member member = memberRepository.findByEmail("test@fortishop.com")
                .orElseThrow(() -> new IllegalArgumentException("회원 없음: " + "test@fortishop.com"));

        return refreshTokenRepository.findByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken 없음: " + "test@fortishop.com"));
    }

    private String savePoint(Long memberId, int amount) {
        String uniqueId = String.valueOf(System.currentTimeMillis());
        String email = "admin-" + uniqueId + "@fortishop.com";
        String nickname = "관리자-" + uniqueId;

        restTemplate.postForEntity(getBaseUrl() + "/signup", new SignupRequest(email, "admin123", nickname),
                Void.class);

        // 로그인
        LoginRequest login = new LoginRequest(email, "admin123");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> loginRes = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auths/login",
                new HttpEntity<>(login, loginHeaders),
                Void.class
        );

        String accessToken = Objects.requireNonNull(loginRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);
        String refreshTokenCookie = loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        // 관리자 권한 부여
        Member admin = memberRepository.findByEmail(email).orElseThrow();
        admin.updateRole(Role.ROLE_ADMIN);
        memberRepository.save(admin);

        // 토큰 재발급
        HttpHeaders reissueHeaders = new HttpHeaders();
        reissueHeaders.setBearerAuth(accessToken);
        reissueHeaders.set(HttpHeaders.COOKIE, refreshTokenCookie);
        ResponseEntity<Void> reissueRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/auths/reissue",
                HttpMethod.PATCH,
                new HttpEntity<>(reissueHeaders),
                Void.class
        );
        String adminAccessToken = Objects.requireNonNull(reissueRes.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .substring(7);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음: " + "test@fortishop.com"));
        String transactionId = UUID.randomUUID().toString();
        String traceId = "test-trace-id";
        PointSourceService sourceService = PointSourceService.MEMBER_ADJUST;
        BigDecimal amountDec = BigDecimal.valueOf(amount);
        pointService.savePoint(member.getEmail(), amountDec, "포인트 적립", transactionId, traceId, sourceService);

        return adminAccessToken;
    }

    private static void createTopicIfNotExists(String topic, String bootstrapServers) {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(config)) {
            Set<String> existingTopics = admin.listTopics().names().get(3, TimeUnit.SECONDS);
            if (!existingTopics.contains(topic)) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                            .all().get(3, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                        System.out.println("Topic already exists: " + topic);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check or create topic: " + topic, e);
        }
    }
}
