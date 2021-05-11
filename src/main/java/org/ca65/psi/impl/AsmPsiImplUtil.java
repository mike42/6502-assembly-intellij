package org.ca65.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import org.ca65.AsmIcons;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmPsiImplUtil {
    public static String getLabelName(AsmMarker element) {
        ASTNode keyNode = element.getNode().findChildByType(AsmTypes.LABEL);
        if (keyNode != null) {
            // ??? do we need to deal with colon?
            return keyNode.getText().replaceAll("\\\\ ", " ");
        } else {
            return null;
        }
    }

    public static ItemPresentation getPresentation(final AsmMarker element) {
        return new ItemPresentation() {
            @Nullable
            @Override
            public String getPresentableText() {
                return element.getText().replaceAll(":$", "");
            }

            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile().getName();
            }

            @Nullable
            @Override
            public Icon getIcon(boolean unused) {
                return AsmIcons.ASSEMBLY_ICON;
            }
        };
    }
}
