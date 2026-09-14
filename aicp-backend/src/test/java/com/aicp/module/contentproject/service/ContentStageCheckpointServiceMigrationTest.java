package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.domain.ScriptStageState;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentStageCheckpoint;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentStageCheckpointServiceMigrationTest {

    @Mock ContentStageCheckpointMapper checkpointMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ContentUnitMapper contentUnitMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock StoryboardHandoffSnapshotMapper handoffSnapshotMapper;
    @Mock ProjectAccessService accessService;
    @Mock StageGateRegistry gateRegistry;
    @Mock ScriptStageTruthProperties truthProperties;

    ContentStageCheckpointService service;
    AtomicLong idSeq = new AtomicLong(1);
    List<ContentStageCheckpoint> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        idSeq.set(1);
        store.clear();
        unitSelects = 0;
        service = new ContentStageCheckpointService(
                checkpointMapper, projectMapper, contentUnitMapper, contentVersionMapper,
                handoffSnapshotMapper, accessService, gateRegistry, truthProperties, new ObjectMapper());

        when(checkpointMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(checkpointMapper.insert(any(ContentStageCheckpoint.class))).thenAnswer(inv -> {
            ContentStageCheckpoint row = inv.getArgument(0);
            row.setId(idSeq.getAndIncrement());
            store.add(row);
            return 1;
        });
        when(checkpointMapper.updateById(any(ContentStageCheckpoint.class))).thenAnswer(inv -> {
            ContentStageCheckpoint updated = inv.getArgument(0);
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(updated.getId())) {
                    store.set(i, updated);
                    break;
                }
            }
            return 1;
        });
    }

    @Test
    void migratesLegacyResumeKeyAndAttachesParameterEvidence() {
        ContentProject project = new ContentProject();
        project.setId(10L);
        project.setLastStageKey("story_seed");
        project.setCurrentParameterVersionId(41L);

        service.ensureEight(project, 7L);

        ArgumentCaptor<ContentProject> projectCaptor = ArgumentCaptor.forClass(ContentProject.class);
        verify(projectMapper, atLeastOnce()).updateById(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getLastStageKey()).isEqualTo("creation_settings");

        assertThat(store).hasSize(8);
        ContentStageCheckpoint settings = byKey("creation_settings");
        assertThat(settings.getState()).isEqualTo(ScriptStageState.IN_PROGRESS.name());
        assertThat(settings.getPrimaryArtifactType()).isEqualTo("PARAMETER_VERSION");
        assertThat(settings.getPrimaryArtifactId()).isEqualTo(41L);

        ContentStageCheckpoint later = byKey("script_body");
        assertThat(later.getState()).isEqualTo(ScriptStageState.NOT_STARTED.name());
    }

    @Test
    void priorStageWithAcceptedVersionBecomesCompleted() {
        ContentProject project = new ContentProject();
        project.setId(11L);
        project.setLastStageKey("adaptation");
        project.setCurrentParameterVersionId(5L);

        ContentVersion accepted = new ContentVersion();
        accepted.setId(201L);
        accepted.setStatus("accepted");
        accepted.setVersionNo(1);

        when(contentUnitMapper.selectOne(any())).thenAnswer(inv -> stubUnit());
        when(contentVersionMapper.selectCount(any())).thenReturn(1L);
        when(contentVersionMapper.selectOne(any())).thenReturn(accepted);

        service.ensureEight(project, 1L);

        assertThat(byKey("creation_settings").getState()).isEqualTo(ScriptStageState.COMPLETED.name());
        assertThat(byKey("creation_settings").getPrimaryArtifactId()).isEqualTo(5L);
        assertThat(byKey("adaptation").getState()).isEqualTo(ScriptStageState.IN_PROGRESS.name());
        // novel_upload is before adaptation and got accepted version
        assertThat(byKey("novel_upload").getState()).isEqualTo(ScriptStageState.COMPLETED.name());
        assertThat(byKey("novel_upload").getAdoptedContentVersionId()).isEqualTo(201L);
    }

    private int unitSelects;

    private ContentUnit stubUnit() {
        unitSelects++;
        if (unitSelects == 1) {
            ContentUnit unit = new ContentUnit();
            unit.setId(90L);
            unit.setUnitType("novel_upload");
            return unit;
        }
        return null;
    }

    @Test
    void secondEnsureEightDoesNotRewritWhenArtifactsPresent() {
        unitSelects = 0;
        ContentProject project = new ContentProject();
        project.setId(12L);
        project.setLastStageKey("creation_settings");
        project.setCurrentParameterVersionId(9L);

        service.ensureEight(project, 1L);
        int revisionAfterFirst = byKey("creation_settings").getRevision();
        service.ensureEight(project, 1L);

        assertThat(store).hasSize(8);
        assertThat(byKey("creation_settings").getRevision()).isEqualTo(revisionAfterFirst);
    }

    private ContentStageCheckpoint byKey(String key) {
        return store.stream().filter(r -> key.equals(r.getStageKey())).findFirst().orElseThrow();
    }
}
