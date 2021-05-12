package org.ca65;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.impl.AsmPsiImplUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AsmChooseByNameContributor implements ChooseByNameContributor {
    @NotNull
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
        List<AsmMarker> markers = AsmUtil.findLabels(project, name);
        return markers.toArray(new NavigationItem[markers.size()]);
    }

    @Override
    public String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
        List<AsmMarker> markers = AsmUtil.findLabels(project);
        List<String> names = new ArrayList<>(markers.size());
        for (AsmMarker marker : markers) {
            String labelName = AsmPsiImplUtil.getLabelName(marker);
            if (labelName != null) {
                names.add(labelName);
            }
        }
        return names.toArray(new String[names.size()]);
    }
}
