package org.ca65;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.*;
import org.ca65.psi.impl.*;

import org.jetbrains.annotations.NotNull;

import com.intellij.psi.PsiNamedElement;
import java.util.ArrayList;
import java.util.List;

public class AsmStructureViewElement implements StructureViewTreeElement, SortableTreeElement {
    private final NavigatablePsiElement element;

    public AsmStructureViewElement(@NotNull NavigatablePsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return this.element;
    }

    @Override
    public @NotNull String getAlphaSortKey() {
        String name = this.element.getName(); // Different to getLabelName() on labels?
        return name != null ? name : "";
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (this.element instanceof AsmMarker) {
            return AsmPsiImplUtil.getPresentation((AsmMarker)this.element);
        } else if (this.element instanceof AsmDefineConstantNumeric) {
            return AsmPsiImplUtil.getPresentation((AsmDefineConstantNumeric)this.element);
        } else if (this.element instanceof AsmDefineConstantLabel) {
            return AsmPsiImplUtil.getPresentation((AsmDefineConstantLabel)this.element);
        }
        ItemPresentation presentation = this.element.getPresentation();
        return presentation != null ? presentation : new PresentationData();
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        // File, scope and proc are all containers: their definitions are listed (and scopes/procs
        // recurse). Anonymous struct/union/enum members spill in, mirroring `Scope::member` access.
        if (this.element instanceof AsmFile
                || this.element instanceof AsmScopeDef
                || this.element instanceof AsmProcDef) {
            return containerChildren(this.element);
        }
        // Children for enum, struct, union containers
        if (this.element instanceof AsmEnumDef enumDef) {
            return enumDef.getEnumMemberList().stream()
                    .map(m -> (TreeElement) new AsmStructureViewElement((AsmEnumMemberImpl) m))
                    .toArray(TreeElement[]::new);
        }
        if (this.element instanceof AsmStructDef structDef) {
            return structDef.getStructMemberList().stream()
                    .filter(m -> ((PsiNamedElement) m).getName() != null)
                    .map(m -> (TreeElement) new AsmStructureViewElement((AsmStructMemberImpl) m))
                    .toArray(TreeElement[]::new);
        }
        if (this.element instanceof AsmUnionDef unionDef) {
            return unionDef.getStructMemberList().stream()
                    .filter(m -> ((PsiNamedElement) m).getName() != null)
                    .map(m -> (TreeElement) new AsmStructureViewElement((AsmStructMemberImpl) m))
                    .toArray(TreeElement[]::new);
        }
        return EMPTY_ARRAY;
    }

    private static TreeElement @NotNull [] containerChildren(NavigatablePsiElement container) {
        List<TreeElement> treeElements = new ArrayList<>();
        NavigatablePsiElement procName = container instanceof AsmProcDef
                ? (NavigatablePsiElement) ((AsmProcDefMixin) container).getNameElement() : null;

        for (AsmMarker marker : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmMarker.class)) {
            if (marker.getText().startsWith(":") || marker == procName) {
                continue; // skip unnamed (':') labels and a proc's own name
            }
            treeElements.add(new AsmStructureViewElement((AsmMarkerImpl) marker));
        }
        for (AsmDefineConstantNumeric constant : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmDefineConstantNumeric.class)) {
            treeElements.add(new AsmStructureViewElement((AsmDefineConstantNumericImpl) constant));
        }
        for (AsmDefineConstantLabel constant : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmDefineConstantLabel.class)) {
            treeElements.add(new AsmStructureViewElement((AsmDefineConstantLabelImpl) constant));
        }
        for (AsmEnumDef enumDef : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmEnumDef.class)) {
            if (enumDef.getIdentifierdef() != null) {
                treeElements.add(new AsmStructureViewElement((AsmEnumDefImpl) enumDef));
            } else { // anonymous: members spill into this container
                for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                    treeElements.add(new AsmStructureViewElement((AsmEnumMemberImpl) member));
                }
            }
        }
        for (AsmStructDef structDef : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmStructDef.class)) {
            if (structDef.getIdentifierdef() != null) {
                treeElements.add(new AsmStructureViewElement((AsmStructDefImpl) structDef));
            } else {
                addNamedMembers(structDef.getStructMemberList(), treeElements);
            }
        }
        for (AsmUnionDef unionDef : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmUnionDef.class)) {
            if (unionDef.getIdentifierdef() != null) {
                treeElements.add(new AsmStructureViewElement((AsmUnionDefImpl) unionDef));
            } else {
                addNamedMembers(unionDef.getStructMemberList(), treeElements);
            }
        }
        for (AsmScopeDef scopeDef : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmScopeDef.class)) {
            if (((PsiNamedElement) scopeDef).getName() != null) {
                treeElements.add(new AsmStructureViewElement((AsmScopeDefImpl) scopeDef));
            }
        }
        for (AsmProcDef procDef : PsiTreeUtil.getChildrenOfTypeAsList(container, AsmProcDef.class)) {
            if (((PsiNamedElement) procDef).getName() != null) {
                treeElements.add(new AsmStructureViewElement((AsmProcDefImpl) procDef));
            }
        }
        return treeElements.toArray(new TreeElement[0]);
    }

    private static void addNamedMembers(List<AsmStructMember> members, List<TreeElement> out) {
        for (AsmStructMember member : members) {
            if (((PsiNamedElement) member).getName() != null) {
                out.add(new AsmStructureViewElement((AsmStructMemberImpl) member));
            }
        }
    }

    @Override
    public void navigate(boolean requestFocus) {
        this.element.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return this.element.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return this.element.canNavigateToSource();
    }
}
