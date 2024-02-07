/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.vbuilder.NativeTarget;

/**
  Specify a target for building a native library.
  A target is described by a machine architecture, a vendor, and an operating system.
  For macOS, the operating system can include a minimum supported release.
  <p>
  Examples:
  {@snippet :
  <target value="x86_64-apple-macos10.10"/>
  <target value="arm64-apple-macos11"/>
  }

  @ant.type name="target"
*/

public class Target
  extends DataType
{
    private @Nullable String value;

    public Target()
    {
    }

    public Target(@NotNull Project p)
    {
    }

    @Override
    public void setRefid(@NotNull Reference r)
    {
        if (value != null) {
            throw tooManyAttributes();
        }
        super.setRefid(r);
    }

    public @Nullable NativeTarget asTarget()
    {
        if (isReference()) {
            return getRef().asTarget();
        }
        String s = getValue();
        return NativeTarget.parse(s);
    }

    /**
      The target string.
      @ant.prop name="value"
      @ant.required
    */

    public void setValue(@NotNull String s)
    {
        checkAttributesAllowed();
        value = s;
    }

    public @NotNull String getValue()
    {
        if (isReference()) {
            return getRef().getValue();
        }
        if (value == null) {
            throw new BuildException("A value is required");
        }
        return value;
    }

    private Target getRef()
    {
        Project p = getProject();
        return getCheckedRef(Target.class, getDataTypeName(), p);
    }
}
