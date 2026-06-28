package com.workouttracker.auth;

import com.workouttracker.auth.dto.LoginRequest;
import com.workouttracker.auth.dto.LoginResponse;
import com.workouttracker.auth.dto.MeResponse;
import com.workouttracker.auth.dto.RefreshRequest;
import com.workouttracker.auth.dto.SignupRequest;
import com.workouttracker.auth.dto.SignupResponse;
import com.workouttracker.auth.email.EmailVerificationService;
import com.workouttracker.auth.email.dto.ResendVerificationRequest;
import com.workouttracker.auth.email.dto.VerifyEmailRequest;
import com.workouttracker.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 / 로그인 / 리프레시 / 로그아웃 / 내 정보")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임으로 신규 사용자 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = SignupResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이메일 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements // 공개
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "이메일 인증 코드 검증",
            description = "발송된 6자리 코드를 검증하고 성공 시 이메일 인증을 완료한다. " +
                    "이미 인증된 이메일은 멱등하게 200 을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 완료(또는 이미 인증됨)"),
            @ApiResponse(responseCode = "400", description = "코드 형식 오류 또는 코드 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "코드 만료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "코드 입력 시도 횟수 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements // 공개
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "이메일 인증 코드 재발송",
            description = "인증 코드를 재발송한다. 이메일 열거 방어를 위해 미가입/이미인증 이메일에도 " +
                    "동일하게 202 를 반환하며, 실제 발송은 조건부로만 수행한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "요청 접수(실제 발송 여부는 비공개)"),
            @ApiResponse(responseCode = "429", description = "재발송 레이트리밋 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements // 공개
    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "로그인",
            description = "이메일/비밀번호 검증 후 Access + Refresh 토큰 발급. Refresh 는 Redis 에 저장된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "토큰 리프레시 (rotation)",
            description = "Refresh Token 으로 새 Access + Refresh 발급. 옛 Refresh 는 즉시 무효화된다. " +
                    "옛 Refresh 의 재사용이 감지되면 그 사용자의 모든 세션이 무효화된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh 토큰 무효 / 만료 / 재사용 감지",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements // 공개 (Refresh 토큰 자체가 인증 수단)
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃",
            description = "refreshToken 제공 시 해당 기기 세션만 무효화, 미제공 시 모든 세션 무효화.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 처리됨"),
            @ApiResponse(responseCode = "401", description = "Access Token 없음/만료/위변조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) RefreshRequest request
    ) {
        String refreshToken = (request == null) ? null : request.refreshToken();
        authService.logout(userId, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "전체 기기 로그아웃",
            description = "해당 사용자의 모든 활성 Refresh 토큰을 무효화 (모든 기기 강제 로그아웃).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리됨"),
            @ApiResponse(responseCode = "401", description = "Access Token 없음/만료/위변조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Long userId) {
        authService.logoutAll(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자 정보 반환 (Bearer 토큰 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "401", description = "토큰 없음/만료/위변조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(authService.getMe(userId));
    }
}
