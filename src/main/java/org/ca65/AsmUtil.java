package org.ca65;

import com.intellij.openapi.project.Project;
import org.ca65.psi.AsmMarker;

import java.util.Arrays;
import java.util.List;

public class AsmUtil {
    public static List<AsmMarker> findLabels(Project project, String label) {
        // No hits
        return Arrays.asList();
    }
}
