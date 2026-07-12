package org.ca65.run;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import org.ca65.Asm6502Bundle;
import org.ca65.AsmIcons;
import org.jetbrains.annotations.NotNull;

/** "6502 Program" entry in Run | Edit Configurations | Add. */
public class Ca65RunConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "Ca65RunConfiguration";

    public Ca65RunConfigurationType() {
        super(ID, Asm6502Bundle.message("RUN.config.type.name"),
                Asm6502Bundle.message("RUN.config.type.description"),
                NotNullLazyValue.createValue(() -> AsmIcons.ASSEMBLY_FILE));
        addFactory(new ConfigurationFactory(this) {
            @Override
            public @NotNull String getId() {
                return ID;
            }

            @Override
            public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
                return new Ca65RunConfiguration(project, this);
            }

            @Override
            public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID,
                                                       BeforeRunTask task) {
                if (providerID == Ca65BuildBeforeRunTaskProvider.ID) {
                    task.setEnabled(true);
                }
            }
        });
    }

    public static Ca65RunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(Ca65RunConfigurationType.class);
    }
}
