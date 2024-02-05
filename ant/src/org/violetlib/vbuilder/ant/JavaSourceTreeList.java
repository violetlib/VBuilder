package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IList;

import java.util.ArrayList;
import java.util.List;

/**
  A collection of package-structured file trees containing Java source files.

  @ant.type name="sources"
*/

public class JavaSourceTreeList
  extends DataType
{
    private final @NotNull List<JavaSourceTree> trees = new ArrayList<>();

    public JavaSourceTreeList(@NotNull Project project)
    {
        setProject(project);
    }

    @Override
    public void setRefid(@NotNull Reference r)
    {
        if (!trees.isEmpty()) {
            throw noChildrenAllowed();
        }
        super.setRefid(r);
    }

    /**
      Specify a source tree.
    */

    public void add(@NotNull JavaSourceTree e)
    {
        checkChildrenAllowed();
        trees.add(e);
    }

    public @NotNull IList<JavaSourceTree> getTrees()
    {
        if (isReference()) {
            return getRef().getTrees();
        }
        return IList.create(trees);
    }

    private JavaSourceTreeList getRef()
    {
        Project p = getProject();
        return getCheckedRef(JavaSourceTreeList.class, getDataTypeName(), p);
    }
}
