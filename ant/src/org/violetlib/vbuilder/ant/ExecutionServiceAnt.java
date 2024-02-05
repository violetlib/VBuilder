package org.violetlib.vbuilder.ant;

import org.violetlib.vbuilder.ExecutionConfiguration;
import org.violetlib.vbuilder.ExecutionResult;
import org.violetlib.vbuilder.ExecutionService;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/**
  An execution service that uses ant Execute.
*/

public class ExecutionServiceAnt
  implements ExecutionService
{
    public static @NotNull ExecutionService create()
    {
        return new ExecutionServiceAnt();
    }

    public ExecutionServiceAnt()
    {
    }

    @Override
    public @NotNull ExecutionResult execute(@NotNull ExecutionConfiguration g)
      throws IOException
    {
        File program = g.program;
        if (!Files.isRegularFile(program.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new FileNotFoundException(program.getAbsolutePath());
        }

        String[] commandLine = g.arguments.prepending(program.getAbsolutePath()).toJavaArray(new String[0]);

        ByteArrayOutputStream oss = new ByteArrayOutputStream();
        ByteArrayOutputStream ess = new ByteArrayOutputStream();
        PumpStreamHandler eh = new PumpStreamHandler(oss, ess);

        Execute e = new Execute(eh);
        if (Os.isFamily("openvms")) {
            // Use the VM launcher instead of shell launcher on VMS for java
            e.setVMLauncher(true);
        }
        e.setCommandline(commandLine);
        int rc = e.execute();
        byte[] osb = oss.toByteArray();
        String os = oss.toString(StandardCharsets.UTF_8);
        String es = ess.toString(StandardCharsets.UTF_8);
        return ExecutionResult.createResult(rc, osb, os, es);
    }
}
