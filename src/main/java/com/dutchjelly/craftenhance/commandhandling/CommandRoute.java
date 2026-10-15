package com.dutchjelly.craftenhance.commandhandling;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

//TODO: make this use PermissionTypes enum instead of string for perms.

@Retention(value = RetentionPolicy.RUNTIME)
public @interface CommandRoute {
	public String[] cmdPath();
	public String perms();

	/** Arguments after the canonical path; empty for commands with no arguments. */
	public String arguments() default "";
}
