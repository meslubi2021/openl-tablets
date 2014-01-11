package org.openl.rules.webstudio.web.repository;

import static org.openl.rules.security.AccessManager.isGranted;
import static org.openl.rules.security.DefaultPrivileges.PRIVILEGE_DELETE_DEPLOYMENT;
import static org.openl.rules.security.DefaultPrivileges.PRIVILEGE_DELETE_PROJECTS;
import static org.openl.rules.security.DefaultPrivileges.PRIVILEGE_UNLOCK_DEPLOYMENT;
import static org.openl.rules.security.DefaultPrivileges.PRIVILEGE_UNLOCK_PROJECTS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openl.commons.web.jsf.FacesUtils;
import org.openl.rules.common.ArtefactPath;
import org.openl.rules.common.ProjectException;
import org.openl.rules.common.ProjectVersion;
import org.openl.rules.common.PropertyException;
import org.openl.rules.common.RulesRepositoryArtefact;
import org.openl.rules.common.impl.CommonVersionImpl;
import org.openl.rules.project.abstraction.ADeploymentProject;
import org.openl.rules.project.abstraction.AProject;
import org.openl.rules.project.abstraction.AProjectArtefact;
import org.openl.rules.project.abstraction.AProjectFolder;
import org.openl.rules.project.abstraction.AProjectResource;
import org.openl.rules.project.abstraction.RulesProject;
import org.openl.rules.project.abstraction.UserWorkspaceProject;
import org.openl.rules.project.instantiation.ReloadType;
import org.openl.rules.repository.api.ArtefactProperties;
import org.openl.rules.ui.WebStudio;
import org.openl.rules.webstudio.filter.RepositoryFileExtensionFilter;
import org.openl.rules.webstudio.util.ExportModule;
import org.openl.rules.webstudio.util.NameChecker;
import org.openl.rules.webstudio.web.repository.project.ExcelFilesProjectCreator;
import org.openl.rules.webstudio.web.repository.project.ProjectFile;
import org.openl.rules.webstudio.web.repository.tree.TreeNode;
import org.openl.rules.webstudio.web.repository.tree.TreeRepository;
import org.openl.rules.webstudio.web.repository.upload.ProjectUploader;
import org.openl.rules.webstudio.web.util.WebStudioUtils;
import org.openl.rules.workspace.filter.PathFilter;
import org.openl.rules.workspace.uw.UserWorkspace;
import org.openl.rules.workspace.uw.impl.ProjectExportHelper;
import org.openl.util.filter.IFilter;
import org.richfaces.event.FileUploadEvent;
import org.richfaces.model.UploadedFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ResourceUtils;

/**
 * Repository tree controller. Used for retrieving data for repository tree and
 * performing repository actions.
 * 
 * @author Aleh Bykhavets
 * @author Andrey Naumenko
 */
@ManagedBean
@ViewScoped
public class RepositoryTreeController {

    private static final Date SPECIAL_DATE = new Date(0);
    private static final String TEMPLATES_PATH = "org.openl.rules.demo.";
    private static final String PROJECT_HISTORY_HOME = "project.history.home";

    private final Log log = LogFactory.getLog(RepositoryTreeController.class);

    @ManagedProperty(value = "#{repositoryTreeState}")
    private RepositoryTreeState repositoryTreeState;

    @ManagedProperty(value = "#{rulesUserSession.userWorkspace}")
    private UserWorkspace userWorkspace;

    @ManagedProperty(value = "#{repositoryArtefactPropsHolder}")
    private RepositoryArtefactPropsHolder repositoryArtefactPropsHolder;

    @ManagedProperty(value = "#{zipFilter}")
    private PathFilter zipFilter;

    private WebStudio studio = WebStudioUtils.getWebStudio(true);

    private String projectName;
    private String newProjectTemplate;
    private String folderName;
    private List<UploadedFile> uploadedFiles = new ArrayList<UploadedFile>();
    private String fileName;
    private String uploadFrom;
    private String newProjectName;
    private String version;

    private String filterString;
    private boolean hideDeleted;

    public PathFilter getZipFilter() {
        return zipFilter;
    }

    public void setZipFilter(PathFilter zipFilter) {
        this.zipFilter = zipFilter;
    }

    /**
     * Adds new file to active node (project or folder).
     * 
     * @return
     */
    public String addFile() {
        if (getLastUploadedFile() == null) {
            FacesUtils.addErrorMessage("Please select file to be uploaded.");
            return null;
        }
        if (StringUtils.isEmpty(fileName)) {
            FacesUtils.addErrorMessage("File name must not be empty.");
            return null;
        }

        String errorMessage = uploadAndAddFile();

        if (errorMessage == null) {
            resetStudioModel();
            FacesUtils.addInfoMessage("File was uploaded successfully.");
        } else {
            FacesUtils.addErrorMessage(errorMessage);
        }

        /* Clear the load form */
        this.clearForm();

        return null;
    }

    public String addFolder() {
        AProjectArtefact projectArtefact = repositoryTreeState.getSelectedNode().getData();
        String errorMessage = null;

        if (projectArtefact instanceof AProjectFolder) {
            if (folderName != null && !folderName.isEmpty()) {
                if (NameChecker.checkName(folderName)) {
                    if (!NameChecker.checkIsFolderPresent((AProjectFolder) projectArtefact, folderName)) {
                        AProjectFolder folder = (AProjectFolder) projectArtefact;

                        try {
                            AProjectFolder addedFolder = folder.addFolder(folderName);
                            repositoryTreeState.addNodeToTree(repositoryTreeState.getSelectedNode(), addedFolder);
                            resetStudioModel();
                        } catch (ProjectException e) {
                            log.error("Failed to create folder '" + folderName + "'.", e);
                            errorMessage = e.getMessage();
                        }
                    } else {
                        errorMessage = "Folder name '" + folderName + "' is invalid. " + NameChecker.FOLDER_EXISTS;
                    }
                } else {

                    errorMessage = "Folder name '" + folderName + "' is invalid. " + NameChecker.BAD_NAME_MSG;
                }
            } else {
                errorMessage = "Folder name '" + folderName + "' is invalid. " + NameChecker.FOLDER_NAME_EMPTY;
            }
        }

        if (errorMessage != null) {
            FacesUtils.addErrorMessage("Failed to create folder.", errorMessage);
        }
        return null;
    }

    public String saveProject() {
        try {
            UserWorkspaceProject project = repositoryTreeState.getSelectedProject();

            project.save();

            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = e.getMessage();
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg);
        }
        return null;
    }

    public String editProject() {
        try {
            repositoryTreeState.getSelectedProject().edit();
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = "Failed to edit project.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
        return null;
    }

    public String closeProject() {
        try {
            repositoryTreeState.getSelectedProject().close();
            repositoryTreeState.refreshSelectedNode();
            if (repositoryTreeState.getSelectedProject().equals(studio.getModel().getProject())) {
                studio.getModel().clearModuleInfo();
                studio.setCurrentModule(null);
            }
            resetStudioModel();
        } catch (Exception e) {
            String msg = "Failed to close project.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
        return null;
    }

    public String copyDeploymentProject() {
        String errorMessage = null;
        ADeploymentProject project;

        try {
            project = userWorkspace.getDDProject(projectName);
        } catch (ProjectException e) {
            log.error("Cannot obtain deployment project '" + projectName + "'.", e);
            FacesUtils.addErrorMessage(e.getMessage());
            return null;
        }

        if (project == null) {
            errorMessage = "No project is selected.";
        } else if (StringUtils.isBlank(newProjectName)) {
            errorMessage = "Project name is empty.";
        } else if (!NameChecker.checkName(newProjectName)) {
            errorMessage = "Project name '" + newProjectName + "' is invalid. " + NameChecker.BAD_NAME_MSG;
        } else if (userWorkspace.hasDDProject(newProjectName)) {
            errorMessage = "Deployment project '" + newProjectName + "' already exists.";
        }

        if (errorMessage != null) {
            FacesUtils.addErrorMessage("Cannot copy deployment project.", errorMessage);
            return null;
        }

        try {
            userWorkspace.copyDDProject(project, newProjectName);
            ADeploymentProject newProject = userWorkspace.getDDProject(newProjectName);
            repositoryTreeState.addDeploymentProjectToTree(newProject);
        } catch (ProjectException e) {
            String msg = "Failed to copy deployment project.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }

        return null;
    }

    public String copyProject() {
        String errorMessage = null;
        AProject project;

        try {
            project = userWorkspace.getProject(projectName);
        } catch (ProjectException e) {
            log.error("Cannot obtain rules project '" + projectName + "'.", e);
            FacesUtils.addErrorMessage(e.getMessage());
            return null;
        }

        if (project == null) {
            errorMessage = "No project is selected.";
        } else if (StringUtils.isBlank(newProjectName)) {
            errorMessage = "Project name is empty.";
        } else if (!NameChecker.checkName(newProjectName)) {
            errorMessage = "Project name '" + newProjectName + "' is invalid. " + NameChecker.BAD_NAME_MSG;
        } else if (userWorkspace.hasProject(newProjectName)) {
            errorMessage = "Project '" + newProjectName + "' already exists.";
        }

        if (errorMessage != null) {
            FacesUtils.addErrorMessage("Cannot copy project.", errorMessage);
            return null;
        }

        try {
            userWorkspace.copyProject(project, newProjectName);
            AProject newProject = userWorkspace.getProject(newProjectName);
            repositoryTreeState.addRulesProjectToTree(newProject);
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = "Failed to copy project.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }

        return null;
    }

    public String createDeploymentConfiguration() {
        try {
            if (userWorkspace.hasDDProject(projectName)) {
                String msg = "Cannot create configuration because configuration with such name already exists.";
                FacesUtils.addErrorMessage(msg, null);

                return null;
            }

            userWorkspace.createDDProject(projectName);
            ADeploymentProject createdProject = userWorkspace.getDDProject(projectName);
            createdProject.edit();
            repositoryTreeState.addDeploymentProjectToTree(createdProject);
        } catch (ProjectException e) {
            String msg = "Failed to create deployment project '" + projectName + "'.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }

        /* Clear the load form */
        this.clearForm();

        return null;
    }

    public String createNewRulesProject() {
        String msg = null;
        if (StringUtils.isBlank(projectName)) {
            msg = "Project name must not be empty.";
        } else if (!NameChecker.checkName(projectName)) {
            msg = "Specified name is not a valid project name." + " " + NameChecker.BAD_NAME_MSG;
        } else if (userWorkspace.hasProject(projectName)) {
            msg = "Cannot create project because project with such name already exists.";
        } 
        
        if (msg != null) {
            this.clearForm();
            FacesUtils.addErrorMessage(msg);
            return null;
        }

        ProjectFile[] templateFiles = getProjectTemplateFiles(TEMPLATES_PATH + newProjectTemplate);
        if (templateFiles.length <= 0) {
            this.clearForm();
            String errorMessage = String.format("Can`t load template files: %s", newProjectTemplate);
            FacesUtils.addErrorMessage(errorMessage);
            return null;
        }

        ExcelFilesProjectCreator projectCreator = new ExcelFilesProjectCreator(projectName,
            userWorkspace,
            templateFiles);
        String creationMessage = projectCreator.createRulesProject();
        if (creationMessage == null) {
            try {
                AProject createdProject = userWorkspace.getProject(projectName);

                repositoryTreeState.addRulesProjectToTree(createdProject);
                selectProject(projectName, repositoryTreeState.getRulesRepository());

                repositoryTreeState.getSelectedProject().close();
                repositoryTreeState.refreshSelectedNode();

                resetStudioModel();

                FacesUtils.addInfoMessage("Project was created successfully.");
                /* Clear the load form */
                this.clearForm();
                this.editProject();
            } catch (ProjectException e) {
                creationMessage = e.getMessage();
            }
        } else {
            FacesUtils.addErrorMessage(creationMessage);
        }

        return creationMessage;
    }

    /*
     * Because of renaming 'Deployment project' to 'Deployment Configuration'
     * the method was renamed too.
     */
    public String deleteDeploymentConfiguration() {
        String projectName = FacesUtils.getRequestParameter("deploymentProjectName");

        try {
            ADeploymentProject project = userWorkspace.getDDProject(projectName);
            project.delete(userWorkspace.getUser());
            if (repositoryTreeState.isHideDeleted()) {
                TreeNode projectInTree = repositoryTreeState.getDeploymentRepository()
                    .getChild(RepositoryUtils.getTreeNodeId(project.getName()));
                repositoryTreeState.deleteNode(projectInTree);
            }

            FacesUtils.addInfoMessage("Configuration was deleted successfully.");
        } catch (ProjectException e) {
            log.error("Cannot delete deployment configuration '" + projectName + "'.", e);
            FacesUtils.addErrorMessage("Failed to delete deployment configuration.", e.getMessage());
        }
        return null;
    }

    public String deleteElement() {
        AProjectFolder projectArtefact = (AProjectFolder) repositoryTreeState.getSelectedNode().getData();
        String childName = FacesUtils.getRequestParameter("element");

        try {
            projectArtefact.deleteArtefact(childName);
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();

            FacesUtils.addInfoMessage("Element was deleted successfully.");
        } catch (ProjectException e) {
            log.error("Error deleting element.", e);
            FacesUtils.addErrorMessage("Error deleting.", e.getMessage());
        }
        return null;
    }

    public String deleteNode() {
        AProjectArtefact projectArtefact = repositoryTreeState.getSelectedNode().getData();
        try {
            projectArtefact.delete();
            String nodeType = repositoryTreeState.getSelectedNode().getType();
            boolean wasMarkedForDeletion = UiConst.TYPE_DEPLOYMENT_PROJECT.equals(nodeType) || (UiConst.TYPE_PROJECT.equals(nodeType) && !((UserWorkspaceProject) projectArtefact).isLocalOnly());
            if (wasMarkedForDeletion && !repositoryTreeState.isHideDeleted()) {
                repositoryTreeState.refreshSelectedNode();
            } else {
                repositoryTreeState.deleteSelectedNodeFromTree();
            }
            resetStudioModel();

            FacesUtils.addInfoMessage("File was deleted successfully.");
        } catch (ProjectException e) {
            log.error("Failed to delete node.", e);
            FacesUtils.addErrorMessage("Failed to delete node.", e.getMessage());
        }

        return null;
    }

    public String unlockNode() {
        AProjectArtefact projectArtefact = repositoryTreeState.getSelectedNode().getData();
        try {
            projectArtefact.unlock(userWorkspace.getUser());
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();

            FacesUtils.addInfoMessage("File was unlocked successfully.");
        } catch (ProjectException e) {
            log.error("Failed to unlock node.", e);
            FacesUtils.addErrorMessage("Failed to unlock node.", e.getMessage());
        }

        return null;
    }

    public String deleteRulesProject() {
        String projectName = FacesUtils.getRequestParameter("projectName");

        try {
            RulesProject project = userWorkspace.getProject(projectName);
            if (project.isLocalOnly()) {
                project.erase(userWorkspace.getUser());
                TreeNode projectInTree = repositoryTreeState.getRulesRepository()
                    .getChild(RepositoryUtils.getTreeNodeId(project.getName()));
                repositoryTreeState.deleteNode(projectInTree);
            } else {
                project.delete(userWorkspace.getUser());
                if (repositoryTreeState.isHideDeleted()) {
                    TreeNode projectInTree = repositoryTreeState.getRulesRepository()
                        .getChild(RepositoryUtils.getTreeNodeId(project.getName()));
                    repositoryTreeState.deleteNode(projectInTree);
                }
            }
            if (project.equals(studio.getModel().getProject())) {
                studio.getModel().clearModuleInfo();
                studio.setCurrentModule(null);
            }
            resetStudioModel();
        } catch (Exception e) {
            log.error("Cannot delete rules project '" + projectName + "'.", e);
            FacesUtils.addErrorMessage("Failed to delete rules project.", e.getMessage());
        }
        return null;
    }

    public String unlockProject() {
        String projectName = FacesUtils.getRequestParameter("projectName");

        try {
            RulesProject project = userWorkspace.getProject(projectName);
            project.unlock(userWorkspace.getUser());
            resetStudioModel();
        } catch (ProjectException e) {
            log.error("Cannot unlock rules project '" + projectName + "'.", e);
            FacesUtils.addErrorMessage("Failed to unlock rules project.", e.getMessage());
        }
        return null;
    }

    public String unlockDeploymentConfiguration() {
        String deploymentProjectName = FacesUtils.getRequestParameter("deploymentProjectName");

        try {
            ADeploymentProject deploymentProject = userWorkspace.getDDProject(deploymentProjectName);
            deploymentProject.unlock(userWorkspace.getUser());
            resetStudioModel();
        } catch (ProjectException e) {
            log.error("Cannot unlock deployment project '" + deploymentProjectName + "'.", e);
            FacesUtils.addErrorMessage("Failed to unlock deployment project.", e.getMessage());
        }
        return null;
    }

    public String eraseProject() {
        UserWorkspaceProject project = repositoryTreeState.getSelectedProject();
        // EPBDS-225
        if (project == null) {
            return null;
        }

        if (!project.isDeleted()) {
            repositoryTreeState.invalidateTree();
            repositoryTreeState.invalidateSelection();
            FacesUtils.addErrorMessage("Cannot erase project '" + project.getName() + "'. It must be marked for deletion first!");
            return null;
        }

        try {
            project.erase();
            deleteProjectHistory(project.getName());
            userWorkspace.refresh();

            repositoryTreeState.deleteSelectedNodeFromTree();
            repositoryTreeState.invalidateTree();
            repositoryTreeState.invalidateSelection();

            resetStudioModel();
        } catch (ProjectException e) {
            repositoryTreeState.invalidateTree();
            String msg = "Cannot erase project '" + project.getName() + "'.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg);
        }
        return null;
    }
    
    public void deleteProjectHistory(String projectName) {
        try {
            String projectHistoryPath = studio.getSystemConfigManager().getPath(PROJECT_HISTORY_HOME) + File.separator + projectName;
            FileUtils.deleteDirectory(new File(projectHistoryPath));
        } catch (Exception e) {
            String msg = "Failed to clean history of project '" + projectName + "'!";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
    }

    public String exportProjectVersion() {
        File zipFile = null;
        String zipFileName = null;
        try {
            AProject selectedProject = repositoryTreeState.getSelectedProject();
            AProject forExport = userWorkspace.getDesignTimeRepository().getProject(selectedProject.getName(),
                new CommonVersionImpl(version));
            zipFile = new ProjectExportHelper().export(userWorkspace.getUser(), forExport);
            zipFileName = String.format("%s-%s.zip", selectedProject.getName(), version);
        } catch (ProjectException e) {
            String msg = "Failed to export project version.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }

        if (zipFile != null) {
            final FacesContext facesContext = FacesUtils.getFacesContext();
            HttpServletResponse response = (HttpServletResponse) FacesUtils.getResponse();
            ExportModule.writeOutContent(response, zipFile, zipFileName, "zip");
            facesContext.responseComplete();

            zipFile.delete();
        }
        return null;
    }

    public String exportFileVersion() {
        File file = null;
        String fileName = null;
        InputStream is = null;
        OutputStream os = null;
        try {
            AProject selectedProject = repositoryTreeState.getSelectedProject();
            AProject forExport = userWorkspace.getDesignTimeRepository().getProject(selectedProject.getName(),
                new CommonVersionImpl(version));
            TreeNode selectedNode = repositoryTreeState.getSelectedNode();
            fileName = selectedNode.getName();
            ArtefactPath selectedNodePath = selectedNode.getData().getArtefactPath().withoutFirstSegment();

            is = ((AProjectResource) forExport.getArtefactByPath(selectedNodePath)).getContent();
            file = File.createTempFile("export-", "-file");
            os = new FileOutputStream(file);
            IOUtils.copy(is, os);

            final FacesContext facesContext = FacesUtils.getFacesContext();
            HttpServletResponse response = (HttpServletResponse) FacesUtils.getResponse();
            String fileType = fileName.endsWith("xls") ? "xls" : "xlsx";
            ExportModule.writeOutContent(response, file, fileName, fileType);
            facesContext.responseComplete();
        } catch (Exception e) {
            String msg = "Failed to export file version.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
            FileUtils.deleteQuietly(file);
        }

        return null;
    }

    public boolean isHideDeleted() {
        hideDeleted = repositoryTreeState.isHideDeleted();
        return hideDeleted;
    }

    public void setHideDeleted(boolean hideDeleted) {
        this.hideDeleted = hideDeleted;
    }

    public String filter() {
        IFilter<AProjectArtefact> filter = null;
        if (StringUtils.isNotBlank(filterString)) {
            filter = new RepositoryFileExtensionFilter(filterString);
        }
        repositoryTreeState.setFilter(filter);
        repositoryTreeState.setHideDeleted(hideDeleted);
        return null;
    }

    public String getAttribute1() {
        return (String) getProperty(ArtefactProperties.PROP_ATTRIBUTE + 1);
    }

    public Date getAttribute10() {
        return getDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 10);
    }

    public String getAttribute11() {
        return getNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 11);
    }

    public String getAttribute12() {
        return getNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 12);
    }

    public String getAttribute13() {
        return getNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 13);
    }

    public String getAttribute14() {
        return getNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 14);
    }

    public String getAttribute15() {
        return getNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 15);
    }

    public String getAttribute2() {
        return (String) getProperty(ArtefactProperties.PROP_ATTRIBUTE + 2);
    }

    public String getAttribute3() {
        return (String) getProperty(ArtefactProperties.PROP_ATTRIBUTE + 3);
    }

    public String getAttribute4() {
        return (String) getProperty(ArtefactProperties.PROP_ATTRIBUTE + 4);
    }

    public String getAttribute5() {
        return (String) getProperty(ArtefactProperties.PROP_ATTRIBUTE + 5);
    }

    public Date getAttribute6() {
        return getDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 6);
    }

    public Date getAttribute7() {
        return getDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 7);
    }

    public Date getAttribute8() {
        return getDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 8);
    }

    public Date getAttribute9() {
        return getDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 9);
    }

    /**
     * Gets date type property from a rules repository.
     * 
     * @param propName name of property
     * @return value of property
     */
    private Date getDateProperty(String propName) {
        Object prop = getProperty(propName);
        if (prop instanceof Date) {
            return (Date) prop;
        } else if (prop instanceof Long) {
            return new Date((Long) prop);
        } else {
            return null;
        }
    }

    public String getDeploymentProjectName() {
        // EPBDS-92 - clear newDProject dialog every time
        return null;
    }

    /**
     * Gets all deployments projects from a repository.
     * 
     * @return list of deployments projects
     */
    public List<TreeNode> getDeploymentProjects() {
        return repositoryTreeState.getDeploymentRepository().getChildNodes();
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getFilterString() {
        return filterString;
    }

    public String getFolderName() {
        return null;
    }

    public String getVersionComment() {
        return "";
    }

    public String getNewProjectName() {
        // EPBDS-92 - clear newProject dialog every time
        return null;
    }

    /**
     * Gets number type property from a rules repository.
     * 
     * @param propName name of property
     * @return value of property
     */
    private String getNumberProperty(String propName) {
        Object prop = getProperty(propName);
        if (prop instanceof Double) {
            return String.valueOf(prop);
        } else {
            return null;
        }
    }

    public String getProjectName() {
        // EPBDS-92 - clear newProject dialog every time
        // return null;
        return projectName;
    }

    private ProjectVersion getProjectVersion() {
        AProject project = repositoryTreeState.getSelectedProject();

        if (project != null) {
            return project.getVersion();
        }
        return null;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        /*
         * Object dataBean = FacesUtils.getFacesVariable(
         * "#{repositoryTreeController.selected.dataBean}");
         */
        return properties;
    }

    /**
     * Gets property from a rules repository.
     * 
     * @param propName name of property
     * @return value of property
     */
    private Object getProperty(String propName) {
        Map<String, Object> props = getProps();
        if (props != null) {
            return props.get(propName);
        }
        return null;
    }

    /**
     * Gets all properties from a rules repository.
     * 
     * @return map of properties
     */
    private Map<String, Object> getProps() {
        RulesRepositoryArtefact dataBean = repositoryTreeState.getSelectedNode().getData();
        if (dataBean != null) {
            return dataBean.getProps();
        }
        return null;
    }

    /**
     * Gets UI name of property.
     * 
     * @param propName name of property
     * @return UI name of property
     */
    private String getPropUIName(String propName) {
        if (propName == null) {
            return StringUtils.EMPTY;
        }
        String propUIName = getPropUINames().get(propName);
        if (StringUtils.isBlank(propUIName)) {
            propUIName = propName;
        }
        return propUIName;
    }

    public Map<String, String> getPropUINames() {
        return repositoryArtefactPropsHolder.getProps();
    }

    public int getRevision() {
        ProjectVersion v = getProjectVersion();
        if (v != null) {
            return v.getRevision();
        }
        return 0;
    }

    /**
     * Gets all rules projects from a rule repository.
     * 
     * @return list of rules projects
     */
    public List<TreeNode> getRulesProjects() {
        return repositoryTreeState.getRulesRepository().getChildNodes();
    }

    public SelectItem[] getSelectedProjectVersions() {
        Collection<ProjectVersion> versions = repositoryTreeState.getSelectedNode().getVersions();

        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        for (ProjectVersion version : versions) {
            selectItems.add(new SelectItem(version.getVersionName()));
        }
        return selectItems.toArray(new SelectItem[selectItems.size()]);
    }

    public String getUploadFrom() {
        return uploadFrom;
    }

    public String getVersion() {
        return version;
    }

    public String openProject() {
        try {
            repositoryTreeState.getSelectedProject().open();
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = "Failed to open project.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
        return null;
    }

    public String openProjectVersion() {
        try {
            if (repositoryTreeState.getSelectedProject().isOpenedForEditing()) {
                repositoryTreeState.getSelectedProject().close();
            }

            repositoryTreeState.getSelectedProject().openVersion(new CommonVersionImpl(version));
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = "Failed to open project version.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
        return null;
    }

    public String openProjectVersion(String version) {
        this.version = version;
        openProjectVersion();

        return null;
    }

    public String refreshTree() {
        repositoryTreeState.invalidateTree();
        repositoryTreeState.invalidateSelection();
        return null;
    }

    public String selectDeploymentProject() {
        String projectName = FacesUtils.getRequestParameter("projectName");
        selectProject(projectName, repositoryTreeState.getDeploymentRepository());
        return null;
    }

    private void selectProject(String projectName, TreeRepository root) {
        for (TreeNode node : root.getChildNodes()) {
            if (node.getName().equals(projectName)) {
                repositoryTreeState.setSelectedNode(node);
                repositoryTreeState.refreshSelectedNode();
                break;
            }
        }
    }

    public String selectRulesProject() {
        String projectName = FacesUtils.getRequestParameter("projectName");
        selectProject(projectName, repositoryTreeState.getRulesRepository());
        return null;
    }

    public void setAttribute1(String attribute1) {
        setProperty(ArtefactProperties.PROP_ATTRIBUTE + 1, attribute1);
    }

    public void setAttribute10(Date attribute10) {
        setDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 10, attribute10);
    }

    public void setAttribute11(String attribute11) {
        setNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 11, attribute11);
    }

    public void setAttribute12(String attribute12) {
        setNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 12, attribute12);
    }

    public void setAttribute13(String attribute13) {
        setNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 13, attribute13);
    }

    public void setAttribute14(String attribute14) {
        setNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 14, attribute14);
    }

    public void setAttribute15(String attribute15) {
        setNumberProperty(ArtefactProperties.PROP_ATTRIBUTE + 15, attribute15);
    }

    public void setAttribute2(String attribute2) {
        setProperty(ArtefactProperties.PROP_ATTRIBUTE + 2, attribute2);
    }

    public void setAttribute3(String attribute3) {
        setProperty(ArtefactProperties.PROP_ATTRIBUTE + 3, attribute3);
    }

    public void setAttribute4(String attribute4) {
        setProperty(ArtefactProperties.PROP_ATTRIBUTE + 4, attribute4);
    }

    public void setAttribute5(String attribute5) {
        setProperty(ArtefactProperties.PROP_ATTRIBUTE + 5, attribute5);
    }

    public void setAttribute6(Date attribute6) {
        setDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 6, attribute6);
    }

    public void setAttribute7(Date attribute7) {
        setDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 7, attribute7);
    }

    public void setAttribute8(Date attribute8) {
        setDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 8, attribute8);
    }

    public void setAttribute9(Date attribute9) {
        setDateProperty(ArtefactProperties.PROP_ATTRIBUTE + 9, attribute9);
    }

    /**
     * Sets date type property to rules repository.
     * 
     * @param propName name of property
     * @param propValue value of property
     */
    public void setDateProperty(String propName, Date propValue) {
        if (!SPECIAL_DATE.equals(propValue)) {
            setProperty(propName, propValue);
        } else {
            FacesUtils.addErrorMessage("Specified " + getPropUIName(propName) + " value is not a valid date.");
        }
    }

    public void uploadListener(FileUploadEvent event) {
        UploadedFile file = event.getUploadedFile();
        uploadedFiles.add(file);

        this.setFileName(FilenameUtils.getName(file.getName()));

        if (fileName.indexOf(".") > -1) {
            this.setProjectName(fileName.substring(0, fileName.lastIndexOf(".")));
        } else {
            this.setProjectName(fileName);
        }
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public void setVersionComment(String versionComment) {
        try {
            repositoryTreeState.getSelectedNode().getData().setVersionComment(versionComment);
        } catch (PropertyException e) {
            log.error("Failed to set LOB!", e);
            FacesUtils.addErrorMessage("Can not set line of business.", e.getMessage());
        }
    }

    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName;
    }

    /**
     * Sets number type property to rules repository.
     * 
     * @param propName name of property
     * @param propValue value of property
     */
    public void setNumberProperty(String propName, String propValue) {
        Double numberValue = null;
        try {
            if (StringUtils.isNotBlank(propValue)) {
                numberValue = Double.valueOf(propValue);
            }
            setProperty(propName, numberValue);
        } catch (NumberFormatException e) {
            FacesUtils.addErrorMessage("Specified " + getPropUIName(propName) + " value is not a number.");
        }
    }

    public void setProjectName(String newProjectName) {
        projectName = newProjectName;
    }

    /**
     * Sets property to rules repository.
     * 
     * @param propName name of property
     * @param propValue value of property
     */
    private void setProperty(String propName, Object propValue) {
        try {
            Map<String, Object> props = getProps();
            if (props == null) {
                props = new HashMap<String, Object>();
            } else {
                props = new HashMap<String, Object>(props);
            }
            props.put(propName, propValue);
            repositoryTreeState.getSelectedNode().getData().setProps(props);
        } catch (PropertyException e) {
            String propUIName = getPropUIName(propName);
            log.error("Failed to set " + propUIName + "!", e);
            FacesUtils.addErrorMessage("Can not set " + propUIName + ".", e.getMessage());
        }
    }

    public void setRepositoryArtefactPropsHolder(RepositoryArtefactPropsHolder repositoryArtefactPropsHolder) {
        this.repositoryArtefactPropsHolder = repositoryArtefactPropsHolder;
    }

    public void setRepositoryTreeState(RepositoryTreeState repositoryTreeState) {
        this.repositoryTreeState = repositoryTreeState;
    }

    public void setUploadFrom(String uploadFrom) {
        this.uploadFrom = uploadFrom;
    }

    public void setUserWorkspace(UserWorkspace userWorkspace) {
        this.userWorkspace = userWorkspace;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String undeleteProject() {
        AProject project = repositoryTreeState.getSelectedProject();
        if (!project.isDeleted()) {
            FacesUtils.addErrorMessage("Cannot undelete project '" + project.getName() + "'.",
                "Project is not marked for deletion.");
            return null;
        }

        try {
            project.undelete();
            repositoryTreeState.refreshSelectedNode();
            resetStudioModel();
        } catch (ProjectException e) {
            String msg = "Cannot undelete project '" + project.getName() + "'.";
            log.error(msg, e);
            FacesUtils.addErrorMessage(msg, e.getMessage());
        }
        return null;
    }

    /**
     * Updates file (active node)
     * 
     * @return
     */
    public String updateFile() {
        String errorMessage = uploadAndUpdateFile();
        if (errorMessage == null) {
            resetStudioModel();
            FacesUtils.addInfoMessage(("File was successfully updated."));
        } else {
            FacesUtils.addErrorMessage(errorMessage, "Error occured during uploading file. " + errorMessage);
        }

        /* Clear the load form */
        clearForm();

        return null;
    }

    public String upload() {
        String errorMessage = uploadProject();
        if (errorMessage == null) {
            try {
                AProject createdProject = userWorkspace.getProject(projectName);
                repositoryTreeState.addRulesProjectToTree(createdProject);
                resetStudioModel();
            } catch (ProjectException e) {
                FacesUtils.addErrorMessage(e.getMessage());
            }
            FacesUtils.addInfoMessage("Project was uploaded successfully.");
        }

        /* Clear the load form */
        clearForm();

        return null;
    }

    public String createProjectWithFiles() {
        String errorMessage = createProject();
        if (errorMessage == null) {
            try {
                AProject createdProject = userWorkspace.getProject(projectName);
                repositoryTreeState.addRulesProjectToTree(createdProject);
                resetStudioModel();
            } catch (ProjectException e) {
                FacesUtils.addErrorMessage(e.getMessage());
            }
            FacesUtils.addInfoMessage("Project was created successfully.");
        }

        /* Clear the load form */
        clearForm();

        return null;
    }

    private String createProject() {
        String errorMessage = null;

        if (StringUtils.isNotBlank(projectName)) {
            if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
                ProjectUploader projectUploader = new ProjectUploader(uploadedFiles,
                    projectName,
                    userWorkspace,
                    zipFilter);
                errorMessage = projectUploader.uploadProject();
            } else {
                errorMessage = "There are no uploaded files.";
            }
        } else {
            errorMessage = "Project name must not be empty.";
        }

        if (errorMessage == null) {
            clearUploadedFiles();
        } else {
            clearUploadedFiles();
            FacesUtils.addErrorMessage(errorMessage);
        }

        return errorMessage;
    }

    private void clearForm() {
        this.setFileName(null);
        this.setProjectName(null);
        this.uploadedFiles.clear();
    }

    private String uploadAndAddFile() {
        if (!NameChecker.checkName(fileName)) {
            return "File name '" + fileName + "' is invalid. " + NameChecker.BAD_NAME_MSG;
        }

        try {
            AProjectFolder node = (AProjectFolder) repositoryTreeState.getSelectedNode().getData();

            AProjectResource addedFileResource = node.addResource(fileName, getLastUploadedFile().getInputStream());

            repositoryTreeState.addNodeToTree(repositoryTreeState.getSelectedNode(), addedFileResource);
            clearUploadedFiles();
        } catch (Exception e) {
            /*
             * If an error is IOException then an error will not be written to
             * the console. This error throw when upload file is exist in the
             * upload folder
             */
            if (!e.getCause().getClass().equals(java.io.IOException.class)) {
                log.error("Error adding file to user workspace.", e);
            }

            return e.getMessage();
        }

        return null;
    }

    private String uploadAndUpdateFile() {
        if (getLastUploadedFile() == null) {
            return "There are no uploaded files.";
        }

        try {
            AProjectResource node = (AProjectResource) repositoryTreeState.getSelectedNode().getData();
            node.setContent(getLastUploadedFile().getInputStream());

            clearUploadedFiles();
        } catch (Exception e) {
            log.error("Error updating file in user workspace.", e);
            return e.getMessage();
        }

        return null;
    }

    private UploadedFile getLastUploadedFile() {
        if (!uploadedFiles.isEmpty()) {
            return uploadedFiles.get(uploadedFiles.size() - 1);
        }
        return null;
    }

    private String uploadProject() {
        String errorMessage = null;

        if (StringUtils.isNotBlank(projectName)) {
            UploadedFile uploadedItem = getLastUploadedFile();
            if (uploadedItem != null) {
                ProjectUploader projectUploader = new ProjectUploader(uploadedItem,
                    projectName,
                    userWorkspace,
                    zipFilter);
                errorMessage = projectUploader.uploadProject();
            } else {
                errorMessage = "There are no uploaded files.";
            }
        } else {
            errorMessage = "Project name must not be empty.";
        }

        if (errorMessage == null) {
            clearUploadedFiles();
        } else {
            FacesUtils.addErrorMessage(errorMessage);
        }

        return errorMessage;
    }

    private void clearUploadedFiles() {
        uploadedFiles.clear();
    }

    public String getNewProjectTemplate() {
        return newProjectTemplate;
    }

    public void setNewProjectTemplate(String newProjectTemplate) {
        this.newProjectTemplate = newProjectTemplate;
    }

    public String[] getProjectTemplates(String category) {
        List<String> templateNames = new ArrayList<String>();
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        Resource[] templates = null;

        try {
            // JAR file
            templates = resourceResolver.getResources(TEMPLATES_PATH + category + "/*/");
            if (templates.length == 0) {
                // File System
                templates = resourceResolver.getResources(TEMPLATES_PATH + category + "/*");
            }

            for (Resource resource : templates) {
                if (!ResourceUtils.isFileURL(resource.getURL())) {
                    // JAR file
                    // In most of cases protocol is "jar", but in case of IBM WebSphere protocol is "wsjar"
                    String templateUrl = URLDecoder.decode(resource.getURL().getPath(), "UTF8");
                    String[] templateParsed = templateUrl.split("/");
                    templateNames.add(templateParsed[templateParsed.length - 1]);
                } else {
                    // File System
                    templateNames.add(resource.getFilename());
                }
            }

        } catch (Exception e) {
            log.error("Failed to get project templates", e);
        }

        return templateNames.isEmpty() ? new String[0] : templateNames.toArray(new String[0]);
    }

    private ProjectFile[] getProjectTemplateFiles(String url) {
        List<ProjectFile> templateFiles = new ArrayList<ProjectFile>();
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] templates = resourceResolver.getResources(url + "/*");
            if (templates.length == 0) {
                resourceResolver = new EncodedJarPathResourcePatternResolver();
                templates = resourceResolver.getResources(url + "/*");
            }
            for (Resource resource : templates) {
                templateFiles.add(new ProjectFile(resource.getFilename(), resource.getInputStream()));
            }
        } catch (Exception e) {
            log.error("Failed to get project template: " + url, e);
        }

        return templateFiles.isEmpty() ? new ProjectFile[0] : templateFiles.toArray(new ProjectFile[0]);
    }

    public boolean getCanDelete() {
        return isGranted(PRIVILEGE_DELETE_PROJECTS);
    }

    public boolean getCanUnlock() {
        return isGranted(PRIVILEGE_UNLOCK_PROJECTS);
    }

    public boolean getCanUnlockDeployment() {
        return isGranted(PRIVILEGE_UNLOCK_DEPLOYMENT);
    }

    public boolean getCanDeleteDeployment() {
        return isGranted(PRIVILEGE_DELETE_DEPLOYMENT);
    }

    private void resetStudioModel() {
        studio.reset(ReloadType.FORCED);
    }
}