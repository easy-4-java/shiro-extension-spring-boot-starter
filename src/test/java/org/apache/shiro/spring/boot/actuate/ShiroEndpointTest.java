package org.apache.shiro.spring.boot.actuate;

import java.util.List;

import org.apache.shiro.biz.web.Constants;
import org.apache.shiro.session.mgt.SimpleOnlineSession;
import org.apache.shiro.session.mgt.SimpleOnlineSession.OnlineStatus;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.session.mgt.eis.MemorySessionDAO;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShiroEndpointTest {

    @Test
    void listsOnlineAndOrdinarySessionsAndShowsForcedLogout() {
        var sessions = new MemorySessionDAO();
        var online = new SimpleOnlineSession();
        online.setHost("online-host");
        online.setSystemHost("system-host");
        online.setUserAgent("browser");
        online.setTimeout(9000);
        var ordinary = new SimpleSession("ordinary-host");
        sessions.create(online);
        sessions.create(ordinary);
        var endpoint = new ShiroEndpoint(sessions);

        var listed = endpoint.shiroSessions().getSessions();
        assertThat(listed).containsOnlyKeys(online.getId(), ordinary.getId());
        var descriptor = listed.get(online.getId());
        assertThat(descriptor.getHost()).isEqualTo("online-host");
        assertThat(descriptor.getSystemHost()).isEqualTo("system-host");
        assertThat(descriptor.getUserAgent()).isEqualTo("browser");
        assertThat(descriptor.getStatus()).isEqualTo(OnlineStatus.ON_LINE.getInfo());
        assertThat(descriptor.getSessionId()).isEqualTo(online.getId());
        assertThat(descriptor.getStartTimestamp()).isNotBlank();
        assertThat(descriptor.getLastAccessTime()).isNotBlank();
        assertThat(descriptor.getTimeout()).isEqualTo(9000);
        assertThat(listed.get(ordinary.getId()).getStatus()).isNull();

        String id = online.getId().toString();
        assertThat(endpoint.getSession(id).getStatus()).isEqualTo(OnlineStatus.ON_LINE.getInfo());
        assertThat(endpoint.forceLogout(id)).isTrue();
        assertThat(sessions.readSession(id).getAttribute(Constants.SESSION_FORCE_LOGOUT_KEY)).isEqualTo(true);
        assertThat(online.getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT);
        assertThat(endpoint.getSession(id).getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT.getInfo());
        assertThat(endpoint.shiroSessions().getSessions().get(id).getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT.getInfo());

        ordinary.setAttribute(Constants.SESSION_FORCE_LOGOUT_KEY, Boolean.TRUE);
        assertThat(endpoint.getSession(ordinary.getId().toString()).getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT.getInfo());
    }

    @Test
    void handlesAbsentSessionsAndReportsDaoErrorsOnLogout() {
        SessionDAO dao = mock(SessionDAO.class);
        when(dao.getActiveSessions()).thenReturn(List.of(), null);
        when(dao.readSession("offline")).thenReturn(null);
        when(dao.readSession("unavailable")).thenThrow(new IllegalStateException("DAO unavailable"));
        var endpoint = new ShiroEndpoint(dao);
        assertThat(endpoint.shiroSessions().getSessions()).isEmpty();
        assertThat(endpoint.shiroSessions().getSessions()).isEmpty();
        assertThat(endpoint.getSession("offline")).isNull();
        assertThat(endpoint.forceLogout("offline")).isTrue();
        assertThat(endpoint.forceLogout("unavailable")).isFalse();
    }
}
