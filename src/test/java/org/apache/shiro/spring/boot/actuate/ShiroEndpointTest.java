package org.apache.shiro.spring.boot.actuate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

import org.apache.shiro.biz.web.Constants;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.session.mgt.SimpleOnlineSession;
import org.apache.shiro.session.mgt.SimpleOnlineSession.OnlineStatus;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.apache.shiro.spring.boot.actuate.ShiroEndpoint.SessionDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ShiroEndpointTest {

    private final SessionDAO sessions = mock(SessionDAO.class);
    private final ShiroEndpoint endpoint = new ShiroEndpoint(sessions);

    @Test
    void formatsCalendarMonthAndIncludesOnlineMetadataInListAndDetail() {
        SimpleOnlineSession online = new SimpleOnlineSession("127.0.0.1");
        online.setId("online");
        online.setUserAgent("test-client");
        online.setSystemHost("test-host");
        online.setStartTimestamp(atLocalTime(10, 17));
        online.setLastAccessTime(atLocalTime(11, 42));
        online.setTimeout(60_000L);
        SimpleSession plain = new SimpleSession("127.0.0.2");
        plain.setId("plain");

        when(sessions.getActiveSessions()).thenReturn(Arrays.asList(online, plain));
        when(sessions.readSession("online")).thenReturn(online);

        SessionDescriptor detail = endpoint.getSession("online");
        SessionDescriptor listed = endpoint.shiroSessions().getSessions().get("online");
        assertThat(listed).usingRecursiveComparison().isEqualTo(detail);
        assertThat(detail.getStartTimestamp()).isEqualTo("2026-08-24 10:17:00");
        assertThat(detail.getLastAccessTime()).isEqualTo("2026-08-24 11:42:00");
        assertThat(detail.getSessionId()).isEqualTo("online");
        assertThat(detail.getHost()).isEqualTo("127.0.0.1");
        assertThat(detail.getTimeout()).isEqualTo(60_000L);
        assertThat(detail.getUserAgent()).isEqualTo("test-client");
        assertThat(detail.getSystemHost()).isEqualTo("test-host");
        assertThat(detail.getStatus()).isEqualTo(OnlineStatus.ON_LINE.getInfo());
        assertThat(endpoint.shiroSessions().getSessions()).containsKeys("plain", "online");
    }

    @Test
    void persistsTheForcedLogoutMarkerThroughTheSessionDao() {
        SimpleOnlineSession online = new SimpleOnlineSession();
        online.setId("online");
        when(sessions.readSession("online")).thenReturn(online);

        assertThat(endpoint.forceLogout("online")).isTrue();

        verify(sessions).update(online);
        assertThat(online.getAttribute(Constants.SESSION_FORCE_LOGOUT_KEY)).isEqualTo(Boolean.TRUE);
        assertThat(online.getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT);
        assertThat(endpoint.getSession("online").getStatus()).isEqualTo(OnlineStatus.FORCE_LOGOUT.getInfo());
    }

    @Test
    void reportsMissingSessionsForBothLookupAndForceLogout() {
        when(sessions.readSession("expired")).thenThrow(new UnknownSessionException());

        assertThat(endpoint.getSession("expired")).isNull();
        assertThat(endpoint.forceLogout("expired")).isFalse();
        assertThat(endpoint.getSession("absent")).isNull();
        assertThat(endpoint.forceLogout("absent")).isFalse();
        verify(sessions, times(2)).readSession("expired");
        verify(sessions, times(2)).readSession("absent");
        verifyNoMoreInteractions(sessions);
    }

    private Date atLocalTime(int hour, int minute) {
        return Date.from(LocalDateTime.of(2026, 8, 24, hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }
}
