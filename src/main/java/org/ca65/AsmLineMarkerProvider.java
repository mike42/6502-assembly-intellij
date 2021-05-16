package org.ca65;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static org.ca65.psi.AsmTypes.LABEL;

public class AsmLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof LeafPsiElement)) {
            return;
        }
        LeafPsiElement leafElement = (LeafPsiElement) element;
        if (leafElement.getElementType() != LABEL) {
            return;
        }
        NavigationGutterIconBuilder<PsiElement> builder =
                NavigationGutterIconBuilder.create(leafElement.getText().startsWith("@") ? AllIcons.Nodes.Annotationtype : AsmIcons.LABEL)
                        .setTarget(leafElement)
                        .setTooltipText("Navigate to label");
        result.add(builder.createLineMarkerInfo(element));

    }
}
