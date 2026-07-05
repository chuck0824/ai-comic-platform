package com.aicp.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/login",
            "/home",
            "/script-gen",
            "/script-gen/{path:[^\\.]*}",
            "/storyboard/{path:[^\\.]*}",
            "/content-projects/{path:[^\\.]*}",
            "/canvas",
            "/canvas/{path:[^\\.]*}",
            "/canvas-projects",
            "/canvas-projects/{path:[^\\.]*}",
            "/画布视频工作台",
            "/画布视频工作台/{path:[^\\.]*}",
            "/warehouse",
            "/warehouse/{path:[^\\.]*}",
            "/tag-editor/{path:[^\\.]*}",
            "/market",
            "/asset-market",
            "/enterprise",
            "/sop/{path:[^\\.]*}",
            "/profile",
            "/agent",
            "/agent-config",
            "/agent-config/{path:[^\\.]*}",
            "/asset-history",
            "/task-monitor",
            "/script-gen-legacy"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
