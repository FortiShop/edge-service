package org.fortishop.edgeservice.global.config;

import org.fortishop.edgeservice.auth.filter.AddMemberRoleFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayFilterConfig {
    @Bean
    public FilterRegistrationBean<AddMemberRoleFilter> addMemberRoleFilter() {
        FilterRegistrationBean<AddMemberRoleFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AddMemberRoleFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(2);
        return registrationBean;
    }
}
