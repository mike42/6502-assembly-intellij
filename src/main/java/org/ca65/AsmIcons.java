package org.ca65;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class AsmIcons {
    public static final Icon ASSEMBLY_FILE = IconLoader.getIcon("/icons/assembly.svg", AsmIcons.class);
    public static final Icon LABEL = IconLoader.getIcon("/icons/label.svg", AsmIcons.class);
    public static final Icon NUMERIC_CONST = IconLoader.getIcon("/icons/numericConst.svg", AsmIcons.class);
    public static final Icon STRUCT = IconLoader.getIcon("/icons/struct.svg", AsmIcons.class);
    public static final Icon STRUCT_MEMBER = IconLoader.getIcon("/icons/struct_member.svg", AsmIcons.class);
    public static final Icon ENUM = IconLoader.getIcon("/icons/enum.svg", AsmIcons.class);
    public static final Icon ENUM_MEMBER = IconLoader.getIcon("/icons/enum_member.svg", AsmIcons.class);

    public static final Icon JUMP_TO_LABEL = IconLoader.getIcon("/icons/jumpToLabel.svg", AsmIcons.class);
    public static final Icon JUMP_TO_LOCAL_LABEL = IconLoader.getIcon("/icons/jumpToLocalLabel.svg", AsmIcons.class);
    public static final Icon JUMP_TO_UNNAMED_LABEL = IconLoader.getIcon("/icons/jumpToUnnamedLabel.svg", AsmIcons.class);
}
