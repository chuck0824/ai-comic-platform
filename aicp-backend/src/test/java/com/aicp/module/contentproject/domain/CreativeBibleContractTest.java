package com.aicp.module.contentproject.domain;

import com.aicp.module.contentproject.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Creative Bible 持久化契约")
class CreativeBibleContractTest {

    @Test
    @DisplayName("实体暴露版本和作用域字段")
    void entitiesExposeVersionAndScopeContracts() {
        CreativeBibleVersion bible = new CreativeBibleVersion();
        bible.setVersionNo(1);
        bible.setStatus("confirmed");

        ProjectWritingGuide guide = new ProjectWritingGuide();
        guide.setScopeType("content_unit");
        guide.setScopeId(42L);

        GenerationContextSnapshot snapshot = new GenerationContextSnapshot();
        snapshot.setPayloadHash("abc");

        assertThat(bible.getVersionNo()).isEqualTo(1);
        assertThat(guide.getScopeType()).isEqualTo("content_unit");
        assertThat(snapshot.getPayloadHash()).isEqualTo("abc");
    }

    @Test
    @DisplayName("EcosystemRule 实体支持所有规则类型")
    void ecosystemRuleSupportsAllRuleTypes() {
        EcosystemRule rule = new EcosystemRule();
        rule.setRuleType("world_rule");
        rule.setName("能力使用必须付出记忆代价");
        rule.setStatus("draft");
        rule.setRevision(0);

        assertThat(rule.getRuleType()).isEqualTo("world_rule");
        assertThat(rule.getName()).isEqualTo("能力使用必须付出记忆代价");
        assertThat(rule.getStatus()).isEqualTo("draft");
        assertThat(rule.getRevision()).isEqualTo(0);
    }

    @Test
    @DisplayName("GenerationContextSnapshot 包含角色口径 ID 列表字段")
    void generationContextSnapshotHasCharacterGuideIds() {
        GenerationContextSnapshot snapshot = new GenerationContextSnapshot();
        snapshot.setCharacterGuideIdsJson("[1, 2, 3]");
        snapshot.setSelectedVersionsJson("{}");
        snapshot.setResolvedGuideJson("{}");
        snapshot.setPayloadJson("{}");

        assertThat(snapshot.getCharacterGuideIdsJson()).isEqualTo("[1, 2, 3]");
    }
}
