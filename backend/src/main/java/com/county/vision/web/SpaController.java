package com.county.vision.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用 history 路由回退：
 * 非 /api、非静态资源的路径统一返回 index.html，由 vue-router 处理。
 */
@Controller
public class SpaController {

    @GetMapping({
            "/login",
            "/dashboard",
            "/students",
            "/students/**",
            "/screening",
            "/review",
            "/parent",
            "/visit",
            "/subsidy",
            "/verification",
            "/followup",
            "/issues"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
