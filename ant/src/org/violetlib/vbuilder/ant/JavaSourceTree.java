package org.violetlib.vbuilder.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.collections.IList;
import org.violetlib.collections.ISet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
  Specify a package-structured file tree containing Java source files.
  An associated JDK release number may be specified, to indicate the minimum release that
  the compiled class version must support.

  @ant.type name="source"
*/

public class JavaSourceTree
  extends DataType
{
    private @Nullable File base;
    private @Nullable File optionalBase;  // to simplify shared build scripts
    private @Nullable String releaseText;
    private final @NotNull List<String> packages = new ArrayList<>();
    private int release = 0;

    public void validate()
    {
        if (isReference()) {
            getRef().validate();
        } else {
            if (releaseText != null) {
                try {
                    release = Integer.parseInt(releaseText);
                } catch (NumberFormatException ignore) {
                }
                if (release <= 0) {
                    throw new BuildException("Invalid release: " + releaseText);
                }
            }
            if (base == null && optionalBase == null) {
                throw new BuildException("Base directory required");
            }
            if (base != null && optionalBase != null) {
                throw new BuildException("Multiple configured base directory");
            }
        }
    }

    protected @NotNull JavaSourceTree getRef()
    {
        return getCheckedRef(JavaSourceTree.class);
    }

    @Override
    public void setRefid(Reference r)
      throws BuildException
    {
        if (base != null || releaseText != null || !packages.isEmpty()) {
            throw tooManyAttributes();
        }
        super.setRefid(r);
    }

    /**
      The root directory of the source tree. This directory must exist.
      @ant.optional
      Either {@code base} or {@code optionalBase} must be specified.
    */

    public void setBase(@NotNull File f)
    {
        checkAttributesAllowed();
        this.base = f;
    }

    /**
      The root directory of the source tree. This directory may not exist. If it does not exist, thie
      attribute is ignored.
      @ant.prop name="optionalBase"
      @ant.optional
      Either {@code base} or {@code optionalBase} must be specified.
    */

    public void setOptionalBase(@NotNull File f)
    {
        checkAttributesAllowed();
        this.optionalBase = f;
    }

    /**
      The minimum JDK release that this source supports.
      @ant.optional
      If not specified, a default release is used.
    */

    public void setRelease(@NotNull String r)
    {
        checkAttributesAllowed();
        this.releaseText = r;
    }

    /**
      The packages from the source tree that are to be included.
      @ant.optional
      If not specified, all packages from the source tree are included.
    */

    public void setPackages(@NotNull String s)
    {
        checkAttributesAllowed();
        String[] ps = s.split(";");
        packages.addAll(Arrays.asList(ps));
    }

    public @Nullable File getBase()
    {
        if (isReference()) {
            return getRef().getBase();
        }
        return base != null ? base : optionalBase;
    }

    public boolean isOptional()
    {
        if (isReference()) {
            return getRef().isOptional();
        }
        return optionalBase != null;
    }

    public int getRelease()
    {
        if (isReference()) {
            return getRef().getRelease();
        }
        return release;
    }

    public @NotNull ISet<String> getPackages()
    {
        if (isReference()) {
            return getRef().getPackages();
        }
        return ISet.create(packages);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaSourceTree that = (JavaSourceTree) o;
        return getRelease() == that.getRelease()
          && Objects.equals(getBase(), that.getBase())
          && isOptional() == that.isOptional()
          && Objects.equals(getPackages(), that.getPackages());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRelease(), getBase(), isOptional(), getPackages());
    }
}
