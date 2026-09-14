package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectViews.DraftView;
import com.aicp.module.contentproject.dto.ContentProjectViews.GenerationJobView;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteAdoptRequest;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteRequest;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.PatchOp;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalRewriteServiceTest {

    @Mock ContentUnitMapper unitMapper;
    @Mock ContentVersionMapper versionMapper;
    @Mock ProjectAccessService accessService;
    @Mock ContentGenerationJobService generationJobService;
    @Mock ContentGenerationExecutor generationExecutor;
    @Mock ContentUnitService contentUnitService;

    LocalRewriteService service;

    @BeforeEach
    void setUp() {
        service = new LocalRewriteService(
                unitMapper, versionMapper, accessService, generationJobService,
                generationExecutor, contentUnitService, new ObjectMapper());
    }

    @Test
    void createRewrite_createsJobAndReturnsCandidatePatches() {
        ContentUnit unit = unit(1L, 10L, 3);
        when(unitMapper.selectById(10L)).thenReturn(unit);
        when(versionMapper.selectOne(any())).thenReturn(draft("hello world", "hello world"));
        when(versionMapper.selectList(any())).thenReturn(List.of());

        GenerationJobView job = new GenerationJobView(
                99L, "uuid", "local_rewrite", "content_unit", 10L,
                "completed", 0, 1, null, null, null, null, null, null, null, 7L, null, null);
        when(generationJobService.createJob(eq(7L), eq(1L), any(), anyString(), eq(false))).thenReturn(job);
        when(generationJobService.getJob(7L, 99L)).thenReturn(job);

        ContentVersion candidate = new ContentVersion();
        candidate.setId(501L);
        candidate.setContentJson("""
                {"before":"world","after":"WORLD","patches":[{"startOffset":6,"endOffset":11,"expectedTextHash":"%s","replacement":"WORLD","reason":"rewrite"}]}
                """.formatted(sha256("world")));
        candidate.setPlainText("WORLD");
        when(generationExecutor.completeLocalRewrite(99L)).thenReturn(candidate);

        Map<String, Object> result = service.createRewrite(7L, 10L, new LocalRewriteRequest(
                5L, 3, sha256("hello world"), 6, 11, sha256("world"), "rewrite", null, null));

        assertThat(result.get("candidate_version_id")).isEqualTo(501L);
        assertThat(((Map<?, ?>) result.get("diff")).get("after")).isEqualTo("WORLD");
        verify(generationJobService).createJob(eq(7L), eq(1L), any(), anyString(), eq(false));
        verify(generationExecutor).completeLocalRewrite(99L);
    }

    @Test
    void adoptPatches_appliesFromTailAndUpdatesPlainText() {
        ContentUnit unit = unit(1L, 10L, 2);
        when(unitMapper.selectById(10L)).thenReturn(unit);
        ContentVersion draft = draft("abcXYZ", "abcXYZ");
        draft.setId(20L);
        when(versionMapper.selectOne(any())).thenReturn(draft);
        when(unitMapper.update(isNull(), any())).thenReturn(1);

        DraftView view = service.adoptPatches(7L, 10L, new LocalRewriteAdoptRequest(
                2,
                sha256("abcXYZ"),
                List.of(new PatchOp(3, 6, sha256("XYZ"), "123", "rewrite")),
                null));

        assertThat(view.plainText()).isEqualTo("abc123");
        assertThat(view.revision()).isEqualTo(3);
        verify(versionMapper).updateById(argThat(v -> "abc123".equals(v.getPlainText())));
    }

    @Test
    void adoptPatches_staleCandidate_throws() {
        ContentUnit unit = unit(1L, 10L, 2);
        when(unitMapper.selectById(10L)).thenReturn(unit);
        when(versionMapper.selectOne(any())).thenReturn(draft("abc", "abc"));

        ContentVersion candidate = new ContentVersion();
        candidate.setId(9L);
        candidate.setContentUnitId(10L);
        candidate.setStatus("stale");
        when(versionMapper.selectById(9L)).thenReturn(candidate);

        assertThatThrownBy(() -> service.adoptPatches(7L, 10L, new LocalRewriteAdoptRequest(
                2, sha256("abc"),
                List.of(new PatchOp(0, 3, sha256("abc"), "x", "rewrite")),
                9L)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.AI_CANDIDATE_STALE.getCode()));
    }

    private static ContentUnit unit(Long projectId, Long id, int revision) {
        ContentUnit unit = new ContentUnit();
        unit.setId(id);
        unit.setProjectId(projectId);
        unit.setRevision(revision);
        unit.setIsDeleted(0);
        return unit;
    }

    private static ContentVersion draft(String plain, String json) {
        ContentVersion draft = new ContentVersion();
        draft.setStatus("draft");
        draft.setPlainText(plain);
        draft.setContentJson(json);
        draft.setContentHash(sha256(plain));
        return draft;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
