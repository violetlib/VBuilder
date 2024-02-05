package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.violetlib.collections.IList;
import org.violetlib.collections.ListBuilder;
import org.violetlib.vbuilder.NativeTarget;

import java.util.ArrayList;
import java.util.List;

/**
  A collection of targets.
  @ant.type name="targets"
*/

public class TargetContainer
  extends DataType
{
    private final @NotNull List<Target> targets = new ArrayList<>();

    public TargetContainer(@NotNull Project project)
    {
        setProject(project);
    }

    @Override
    public void setRefid(@NotNull Reference r)
    {
        if (!targets.isEmpty()) {
            throw noChildrenAllowed();
        }
        super.setRefid(r);
    }

    public void add(@NotNull Target e)
    {
        checkChildrenAllowed();
        targets.add(e);
    }

    public void add(@NotNull TargetContainer c)
    {
        checkChildrenAllowed();
        if (c == this) {
            throw circularReference();
        }
        targets.addAll(c.targets);
    }

    public @NotNull IList<NativeTarget> getTargets()
    {
        if (isReference()) {
            return getRef().getTargets();
        }
        ListBuilder<NativeTarget> b = IList.builder();
        for (Target t : targets) {
            b.addOptional(t.asTarget());
        }
        return b.values();
    }

    private TargetContainer getRef()
    {
        Project p = getProject();
        return getCheckedRef(TargetContainer.class, getDataTypeName(), p);
    }
}
