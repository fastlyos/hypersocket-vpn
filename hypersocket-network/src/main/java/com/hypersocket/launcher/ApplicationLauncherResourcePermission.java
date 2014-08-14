/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.launcher;

import com.hypersocket.permissions.PermissionType;


public enum ApplicationLauncherResourcePermission implements PermissionType {
	
	CREATE("create"),
	READ("read"),
	UPDATE("update"),
	DELETE("delete");
	
	private final String val;
	
	/**
	 * TODO place your resource name in this final static string e.g. applications
	 */
	private final static String name = "launcher";
	
	private ApplicationLauncherResourcePermission(final String val) {
		this.val = name + "." + val;
	}
	
	public String toString() {
		return val;
	}

	@Override
	public String getResourceKey() {
		return val;
	}
	
	@Override
	public boolean isSystem() {
		return false;
	}
}
