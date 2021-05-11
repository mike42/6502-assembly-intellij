package org.ca65;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.impl.AsmMarkerImpl;
import org.ca65.psi.impl.AsmPsiImplUtil;
import org.jetbrains.annotations.NotNull;

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
            // Not sure why this.element.getPresentation() is always null for this?
            return AsmPsiImplUtil.getPresentation((AsmMarker)this.element);
        }
        ItemPresentation presentation = this.element.getPresentation();
        return presentation != null ? presentation : new PresentationData();
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        if (this.element instanceof AsmFile) {
            List<AsmMarker> markers = PsiTreeUtil.getChildrenOfTypeAsList(this.element, AsmMarker.class);
            List<TreeElement> treeElements = new ArrayList<>(markers.size());
            for (AsmMarker marker : markers) {
                AsmMarkerImpl markerImpl = (AsmMarkerImpl) marker;
                if(markerImpl.getText().startsWith(":")) {
                    // exclude blank labels
                    continue;
                }
                treeElements.add(new AsmStructureViewElement(markerImpl));

            }
            return treeElements.toArray(new TreeElement[0]);
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
