package com.workouttracker.exercise;

import com.workouttracker.common.error.ErrorResponse;
import com.workouttracker.exercise.dto.ExerciseListResponse;
import com.workouttracker.exercise.dto.ExerciseStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exercise", description = "운동 종류 마스터 조회")
@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseStatsService exerciseStatsService;

    @Operation(summary = "운동 종류 목록 조회",
            description = "body_part 필터 (선택). 허용 값: CHEST, BACK, LEG, SHOULDER, ARM, CORE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = ExerciseListResponse.class))),
            @ApiResponse(responseCode = "400", description = "bodyPart 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ExerciseListResponse> list(
            @Parameter(description = "운동 부위 필터", example = "CHEST")
            @RequestParam(value = "bodyPart", required = false) String bodyPart) {
        return ResponseEntity.ok(exerciseService.list(bodyPart));
    }

    @Operation(summary = "운동별 PR / 최근 기록",
            description = "본인 기록 기준 최고 무게 1건 + 최근 세션 5건의 topSet 반환. 기록 없으면 PR 은 null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = ExerciseStatsResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "운동 ID 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/stats")
    public ResponseEntity<ExerciseStatsResponse> stats(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "운동 ID", example = "1")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(exerciseStatsService.getStats(userId, id));
    }
}
