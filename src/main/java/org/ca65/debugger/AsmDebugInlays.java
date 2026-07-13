package org.ca65.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The gray "name = value" annotation shown after the execution line while
 * suspended. One instance per debug session; {@link #show} replaces the
 * previous annotation, {@link #clear} removes it (on resume/stop).
 */
class AsmDebugInlays {
    private final Project myProject;
    /** EDT-confined. */
    private final List<Inlay<?>> myInlays = new ArrayList<>();

    AsmDebugInlays(@NotNull Project project) {
        myProject = project;
    }

    /** May be called from any thread. */
    void show(@NotNull XSourcePosition position, @NotNull String text) {
        ApplicationManager.getApplication().invokeLater(() -> {
            clearOnEdt();
            if (myProject.isDisposed()) {
                return;
            }
            Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
            if (document == null || position.getLine() >= document.getLineCount()) {
                return;
            }
            int offset = document.getLineEndOffset(position.getLine());
            for (Editor editor : EditorFactory.getInstance().getEditors(document, myProject)) {
                Inlay<?> inlay = editor.getInlayModel()
                        .addAfterLineEndElement(offset, true, new TextRenderer(text));
                if (inlay != null) {
                    myInlays.add(inlay);
                }
            }
        });
    }

    /** May be called from any thread. */
    void clear() {
        ApplicationManager.getApplication().invokeLater(this::clearOnEdt);
    }

    private void clearOnEdt() {
        for (Inlay<?> inlay : myInlays) {
            inlay.dispose();
        }
        myInlays.clear();
    }

    private record TextRenderer(@NotNull String text) implements EditorCustomElementRenderer {
        private static final String MARGIN = "  ";

        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
            return metrics(inlay.getEditor()).stringWidth(MARGIN + text);
        }

        @Override
        public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle target,
                          @NotNull TextAttributes textAttributes) {
            Editor editor = inlay.getEditor();
            g.setColor(JBColor.GRAY);
            g.setFont(font(editor));
            g.drawString(MARGIN + text, target.x, target.y + editor.getAscent());
        }

        private static FontMetrics metrics(Editor editor) {
            return editor.getContentComponent().getFontMetrics(font(editor));
        }

        private static Font font(Editor editor) {
            return editor.getColorsScheme().getFont(EditorFontType.ITALIC);
        }
    }
}
