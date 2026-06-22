package com.aicp.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/login",
            "/dashboard",
            "/script-gen",
            "/storyboard/{path:[^\\.]*}",
            "/canvas",
            "/canvas/{path:[^\\.]*}",
            "/画布工作台",
            "/画布工作台/{path:[^\\.]*}",
            "/warehouse",
            "/tag-editor/{path:[^\\.]*}",
            "/market",
            "/asset-market",
            "/enterprise",
            "/sop/{path:[^\\.]*}",
            "/profile"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
