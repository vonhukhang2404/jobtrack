package com.khang.jobtrack.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * すべての画面に「今のURLのパス」を渡す（ナビの現在地を強調するため）。
 * Thymeleaf 3.1 では画面から request を直接参照できないので、ここで Model に入れる。
 */
@ControllerAdvice
public class CurrentPathAdvice {

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
