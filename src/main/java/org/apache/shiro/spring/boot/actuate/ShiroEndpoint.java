/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.shiro.spring.boot.actuate;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.shiro.biz.web.Constants;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.session.mgt.SimpleOnlineSession;
import org.apache.shiro.session.mgt.SimpleOnlineSession.OnlineStatus;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;


/**
 * {@link Endpoint} to expose shiro info.
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Endpoint(id = "shiro")
public class ShiroEndpoint {
	
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SessionDAO sessionDAO;

    public ShiroEndpoint(SessionDAO sessionDAO) {
        this.sessionDAO = sessionDAO;
    }

    @ReadOperation
    public ApplicationShiroSessions shiroSessions() {
        Map<Serializable, SessionDescriptor> descriptors = new HashMap<>();
        Collection<Session> sessions = sessionDAO.getActiveSessions();
        if (Objects.nonNull(sessions)) {
            for (Session session : sessions) {
                descriptors.put(session.getId(), new SessionDescriptor(session));
            }
        }
        return new ApplicationShiroSessions(descriptors);
    }

    @ReadOperation
    public SessionDescriptor getSession(@Selector String sessionId) {
        try {
            Session session = sessionDAO.readSession(sessionId);
            return Objects.isNull(session) ? null : new SessionDescriptor(session);
        } catch (UnknownSessionException e) {
            return null;
        }
    }

    @WriteOperation
    public boolean forceLogout(@Selector String sessionId) {
        try {
            Session session = sessionDAO.readSession(sessionId);
            if (Objects.isNull(session)) {
                return false;
            }
            if (session instanceof SimpleOnlineSession) {
                ((SimpleOnlineSession) session).setStatus(OnlineStatus.FORCE_LOGOUT);
            }
            session.setAttribute(Constants.SESSION_FORCE_LOGOUT_KEY, Boolean.TRUE);
            sessionDAO.update(session);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

	/**
	 * Description of an shiro's {@link Session} sessions, primarily intended for
	 * serialization to JSON.
	 */
	public static final class ApplicationShiroSessions {

		private final Map<Serializable, SessionDescriptor> sessions;

		private ApplicationShiroSessions(Map<Serializable, SessionDescriptor> shiroSessions) {
			this.sessions = shiroSessions;
		}

		public Map<Serializable, SessionDescriptor> getSessions() {
			return this.sessions;
		}

	}
	
	/**
	 * Description of a {@link Session} session, primarily intended for serialization to JSON.
	 */
	public static class SessionDescriptor {

		/** 会话ID */
		private Serializable sessionId;
		/** 主机地址 */
		private String host;
		/** 登录时间 */
		private String startTimestamp;
		/** 最后访问时间 */
		private String lastAccessTime;
		/** 会话多久后过期（毫秒） */
		private long timeout;
		/** 用户浏览器类型 */
		protected String userAgent;
		/** 用户登录时系统IP */
		protected String systemHost;
		/** 在线状态 */
		protected String status;
		/** 已强制退出:1:是，0:否 */
		private String forceLogout;
		
		public SessionDescriptor() {
			super();
		}

		public SessionDescriptor(Serializable sessionId, String host, String startTimestamp, String lastAccessTime,
				long timeout) {
			this.sessionId = sessionId;
			this.host = host;
			this.startTimestamp = startTimestamp;
			this.lastAccessTime = lastAccessTime;
			this.timeout = timeout;
		}

        public SessionDescriptor(Session session) {
            this(session.getId(), session.getHost(), DATE_FORMAT.format(session.getStartTimestamp().toInstant()),
                    DATE_FORMAT.format(session.getLastAccessTime().toInstant()), session.getTimeout());
            if (session instanceof SimpleOnlineSession) {
                SimpleOnlineSession onlineSession = (SimpleOnlineSession) session;
                this.status = onlineSession.getStatus().getInfo();
                this.userAgent = onlineSession.getUserAgent();
                this.systemHost = onlineSession.getSystemHost();
            }
            if (Boolean.TRUE.equals(session.getAttribute(Constants.SESSION_FORCE_LOGOUT_KEY))) {
                this.status = OnlineStatus.FORCE_LOGOUT.getInfo();
            }
        }

		public Serializable getSessionId() {
			return sessionId;
		}

		public void setSessionId(Serializable sessionId) {
			this.sessionId = sessionId;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getStartTimestamp() {
			return startTimestamp;
		}

		public void setStartTimestamp(String startTimestamp) {
			this.startTimestamp = startTimestamp;
		}

		public String getLastAccessTime() {
			return lastAccessTime;
		}

		public void setLastAccessTime(String lastAccessTime) {
			this.lastAccessTime = lastAccessTime;
		}

		public long getTimeout() {
			return timeout;
		}

		public void setTimeout(long timeout) {
			this.timeout = timeout;
		}

		public String getUserAgent() {
			return userAgent;
		}

		public void setUserAgent(String userAgent) {
			this.userAgent = userAgent;
		}
		
		public String getSystemHost() {
			return systemHost;
		}

		public void setSystemHost(String systemHost) {
			this.systemHost = systemHost;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getForceLogout() {
			return forceLogout;
		}

		public void setForceLogout(String forceLogout) {
			this.forceLogout = forceLogout;
		}

	}

}
