package org.ca65;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.psi.AsmEnumDef;
import org.ca65.psi.AsmEnumMember;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmIdentifierdef;
import org.ca65.psi.impl.AsmEnumMemberMixin;
import org.ca65.psi.impl.AsmIdentifierImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-assigned ordinal values for enum members, including a member whose explicit value references
 * a peer member ({@code LIGHTRED = PINK}), and the reference resolution into enum members.
 */
public class AsmEnumValueTest extends BasePlatformTestCase {
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    /** Each member's computed value, including the counter continuing past a peer reference. */
    public void testComputedOrdinalValues() {
        myFixture.configureByFile("enum/colors.s");
        Map<String, Long> values = computedValues();

        assertEquals(Long.valueOf(0), values.get("BLACK"));   // explicit $00
        assertEquals(Long.valueOf(1), values.get("WHITE"));   // auto
        assertEquals(Long.valueOf(10), values.get("PINK"));   // auto, $0A
        assertEquals(Long.valueOf(10), values.get("LIGHTRED")); // = PINK (peer reference)
        assertEquals(Long.valueOf(11), values.get("GRAY1"));  // counter continues past LIGHTRED
        assertEquals(Long.valueOf(15), values.get("GRAY3"));  // last member
    }

    /** Hex enums display lowercase, padded to a multiple of 8 bits ($0a, never $A). */
    public void testHexDisplayIsLowercasePadded() {
        myFixture.configureByFile("enum/colors.s");
        Map<String, String> display = displayValues();
        assertEquals("$01", display.get("WHITE"));
        assertEquals("$0a", display.get("PINK"));
        assertEquals("$0f", display.get("GRAY3"));
    }

    /** An enum written in decimal counts forward in decimal. */
    public void testDecimalEnumCountsInDecimal() {
        myFixture.configureByFile("enum/sizes.s");
        Map<String, String> display = displayValues();
        assertEquals("11", display.get("MEDIUM"));
        assertEquals("12", display.get("LARGE"));
    }

    /** A bare reference to a peer member (`LIGHTRED = PINK`) resolves to that member's definition. */
    public void testPeerMemberReferenceResolves() {
        myFixture.configureByFile("enum/colors.s");
        PsiElement target = resolveIdentifierAt("PINK", false);
        assertNotNull("PINK should resolve", target);
        assertEquals("PINK", ((AsmIdentifierdef) target).getName());
        assertTrue("PINK should resolve to an enum member definition",
                target.getParent() instanceof AsmEnumMember);
    }

    /** Scoped access `COLOR::PINK` resolves to the PINK enum member definition. */
    public void testScopedMemberReferenceResolves() {
        myFixture.configureByFile("enum/colors.s");
        PsiElement target = resolveIdentifierAt("PINK", true);
        assertNotNull("COLOR::PINK should resolve", target);
        assertTrue("scoped PINK should resolve to an enum member definition",
                target.getParent() instanceof AsmEnumMember);
    }

    private Map<String, Long> computedValues() {
        AsmFile file = (AsmFile) myFixture.getFile();
        AsmEnumDef enumDef = PsiTreeUtil.findChildOfType(file, AsmEnumDef.class);
        assertNotNull("enum should parse", enumDef);
        Map<String, Long> values = new HashMap<>();
        for (AsmEnumMember member : enumDef.getEnumMemberList()) {
            AsmEnumMemberMixin mixin = (AsmEnumMemberMixin) member;
            values.put(mixin.getName(), mixin.getComputedValue());
        }
        return values;
    }

    private Map<String, String> displayValues() {
        AsmFile file = (AsmFile) myFixture.getFile();
        AsmEnumDef enumDef = PsiTreeUtil.findChildOfType(file, AsmEnumDef.class);
        assertNotNull("enum should parse", enumDef);
        Map<String, String> values = new HashMap<>();
        for (AsmEnumMember member : enumDef.getEnumMemberList()) {
            AsmEnumMemberMixin mixin = (AsmEnumMemberMixin) member;
            values.put(mixin.getName(), mixin.getDisplayValue());
        }
        return values;
    }

    /**
     * Resolve a {@code PINK} identifier reference. When {@code scoped} is true, picks the one that
     * follows a {@code ::} (the {@code COLOR::PINK} usage); otherwise the bare {@code = PINK} usage.
     */
    private PsiElement resolveIdentifierAt(String name, boolean scoped) {
        AsmFile file = (AsmFile) myFixture.getFile();
        for (AsmIdentifierImpl identifier : PsiTreeUtil.findChildrenOfType(file, AsmIdentifierImpl.class)) {
            if (!name.equals(identifier.getName())) continue;
            if (identifier.getParent() instanceof AsmIdentifierdef) continue; // a definition, not a use
            boolean isScoped = identifier.getText().equals(name)
                    && precededByScopeAccess(identifier);
            if (isScoped != scoped) continue;
            PsiReference reference = identifier.getReference();
            return reference == null ? null : reference.resolve();
        }
        fail("No matching '" + name + "' reference found (scoped=" + scoped + ")");
        return null;
    }

    private static boolean precededByScopeAccess(PsiElement identifier) {
        // walk up to the `anything` wrapper, then look at the previous sibling's text
        PsiElement node = identifier.getParent(); // anything
        PsiElement sib = node.getPrevSibling();
        while (sib != null && sib.getText().isBlank()) sib = sib.getPrevSibling();
        return sib != null && sib.getText().contains("::");
    }
}
