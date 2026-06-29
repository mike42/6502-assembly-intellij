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
        if (this.element instanceof AsmFile) {
            List<TreeElement> treeElements = new ArrayList<>();
            for (AsmMarker marker : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmMarker.class)) {
                AsmMarkerImpl markerImpl = (AsmMarkerImpl) marker;
                if (markerImpl.getText().startsWith(":")) {
                    continue;
                }
                treeElements.add(new AsmStructureViewElement(markerImpl));
            }
            for (AsmDefineConstantNumeric constant : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmDefineConstantNumeric.class)) {
                treeElements.add(new AsmStructureViewElement((AsmDefineConstantNumericImpl) constant));
            }
            for (AsmDefineConstantLabel constant : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmDefineConstantLabel.class)) {
                treeElements.add(new AsmStructureViewElement((AsmDefineConstantLabelImpl) constant));
            }
            for (AsmEnumDef enumDef : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmEnumDef.class)) {
                if (((AsmEnumDefImpl) enumDef).getName() != null) {
                    treeElements.add(new AsmStructureViewElement((AsmEnumDefImpl) enumDef));
                }
            }
            for (AsmStructDef structDef : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmStructDef.class)) {
                if (((AsmStructDefImpl) structDef).getName() != null) {
                    treeElements.add(new AsmStructureViewElement((AsmStructDefImpl) structDef));
                }
            }
            for (AsmUnionDef unionDef : PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmUnionDef.class)) {
                if (((AsmUnionDefImpl) unionDef).getName() != null) {
                    treeElements.add(new AsmStructureViewElement((AsmUnionDefImpl) unionDef));
                }
            }
            return treeElements.toArray(new TreeElement[0]);
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
