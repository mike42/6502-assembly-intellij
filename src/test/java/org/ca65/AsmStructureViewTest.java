package org.ca65;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/** The structure view lists scopes/procs and recurses into them, including spilled anonymous members. */
public class AsmStructureViewTest extends BasePlatformTestCase {

    private static final String SRC =
            ".struct Point\n" +
            "  XX .word\n" +
            ".endstruct\n" +
            ".scope SYSTEM\n" +
            "  .struct\n" +
            "  CTRL .byte\n" +
            "  .endstruct\n" +
            "  .enum\n" +
            "  INC1 = 1\n" +
            "  .endenum\n" +
            "  MAGIC = $42\n" +
            "  .struct Layer\n" +
            "    CONFIG .byte\n" +
            "  .endstruct\n" +
            "  .scope DISP\n" +
            "    COLUMNS = 80\n" +
            "  .endscope\n" +
            "  .proc reset\n" +
            "    rts\n" +
            "  .endproc\n" +
            ".endscope\n" +
            ".proc _main\n" +
            "  rts\n" +
            ".endproc\n";

    public void testStructureViewShowsScopesProcsAndSpilledMembers() {
        myFixture.configureByText("a.s", SRC);
        AsmStructureViewElement root = new AsmStructureViewElement(myFixture.getFile());

        // Top level: the struct, the scope, and the proc.
        List<String> top = childNames(root);
        assertContainsElements(top, "Point", "SYSTEM", "_main");

        // Inside SYSTEM: spilled anon members (CTRL, INC1), constant, named struct, nested scope, proc.
        AsmStructureViewElement system = child(root, "SYSTEM");
        assertNotNull("SYSTEM should be present", system);
        List<String> inSystem = childNames(system);
        assertContainsElements(inSystem, "CTRL", "MAGIC", "Layer", "DISP", "reset");
        // The spilled anon-enum member shows with its value, e.g. "INC1 = 1".
        assertTrue("spilled enum member INC1 should appear",
                inSystem.stream().anyMatch(n -> n != null && n.startsWith("INC1")));

        // Nested struct and scope expand further.
        assertContainsElements(childNames(child(system, "Layer")), "CONFIG");
        assertContainsElements(childNames(child(system, "DISP")), "COLUMNS");
    }

    private static AsmStructureViewElement child(AsmStructureViewElement parent, String name) {
        for (TreeElement t : parent.getChildren()) {
            AsmStructureViewElement e = (AsmStructureViewElement) t;
            if (name.equals(e.getPresentation().getPresentableText())) {
                return e;
            }
        }
        return null;
    }

    private static List<String> childNames(StructureViewTreeElement parent) {
        List<String> names = new ArrayList<>();
        for (TreeElement t : parent.getChildren()) {
            names.add(((AsmStructureViewElement) t).getPresentation().getPresentableText());
        }
        return names;
    }
}
