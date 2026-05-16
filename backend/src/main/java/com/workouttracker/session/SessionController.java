package com.workouttracker.session;

import com.workouttracker.common.error.ErrorResponse;
import com.workouttracker.session.dto.SessionCreateRequest;
import com.workouttracker.session.dto.SessionCreateResponse;
import com.workouttracker.session.dto.SessionDetailResponse;
import com.workouttracker.session.dto.SessionPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * 세션 도메인 컨트롤러.
 *
 * <p>출처: docs/design.md 3.5 / 3.6 / 7.1
 *
 * <p>모든 엔드포인트는 인증 필수. 인증된 사용자 ID 는
 * {@link AuthenticationPrincipal} 로 주입 (JwtAuthenticationFilter 에서 SecurityContext 에 세팅).
 */
@Tag(name = "Session", description = "운동 세션 / 세트 기록")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "세션 생성 (단일 트랜잭션)",
            description = "session + session_exercises + exercise_sets 를 하나의 트랜잭션으로 일괄 INSERT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = SessionCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<SessionCreateResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SessionCreateRequest request) {

        SessionCreateResponse response = sessionService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.sessionId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "내 세션 목록 (페이징 + 집계)",
            description = "performedOn DESC, id DESC 고정 정렬. size 최대 50.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SessionPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SessionPageResponse> list(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @Parameter(description = "페이지 크기 (최대 50, 기본 20)", example = "20")
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {

        return ResponseEntity.ok(sessionService.list(userId, page, size));
    }

    @Operation(summary = "세션 상세",
            description = "운동/세트 포함 상세 응답. 소유권 위반 시 404 (존재 자체 숨김).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SessionDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SessionDetailResponse> detail(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "세션 ID", example = "123")
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(sessionService.getDetail(userId, id));
    }

    @Operation(summary = "세션 삭제",
            description = "CASCADE 로 session_exercises / exercise_sets 자동 삭제. 소유권 위반 시 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "세션 없음 또는 소유권 위반",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "세션 ID", example = "123")
            @PathVariable("id") Long id) {

        sessionService.delete(userId, id);
    }
}
