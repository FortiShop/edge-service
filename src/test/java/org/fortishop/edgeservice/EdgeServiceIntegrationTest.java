package org.fortishop.edgeservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.fortishop.edgeservice.domain.Member;
import org.fortishop.edgeservice.domain.RefreshToken;
import org.fortishop.edgeservice.repository.MemberRepository;
import org.fortishop.edgeservice.repository.RefreshTokenRepository;
import org.fortishop.edgeservice.request.LoginRequest;
import org.fortishop.edgeservice.request.MemberUpdateNicknameRequest;
import org.fortishop.edgeservice.request.PasswordUpdateRequest;
import org.fortishop.edgeservice.request.SignupRequest;
import org.fortishop.edgeservice.response.MemberResponse;
import org.fortishop.edgeservice.response.MemberUpdateNicknameResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    RefreshTokenRepository refreshTokenRepository;

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

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        mysql.start();
        rabbit.start();
        redis.start();

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private String accessToken;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/members";
    }

    @BeforeEach
    void setUpUser(TestInfo testInfo) {
        String methodName = testInfo.getTestMethod().map(Method::getName).orElse("");
        if (methodName.contains("checkEmailDuplicate") || methodName.contains("checkNicknameDuplicate")
                || methodName.contains("signup_duplicateEmail") || methodName.contains("login_wrongPassword")) {
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

    @DisplayName("이메일 중복으로 회원가입에 실패한다")
    @Test
    void signup_duplicateEmail_fail() {
        SignupRequest dup = new SignupRequest("test@fortishop.com", "newPw123!", "새닉네임");
        ResponseEntity<Void> response = restTemplate.postForEntity(getBaseUrl() + "/signup", dup,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
    @DisplayName("회원 탈퇴 후 로그인 시도 시 실패한다")
    void loginAfterWithdraw_fail() {
        // 회원 탈퇴
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> logoutRequest = new HttpEntity<>(logoutHeaders);

        ResponseEntity<Void> withdrawResponse = restTemplate.exchange(
                getBaseUrl() + "/me", HttpMethod.DELETE, logoutRequest, Void.class);
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 탈퇴 후 로그인 시도
        LoginRequest login = new LoginRequest("test@fortishop.com", "pw1234");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(login, headers);

        ResponseEntity<Void> loginResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/auths/login", entity, Void.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private RefreshToken getRefreshTokenFromDB() {
        Member member = memberRepository.findByEmail("test@fortishop.com")
                .orElseThrow(() -> new IllegalArgumentException("회원 없음: " + "test@fortishop.com"));

        return refreshTokenRepository.findByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken 없음: " + "test@fortishop.com"));
    }
}
