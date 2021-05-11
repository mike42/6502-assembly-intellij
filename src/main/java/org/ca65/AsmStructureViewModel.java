package org.ca65;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.ca65.psi.AsmFile;
import org.jetbrains.annotations.NotNull;

public class AsmStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    public AsmStructureViewModel(@NotNull PsiFile psiFile) {
        super(psiFile, new AsmStructureViewElement(psiFile));
    }

    @NotNull
    public Sorter[] getSorters() {
        return new Sorter[]{Sorter.ALPHA_SORTER};
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element instanceof AsmFile;
    }
}
