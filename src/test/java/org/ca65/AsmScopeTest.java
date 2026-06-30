package org.ca65;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmIdentifierdef;
import org.ca65.psi.AsmProcDef;
import org.ca65.psi.AsmScopeDef;
import org.ca65.psi.AsmStructMember;
import org.ca65.psi.impl.AsmIdentifierImpl;
import org.ca65.psi.impl.AsmPsiImplUtil;

import java.util.List;

/**
 * Scopes ({@code .scope}), anonymous struct/enum member spilling, nested {@code A::B::C} access,
 * {@code .proc} as a jump target, and lexical visibility of scope-local symbols.
 */
public class AsmScopeTest extends BasePlatformTestCase {

    private static final String SRC =
            ".scope SYSTEM\n" +
            "  .struct\n" +
            "  CTRL    .byte\n" +
            "  ADDR    .word\n" +
            "  .endstruct\n" +
            "  .enum\n" +
            "  INCR1   = 1\n" +
            "  INCR2   = 2\n" +
            "  .endenum\n" +
            "  INNER   = 5\n" +
            "  .scope DISP\n" +
            "    .enum ENABLE\n" +
            "      LAYER0 = 1\n" +
            "      LAYER1 = 2\n" +
            "    .endenum\n" +
            "  .endscope\n" +
            "  .struct LAYER\n" +
            "    CONFIG  .byte\n" +
            "    BASE    .byte\n" +
            "  .endstruct\n" +
            ".endscope\n" +
            "\n" +
            ".proc myproc\n" +
            "  rts\n" +
            ".endproc\n" +
            "\n" +
            "start:\n" +
            "  stz SYSTEM::CTRL\n" +
            "  lda #SYSTEM::INCR1\n" +
            "  lda SYSTEM::LAYER::CONFIG\n" +
            "  lda #SYSTEM::DISP::ENABLE::LAYER0\n" +
            "  lda #INNER\n" +
            "  jsr myproc\n";

    /** A member of an anonymous struct inside a scope resolves under {@code Scope::member}. */
    public void testAnonymousStructMemberResolves() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("CTRL");
        assertTrue("CTRL should resolve to a struct member", target instanceof AsmStructMember);
        assertEquals("CTRL", ((PsiNamedElement) target).getName());
    }

    /** A member of an anonymous enum inside a scope resolves under {@code Scope::member}. */
    public void testAnonymousEnumMemberResolves() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("INCR1");
        assertTrue("INCR1 should resolve", target instanceof AsmIdentifierdef);
        assertEquals("INCR1", AsmPsiImplUtil.getName((AsmIdentifierdef) target));
    }

    /** A named struct nested in a scope is reached with {@code Scope::Struct::member}. */
    public void testNestedNamedStructMemberResolves() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("CONFIG");
        assertTrue("CONFIG should resolve to a struct member", target instanceof AsmStructMember);
        assertEquals("CONFIG", ((PsiNamedElement) target).getName());
    }

    /** A deeply nested scope/enum chain resolves: {@code SYSTEM::DISP::ENABLE::LAYER0}. */
    public void testDeepNestedScopeMemberResolves() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("LAYER0");
        assertTrue("LAYER0 should resolve", target instanceof AsmIdentifierdef);
        assertEquals("LAYER0", AsmPsiImplUtil.getName((AsmIdentifierdef) target));
    }

    /** The scope name itself resolves to the {@code .scope} definition. */
    public void testScopeNameResolves() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("SYSTEM");
        assertTrue("SYSTEM should resolve to a scope", target instanceof AsmScopeDef);
    }

    /** {@code jsr myproc} resolves to the {@code .proc} definition. */
    public void testProcResolvesAsJumpTarget() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("myproc");
        assertTrue("myproc should resolve to a proc", target instanceof AsmProcDef);
        assertEquals("myproc", ((PsiNamedElement) target).getName());
    }

    /** A symbol defined inside a scope is NOT visible unqualified from outside it. */
    public void testScopeLocalSymbolNotVisibleOutside() {
        myFixture.configureByText("a.s", SRC);
        PsiElement target = resolveUse("INNER");
        assertNull("INNER is scope-local and must not resolve unqualified from file scope", target);
    }

    /** Completion after {@code SYSTEM::} offers that scope's members (incl. spilled + nested names). */
    public void testScopeMemberCompletion() {
        myFixture.configureByText("a.s", SRC.replace("  stz SYSTEM::CTRL\n", "  stz SYSTEM::<caret>\n"));
        myFixture.complete(CompletionType.BASIC);
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertContainsElements(items, "CTRL", "ADDR", "INCR1", "INCR2", "INNER", "DISP", "LAYER");
        assertDoesntContain(items, "LAYER0", "myproc", "start");
    }

    /** The colon form {@code .proc name: near} parses and the name still resolves as a jump target. */
    public void testProcWithAddressSizeSuffixResolves() {
        myFixture.configureByText("a.s",
                ".proc waitloop: near\n" +
                "  rts\n" +
                ".endproc\n" +
                "  jmp waitloop\n");
        PsiElement target = resolveUse("waitloop");
        assertTrue("waitloop should resolve to a proc", target instanceof AsmProcDef);
        assertEquals("waitloop", ((PsiNamedElement) target).getName());
    }

    /** Resolve a <em>use</em> (not a definition) of {@code name} via its reference. */
    private PsiElement resolveUse(String name) {
        AsmFile file = (AsmFile) myFixture.getFile();
        for (AsmIdentifierImpl id : PsiTreeUtil.findChildrenOfType(file, AsmIdentifierImpl.class)) {
            if (name.equals(id.getName()) && !(id.getParent() instanceof AsmIdentifierdef)) {
                PsiReference reference = id.getReference();
                return reference == null ? null : reference.resolve();
            }
        }
        fail("No use of identifier '" + name + "' found");
        return null;
    }
}
