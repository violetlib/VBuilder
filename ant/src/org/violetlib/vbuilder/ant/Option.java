/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.ProjectComponent;
import org.jetbrains.annotations.NotNull;

/**
  A possibly named option.
  @ant.type ignore="true"
*/

public class Option
  extends ProjectComponent
{
    private String value;

    public Option()
    {
        value = "";
    }

    public Option(@NotNull String s)
    {
        value = s;
    }

    /**
      Specify the option value as the content of the nested element.
    */

    public void addText(String s)
    {
        value = s;
    }

    /**
      The name of the option.
      @ant.prop name="name"
      @ant.required Required except in contexts where there is only one kind of option.
    */

    public void setName(String s)
    {
        value = s;
    }

    public String getName()
    {
        return value;
    }

    /**
      The value of the option.
      @ant.prop name="value"
      @ant.required Required unless the value is specified using text content.
    */

    public void setValue(String s)
    {
        value = s;
    }

    public String getValue()
    {
        return value;
    }

    public String toString()
    {
        return value;
    }
}
