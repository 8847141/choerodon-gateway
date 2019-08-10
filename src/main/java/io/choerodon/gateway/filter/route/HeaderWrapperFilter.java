package io.choerodon.gateway.filter.route;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import io.choerodon.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static io.choerodon.core.variable.RequestVariableHolder.HEADER_JWT;
import static io.choerodon.core.variable.RequestVariableHolder.HEADER_TOKEN;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;

/**
 * 添加token和label到请求header
 *
 * @author flyleft
 */
public class HeaderWrapperFilter extends ZuulFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderWrapperFilter.class);

    private GatewayProperties gatewayHelperProperties;

    public HeaderWrapperFilter(GatewayProperties gatewayHelperProperties) {
        this.gatewayHelperProperties = gatewayHelperProperties;
    }

    private static final int HEADER_WRAPPER_FILTER = -1;

    @Override
    public String filterType() {
        return PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return HEADER_WRAPPER_FILTER;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        String token = (String) request.getAttribute(HEADER_JWT);
        boolean isPublic = Boolean.valueOf((String) request.getAttribute("isPublic"));
        if (isPublic) {
            //移除Authorization header,防止其他服务解析jwt时报不合法的token
            ctx.setRequest(removeAuthorizationHeader(request));
        }
        if (StringUtils.isEmpty(token)) {
            LOGGER.info("Request get empty jwt , request uri: {} method: {}", request.getRequestURI(), request.getMethod());
        } else {
            ctx.addZuulRequestHeader(HEADER_TOKEN, token);
            if (gatewayHelperProperties.isEnabledJwtLog()) {
                LOGGER.info("Request get jwt , request uri: {} method: {} JWT: {}",
                        request.getRequestURI(), request.getMethod(), token);
            }
        }
        return null;
    }

    private HttpServletRequest removeAuthorizationHeader(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            private Set<String> headerNameSet;

            @Override
            public Enumeration<String> getHeaderNames() {
                if (headerNameSet == null) {
                    // first time this method is called, cache the wrapped request's header names:
                    headerNameSet = new HashSet<>();
                    Enumeration<String> wrappedHeaderNames = super.getHeaderNames();
                    while (wrappedHeaderNames.hasMoreElements()) {
                        String headerName = wrappedHeaderNames.nextElement();
                        if (!HEADER_TOKEN.equalsIgnoreCase(headerName)) {
                            headerNameSet.add(headerName);
                        }
                    }
                }
                return Collections.enumeration(headerNameSet);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (HEADER_TOKEN.equalsIgnoreCase(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public String getHeader(String name) {
                if (HEADER_TOKEN.equalsIgnoreCase(name)) {
                    return null;
                }
                return super.getHeader(name);
            }
        };
    }
}
