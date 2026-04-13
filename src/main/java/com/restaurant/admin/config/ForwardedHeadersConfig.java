package com.restaurant.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class ForwardedHeadersConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> filterReg = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        filterReg.setOrder(0);
        return filterReg;
    }
}
