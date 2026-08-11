package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.dto.ContentProjectRequests.SaveDraftRequest;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentUnitCandidateIsolationServiceTest {

    @Mock ContentUnitMapper unitMapper;
    @Mock ContentVersionMapper versionMapper;
    private ContentUnitService service;

    @BeforeEach
    void setUp() {
        service = new ContentUnitService(unitMapper, versionMapper);
    }

    @Test
    void publicVersionListHidesCandidateAndDiscardedVersions() {
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(101L, "candidate"), version(102L, "discarded"), version(103L, "accepted")));

        assertThat(service.listVersions(17L)).extracting("id").containsExactly(103L);
    }

    @Test
    void restoreRejectsCandidateAndDiscardedVersions() {
        ContentUnit unit = unit();
        when(unitMapper.selectById(17L)).thenReturn(unit);
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "candidate"));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, "discarded"));

        assertThatThrownBy(() -> service.restoreVersion(501L, 17L, 101L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.restoreVersion(501L, 17L, 102L)).isInstanceOf(BizException.class);
        verify(versionMapper, never()).insert(any(ContentVersion.class));
        verify(versionMapper, never()).updateById(any(ContentVersion.class));
    }

    @Test
    void staleAutosaveCannotOverwriteRevisionOrCurrentVersion() {
        ContentUnit unit = unit();
        unit.setCurrentVersionId(103L);
        unit.setRevision(4);
        when(unitMapper.selectById(17L)).thenReturn(unit);
        when(unitMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.saveDraft(501L, 17L,
                new SaveDraftRequest(4, "{}", ""))).isInstanceOf(BizException.class);

        verify(versionMapper, never()).insert(any(ContentVersion.class));
        verify(versionMapper, never()).updateById(any(ContentVersion.class));
        assertThat(unit.getCurrentVersionId()).isEqualTo(103L);
    }

    private ContentUnit unit() {
        ContentUnit unit = new ContentUnit();
        unit.setId(17L);
        unit.setProjectId(9L);
        unit.setRevision(0);
        unit.setIsDeleted(0);
        return unit;
    }

    private ContentVersion version(Long id, String status) {
        ContentVersion version = new ContentVersion();
        version.setId(id);
        version.setProjectId(9L);
        version.setContentUnitId(17L);
        version.setVersionNo(Math.toIntExact(id - 100L));
        version.setStatus(status);
        version.setContentJson("{}");
        version.setPlainText("");
        return version;
    }
}
