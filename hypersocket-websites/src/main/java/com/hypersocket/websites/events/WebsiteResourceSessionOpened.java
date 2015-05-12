package com.hypersocket.websites.events;

import org.apache.commons.lang3.ArrayUtils;

import com.hypersocket.session.Session;
import com.hypersocket.websites.WebsiteResource;

public class WebsiteResourceSessionOpened extends WebsiteResourceSessionEvent {

	private static final long serialVersionUID = -904225011629709870L;

	public static final String EVENT_RESOURCE_KEY = "website.sessionOpened";
	
	public WebsiteResourceSessionOpened(Object source,
			boolean success, WebsiteResource resource, Session session) {
		super(source, EVENT_RESOURCE_KEY, success, resource, session);

	}

	public WebsiteResourceSessionOpened(Object source,
			Throwable e, WebsiteResource resource, Session session) {
		super(source, EVENT_RESOURCE_KEY, resource, e, session);
	}

	public String[] getResourceKeys() {
		return ArrayUtils.add(super.getResourceKeys(), EVENT_RESOURCE_KEY);
	}
}