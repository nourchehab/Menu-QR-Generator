package com.restaurant.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class HomeController {

    @GetMapping("/ping")
    @ResponseBody
    public String ping() {
        return "OK";
    }

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/menus")
    public String menus() {
        return "menus";
    }
}
