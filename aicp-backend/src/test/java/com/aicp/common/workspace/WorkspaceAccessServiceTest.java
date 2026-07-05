package com.aicp.common.workspace;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceAccessService 单元测试")
class WorkspaceAccessServiceTest {

    @Mock
    private AccountCenterPermissionClient client;

    private WorkspaceAccessService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceAccessService(client);
    }

    @Nested
    @DisplayName("Workspace 上下文解析")
    class ContextResolution {

        @Test
        @DisplayName("企业 workspace 成员解析成功")
        void resolvesEnterpriseMember() throws Exception {
            when(client.membership(eq("ent_100"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "ent_100", "enterprise", 9L, "MEMBER",
                            List.of("asset.view", "asset.use"), List.of(), List.of()));

            WorkspaceContext ctx = service.resolve("ent_100", "Bearer token", 9L);

            assertThat(ctx.workspaceId()).isEqualTo("ent_100");
            assertThat(ctx.workspaceType()).isEqualTo("enterprise");
            assertThat(ctx.userId()).isEqualTo(9L);
            assertThat(ctx.permissions()).containsExactlyInAnyOrder("asset.view", "asset.use");
        }

        @Test
        @DisplayName("个人 workspace 成员解析成功")
        void resolvesPersonalMember() throws Exception {
            when(client.membership(eq("personal_7"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "personal_7", "personal", 7L, "OWNER",
                            List.of("asset.view", "asset.use", "asset.manage"), List.of(), List.of()));

            WorkspaceContext ctx = service.resolve("personal_7", "Bearer token", 7L);

            assertThat(ctx.workspaceType()).isEqualTo("personal");
            assertThat(ctx.permissions()).contains("asset.manage");
        }

        @Test
        @DisplayName("返回的 user_id 与认证用户不一致 → 403")
        void rejectsMismatchedUser() throws Exception {
            when(client.membership(eq("ent_100"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "ent_100", "enterprise", 9L, "MEMBER",
                            List.of("asset.view"), List.of(), List.of()));

            assertThatThrownBy(() -> service.resolve("ent_100", "Bearer token", 99L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无资产操作权限");
        }
    }

    @Nested
    @DisplayName("权限校验")
    class PermissionChecks {

        @Test
        @DisplayName("持有权限 → require 不抛异常")
        void requireSucceedsForHeldPermission() {
            WorkspaceContext ctx = new WorkspaceContext("ent_100", "enterprise", 9L,
                    java.util.Set.of("asset.view", "asset.use"));
            ctx.require("asset.use"); // should not throw
        }

        @Test
        @DisplayName("缺少权限 → require 抛出 BizException")
        void requireFailsForMissingPermission() {
            WorkspaceContext ctx = new WorkspaceContext("ent_100", "enterprise", 9L,
                    java.util.Set.of("asset.view"));

            assertThatThrownBy(() -> ctx.require("asset.publish.approve"))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", 48002);
        }

        @Test
        @DisplayName("空权限集 → require 抛出异常")
        void requireFailsForEmptyPermissions() {
            WorkspaceContext ctx = new WorkspaceContext("ent_100", "enterprise", 9L,
                    java.util.Set.of());

            assertThatThrownBy(() -> ctx.require("asset.view"))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("has 方法正确判断权限存在与否")
        void hasMethodCorrectlyChecksPermission() {
            WorkspaceContext ctx = new WorkspaceContext("ent_100", "enterprise", 9L,
                    java.util.Set.of("asset.view"));

            assertThat(ctx.has("asset.view")).isTrue();
            assertThat(ctx.has("asset.publish.approve")).isFalse();
        }
    }

    @Nested
    @DisplayName("Workspace ID 格式规范化")
    class IdentityNormalization {

        @Test
        @DisplayName("personal_7 格式为个人 workspace")
        void personalUnderscoreFormatResolvesPersonal() throws Exception {
            when(client.membership(eq("personal_7"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "personal_7", "personal", 7L, "OWNER", List.of("asset.view"), List.of(), List.of()));

            WorkspaceContext ctx = service.resolve("personal_7", "Bearer token", 7L);
            assertThat(ctx.workspaceType()).isEqualTo("personal");
            assertThat(ctx.workspaceId()).isEqualTo("personal_7");
        }

        @Test
        @DisplayName("enterprise_ 前缀为 enterprise workspace")
        void enterpriseUnderscoreFormatResolvesEnterprise() throws Exception {
            when(client.membership(eq("enterprise_42"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "enterprise_42", "enterprise", 9L, "MEMBER", List.of("asset.view"), List.of(), List.of()));

            WorkspaceContext ctx = service.resolve("enterprise_42", "Bearer token", 9L);
            assertThat(ctx.workspaceType()).isEqualTo("enterprise");
        }

        @Test
        @DisplayName("ent_ 格式也识别为 enterprise")
        void entPrefixFormatResolvesEnterprise() throws Exception {
            when(client.membership(eq("ent_100"), anyString()))
                    .thenReturn(new AccountCenterPermissionClient.MembershipResponse(
                            "ent_100", "enterprise", 10L, "MEMBER", List.of("asset.view"), List.of(), List.of()));

            WorkspaceContext ctx = service.resolve("ent_100", "Bearer token", 10L);
            assertThat(ctx.workspaceType()).isEqualTo("enterprise");
            assertThat(ctx.workspaceId()).isEqualTo("ent_100");
        }
    }

    @Nested
    @DisplayName("账户中心不可用时 fail-closed")
    class UpstreamFailure {

        @Test
        @DisplayName("3001 不可用 → 使用明确的 WORKSPACE_UPSTREAM_UNAVAILABLE 错误码")
        void upstreamFailureUsesStableErrorCode() throws Exception {
            when(client.membership(eq("personal_7"), anyString()))
                    .thenThrow(new AccountCenterPermissionClient.UpstreamUnavailableException(
                            "down", null));

            assertThatThrownBy(() -> service.resolve("personal_7", "Bearer token", 7L))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(41012);
        }

        @Test
        @DisplayName("3001 不可用 → 抛出业务异常（不可用提示）")
        void rejectsWhenAccountCenterUnavailable() throws Exception {
            when(client.membership(eq("ent_100"), anyString()))
                    .thenThrow(new AccountCenterPermissionClient.UpstreamUnavailableException(
                            "连接超时", new RuntimeException("Connection refused")));

            assertThatThrownBy(() -> service.resolve("ent_100", "Bearer token", 9L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("账户中心暂不可用");
        }

        @Test
        @DisplayName("3001 返回 null（无成员资格） → 404")
        void returnsNotFoundWhenNoMembership() throws Exception {
            when(client.membership(eq("ent_100"), anyString()))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.resolve("ent_100", "Bearer token", 9L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("资产不存在");
        }
    }
}
