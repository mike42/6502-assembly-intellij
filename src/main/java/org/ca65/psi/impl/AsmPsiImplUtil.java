package org.ca65.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import org.ca65.AsmIcons;
import org.ca65.psi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmPsiImplUtil {
    public static String getLabelName(AsmMarker element) {
        ASTNode keyNode = element.getNode().findChildByType(AsmTypes.LABEL);
        if (keyNode != null) {
            return keyNode.getText().replaceAll("\\\\ ", " ").replaceAll(":$", "").replaceAll("^@", "");
        } else {
            return null;
        }
    }

    public static String getLabelName(AsmIdentifierdef element) {
        ASTNode keyNode = element.getNode().findChildByType(AsmTypes.IDENTIFIERR);
        if (keyNode != null) {
            return keyNode.getText().replaceAll("\\\\ ", " ");
        } else {
            return null;
        }
    }

    public static String getName(AsmMarker element) {
        return getLabelName(element);
    }

    public static PsiElement setName(AsmMarker element, String newName) {
        ASTNode labelNode = element.getNode().findChildByType(AsmTypes.LABEL);
        if (labelNode == null) {
            return element;
        }
        if(element.getText().startsWith("@")) {
            // identifier name for local labels always shown w/o '@' symbol.
            newName = "@" + newName;
        }
        AsmMarker newMarker = AsmElementFactory.createMarker(element.getProject(), newName);
        ASTNode newLabelNode = newMarker.getFirstChild().getNode();
        element.getNode().replaceChild(labelNode, newLabelNode);
        return element;
    }

    public static PsiElement setName(AsmIdentifierdef element, String newName) {
        ASTNode identifierNode = element.getNode().findChildByType(AsmTypes.IDENTIFIERR);
        if (identifierNode == null) {
            return element;
        }
        AsmIdentifierdef newIdentifierDef = AsmElementFactory.createIdentifierDef(element.getProject(), newName);
        ASTNode newIdentifierNode = newIdentifierDef.getFirstChild().getNode();
        element.getNode().replaceChild(identifierNode, newIdentifierNode);
        return element;
    }

    public static PsiElement getNameIdentifier(AsmMarker element) {
        ASTNode labelNode = element.getNode().findChildByType(AsmTypes.LABEL);
        if (labelNode != null) {
            return labelNode.getPsi();
        } else {
            return null;
        }
    }

    public static PsiElement getNameIdentifier(AsmIdentifierdef element) {
        ASTNode labelNode = element.getNode().findChildByType(AsmTypes.IDENTIFIERR);
        if (labelNode != null) {
            return labelNode.getPsi();
        } else {
            return null;
        }
    }

    public static ItemPresentation getPresentation(AsmMarker element) {
        return new ItemPresentation() {
            @Nullable
            @Override
            public String getPresentableText() {
                String rawText = element.getText();
                if(rawText.startsWith(":")) {
                    return "(unnamed label)";
                }
                return rawText.replaceAll(":$", "").replaceAll("^@", "");

            }

            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile().getName();
            }

            @Nullable
            @Override
            public Icon getIcon(boolean unused) {
                return element.getText().startsWith("@") || element.getText().startsWith(":") ? AllIcons.Nodes.Annotationtype : AsmIcons.LABEL;
            }
        };
    }

    public static String getName(AsmIdentifierr asmIdentifierr) {
        ASTNode keyNode = asmIdentifierr.getNode().findChildByType(AsmTypes.IDENTIFIER);
        if (keyNode != null) {
            return keyNode.getText().replaceAll("\\\\ ", " ");
        } else {
            return null;
        }
    }

    public static String getName(AsmLocalLabelRrefImpl asmLocalLabelRref) {
        ASTNode keyNode = asmLocalLabelRref.getNode();
        if (keyNode != null) {
            return keyNode.getText().replaceAll("\\\\ ", " ").replaceAll("^@", "");
        } else {
            return null;
        }
    }

    public static String getName(AsmIdentifierdef asmIdentifierdef) {
        ASTNode keyNode = asmIdentifierdef.getNode().findChildByType(AsmTypes.IDENTIFIERR);
        if (keyNode != null) {
            return keyNode.getText().replaceAll("\\\\ ", " ");
        } else {
            return null;
        }
    }
}
