package org.violetlib.vbuilder;

import org.jetbrains.annotations.NotNull;

/**

*/

public final class StringReporter
  implements Reporter
{
    public static @NotNull StringReporter create()
    {
        return new StringReporter();
    }

    private final @NotNull StringBuilder sb = new StringBuilder();

    private StringReporter()
    {
    }

    @Override
    public void info(@NotNull String msg)
    {
        sb.append(msg);
        sb.append("\n");
    }

    @Override
    public void verbose(@NotNull String msg)
    {
        info(msg);
    }

    @Override
    public void error(@NotNull String msg)
    {
        info(msg);
    }

    public @NotNull String getMessages()
    {
        return sb.toString();
    }
}
