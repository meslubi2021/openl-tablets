package org.openl.rules.project.instantiation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.LogFactoryImpl;
import org.openl.dependency.loader.IDependencyLoader;
import org.openl.rules.project.dependencies.ProjectExternalDependenciesHelper;
import org.openl.rules.project.model.Module;
import org.openl.rules.project.model.ProjectDescriptor;
import org.openl.syntax.code.IDependency;

public class SimpleProjectDependencyManager extends AbstractProjectDependencyManager {

    private final Log log = LogFactoryImpl.getLog(SimpleProjectDependencyManager.class);

    private Collection<ProjectDescriptor> projects;

    private boolean singleModuleMode = false;
    private boolean executionMode = true;

    public SimpleProjectDependencyManager(Collection<ProjectDescriptor> projects,
            boolean singleModuleMode,
            boolean executionMode) {
        super();
        if (projects == null) {
            throw new IllegalArgumentException("projects can't be null!");
        }
        this.projects = projects;
        this.singleModuleMode = singleModuleMode;
        this.executionMode = executionMode;
        this.singleModuleMode = singleModuleMode;
    }

    public SimpleProjectDependencyManager(Collection<ProjectDescriptor> projects, boolean singleModuleMode) {
        this(projects, singleModuleMode, true);
    }

    @Override
    public List<IDependencyLoader> getDependencyLoaders() {
        if (dependencyLoaders != null) {
            return dependencyLoaders;
        }
        dependencyLoaders = new ArrayList<IDependencyLoader>();
        for (ProjectDescriptor project : projects) {
            try {
                Collection<Module> modulesOfProject = project.getModules();
                if (!modulesOfProject.isEmpty()) {
                    for (final Module m : modulesOfProject) {
                        dependencyLoaders.add(new SimpleProjectDependencyLoader(m.getName(),
                            Arrays.asList(m),
                            singleModuleMode,
                            executionMode));
                    }
                }

                String dependencyName = ProjectExternalDependenciesHelper.buildDependencyNameForProjectName(project.getName());
                IDependencyLoader projectLoader = new SimpleProjectDependencyLoader(dependencyName,
                    project.getModules(),
                    singleModuleMode,
                    executionMode);
                dependencyLoaders.add(projectLoader);
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    String message = String.format("Build dependency manager loaders for project %s was failed!",
                        project.getName());
                    log.error(message, e);
                }
            }
        }

        return dependencyLoaders;
    }

    @Override
    public void reset(IDependency dependency) {
        if (dependencyLoaders == null) {
            return;
        }

        String dependencyName = dependency.getNode().getIdentifier();

        ProjectDescriptor projectToReset = null;

        searchProject: for (ProjectDescriptor project : projects) {
            if (dependencyName.equals(ProjectExternalDependenciesHelper.buildDependencyNameForProjectName(project.getName()))) {
                projectToReset = project;
                break;
            }

            for (Module module : project.getModules()) {
                if (dependencyName.equals(module.getName())) {
                    projectToReset = project;
                    break searchProject;
                }
            }
        }

        if (projectToReset != null) {
            clearClassLoader(projectToReset.getName());
            String projectDependency = ProjectExternalDependenciesHelper.buildDependencyNameForProjectName(projectToReset.getName());

            for (IDependencyLoader dependencyLoader : dependencyLoaders) {
                SimpleProjectDependencyLoader loader = (SimpleProjectDependencyLoader) dependencyLoader;
                String loaderDependencyName = loader.getDependencyName();

                if (loaderDependencyName.equals(projectDependency)) {
                    loader.reset();
                }

                for (Module module : projectToReset.getModules()) {
                    if (loaderDependencyName.equals(module.getName())) {
                        loader.reset();
                    }
                }
            }
        }
    }

    @Override
    public void resetAll() {
        if (dependencyLoaders == null) {
            return;
        }
        clearAllClassLoader();
    }
}