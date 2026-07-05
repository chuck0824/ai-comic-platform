package com.aicp.module.agent.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Agent 配置中心 Schema 验证")
class AgentConfigurationSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("六张核心表均存在")
    void sixTablesExist() {
        for (String table : List.of("agent_blueprints", "user_agent_definitions",
                "agent_versions", "agent_bindings", "agent_test_runs",
                "agent_execution_snapshots")) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table.toUpperCase());
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("四类 ACTIVE Blueprint 种子存在")
    void fourActiveBlueprintsExist() {
        Integer count = jdbc.queryForObject(
                "select count(*) from agent_blueprints where status = 'ACTIVE'",
                Integer.class);
        assertThat(count).isEqualTo(4);

        List<String> roles = jdbc.queryForList(
                "select distinct role_type from agent_blueprints where status = 'ACTIVE' order by role_type",
                String.class);
        assertThat(roles).containsExactly("DIRECTOR", "HOOK", "SCREENWRITER", "STORYBOARD");
    }

    @Test
    @DisplayName("Blueprint role_type + blueprint_version 唯一")
    void blueprintRoleVersionUnique() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into agent_blueprints(uuid,role_type,name,parameter_schema_json," +
                "default_parameters_json,locked_system_prompt,editable_prompt_template," +
                "input_schema_json,output_schema_json,allowed_tools_json,context_policy_json," +
                "model_policy_json,blueprint_version,status) values " +
                "('dup-bp','HOOK','Duplicate','{}','{}','','','{}','{}','[]','{}','{}',1,'ACTIVE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("binding scope_type + scope_id + role_type 唯一")
    void bindingScopeAndRoleUnique() {
        jdbc.update("insert into agent_bindings(uuid,scope_type,scope_id,role_type," +
                "user_agent_id,agent_version_id,created_by) " +
                "values ('b1','USER','1','HOOK',1,1,1)");
        assertThatThrownBy(() -> jdbc.update(
                "insert into agent_bindings(uuid,scope_type,scope_id,role_type," +
                "user_agent_id,agent_version_id,created_by) " +
                "values ('b2','USER','1','HOOK',1,1,1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("user_agent_definitions owner + name 唯一")
    void definitionOwnerNameUnique() {
        jdbc.update("insert into user_agent_definitions(uuid,blueprint_id,owner_user_id,name) " +
                "values ('def1',1,7,'我的钩子')");
        assertThatThrownBy(() -> jdbc.update(
                "insert into user_agent_definitions(uuid,blueprint_id,owner_user_id,name) " +
                "values ('def2',1,7,'我的钩子')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("agent_versions user_agent_id + version_no 唯一")
    void versionAgentVersionNoUnique() {
        jdbc.update("insert into user_agent_definitions(uuid,blueprint_id,owner_user_id,name) " +
                "values ('def-v1',1,7,'版本测试')");
        jdbc.update("insert into agent_versions(uuid,user_agent_id,blueprint_id,version_no) " +
                "values ('v1',(select id from user_agent_definitions where uuid='def-v1'),1,1)");
        assertThatThrownBy(() -> jdbc.update(
                "insert into agent_versions(uuid,user_agent_id,blueprint_id,version_no) " +
                "values ('v1-dup',(select id from user_agent_definitions where uuid='def-v1'),1,1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
